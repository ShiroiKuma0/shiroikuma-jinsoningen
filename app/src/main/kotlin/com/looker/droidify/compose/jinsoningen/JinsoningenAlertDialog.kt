/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the house AlertDialog.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.compose.jinsoningen

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.looker.droidify.jinsoningen.LocalJinsoningenUi
import androidx.compose.material3.AlertDialog as Material3AlertDialog

/**
 * A drop-in replacement for `androidx.compose.material3.AlertDialog` wearing the house look: the
 * surface colour from the knobs and, above all, a **yellow border**.
 *
 * On a black ground a borderless dialog has no edge at all — it reads as text floating over the
 * page it covers, which is exactly what a black-on-black Material dialog looks like here. The
 * border is what makes it a dialog.
 *
 * The signature matches Material's, so a call site adopts it by changing one import:
 *
 * ```
 * - import androidx.compose.material3.AlertDialog
 * + import com.looker.droidify.compose.jinsoningen.AlertDialog
 * ```
 *
 * Screens upstream adds later inherit the styling the moment their import is switched — no
 * per-dialog patching, and nothing to re-apply at each call site on a rebase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape? = null,
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    /** Overrides the border colour — used to make a failure dialog's edge the warning colour. */
    borderColor: Color = Color.Unspecified,
) {
    val ui = LocalJinsoningenUi.current
    val dialogShape = shape ?: RoundedCornerShape(ui.cornerRadius.dp)

    Material3AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        // The border rides on the modifier so a caller's own modifier still composes with it.
        modifier = modifier.border(
            width = maxOf(ui.borderWidth, 2).dp,
            color = borderColor.orElse(Color(ui.borderColor)),
            shape = dialogShape,
        ),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = dialogShape,
        containerColor = containerColor.orElse(Color(ui.surface)),
        iconContentColor = iconContentColor.orElse(Color(ui.accentColor)),
        titleContentColor = titleContentColor.orElse(Color(ui.accentColor)),
        textContentColor = textContentColor.orElse(Color(ui.textColor)),
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

private fun Color.orElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this
