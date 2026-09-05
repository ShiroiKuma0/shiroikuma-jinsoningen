/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the 保存復元 automation entry point.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.jinsoningen.automation

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.looker.droidify.jinsoningen.JinsoningenBackup

/**
 * The exported receiver 白い熊 自由作業盤 fires at, carrying the three contract actions.
 *
 * It does **no work**: it checks the gate, answers `LIST_CATEGORIES` inline (instant), hands
 * `EXPORT_STATE` to a foreground service and returns at once, and signals a running export for
 * `CANCEL_EXPORT`. A manifest receiver must reach `finish()` inside Android's broadcast window —
 * running an export here is what gets an app ANR'd and killed mid-write.
 *
 * No `android:permission` on the receiver, and since contract v2 no compulsory token either: this
 * is deliberately the **unauthenticated** half of the surface. It only ever writes where it was
 * told to and reports what it did. Everything that moves data through a caller-supplied descriptor
 * lives behind [AutomationProvider], which knows who is calling. `import` is there and only there.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN)

        when (action) {
            "${app.packageName}$ACTION_LIST_CATEGORIES" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val replyId = intent.getStringExtra(EXTRA_REPLY_ID) ?: return

                val result = AutomationAuth.refuse(app, token) ?: ("OK:" + categoryLines())
                reply(app, replyAction, replyPackage, replyId, result)
            }

            "${app.packageName}$ACTION_EXPORT_STATE" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val replyId = intent.getStringExtra(EXTRA_REPLY_ID) ?: return

                AutomationAuth.refuse(app, token)?.let { refusal ->
                    reply(app, replyAction, replyPackage, replyId, refusal)
                    return
                }

                // Validate `items` here, where an error is still cheap to report.
                val items = intent.getStringExtra(EXTRA_ITEMS).orEmpty()
                val unknown = items.split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() && JinsoningenBackup.Cat.ofId(it) == null }
                if (unknown.isNotEmpty()) {
                    reply(
                        app,
                        replyAction,
                        replyPackage,
                        replyId,
                        "ERROR:unknown category in items: ${unknown.joinToString(",")}",
                    )
                    return
                }

                // A broadcast is a BACKGROUND start on API 31+. Without a foreground-start
                // allowance — which comes from recent interaction — this throws
                // ForegroundServiceStartNotAllowedException, and an exception escaping onReceive
                // kills the process. Open the app and run a backup by hand and it always works;
                // leave it cold and run the unattended batch, or restore onto a clean phone, and it
                // throws. The failure is inversely correlated with how closely anyone is watching,
                // which is why it survived the rollout (天気, 2026-09-04).
                //
                // Catching alone would only convert the crash into a SILENT no-export: the caller
                // waits out its whole timeout and reports "no response", indistinguishable from an
                // app that never implemented the contract. The ERROR: reply is what makes it
                // diagnosable, and it is rendered straight into the caller's failure dialog.
                try {
                    ContextCompat.startForegroundService(
                        app,
                        Intent(app, StateExportService::class.java).apply {
                            putExtra(EXTRA_PATH, intent.getStringExtra(EXTRA_PATH))
                            putExtra(EXTRA_ITEMS, items)
                            putExtra(
                                EXTRA_PROGRESS_ACTION,
                                intent.getStringExtra(EXTRA_PROGRESS_ACTION),
                            )
                            putExtra(EXTRA_REPLY_ACTION, replyAction)
                            putExtra(EXTRA_REPLY_PACKAGE, replyPackage)
                            putExtra(EXTRA_REPLY_ID, replyId)
                        },
                    )
                } catch (exception: Exception) {
                    reply(app, replyAction, replyPackage, replyId, wireError(app, exception))
                }
            }

            "${app.packageName}$ACTION_CANCEL_EXPORT" -> {
                // Fire-and-forget, and a silent no-op when nothing is running.
                if (AutomationAuth.refuse(app, token) != null) return
                StateExportService.requestCancel(intent.getStringExtra(EXTRA_REPLY_ID))
            }
        }
    }

    /**
     * `id<TAB>label`, one per line, with the optional third (parent) and fourth (default) fields.
     * Everything here is authored rather than derived — imported font files included, since the
     * app cannot recreate one — so every category ships `on`.
     */
    private fun categoryLines(): String =
        JinsoningenBackup.Cat.entries.joinToString("\n") { cat ->
            if (cat.onByDefault) "${cat.id}\t${cat.label}" else "${cat.id}\t${cat.label}\t\toff"
        }

    companion object {
        /**
         * A failed start, turned into the one line the wire format carries.
         *
         * Returns the **keyed** `ERROR:no-foreground-start` only when a battery-optimisation exemption
         * would actually fix it — see [exemptionWouldFix]. Everything else keeps a descriptive line,
         * because the caller draws its 「電池最適化を除外」 button from the key alone.
         *
         * `result` is **one line** — `LIST_CATEGORIES` answers are newline-delimited, so a message with
         * a newline in it would corrupt a caller's parse rather than merely read badly. And the message
         * can be null, where the class name at least names the failure instead of saying `ERROR:null`.
         */
        fun wireError(context: Context, exception: Exception): String {
            if (exemptionWouldFix(context, exception)) return "ERROR:no-foreground-start"
            val detail = exception.message?.takeIf { it.isNotBlank() }
                ?: exception.javaClass.simpleName
            return "ERROR:" + detail.replace('\n', ' ').replace('\r', ' ').trim()
        }

        /**
         * Whether the exemption the caller's button grants is genuinely the repair for this failure.
         *
         * **A refused foreground start is not single-cause the way a missing All-Files grant is**
         * (handyrss, 2026-09-04). It can also be a missing `FOREGROUND_SERVICE` permission, or — on
         * EMUI — アプリ起動管理 set to 自動管理, which **no app can change for itself**. So the key is
         * earned, not assumed: the easy implementation (catch anything, always emit the key) is exactly
         * the one that manufactures a button which cannot fix the fault, and a row offering a button
         * that changes nothing is worse than a row naming the exception.
         *
         * Two conditions, and the second is the one that does the work: it must be the platform's own
         * refusal, **and** this app must not already hold the exemption. If we are already exempt and
         * the start was still refused, the exemption is demonstrably not the fix.
         */
        private fun exemptionWouldFix(context: Context, exception: Exception): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            if (exception !is ForegroundServiceStartNotAllowedException) return false
            val power = context.getSystemService<PowerManager>() ?: return false
            return !power.isIgnoringBatteryOptimizations(context.packageName)
        }

        const val ACTION_EXPORT_STATE = ".action.EXPORT_STATE"
        const val ACTION_LIST_CATEGORIES = ".action.LIST_CATEGORIES"
        const val ACTION_CANCEL_EXPORT = ".action.CANCEL_EXPORT"

        const val EXTRA_TOKEN = "token"
        const val EXTRA_PATH = "path"
        const val EXTRA_ITEMS = "items"
        const val EXTRA_PROGRESS_ACTION = "progress_action"
        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"
        const val EXTRA_REPLY_ID = "reply_id"

        /**
         * The reply is a **fresh broadcast** — never a Binder. EMUI will not reliably carry a
         * ResultReceiver/PendingIntent/Messenger into another app's manifest receiver, and it
         * severs the ordered-broadcast result channel between third-party apps.
         * FLAG_INCLUDE_STOPPED_PACKAGES matters: without it a backgrounded caller never hears us.
         */
        fun reply(
            context: Context,
            replyAction: String,
            replyPackage: String,
            replyId: String,
            result: String,
        ) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("result", result)
                },
            )
        }
    }
}
