/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the 白い熊 人造人間 UI page.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.compose.jinsoningen

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.looker.droidify.R
import com.looker.droidify.compose.components.BackButton
import com.looker.droidify.jinsoningen.ColorSlot
import com.looker.droidify.jinsoningen.JinsoningenFonts
import com.looker.droidify.jinsoningen.JinsoningenUiState
import com.looker.droidify.jinsoningen.LocalJinsoningenUi
import com.looker.droidify.jinsoningen.automation.AutomationAuth
import com.looker.droidify.utility.common.isIgnoreBatteryEnabled
import com.looker.droidify.utility.common.requestBatteryFreedom

/**
 * The 白い熊 人造人間 UI page — every knob that shapes the app's look, in the kxkb page grammar:
 *
 *  * a top-level group opens with a full-width hairline, then a big bold heading carrying a
 *    **word-width** underline;
 *  * a sub-group repeats that one indent level in, smaller;
 *  * rows sit two levels in (three under a sub-group), with tight vertical padding — the only
 *    generous spacing in the page is between top-level groups, so grouping reads instantly;
 *  * every control previews itself, and since the page is themed by the very values it edits,
 *    the whole app is the preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JinsoningenUiScreen(onBackClick: () -> Unit) {
    val ui = LocalJinsoningenUi.current
    var showExportImport by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(ui.background),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.jinsoningen_ui),
                        color = Color(ui.accentColor),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = { BackButton(onBackClick) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(ui.background),
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---------------------------------------------------------- 保存復元
            SectionHeader(ui, "Export / Import", first = true)
            NavigationRow(
                ui = ui,
                title = "Export / Import settings",
                summary = if (ui.exportDir.isBlank()) {
                    "No backup directory set"
                } else {
                    "Backup directory is set"
                },
                summaryIsWarning = ui.exportDir.isBlank(),
                onClick = { showExportImport = true },
            )
            // The 保存復元 automation rows belong here, under the export rows — a backup feature
            // lives where backup lives, and every sister app looks the same.
            AutomationRows(ui)
            // Same class of permission — "let this app work while you are not looking" — so it
            // keeps them company rather than shouting from the top of Settings.
            BatteryOptimisationRow(ui)

            // ------------------------------------------------------------ colours
            SectionHeader(ui, "Colours")
            SubHeader(ui, "Surfaces")
            ColorRow(ui, "Background", ColorSlot.BACKGROUND, level2 = true)
            ColorRow(ui, "Surface (cards, sheets, dialogs)", ColorSlot.SURFACE, level2 = true)
            SubHeader(ui, "Text")
            ColorRow(ui, "Primary text", ColorSlot.TEXT, level2 = true)
            ColorRow(ui, "Secondary text", ColorSlot.TEXT_DIM, level2 = true)
            SubHeader(ui, "Accents and lines")
            ColorRow(ui, "Accent", ColorSlot.ACCENT, level2 = true)
            ColorRow(ui, "Border", ColorSlot.BORDER, level2 = true)
            ColorRow(ui, "Divider", ColorSlot.DIVIDER, level2 = true)
            ColorRow(ui, "Icons", ColorSlot.ICON, level2 = true)
            ColorRow(ui, "Warnings", ColorSlot.WARN, level2 = true)
            ColorPreview(ui)

            // --------------------------------------------------------- typography
            SectionHeader(ui, "Typography")
            FontRow(ui)
            SubHeader(ui, "Body")
            SliderRow(ui, "Size", ui.fontSize, 8..32, "sp", level2 = true) { ui.updateFontSize(it) }
            SliderRow(ui, "Weight", ui.fontWeight, 100..900, step = 100, level2 = true) {
                ui.updateFontWeight(it)
            }
            ToggleRow(ui, "Italic", ui.fontItalic, level2 = true) { ui.updateFontItalic(it) }
            SubHeader(ui, "Headings")
            SliderRow(ui, "Size", ui.headingSize, 12..40, "sp", level2 = true) {
                ui.updateHeadingSize(it)
            }
            SliderRow(ui, "Weight", ui.headingWeight, 100..900, step = 100, level2 = true) {
                ui.updateHeadingWeight(it)
            }
            SubHeader(ui, "Secondary lines")
            SliderRow(ui, "Size", ui.labelSize, 8..24, "sp", level2 = true) {
                ui.updateLabelSize(it)
            }
            SubHeader(ui, "Spacing")
            SliderRow(ui, "Letter spacing", ui.letterSpacing, 0..20, "/100 em", level2 = true) {
                ui.updateLetterSpacing(it)
            }
            SliderRow(ui, "Line height", ui.lineHeightPct, 100..200, "%", level2 = true) {
                ui.updateLineHeightPct(it)
            }
            TypePreview(ui)

            // ----------------------------------------------------- shape & border
            SectionHeader(ui, "Shape and borders")
            SliderRow(ui, "Corner roundness", ui.cornerRadius, 0..40, "dp") {
                ui.updateCornerRadius(it)
            }
            SliderRow(ui, "Border thickness", ui.borderWidth, 0..8, "dp") {
                ui.updateBorderWidth(it)
            }
            SliderRow(ui, "Divider thickness", ui.dividerWidth, 0..8, "dp") {
                ui.updateDividerWidth(it)
            }
            ShapePreview(ui)

            // -------------------------------------------------------------- icons
            SectionHeader(ui, "Icons")
            SliderRow(ui, "Size", ui.iconSize, 12..64, "dp") { ui.updateIconSize(it) }
            SliderRow(ui, "Roundness", ui.iconRoundness, 0..100, "%") { ui.updateIconRoundness(it) }
            IconPreview(ui)

            // ------------------------------------------------------------ density
            SectionHeader(ui, "Density")
            SliderRow(ui, "Row padding", ui.rowPadding, 0..24, "dp") { ui.updateRowPadding(it) }
            SliderRow(ui, "Space between groups", ui.groupSpacing, 0..40, "dp") {
                ui.updateGroupSpacing(it)
            }
            SliderRow(ui, "Indent per level", ui.indentStep, 0..48, "dp") {
                ui.updateIndentStep(it)
            }

            // -------------------------------------------------------------- reset
            SectionHeader(ui, "Reset")
            NavigationRow(
                ui = ui,
                title = "Restore the black-yellow defaults",
                summary = "Every colour, font and size back to how 白い熊 人造人間 ships. " +
                    "The backup folder is kept.",
                onClick = { ui.resetToDefaults() },
            )
            Spacer(Modifier.height(ui.groupSpacing.dp * 2))
        }
    }

    if (showExportImport) {
        JinsoningenExportImportPanel(
            onDismiss = { showExportImport = false },
            onFinishedAndClose = {
                showExportImport = false
                onBackClick()
            },
        )
    }
}

/**
 * The two contract rows: a master switch (default OFF — nothing is reachable from outside until
 * 白い熊 turns it on) and the token, abbreviated, copied on tap, regenerated on the right.
 */
