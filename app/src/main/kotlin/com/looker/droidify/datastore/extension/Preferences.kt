package com.looker.droidify.datastore.extension

import android.content.Context
import android.content.res.Configuration
import com.looker.droidify.datastore.model.SortOrder
import com.looker.droidify.datastore.model.Theme
import com.looker.droidify.utility.common.SdkCheck
import com.looker.droidify.R.string as stringRes
import com.looker.droidify.R.style as styleRes

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
 * not in night mode — which is what following the system is supposed to mean.
 */
fun Configuration.isLightTheme(theme: Theme): Boolean = when (theme) {
    Theme.LIGHT -> true
    Theme.DARK, Theme.AMOLED -> false
    Theme.SYSTEM, Theme.SYSTEM_BLACK -> (uiMode and Configuration.UI_MODE_NIGHT_YES) == 0
}

fun Configuration.stockThemeRes(theme: Theme, dynamicTheme: Boolean) = when (theme) {
    Theme.SYSTEM -> {
        if ((uiMode and Configuration.UI_MODE_NIGHT_YES) != 0) {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicDark
            } else {
                styleRes.Theme_Main_Dark
            }
        } else {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicLight
            } else {
                styleRes.Theme_Main_Light
            }
        }
    }

    Theme.SYSTEM_BLACK -> {
        if ((uiMode and Configuration.UI_MODE_NIGHT_YES) != 0) {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicAmoled
            } else {
                styleRes.Theme_Main_Amoled
            }
        } else {
            if (SdkCheck.isSnowCake && dynamicTheme) {
                styleRes.Theme_Main_DynamicLight
            } else {
                styleRes.Theme_Main_Light
            }
        }
    }

    Theme.LIGHT -> if (SdkCheck.isSnowCake && dynamicTheme) {
        styleRes.Theme_Main_DynamicLight
    } else {
        styleRes.Theme_Main_Light
    }
    Theme.DARK -> if (SdkCheck.isSnowCake && dynamicTheme) {
        styleRes.Theme_Main_DynamicDark
    } else {
        styleRes.Theme_Main_Dark
    }
    Theme.AMOLED -> if (SdkCheck.isSnowCake && dynamicTheme) {
        styleRes.Theme_Main_DynamicAmoled
    } else {
        styleRes.Theme_Main_Amoled
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
