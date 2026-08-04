/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the UI page's dialogs — the RGBA colour picker,
 * the font picker, and the Export / Import panel.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.compose.jinsoningen

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.looker.droidify.jinsoningen.JinsoningenBackup
import com.looker.droidify.jinsoningen.JinsoningenFonts
import com.looker.droidify.jinsoningen.JinsoningenUiState
import com.looker.droidify.jinsoningen.LocalJinsoningenUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// ------------------------------------------------------------------ colour picker

/**
 * The house colour picker: a row of one-click swatches pre-filled with the colours already in
 * use, then four RGBA sliders over a live preview of exactly what they mix.
 */
@Composable
fun JinsoningenColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit,
) {
    val ui = LocalJinsoningenUi.current
    var alpha by remember { mutableIntStateOf(AndroidColor.alpha(initial)) }
    var red by remember { mutableIntStateOf(AndroidColor.red(initial)) }
    var green by remember { mutableIntStateOf(AndroidColor.green(initial)) }
    var blue by remember { mutableIntStateOf(AndroidColor.blue(initial)) }

    val current = AndroidColor.argb(alpha, red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(ui.surface),
        title = {
            Text(text = title, color = Color(ui.accentColor), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                // One-click swatches: what was picked before, newest first.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ui.recentColors) { swatch ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(ui.cornerRadius.dp))
                                .background(Color(swatch))
                                .border(
                                    width = if (swatch == current) 3.dp else 1.dp,
                                    color = Color(ui.borderColor),
                                    shape = RoundedCornerShape(ui.cornerRadius.dp),
                                )
                                .clickable {
                                    alpha = AndroidColor.alpha(swatch)
                                    red = AndroidColor.red(swatch)
                                    green = AndroidColor.green(swatch)
                                    blue = AndroidColor.blue(swatch)
                                },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Live preview of the mix, over the app background so alpha reads honestly.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(ui.cornerRadius.dp))
                        .background(Color(ui.background))
                        .border(
                            1.dp,
                            Color(ui.borderColor),
                            RoundedCornerShape(ui.cornerRadius.dp),
                        ),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Color(current)),
                    )
                }
                Text(
                    text = "#%08X".format(current),
                    color = Color(ui.textColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                )

                ChannelSlider(ui, "A", alpha) { alpha = it }
                ChannelSlider(ui, "R", red) { red = it }
                ChannelSlider(ui, "G", green) { green = it }
                ChannelSlider(ui, "B", blue) { blue = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(current) }) {
                Text("OK", color = Color(ui.accentColor))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(ui.textDimColor))
            }
        },
    )
}

@Composable
private fun ChannelSlider(
    ui: JinsoningenUiState,
    name: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            color = Color(ui.accentColor),
            modifier = Modifier.width(20.dp),
            fontSize = ui.fontSize.sp,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(ui.accentColor),
                activeTrackColor = Color(ui.accentColor),
                inactiveTrackColor = Color(ui.textDimColor).copy(alpha = 0.4f),
            ),
        )
        Text(
            text = value.toString(),
            color = Color(ui.textColor),
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
            fontSize = ui.labelSize.sp,
        )
    }
}

// -------------------------------------------------------------------- font picker

/** Every candidate font drawn in its own glyphs — you pick a font by looking at it. */
@Composable
fun JinsoningenFontPickerDialog(onDismiss: () -> Unit) {
    val ui = LocalJinsoningenUi.current
    val context = LocalContext.current
    val options = remember(ui.fontRevision) { JinsoningenFonts.available(context) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val id = JinsoningenFonts.import(context, uri)
            ui.onFontsChanged()
            if (id != null) ui.updateFontFamily(id)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(ui.surface),
        title = { Text("Font", color = Color(ui.accentColor), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    val selected = option.id == ui.fontFamilyId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ui.updateFontFamily(option.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = option.displayName,
                                color = Color(if (selected) ui.accentColor else ui.textColor),
                                fontFamily = JinsoningenFonts.family(context, option.id),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = (ui.fontSize + 2).sp,
                            )
                            Text(
                                text = "白い熊 人造人間 — AaBbCc 0123",
                                color = Color(ui.textDimColor),
                                fontFamily = JinsoningenFonts.family(context, option.id),
                                fontSize = ui.labelSize.sp,
                            )
                        }
                        if (option.id.isNotBlank() && !option.id.startsWith("@")) {
                            TextButton(
                                onClick = {
                                    JinsoningenFonts.delete(context, option.id)
                                    if (selected) ui.updateFontFamily(JinsoningenFonts.SYSTEM)
                                    ui.onFontsChanged()
                                },
                            ) {
                                Text(
                                    "Remove",
                                    color = Color(ui.warnColor),
                                    fontSize = ui.labelSize.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    importLauncher.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
                },
            ) {
                Text("Import font…", color = Color(ui.accentColor))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(ui.textDimColor)) }
        },
    )
}