@Composable
private fun AutomationRows(ui: JinsoningenUiState) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AutomationAuth.enabled(context)) }
    var token by remember { mutableStateOf(AutomationAuth.token(context)) }

    ToggleRow(ui, "Automation export", enabled) {
        AutomationAuth.setEnabled(context, it)
        enabled = it
    }
    Text(
        text = "Lets 白い熊 自由作業盤 trigger this app's export through the token-gated intent.",
        color = Color(ui.textDimColor),
        fontSize = ui.labelSize.sp,
        modifier = Modifier.padding(
            start = rowIndent(ui, false),
            end = 16.dp,
            bottom = ui.rowPadding.dp,
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("token", token))
                Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
            }
            .padding(
                start = rowIndent(ui, false),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RowTitle(ui, "Automation token", AutomationAuth.abbreviated(token))
        }
        Text(
            text = "Regenerate",
            color = Color(ui.warnColor),
            fontSize = ui.labelSize.sp,
            modifier = Modifier.clickable {
                token = AutomationAuth.regenerate(context)
                Toast.makeText(
                    context,
                    "New token — update every copy you pasted elsewhere",
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }
}

/**
 * Background access, stated rather than alarmed about.
 *
 * Upstream put this at the top of Settings as a red banner with white text whenever the app was
 * not exempt from battery optimisation; 白い熊 had that removed (2026-08-04). It still needs to be
 * *reachable*, since it is the only in-app route to the system prompt and EMUI will otherwise
 * throttle background syncs — so it lives here, in the house colours, next to the automation
 * switch it resembles: both are "let this app work while you are not looking".
 *
 * The state re-reads on every RESUME, because the system prompt returns no result — coming back
 * from it is the only signal that the answer may have changed.
 */
@Composable
private fun BatteryOptimisationRow(ui: JinsoningenUiState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var exempt by remember { mutableStateOf(context.isIgnoreBatteryEnabled()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exempt = context.isIgnoreBatteryEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavigationRow(
        ui = ui,
        title = "Background access",
        summary = if (exempt) {
            "Exempt from battery optimisation — scheduled syncs run on time"
        } else {
            "Battery optimisation is on, so scheduled syncs may be delayed. Tap to exempt."
        },
        onClick = {
            context.requestBatteryFreedom()
            exempt = context.isIgnoreBatteryEnabled()
        },
    )
}

// ---------------------------------------------------------------------- headings

/** Top-level group heading: full-width hairline above, big bold title, word-width underline. */
@Composable
private fun SectionHeader(ui: JinsoningenUiState, title: String, first: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            Spacer(Modifier.height(ui.groupSpacing.dp))
            if (ui.dividerWidth > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ui.dividerWidth.dp)
                        .background(Color(ui.dividerColor).copy(alpha = 0.4f)),
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = BASE_INDENT.dp, top = 8.dp, bottom = 2.dp)
                .width(IntrinsicSize.Min),
        ) {
            Text(
                text = title,
                color = Color(ui.accentColor),
                fontSize = ui.headingSize.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(2.5.dp)
                    .background(Color(ui.accentColor)),
            )
        }
    }
}

