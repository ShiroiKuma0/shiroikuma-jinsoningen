/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the Export / Import engine — the category ZIP
 * shared by the UI page's panel and the headless 保存復元 automation path.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.jinsoningen

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.fasterxml.jackson.core.JsonToken
import com.looker.droidify.database.Database
import com.looker.droidify.datastore.CustomButtonRepository
import com.looker.droidify.datastore.SettingsRepository
import com.looker.droidify.datastore.SettingsSerializer
import com.looker.droidify.datastore.model.CustomButton
import com.looker.droidify.model.Repository
import com.looker.droidify.utility.common.extension.Json as JacksonJson
import com.looker.droidify.utility.common.extension.forEach
import com.looker.droidify.utility.common.extension.forEachKey
import com.looker.droidify.utility.common.extension.parseDictionary
import com.looker.droidify.utility.common.extension.writeArray
import com.looker.droidify.utility.common.extension.writeDictionary
import com.looker.droidify.utility.serialization.repository
import com.looker.droidify.utility.serialization.serialize
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Category-based backup in the family shape: one `.zip` per export holding a `manifest.json` plus
 * one `<id>.json` per category, absent categories skipped on import, imports merging per key.
 *
 * Filenames follow the mandatory family convention —
 * `shiroikuma-jinsoningen_<yyyy-MM-dd_HH-mm-ss>.zip`, no version and no suffix — because every
 * sister app backs up into one directory.
 *
 * The core is [writeZip]: `(categories, OutputStream, onProgress, isCancelled)`. The UI panel and
 * the automation service are both thin callers of it — the export logic exists exactly once.
 */
object JinsoningenBackup {

    const val FORMAT = "shiroikuma-jinsoningen-backup"
    const val VERSION = 1
    const val FILE_PREFIX = "shiroikuma-jinsoningen_"
    const val MIME_ZIP = "application/zip"

    /** A tickable unit of the backup. [id] is both the zip entry base and the automation id. */
    enum class Cat(val id: String, val label: String, val onByDefault: Boolean = true) {
        UI("ui", "白い熊 人造人間 UI (colours, fonts, sizes)"),
        SETTINGS("settings", "App settings (theme, updates, installer, proxy, sync)"),
        REPOSITORIES("repositories", "Repositories"),
        CUSTOM_BUTTONS("custom_buttons", "Custom app-detail buttons"),
        FONTS("fonts", "Imported fonts"),
        ;

        /** Entry name inside the zip. Fonts are a directory of the original files. */
        val entry: String get() = if (this == FONTS) "fonts/" else "$id.json"

        companion object {
            fun ofId(id: String): Cat? = entries.firstOrNull { it.id == id }

            fun ofEntry(name: String): Cat? = entries.firstOrNull {
                if (it == FONTS) name.startsWith("fonts/") else name == it.entry
            }

            val defaults: Set<Cat> get() = entries.filter { it.onByDefault }.toSet()
        }
    }

    /** Progress callback: the category being written, plus its 1-based position. */
    fun interface Progress {
        fun report(cat: Cat, position: Int, total: Int)
    }

    fun exportFileName(): String =
        FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ------------------------------------------------------------------ the core

    /**
     * Writes the ticked [categories] into [out]. Nothing here touches a UI, a SAF uri or a
     * broadcast — callers own the destination.
     *
     * @param isCancelled checked between categories, so a cancel unwinds at a boundary rather
     *        than tearing a write in half.
     * @return the categories actually written.
     */
    suspend fun writeZip(
        context: Context,
        categories: Set<Cat>,
        out: OutputStream,
        onProgress: Progress? = null,
        isCancelled: () -> Boolean = { false },
    ): Set<Cat> {
        val ordered = Cat.entries.filter { it in categories }
        val written = linkedSetOf<Cat>()

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson(context, ordered).toString(2).toByteArray())
            zip.closeEntry()

