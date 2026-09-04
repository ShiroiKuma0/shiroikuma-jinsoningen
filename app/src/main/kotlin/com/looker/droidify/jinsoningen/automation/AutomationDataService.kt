/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: where a 保存復元 data export or import actually
 * runs — contract v2 §2a.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.jinsoningen.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.looker.droidify.R
import com.looker.droidify.jinsoningen.JinsoningenBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored (応用管理, 2026-09-04).
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and the
 * caller cannot checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * ## Two invariants this method exists to keep
     *
     * **`startForeground` comes before every early return.** Once `startForegroundService` has been
     * called the platform *requires* the notification, whatever this method then decides, and
     * enforces it by killing the process with `ForegroundServiceDidNotStartInTimeException`. A
     * caller retrying with a stale job id must be ignored, not fatal to the app it is retrying
     * against — which is the branch 応用管理 is most likely to hit (猫缶, 2026-09-04).
     *
     * **The descriptor has exactly one owner on every path.** It is drained from [HANDOVER] here
     * and closed here unless the coroutine took it, tracked by a single `handedOff` flag rather
     * than a guard per failure: `startForeground` throwing is only one of several ways to leave the
     * window between the drain and the launch.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) ?: false
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val fd = jobId?.let { HANDOVER.remove(it) }
        var handedOff = false
        try {
            // MUST happen inside 5 s of the service starting, and MUST happen even on the paths
            // that turn straight round and stop.
            startForeground(NOTIFICATION_ID, notification(importing))
            if (jobId == null || fd == null) return stop(startId)
            handedOff = dispatch(intent, startId, jobId, fd, importing)
            return START_NOT_STICKY
        } finally {
            if (!handedOff) runCatching { fd?.close() }
        }
    }

    /** @return true once the coroutine owns [fd] — see [onStartCommand]'s `handedOff`. */
    private fun dispatch(
        intent: Intent,
        startId: Int,
        jobId: String,
        fd: ParcelFileDescriptor,
        importing: Boolean,
    ): Boolean {
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)
        val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast
            // contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            AutomationJobs.finish(jobId)
            if (replyAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    // Without this a caller that has been backgrounded never hears the answer, and
                    // on a clean phone the caller may not have been launched at all.
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(AutomationProvider.KEY_RESULT, result)
                },
            )
        }

        scope.launch {
            // From here the coroutine owns the descriptor and closes it in its own finally.
            // EMUI dozes the CPU with the screen off and force-releases a partial wakelock it does
            // not like; taking one is still what keeps a longer run alive. Released in the finally.
            val wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.apply { setReferenceCounted(false) }
            try {
                wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
                fd.use { open ->
                    if (importing) {
                        runImport(open, ::reply)
                    } else {
                        runExport(jobId, open, items, progressAction, replyPackage, ::reply)
                    }
                }
            } catch (throwable: Throwable) {
                reply("ERROR:${throwable.message ?: throwable.javaClass.simpleName}")
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return true
    }

    /**
     * Streams the ZIP straight into the caller's descriptor. Nothing is staged on our side: the
     * caller owns the destination, and buffering the whole archive first would only add a copy.
     */
    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progressAction: String?,
        replyPackage: String?,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items)
            ?: run { reply("ERROR:unknown category in items: $items"); return }

        var written = 0L
        val done = ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            // Counted as it goes rather than stat'ed afterwards: the caller owns the file and we
            // may not be able to see it at all — it can be an anonymous pipe or a descriptor into a
            // directory this app cannot list.
            val counting = object : OutputStream() {
                override fun write(b: Int) {
                    out.write(b)
                    written++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len)
                    written += len
                }
            }
            JinsoningenBackup.writeZip(
                context = applicationContext,
                categories = cats,
                out = counting,
                onProgress = { cat, position, total ->
                    // §3 applies here too: an app that goes quiet for two minutes is presumed dead
                    // and its slot failed. Same sender as the §1 path, with the job id as the
                    // correlation id.
                    AutomationProgress.send(
                        context = this@AutomationDataService,
                        progressAction = progressAction,
                        replyPackage = replyPackage,
                        correlationId = jobId,
                        cat = cat,
                        position = position,
                        total = total,
                        bytes = written,
                    )
                },
                isCancelled = { AutomationJobs.isCancelled(jobId) },
            )
        }

        if (AutomationJobs.isCancelled(jobId)) reply("ERROR:cancelled")
        else reply("OK:$written|${done.size} categories")
    }

    /**
     * Read the whole archive before touching anything — **to disk, not into a byte array**.
     *
     * Reading it all first is the guarantee: a partial read that failed halfway would import half
     * an archive, and a half-restored app is worse than one that refused. But an archive of ours is
     * as large as the imported fonts inside it, which nothing bounds, so the bound belongs on disk
     * rather than in RAM (自由作業盤 2026-09-04). The spool lives in the app's own cache directory
     * and is deleted in a `finally` — it is a complete copy of this app's state and has no business
     * outliving the restore that asked for it.
     */
    private suspend fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val spool = File(cacheDir, SPOOL_NAME)
        try {
            val size = ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                spool.outputStream().use { output -> input.copyTo(output) }
            }
            if (size == 0L) {
                reply("ERROR:empty archive")
                return
            }
            // Every category the archive actually carries, not every category we know about: asking
            // for one the archive lacks is how a restore ends up reporting success over nothing.
            val present = runCatching { JinsoningenBackup.categoriesIn { spool.inputStream() } }
                .getOrElse { reply("ERROR:archive unreadable"); return }
            if (present.isEmpty()) {
                reply("ERROR:archive carries no categories")
                return
            }
            val restored = JinsoningenBackup.restore(
                context = applicationContext,
                categories = present,
                // The force-stop that protects this import would also discard a queued
                // SharedPreferences write, so the UI knobs go down synchronously before we reply.
                durable = true,
            ) { spool.inputStream() }
            // The caller force-stops us straight after this. That is deliberate and belongs on its
            // side: a running process writes its cached SharedPreferences back out at orderly
            // shutdown and silently undoes the import that just happened (応用管理 paid for this
            // one already).
            reply("OK:$restored restored")
        } finally {
            spool.delete()
        }
    }

    /** `items` absent means OUR DEFAULT SET, not everything. An unknown id refuses the whole run. */
    private fun resolve(items: String?): Set<JinsoningenBackup.Cat>? {
        if (items.isNullOrBlank()) return JinsoningenBackup.Cat.defaults
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { JinsoningenBackup.Cat.ofId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    private fun notification(importing: Boolean): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService<NotificationManager>()?.createNotificationChannel(
                NotificationChannel(CHANNEL, "自動化データ", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.application_name))
            .setContentText(if (importing) "データを戻しています…" else "データを書き出しています…")
            .setOngoing(true)
            .build()
    }

    /** Turn round and stop — from a state that is already foreground, as the platform demands. */
    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "jinsoningen_automation_data"
        private const val NOTIFICATION_ID = 91_002
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"
        private const val SPOOL_NAME = "jinsoningen-automation-import.zip"
        private const val WAKELOCK_TAG = "shiroikuma-jinsoningen:automation-data"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ) {
            HANDOVER[jobId] = fd
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutomationDataService::class.java).apply {
                    putExtra(EXTRA_JOB, jobId)
                    putExtra(EXTRA_IMPORTING, importing)
                    putExtra(
                        AutomationProvider.KEY_ITEMS,
                        extras?.getString(AutomationProvider.KEY_ITEMS),
                    )
                    putExtra(
                        AutomationProvider.KEY_REPLY_ACTION,
                        extras?.getString(AutomationProvider.KEY_REPLY_ACTION),
                    )
                    putExtra(
                        AutomationProvider.KEY_REPLY_PACKAGE,
                        extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE),
                    )
                    putExtra(
                        AutomationProvider.KEY_PROGRESS_ACTION,
                        extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION),
                    )
                },
            )
        }

        /**
         * Drop a descriptor that was handed over for a service that never started.
         *
         * Without this an FGS start the system refuses — and on Android 12+ it may — would leave
         * the caller's file held open forever by a map nothing will ever read.
         */
        fun discard(jobId: String) {
            HANDOVER.remove(jobId)
        }
    }
}