/** One level under a section: same shape, smaller, no full-width rule. */
@Composable
private fun SubHeader(ui: JinsoningenUiState, title: String) {
    Column(
        modifier = Modifier
            .padding(start = (BASE_INDENT + ui.indentStep).dp, top = 6.dp, bottom = 2.dp)
            .width(IntrinsicSize.Min),
    ) {
        Text(
            text = title,
            color = Color(ui.accentColor),
            fontSize = (ui.headingSize - 3).coerceAtLeast(10).sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(1.5.dp)
                .background(Color(ui.accentColor)),
        )
    }
}

// -------------------------------------------------------------------------- rows

private const val BASE_INDENT = 16

@Composable
private fun rowIndent(ui: JinsoningenUiState, level2: Boolean) =
    (BASE_INDENT + ui.indentStep * (if (level2) 3 else 2)).dp

@Composable
private fun RowTitle(
    ui: JinsoningenUiState,
    title: String,
    summary: String?,
    warn: Boolean = false,
) {
    Text(text = title, color = Color(ui.textColor), fontSize = ui.fontSize.sp)
    if (summary != null) {
        Text(
            text = summary,
            color = if (warn) Color(ui.warnColor) else Color(ui.textDimColor),
            fontSize = ui.labelSize.sp,
        )
    }
}

@Composable
private fun NavigationRow(
    ui: JinsoningenUiState,
    title: String,
    summary: String? = null,
    summaryIsWarning: Boolean = false,
    level2: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp,
            ),
    ) {
        RowTitle(ui, title, summary, summaryIsWarning)
    }
}

@Composable
private fun ToggleRow(
    ui: JinsoningenUiState,
    title: String,
    checked: Boolean,
    level2: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) { RowTitle(ui, title, null) }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(ui.background),
                checkedTrackColor = Color(ui.accentColor),
                uncheckedThumbColor = Color(ui.textDimColor),
                uncheckedTrackColor = Color(ui.background),
                uncheckedBorderColor = Color(ui.borderColor),
            ),
        )
    }
}

/** A slider row: title, the live value, and a track that reaches 0 wherever 0 means "none". */
@Composable
private fun SliderRow(
    ui: JinsoningenUiState,
    title: String,
    value: Int,
    range: IntRange,
    unit: String = "",
    step: Int = 1,
    level2: Boolean = false,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp,
                modifier = Modifier.weight(1f),
            )
            Text(text = "$value$unit", color = Color(ui.accentColor), fontSize = ui.labelSize.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (step > 1) ((range.last - range.first) / step) - 1 else 0,
            colors = SliderDefaults.colors(
                thumbColor = Color(ui.accentColor),
                activeTrackColor = Color(ui.accentColor),
                inactiveTrackColor = Color(ui.textDimColor).copy(alpha = 0.4f),
            ),
        )
    }
}

/** A colour row: the swatch is the preview, tapping it opens the RGBA picker. */
@Composable
private fun ColorRow(
    ui: JinsoningenUiState,
    title: String,
    slot: ColorSlot,
    level2: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    val color = ui.colorOf(slot)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) { RowTitle(ui, title, "#%08X".format(color)) }
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(ui.cornerRadius.dp))
                .background(Color(color))
                .border(
                    width = (if (ui.borderWidth > 0) ui.borderWidth else 1).dp,
                    color = Color(ui.borderColor),
                    shape = RoundedCornerShape(ui.cornerRadius.dp),
                ),
        )
    }

    if (showPicker) {
        JinsoningenColorPickerDialog(
            title = title,
            initial = color,
            onDismiss = { showPicker = false },
            onPicked = {
                ui.updateColor(slot, it)
                showPicker = false
            },
        )
    }
}

