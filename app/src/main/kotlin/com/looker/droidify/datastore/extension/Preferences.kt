package com.looker.droidify.datastore.extension

import android.content.Context
import android.content.res.Configuration
import com.looker.droidify.datastore.model.SortOrder
import com.looker.droidify.datastore.model.Theme
import com.looker.droidify.utility.common.SdkCheck
import com.looker.droidify.R.string as stringRes
import com.looker.droidify.R.style as styleRes

/**
 * shiroikuma fork: every theme choice resolves to the one house style.
 *
 * This is the call that decides what `MainActivity.setTheme` applies, and it runs *after* the
 * manifest theme — so leaving it alone meant the whole legacy View shell kept Droid-ify's green
 * palette no matter what the launcher theme said. `Theme.Main.Jinsoningen` inherits
 * `Theme.Main.Amoled`, so upstream's widget styles and text appearances still come through; only
 * the colours are ours.
 *
 * Upstream's light/dark/amoled/dynamic resolution is preserved verbatim in [stockThemeRes] below
 * rather than deleted, so a rebase that touches it stays a clean merge and the fork's divergence
 * is one `return`.
 */
fun Configuration.getThemeRes(theme: Theme, dynamicTheme: Boolean): Int =
    styleRes.Theme_Main_Jinsoningen

@Suppress("unused")
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
