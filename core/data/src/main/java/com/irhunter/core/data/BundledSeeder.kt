package com.irhunter.core.data

import android.content.Context
import com.irhunter.core.database.ProfileJson
import com.irhunter.core.database.RemoteDatabase
import com.irhunter.core.model.DeviceCategory
import com.irhunter.core.model.RemoteProfile

/**
 * Seeds the redistributable device-code catalogs bundled under `assets/` into the
 * local Room database on first run, so the app is useful offline out of the box.
 *
 * Layout: `<catalog>/<DeviceCategory.name>/<Brand>/<Model>.ir` (Flipper `.ir` documents).
 * Each catalog carries its source and license; notices live in `assets/third_party/`.
 *
 * Deterministic ids (`bundle:<path>`) make seeding idempotent: re-running never duplicates.
 * A file that fails to parse is skipped (and reported) rather than aborting the whole seed.
 */
object BundledSeeder {
    data class Catalog(val root: String, val source: String, val license: String)
    val CATALOGS = listOf(
        Catalog("codes", "Flipper-IRDB", "CC0-1.0"),
        Catalog("codes_flipper_mit", "Flipper Devices IRDB", "MIT"),
    )

    private const val CATALOG_VERSION = 2
    private const val PREFS = "bundled-code-catalog"
    data class Report(val added: Int, val commands: Int, val skipped: List<String>, val recordWarnings: Int) {
        val total: Int get() = added + skipped.size
    }

    /**
     * Reads every bundled `.ir` asset and inserts the profiles that are not already present.
     * Must be called off the main thread (it parses files and does DB writes).
     */
    suspend fun seed(context: Context, database: RemoteDatabase): Report {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getInt("installed_version", 0) >= CATALOG_VERSION) {
            return Report(0, 0, emptyList(), 0)
        }
        val present = database.profiles().ids().toHashSet()
        val skipped = mutableListOf<String>()
        var added = 0
        var commandCount = 0
        var recordWarnings = 0
        val batch = mutableListOf<com.irhunter.core.database.ProfileRow>()

        for (catalog in CATALOGS) for (categoryName in listDirs(context, catalog.root)) {
            val category = runCatching { DeviceCategory.valueOf(categoryName) }.getOrNull()
                ?: continue
            for (brandDir in listDirs(context, "${catalog.root}/$categoryName")) {
                val brand = pretty(brandDir).ifBlank { "Unknown Brand" }
                for (file in listFiles(context, "${catalog.root}/$categoryName/$brandDir")) {
                    if (!file.endsWith(".ir")) continue
                    val relPath = "$categoryName/$brandDir/$file"
                    val id = "bundle:${catalog.root}:$relPath"
                    if (id in present) continue
                    val text = readAsset(context, "${catalog.root}/$relPath")
                    if (text == null) {
                        skipped += "$relPath: unreadable"
                        continue
                    }
                    val result = runCatching { FlipperImporter.parse(text) }
                    val parsed = result.getOrNull()
                    val commands = parsed?.commands
                    if (commands.isNullOrEmpty()) {
                        skipped += "$relPath: ${result.exceptionOrNull()?.message ?: "no commands"}"
                        continue
                    }
                    recordWarnings += parsed.warnings.size
                    val model = pretty(file.removeSuffix(".ir")).ifBlank { "Unknown Model" }
                    batch +=
                        ProfileJson.encode(
                            RemoteProfile(
                                id = id,
                                name = "$brand $model",
                                category = category,
                                brand = brand,
                                model = model,
                                source = catalog.source,
                                license = catalog.license,
                                originalText = text,
                                commands = commands,
                                saved = false,
                            )
                        )
                    if (batch.size >= 100) {
                        database.profiles().putAll(batch.toList())
                        batch.clear()
                    }
                    present += id
                    added++
                    commandCount += commands.size
                }
            }
        }
        if (batch.isNotEmpty()) database.profiles().putAll(batch)
        preferences.edit().putInt("installed_version", CATALOG_VERSION).apply()
        return Report(added, commandCount, skipped, recordWarnings)
    }

    private fun listDirs(context: Context, path: String): List<String> =
        (context.assets.list(path) ?: emptyArray()).sorted()

    private fun listFiles(context: Context, path: String): List<String> =
        (context.assets.list(path) ?: emptyArray()).sorted()

    private fun readAsset(context: Context, path: String): String? = try {
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    /** Turns curation-time sanitised names (`GE_Remote`) back into display text (`GE Remote`). */
    private fun pretty(raw: String): String = raw.replace('_', ' ').trim()
}
