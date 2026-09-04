/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the ONE 保存復元 progress broadcast, for both the
 * §1 receiver path and the §2a data door.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.jinsoningen.automation

import android.content.Context
import android.content.Intent
import com.looker.droidify.R
import com.looker.droidify.jinsoningen.JinsoningenBackup

/**
 * Progress with **real numbers, never a percentage** — sent by whichever of the two export paths is
 * running.
 *
 * One sender rather than one per service, deliberately: the two paths differ only in what they call
 * the correlation id, and a second copy is how the `item` extra ends up on one and not the other
 * (自由作業盤 2026-09-04, on the data door having been written with no progress at all — an app
 * silent for two minutes is presumed dead and its slot failed).
 *
 * [correlationId] is the `reply_id` on the §1 path and the `job_id` on the data door; it goes out
 * as **both** extras so a caller reads whichever it is waiting on without either path guessing.
 */
object AutomationProgress {

    fun send(
        context: Context,
        progressAction: String?,
        replyPackage: String?,
        correlationId: String,
        cat: JinsoningenBackup.Cat,
        position: Int,
        total: Int,
        bytes: Long? = null,
    ) {
        if (progressAction.isNullOrBlank() || replyPackage.isNullOrBlank()) return
        context.sendBroadcast(
            Intent(progressAction).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(StateExportReceiver.EXTRA_REPLY_ID, correlationId)
                putExtra(AutomationProvider.KEY_JOB_ID, correlationId)
                putExtra("app", context.getString(R.string.application_name))
                // `item` is what moves the panel's highlight — it cannot be worked out from
                // `current`, which is whatever we happen to be counting at the time.
                putExtra("item", cat.id)
                putExtra("text", "区分 $position/$total — ${cat.label}")
                // When counting categories, `current` is the POSITION of the one being written,
                // not the number finished.
                putExtra("current", position.toLong())
                putExtra("total", total.toLong())
                putExtra("unit", "区分")
                // The second counter, sent only where it is actually known: the data door counts
                // bytes into the caller's descriptor, the §1 path does not know its size until the
                // archive is closed.
                bytes?.let { putExtra("bytes", it) }
            },
        )
    }
}