/** The font row: shows the current family rendered in its own glyphs. */
@Composable
private fun FontRow(ui: JinsoningenUiState) {
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(
                start = rowIndent(ui, false),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "Font", color = Color(ui.textColor), fontSize = ui.fontSize.sp)
            Text(
                text = JinsoningenFonts.displayName(context, ui.fontFamilyId),
                color = Color(ui.textDimColor),
                fontSize = ui.labelSize.sp,
                fontFamily = JinsoningenFonts.family(context, ui.fontFamilyId),
            )
        }
    }

    if (showPicker) {
        JinsoningenFontPickerDialog(onDismiss = { showPicker = false })
    }
}

// ---------------------------------------------------------------------- previews

@Composable
private fun PreviewFrame(ui: JinsoningenUiState, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rowIndent(ui, false), end = 16.dp, top = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(ui.cornerRadius.dp))
            .background(Color(ui.surface))
            .then(
                if (ui.borderWidth > 0) {
                    Modifier.border(
                        ui.borderWidth.dp,
                        Color(ui.borderColor),
                        RoundedCornerShape(ui.cornerRadius.dp),
                    )
                } else {
                    Modifier
                },
            )
            .padding(10.dp),
    ) { content() }
}

@Composable
private fun ColorPreview(ui: JinsoningenUiState) {
    PreviewFrame(ui) {
        Column {
            Text(
                text = "Heading on surface",
                color = Color(ui.accentColor),
                fontSize = ui.headingSize.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "Primary text", color = Color(ui.textColor), fontSize = ui.fontSize.sp)
            Text(
                text = "Secondary text",
                color = Color(ui.textDimColor),
                fontSize = ui.labelSize.sp,
            )
            Text(
                text = "Warning text",
                color = Color(ui.warnColor),
                fontSize = ui.labelSize.sp,
            )
            if (ui.dividerWidth > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(ui.dividerWidth.dp)
                        .background(Color(ui.dividerColor)),
                )
            }
        }
    }
}

@Composable
private fun TypePreview(ui: JinsoningenUiState) {
    val context = LocalContext.current
    val family = JinsoningenFonts.family(context, ui.fontFamilyId)
    PreviewFrame(ui) {
        Column {
            Text(
                text = "白い熊 人造人間 — Heading",
                color = Color(ui.accentColor),
                fontSize = ui.headingSize.sp,
                fontWeight = FontWeight(ui.headingWeight.coerceIn(100, 900)),
                fontFamily = family,
            )
            Text(
                text = "Body text — 見本 AaBbCc 0123",
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp,
                fontWeight = FontWeight(ui.fontWeight.coerceIn(100, 900)),
                fontStyle = if (ui.fontItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = family,
            )
            Text(
                text = "Secondary line — 補足",
                color = Color(ui.textDimColor),
                fontSize = ui.labelSize.sp,
                fontFamily = family,
            )
        }
    }
}

@Composable
private fun ShapePreview(ui: JinsoningenUiState) {
    PreviewFrame(ui) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp, 32.dp)
                    .clip(RoundedCornerShape(ui.cornerRadius.dp))
                    .background(Color(ui.background))
                    .border(
                        (if (ui.borderWidth > 0) ui.borderWidth else 0).dp,
                        Color(ui.borderColor),
                        RoundedCornerShape(ui.cornerRadius.dp),
                    ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Corner ${ui.cornerRadius}dp · border ${ui.borderWidth}dp",
                    color = Color(ui.textColor),
                    fontSize = ui.labelSize.sp,
                )
                Spacer(Modifier.height(4.dp))
                if (ui.dividerWidth > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ui.dividerWidth.dp)
                            .background(Color(ui.dividerColor)),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPreview(ui: JinsoningenUiState) {
    val shape = RoundedCornerShape(percent = (ui.iconRoundness / 2).coerceIn(0, 50))
    PreviewFrame(ui) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(ui.iconSize.dp)
                    .clip(shape)
                    .background(Color(ui.accentColor).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    tint = Color(ui.iconColor),
                    modifier = Modifier.size((ui.iconSize * 0.75f).dp),
                )
            }
            Text(
                text = "${ui.iconSize}dp · ${ui.iconRoundness}% round",
                color = Color(ui.textColor),
                fontSize = ui.labelSize.sp,
            )
        }
    }
}
