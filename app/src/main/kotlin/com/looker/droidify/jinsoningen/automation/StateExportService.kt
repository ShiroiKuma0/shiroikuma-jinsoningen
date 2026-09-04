/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: where the headless 保存復元 export actually runs.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.jinsoningen.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.looker.droidify.R
import com.looker.droidify.jinsoningen.JinsoningenBackup
import com.looker.droidify.jinsoningen.JinsoningenUiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the export off the broadcast window, reports progress with real counts, and sends exactly
 * one terminal reply.
 *
 * A manifest receiver — `goAsync()` or not — must finish inside ~10 s foreground / ~60 s
 * background or the system ANRs and kills the process mid-write, leaving a half-written archive
 * and a caller waiting for a reply that can never come. Hence this service.
 */
class StateExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MUST happen inside 5 s of the service starting, or the system kills us for it.
        startForeground(NOTIFICATION_ID, buildNotification())

        val request = intent ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val replyAction = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_ACTION)
        val replyPackage = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_PACKAGE)
        val replyId = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_ID)
        if (replyAction == null || replyPackage == null || replyId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // §1 forbids two exports at once; a second request is refused rather than interleaved.
        if (!running.compareAndSet(false, true)) {
            StateExportReceiver.reply(
                applicationContext,
                replyAction,
                replyPackage,
                replyId,
                "ERROR:export already running",
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        cancelled = false
        runningReplyId = replyId

        scope.launch {
            val replied = AtomicBoolean(false)
            fun reply(result: String) {
                if (!replied.compareAndSet(false, true)) return
                StateExportReceiver.reply(
                    applicationContext,
                    replyAction,
                    replyPackage,
                    replyId,
                    result,
                )
            }

            // EMUI dozes the CPU with the screen off; a partial wakelock keeps a longer export
            // alive. Released in the finally, always.
            val wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.apply { setReferenceCounted(false) }

            try {
                wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
                runExport(request, replyId, replyPackage, ::reply)
            } catch (exception: Exception) {
                reply("ERROR:${exception.message ?: exception.javaClass.simpleName}")
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                runningReplyId = null
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runExport(
        request: Intent,
        replyId: String,
        replyPackage: String,
        reply: (String) -> Unit,
    ) {
        val progressAction = request.getStringExtra(StateExportReceiver.EXTRA_PROGRESS_ACTION)
        val itemsExtra = request.getStringExtra(StateExportReceiver.EXTRA_ITEMS).orEmpty()

        // `items` absent means OUR DEFAULT SET, not everything.
        val categories = itemsExtra.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { JinsoningenBackup.Cat.ofId(it) }
            .toSet()
            .ifEmpty { JinsoningenBackup.Cat.defaults }

        val bytes = ByteArrayOutputStream()
        val written = JinsoningenBackup.writeZip(
            context = applicationContext,
            categories = categories,
            out = bytes,
            onProgress = { cat, position, total ->
                // The one sender, shared with the data door — see AutomationProgress.
                AutomationProgress.send(
                    context = this@StateExportService,
                    progressAction = progressAction,
                    replyPackage = replyPackage,
                    correlationId = replyId,
                    cat = cat,
                    position = position,
                    total = total,
                )
            },
            isCancelled = { cancelled },
        )

        if (cancelled) {
            reply("ERROR:cancelled")
            return
        }

        val payload = bytes.toByteArray()
        val pathOverride = request.getStringExtra(StateExportReceiver.EXTRA_PATH)
        val configuredDir = JinsoningenUiConfig(applicationContext).exportDir

        val absolutePath = when {
            !pathOverride.isNullOrBlank() -> {
                // Declaring MANAGE_EXTERNAL_STORAGE is not holding it — check, don't discover it
                // by failing: `ERROR:no-storage-access` is what 自由作業盤 keys on to offer the
                // "grant" button on the failed row.
                if (!isExternalStorageManager()) {
                    reply("ERROR:no-storage-access")
                    return
                }
                writeToDirectory(File(pathOverride), payload)
            }

            configuredDir.isNotBlank() -> runCatching {
                val name = JinsoningenBackup.writeToTree(
                    applicationContext,
                    Uri.parse(configuredDir),
                    payload,
                )
                "$configuredDir/$name"
            }.getOrNull()

            else -> {
                reply("ERROR:no-directory")
                return
            }
        } ?: run {
            reply("ERROR:the backup folder could not be written to")
            return
        }

        val size = payload.size.toLong()
        reply(
            "OK:$absolutePath|$size|${JinsoningenBackup.humanSize(size)}|${written.size} categories",
        )
    }

    private fun isExternalStorageManager(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /**
     * Atomic write to a plain directory: `.part` first, renamed only once the archive is whole,
     * and deleted on any failure — a cancelled or killed export leaves the folder exactly as it
     * found it.
     */
    private fun writeToDirectory(dir: File, payload: ByteArray): String? {
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, JinsoningenBackup.exportFileName())
        val part = File(dir, "${target.name}.part")
        return runCatching {
            part.writeBytes(payload)
            if (cancelled || !part.renameTo(target)) {
                part.delete()
                null
            } else {
                target.absolutePath
            }
        }.getOrElse {
            part.delete()
            null
        }
    }

    private fun buildNotification(): Notification {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Automation export", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.application_name))
            .setContentText("Exporting settings…")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        runningReplyId = null
        running.set(false)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jinsoningen_automation_export"
        private const val NOTIFICATION_ID = 91_001
        private const val WAKELOCK_TAG = "shiroikuma-jinsoningen:automation-export"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /**
         * Process-local, never persisted: a persisted "export in progress" flag wedges the app
         * for good after a single crash.
         */
        private val running = AtomicBoolean(false)

        @Volatile
        private var cancelled = false

        @Volatile
        private var runningReplyId: String? = null

        /**
         * Signals the running export to unwind at its next category boundary. A cancel for a run
         * that is not the one in flight — or that arrives when nothing runs at all — is a silent
         * no-op, exactly as the contract requires.
         */
        fun requestCancel(replyId: String?) {
            if (!running.get()) return
            if (replyId != null && replyId != runningReplyId) return
            cancelled = true
        }
    }
}
