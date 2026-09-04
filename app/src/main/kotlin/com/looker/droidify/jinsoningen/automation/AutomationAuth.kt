/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the gate for the 保存復元 automation contract.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.jinsoningen.automation

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The external-automation gate: a master switch, and a token that is now OPTIONAL.
 *
 * ## What changed in contract v2 (白い熊, 2026-09-04)
 *
 * v1 shipped this app **closed**: [enabled] defaulted to false and a caller also had to present a
 * 48-character secret 白い熊 had pasted from here into the caller's settings. That is the wrong
 * shape for where this is going — **a pasted secret cannot survive a wipe**, and the case the
 * family now exists to serve is 白い熊 応用管理 restoring apps *and their data* onto a clean phone,
 * where nothing has been configured and nobody has pasted anything. A gate that only works once
 * the phone is already set up is no gate for setting the phone up.
 *
 * So [enabled] defaults to **true**, and [requireToken] is a new switch defaulting to **false**.
 * The token still exists, still regenerates, still never leaves the phone — it is opt-in now.
 *
 * ## Idempotent about the token — required, not a nicety
 *
 * A caller that sends a token to an app not asking for one is **served, not refused**. Tokens live
 * in task arguments and workspace variables that outlive the setting they were pasted for, so a
 * caller may still send one because it was configured last year or because another app on the
 * batch does want one. Refusing it would turn "白い熊 turned a switch off" into "half the batch
 * mysteriously fails", which is precisely the friction the switch exists to remove.
 *
 * ## Device-local by design
 *
 * These live in their **own** preferences file, which is deliberately not part of any backup
 * category — a token must never travel inside an export ZIP.
 */
object AutomationAuth {

    private const val PREFS = "jinsoningen_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether this app answers automation at all. **Default true** since 2026-09-04.
     *
     * Kept as a switch rather than removed: it is the only way to close this one app off, and a
     * feature that can be turned on but never off is one 白い熊 cannot retreat from.
     */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /**
     * **`commit()`, never `apply()` — this gate fails OPEN.**
     *
     * v2 flipped this key's default from false to **true**, so a write that never reaches disk does
     * not fall back to "off": it falls back to **ON**. And 応用管理 force-stops an app the instant it
     * replies to an import, with `Process.killProcess` — a `SIGKILL`, which leaves an in-flight
     * `apply()` nowhere to land. Turning an app off is the one action 白い熊 has for shutting a
     * sister app out, and it is the action most likely to be running near a force-stop; losing it
     * silently reopens the door. Three tiny, infrequent writes: synchronous is the right trade for
     * every one of them (辞書, 2026-09-04).
     */
    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit(commit = true) { putBoolean(KEY_ENABLED, value) }

    /** Whether a caller must present [token]. **Default false** — the token is opt-in now. */
    fun requireToken(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    /** `commit()`: a lost write here leaves the door not asking for the token 白い熊 just switched
     *  on — the same fail-open as [setEnabled]. */
    fun setRequireToken(context: Context, value: Boolean) =
        prefs(context).edit(commit = true) { putBoolean(KEY_REQUIRE_TOKEN, value) }

    /** The token, minted on first read so the row is never empty. */
    fun token(context: Context): String {
        val stored = prefs(context).getString(KEY_TOKEN, null)
        if (!stored.isNullOrBlank()) return stored
        return regenerate(context)
    }

    fun regenerate(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        // `commit()`: the worst of the three to lose, because 白い熊 may already have pasted this
        // value into a caller — and nothing surfaces it. The caller simply starts failing
        // "bad token", or the lazy mint on the next read hands out a different secret again.
        prefs(context).edit(commit = true) { putString(KEY_TOKEN, token) }
        return token
    }

    /** Constant-time compare — a token check must not leak its answer through timing. */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /**
     * The whole gate, in the one place every entry point asks.
     *
     * Returns null to proceed, or the exact `ERROR:` string to answer with. Written as one function
     * so no receiver, provider or service can implement the two checks in a subtly different order —
     * which is how "disabled" and "bad token" drift apart across forty-two apps.
     *
     * **A token supplied to an app that does not require one is IGNORED, never an error.**
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
