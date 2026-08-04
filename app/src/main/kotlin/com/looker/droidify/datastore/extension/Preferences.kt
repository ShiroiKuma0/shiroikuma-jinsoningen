package com.looker.droidify.datastore.extension

import android.content.Context
import android.content.res.Configuration
import com.looker.droidify.datastore.model.SortOrder
import com.looker.droidify.datastore.model.Theme
import com.looker.droidify.datastore.model.Theme.AMOLED
import com.looker.droidify.datastore.model.Theme.DARK
import com.looker.droidify.datastore.model.Theme.LIGHT
import com.looker.droidify.datastore.model.Theme.SYSTEM
import com.looker.droidify.datastore.model.Theme.SYSTEM_BLACK
import com.looker.droidify.utility.common.SdkCheck
import com.looker.droidify.R.string as stringRes
import com.looker.droidify.R.style as styleRes

val Configuration.isNightMode: Boolean
    get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

fun Configuration.isDarkTheme(theme: Theme): Boolean = when (theme) {
    LIGHT -> false
    DARK, AMOLED -> true
    SYSTEM, SYSTEM_BLACK -> isNightMode
}

fun Configuration.isAmoledTheme(theme: Theme): Boolean = when (theme) {
    AMOLED -> true
    SYSTEM_BLACK -> isNightMode
    else -> false
}

/**
 * shiroikuma fork: **light means upstream's light, dark means the house black-yellow.**
 *
 * This is the call that decides what `MainActivity.setTheme` applies, and it runs *after* the
 * manifest theme — so the fork has to divert it, or the View shell keeps Droid-ify's green palette
 * whatever the launcher theme says. `Theme.Main.Jinsoningen` inherits `Theme.Main.Amoled`, so
 * upstream's widget styles and text appearances still come through; only the colours are ours.
 *
 * Picking **Light** is a real escape hatch (白い熊, 2026-08-04): it hands back upstream's own light
 * style untouched. It has to be honoured beyond this function too — see
 * [com.looker.droidify.jinsoningen.JinsoningenUiState.houseThemeActive], which the Compose theme,
 * the View tinter and the patched attribute lookup all read, so nothing repaints stock light back
 * to black behind the picker's back.
 *
 * Upstream's own resolution is preserved verbatim in [stockThemeRes] and still used for the light
 * outcomes, so a rebase that touches it stays a clean merge.
 */
fun Configuration.getThemeRes(theme: Theme, dynamicTheme: Boolean): Int =
    if (isLightTheme(theme)) {
        stockThemeRes(theme, dynamicTheme)
    } else {
        styleRes.Theme_Main_Jinsoningen
    }

/**
 * True when the choice means "light": LIGHT always, and the two SYSTEM options when the system is
 * not in night mode — which is what following the system is supposed to mean. The inverse of
 * upstream's own [isDarkTheme], so the two can never disagree.
 */
fun Configuration.isLightTheme(theme: Theme): Boolean = !isDarkTheme(theme)

fun Configuration.stockThemeRes(theme: Theme, dynamicTheme: Boolean): Int =
    if (SdkCheck.isSnowCake && dynamicTheme) {
        when (theme) {
            SYSTEM -> if (isNightMode) styleRes.Theme_Main_DynamicDark else styleRes.Theme_Main_DynamicLight
            SYSTEM_BLACK -> if (isNightMode) styleRes.Theme_Main_DynamicAmoled else styleRes.Theme_Main_DynamicLight
            LIGHT -> styleRes.Theme_Main_DynamicLight
            DARK -> styleRes.Theme_Main_DynamicDark
            AMOLED -> styleRes.Theme_Main_DynamicAmoled
        }
    } else {
        when (theme) {
            SYSTEM -> if (isNightMode) styleRes.Theme_Main_Dark else styleRes.Theme_Main_Light
            SYSTEM_BLACK -> if (isNightMode) styleRes.Theme_Main_Amoled else styleRes.Theme_Main_Light
            LIGHT -> styleRes.Theme_Main_Light
            DARK -> styleRes.Theme_Main_Dark
            AMOLED -> styleRes.Theme_Main_Amoled
        }
    }

fun Context?.sortOrderName(sortOrder: SortOrder) = this?.let {
    when (sortOrder) {
        SortOrder.UPDATED -> getString(stringRes.recently_updated)
        SortOrder.ADDED -> getString(stringRes.whats_new)
        SortOrder.NAME -> getString(stringRes.name)
        SortOrder.SIZE -> getString(stringRes.size)
    }
} ?: ""
