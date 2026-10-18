package com.irhunter.core.data

import android.content.Context
import com.irhunter.core.database.ProfileJson
import com.irhunter.core.database.RemoteDatabase
import com.irhunter.core.model.DeviceCategory
import com.irhunter.core.model.RemoteProfile

/**
 * Seeds the curated, license-clean device code subset bundled under `assets/codes/` into the
 * local Room database on first run, so the app is useful offline out of the box.
 *
 * Layout: `codes/<DeviceCategory.name>/<Brand>/<Model>.ir` (Flipper `.ir` documents).
 * Every bundled file comes from the Flipper-IRDB dataset (CC0 1.0); attribution lives in
 * `assets/third_party/ATTRIBUTION.md`.
 *
 * Deterministic ids (`bundle:<path>`) make seeding idempotent: re-running never duplicates.
 * A file that fails to parse is skipped (and reported) rather than aborting the whole seed.
 */
object BundledSeeder {
    const val SOURCE = "Flipper-IRDB"
    const val LICENSE = "CC0-1.0"
    const val ROOT = "codes"

    private const val CATALOG_VERSION = 1
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
        val present = database.profiles().all().mapTo(HashSet()) { it.id }
        val skipped = mutableListOf<String>()
        var added = 0
        var commandCount = 0
        var recordWarnings = 0

        for (categoryName in listDirs(context, ROOT)) {
            val category = runCatching { DeviceCategory.valueOf(categoryName) }.getOrNull()
                ?: continue
            for (brandDir in listDirs(context, "$ROOT/$categoryName")) {
                val brand = pretty(brandDir).ifBlank { "Unknown Brand" }
                for (file in listFiles(context, "$ROOT/$categoryName/$brandDir")) {
                    if (!file.endsWith(".ir")) continue
                    val relPath = "$categoryName/$brandDir/$file"
                    val id = "bundle:$relPath"
                    if (id in present) continue
                    val text = readAsset(context, "$ROOT/$relPath")
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
                    database.profiles().put(
                        ProfileJson.encode(
                            RemoteProfile(
                                id = id,
                                name = "$brand $model",
                                category = category,
                                brand = brand,
                                model = model,
                                source = SOURCE,
                                license = LICENSE,
                                originalText = text,
                                commands = commands,
                                saved = false,
                            )
                        )
                    )
                    present += id
                    added++
                    commandCount += commands.size
                }
            }
        }
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