// ------------------------------------------------------------- export / import

/**
 * The Export / Import panel in the Kōjiki sheet format: the whole panel is one bordered rounded
 * box inset from the screen edges, with a centred bold title over a dim intro, the backup folder
 * as its own bordered tappable box (red until it is set), the newest backup found there, a flat
 * yellow-tinted checklist between thin dividers, and an ArcaneChat button bar — Cancel alone on
 * the left, Import and Export grouped right, all fully round pills.
 */
@Composable
fun JinsoningenExportImportPanel(onDismiss: () -> Unit, onFinishedAndClose: () -> Unit) {
    val ui = LocalJinsoningenUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val checks = remember {
        SnapshotStateMap<JinsoningenBackup.Cat, Boolean>().apply {
            JinsoningenBackup.Cat.entries.forEach { put(it, it.onByDefault) }
        }
    }
    var lastBackup by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var info by remember { mutableStateOf<InfoState?>(null) }

    // The panel queries the directory for the newest export as soon as it opens.
    LaunchedEffect(ui.exportDir) {
        lastBackup = ui.exportDir.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) { JinsoningenBackup.newestBackup(context, Uri.parse(it)) }
        }
    }

    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            ui.updateExportDir(uri.toString())
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selected = checks.filterValues { it }.keys
        scope.launch {
            info = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }
                val restored = JinsoningenBackup.restore(context, bytes, selected)
                ui.reload()
                InfoState(
                    title = "Import finished",
                    message = "$restored categories restored.",
                    success = true,
                    restart = true,
                )
            }.getOrElse {
                InfoState("Import failed", it.message ?: "Unknown error", success = false)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(ui.surface))
                .border(2.dp, Color(ui.borderColor), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text = "Export / Import",
                    color = Color(ui.accentColor),
                    fontWeight = FontWeight.Bold,
                    fontSize = ui.headingSize.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Carry every setting to another install: tick what to take, then " +
                        "Export to the backup folder or Import from a backup file.",
                    color = Color(ui.textDimColor),
                    fontSize = ui.labelSize.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))

                // The backup folder box — red until it is set, yellow once it is.
                val dirUnset = ui.exportDir.isBlank()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ui.cornerRadius.dp))
                        .border(
                            2.dp,
                            Color(if (dirUnset) ui.warnColor else ui.borderColor),
                            RoundedCornerShape(ui.cornerRadius.dp),
                        )
                        .clickable { dirLauncher.launch(null) }
                        .padding(10.dp),
                ) {
                    Column {
                        Text(
                            text = "Backup folder",
                            color = Color(ui.textDimColor),
                            fontSize = ui.labelSize.sp,
                        )
                        Text(
                            text = if (dirUnset) {
                                "No backup directory set — tap to choose one"
                            } else {
                                JinsoningenBackup.treeDisplayName(context, Uri.parse(ui.exportDir))
                            },
                            color = Color(if (dirUnset) ui.warnColor else ui.accentColor),
                            fontWeight = FontWeight.Bold,
                            fontSize = ui.fontSize.sp,
                        )
                    }
                }
                Text(
                    text = lastBackup?.let {
                        "Last backup: ${it.first} · ${JinsoningenBackup.formatTimestamp(it.second)}"
                    } ?: "No backup found in this folder yet",
                    color = Color(ui.textDimColor),
                    fontSize = ui.labelSize.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                ThinDivider(ui)
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val allOn = checks.values.all { it }
                    ChecklistRow(
                        ui = ui,
                        label = "Select all",
                        checked = allOn,
                        bold = true,
                        onToggle = {
                            JinsoningenBackup.Cat.entries.forEach { checks[it] = !allOn }
                        },
                    )
                    JinsoningenBackup.Cat.entries.forEach { cat ->
                        ChecklistRow(
                            ui = ui,
                            label = cat.label,
                            checked = checks[cat] ?: false,
                            onToggle = { checks[cat] = !(checks[cat] ?: false) },
                        )
                    }
                }
                ThinDivider(ui)
                Spacer(Modifier.height(12.dp))

                // ArcaneChat bar: Cancel alone left, Import + Export grouped right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(ui, "Cancel", onClick = onDismiss)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(ui, "Import") {
                            if (checks.none { it.value }) {
                                info = InfoState(
                                    "Import failed",
                                    "No categories selected.",
                                    success = false,
                                )
                            } else {
                                importLauncher.launch(
                                    arrayOf("application/zip", "application/octet-stream", "*/*"),
                                )
                            }
                        }
                        PillButton(ui, "Export") {
                            val selected = checks.filterValues { it }.keys
                            when {
                                selected.isEmpty() -> info = InfoState(
                                    "Export failed",
                                    "No categories selected.",
                                    success = false,
                                )

                                ui.exportDir.isBlank() -> info = InfoState(
                                    "Export failed",
                                    "No backup directory set.",
                                    success = false,
                                )

                                else -> scope.launch {
                                    info = runCatching {
                                        val bytes = ByteArrayOutputStream().also { out ->
                                            JinsoningenBackup.writeZip(context, selected, out)
                                        }.toByteArray()
                                        val name = withContext(Dispatchers.IO) {
                                            JinsoningenBackup.writeToTree(
                                                context,
                                                Uri.parse(ui.exportDir),
                                                bytes,
                                            )
                                        }
                                        lastBackup = withContext(Dispatchers.IO) {
                                            JinsoningenBackup.newestBackup(
                                                context,
                                                Uri.parse(ui.exportDir),
                                            )
                                        }
                                        InfoState(
                                            title = "Export finished",
                                            message = "$name\n" +
                                                "${JinsoningenBackup.humanSize(bytes.size.toLong())}" +
                                                " · ${selected.size} categories",
                                            success = true,
                                        )
                                    }.getOrElse {
                                        InfoState(
                                            "Export failed",
                                            it.message ?: "Unknown error",
                                            success = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    info?.let { state ->
        JinsoningenInfoDialog(
            state = state,
            onAcknowledge = {
                info = null
                // A success closes the whole chain — this dialog, the panel under it, and the UI
                // page itself. A failure leaves the panel open so it can be corrected.
                if (state.success) onFinishedAndClose()
            },
            onRestart = { restartApp(context) },
        )
    }
}

@Composable
private fun ChecklistRow(
    ui: JinsoningenUiState,
    label: String,
    checked: Boolean,
    bold: Boolean = false,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(ui.accentColor),
                uncheckedColor = Color(ui.borderColor),
                checkmarkColor = Color(ui.background),
            ),
        )
        Text(
            text = label,
            color = Color(ui.textColor),
            fontSize = ui.fontSize.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private data class InfoState(
    val title: String,
    val message: String,
    val success: Boolean,
    val restart: Boolean = false,
)

/** Result dialog: yellow border, black ground — and it is what closes the chain. */
@Composable
private fun JinsoningenInfoDialog(
    state: InfoState,
    onAcknowledge: () -> Unit,
    onRestart: () -> Unit,
) {
    val ui = LocalJinsoningenUi.current
    AlertDialog(
        onDismissRequest = onAcknowledge,
        containerColor = Color(ui.surface),
        borderColor = Color(if (state.success) ui.borderColor else ui.warnColor),
        title = {
            Text(
                text = state.title,
                color = Color(if (state.success) ui.accentColor else ui.warnColor),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(text = state.message, color = Color(ui.textColor)) },
        confirmButton = {
            if (state.restart) {
                PillButton(ui, "Restart now", onClick = onRestart)
            } else {
                PillButton(ui, "OK", onClick = onAcknowledge)
            }
        },
        dismissButton = {
            if (state.restart) PillButton(ui, "Later", onClick = onAcknowledge)
        },
    )
}

/** Fully round pill: black fill, yellow stroke, yellow text — the ArcaneChat button shape. */
@Composable
internal fun PillButton(ui: JinsoningenUiState, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color(ui.background))
            .border(1.5.dp, Color(ui.borderColor), RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = Color(ui.accentColor), fontSize = ui.fontSize.sp)
    }
}

@Composable
private fun ThinDivider(ui: JinsoningenUiState) {
    if (ui.dividerWidth <= 0) return
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(ui.dividerWidth.dp)
            .background(Color(ui.dividerColor).copy(alpha = 0.4f)),
    )
}

/**
 * Relaunches the app after an import. Upstream carries no process-restart helper, so this is the
 * plain form: start the launcher intent on a fresh task and end this process.
 */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }
    Runtime.getRuntime().exit(0)
}