            ordered.forEachIndexed { index, cat ->
                if (isCancelled()) return written
                // The contract's rule: `current` is the POSITION of the one being written.
                onProgress?.report(cat, index + 1, ordered.size)

                when (cat) {
                    Cat.UI -> zip.jsonEntry(cat, JinsoningenUiConfig(context).toJson())

                    Cat.SETTINGS -> {
                        zip.putNextEntry(ZipEntry(cat.entry))
                        zip.write(settingsBytes(context))
                        zip.closeEntry()
                    }

                    Cat.REPOSITORIES -> {
                        zip.putNextEntry(ZipEntry(cat.entry))
                        zip.write(repositoriesBytes())
                        zip.closeEntry()
                    }

                    Cat.CUSTOM_BUTTONS -> {
                        zip.putNextEntry(ZipEntry(cat.entry))
                        zip.write(customButtonsBytes(context))
                        zip.closeEntry()
                    }

                    Cat.FONTS -> JinsoningenFonts.imported(context).forEach { font ->
                        if (isCancelled()) return written
                        zip.putNextEntry(ZipEntry("fonts/${font.name}"))
                        zip.write(font.readBytes())
                        zip.closeEntry()
                    }
                }
                written += cat
            }
        }
        return written
    }

    private fun ZipOutputStream.jsonEntry(cat: Cat, json: JSONObject) {
        putNextEntry(ZipEntry(cat.entry))
        write(json.toString(2).toByteArray())
        closeEntry()
    }

    private fun manifestJson(context: Context, cats: List<Cat>) = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("app", context.packageName)
        put(
            "appVersion",
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "",
        )
        put("createdTs", System.currentTimeMillis())
        put("categories", JSONArray(cats.map { it.id }))
    }

    // ------------------------------------------------------------- SAF destination

    /**
     * Writes [bytes] into the SAF tree [treeUri] **atomically**: a `.part` document first, renamed
     * to the final name only once the archive is complete, and deleted if anything goes wrong. A
     * killed export must never leave something that looks like a backup.
     */
    fun writeToTree(context: Context, treeUri: Uri, bytes: ByteArray): String {
        val finalName = exportFileName()
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        var partUri: Uri? = null
        return runCatching {
            val part = DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                MIME_ZIP,
                "$finalName.part",
            ) ?: error("The backup folder could not be written to.")
            partUri = part
            context.contentResolver.openOutputStream(part)?.use { it.write(bytes) }
                ?: error("The backup folder could not be written to.")
            DocumentsContract.renameDocument(context.contentResolver, part, finalName)
            finalName
        }.getOrElse { failure ->
            partUri?.let {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) }
            }
            throw failure
        }
    }

    // ------------------------------------------------------------------ import

    fun categoriesIn(bytes: ByteArray): Set<Cat> {
        val found = mutableSetOf<Cat>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                Cat.ofEntry(entry.name)?.let { found += it }
                entry = zip.nextEntry
            }
        }
        return found
    }

    /**
     * Applies the ticked [categories]. Absent ones are skipped, present ones merge per key.
     * @return how many categories were restored.
     */
    suspend fun restore(context: Context, bytes: ByteArray, categories: Set<Cat>): Int {
        val seen = mutableSetOf<Cat>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val cat = Cat.ofEntry(entry.name)
                if (cat != null && cat in categories) {
                    val content = zip.readBytes()
                    when (cat) {
                        Cat.UI -> JinsoningenUiConfig(context).fromJson(JSONObject(String(content)))
                        Cat.SETTINGS -> restoreSettings(context, content)
                        Cat.REPOSITORIES -> restoreRepositories(content)
                        Cat.CUSTOM_BUTTONS -> restoreCustomButtons(context, content)
                        Cat.FONTS -> {
                            val name = entry.name.removePrefix("fonts/")
                            if (name.isNotBlank()) {
                                File(JinsoningenFonts.fontsDir(context), name).writeBytes(content)
                            }
                        }
                    }
                    seen += cat
                }
                entry = zip.nextEntry
            }
        }
        if (Cat.FONTS in seen) JinsoningenFonts.invalidate()
        return seen.size
    }

    // ------------------------------------------------------------- dependencies

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun customButtonRepository(): CustomButtonRepository
    }

    private fun entryPoint(context: Context): BackupEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BackupEntryPoint::class.java,
        )

    // ---------------------------------------------------------------- settings

    /** The DataStore `Settings` object, in exactly the shape upstream's own exporter writes. */
    private suspend fun settingsBytes(context: Context): ByteArray {
        val settings = entryPoint(context).settingsRepository().getInitial()
        return ByteArrayOutputStream().also { out ->
            SettingsSerializer.writeTo(settings, out)
        }.toByteArray()
    }

    /**
     * Restored through upstream's own `SettingsRepository.import`, so the merge semantics stay
     * theirs (favourites are unioned rather than replaced) and a rebase that changes them changes
     * ours too. It takes a `Uri`, so the entry is staged in the cache directory and deleted after.
     */
    private suspend fun restoreSettings(context: Context, content: ByteArray) {
        val staged = File(context.cacheDir, "jinsoningen-settings-import.json")
        try {
            staged.writeBytes(content)
            entryPoint(context).settingsRepository().import(Uri.fromFile(staged))
        } finally {
            staged.delete()
        }
    }

    // ------------------------------------------------------------ repositories

    /**
     * Repositories live in the legacy SQLite database and serialise through upstream's Jackson
     * helpers. Ids, mirrors of disabled repos and the sync bookkeeping are stripped exactly as
     * upstream's own repository export does, so a restore adds repositories rather than pinning
     * them to another install's row ids.
     */
    private fun repositoriesBytes(): ByteArray = ByteArrayOutputStream().also { out ->
        JacksonJson.factory.createGenerator(out).use { generator ->
            generator.writeDictionary {
                writeArray("repositories") {
                    Database.RepositoryAdapter.getAll().map {
                        it.copy(
                            id = -1,
                            mirrors = if (it.enabled) it.mirrors else emptyList(),
                            lastModified = "",
                            entityTag = "",
                        )
                    }.forEach { repo -> writeDictionary { repo.serialize(this) } }
                }
            }
        }
    }.toByteArray()

    private fun restoreRepositories(content: ByteArray) {
        val list = mutableListOf<Repository>()
        JacksonJson.factory.createParser(content.inputStream()).use { parser ->
            parser?.parseDictionary {
                forEachKey {
                    if (it.array("repositories")) {
                        forEach(JsonToken.START_OBJECT) { list.add(repository()) }
                    }
                }
            }
        }
        if (list.isNotEmpty()) Database.RepositoryAdapter.importRepos(list)
    }

    // ----------------------------------------------------------- custom buttons

    private val buttonJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private suspend fun customButtonsBytes(context: Context): ByteArray {
        val buttons = entryPoint(context).customButtonRepository().getButtons()
        return buttonJson
            .encodeToString(ListSerializer(CustomButton.serializer()), buttons)
            .toByteArray()
    }

    /** Merge per key: an imported button replaces the one with its id, others are appended. */
    private suspend fun restoreCustomButtons(context: Context, content: ByteArray) {
        val imported = buttonJson.decodeFromString(
            ListSerializer(CustomButton.serializer()),
            String(content),
        )
        if (imported.isEmpty()) return
        val repository = entryPoint(context).customButtonRepository()
        val byId = imported.associateBy { it.id }
        val existing = repository.getButtons()
        val merged = existing.map { byId[it.id] ?: it } +
            imported.filter { button -> existing.none { it.id == button.id } }
        repository.reorderButtons(merged)
    }

    // ------------------------------------------------------- newest backup scan

    /** Newest `shiroikuma-jinsoningen_*.zip` in the tree, as `name to lastModified`. */
    fun newestBackup(context: Context, treeUri: Uri): Pair<String, Long>? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        var best: Pair<String, Long>? = null
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                // Half-written archives carry .part and are never "the latest backup".
                if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".zip")) continue
                val modified = cursor.getLong(1)
                val current = best
                if (current == null || modified > current.second) best = name to modified
            }
        }
        best
    }.getOrNull()

    /** The chosen folder's own display name, for the row that shows where backups go. */
    fun treeDisplayName(context: Context, treeUri: Uri): String = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        context.contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: Uri.decode(treeUri.toString().substringAfterLast('/'))

    fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))

    /** Human size for the automation reply — `4.6 MB`, `1.20 GB`. */
    fun humanSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(Locale.ROOT, "%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f kB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
