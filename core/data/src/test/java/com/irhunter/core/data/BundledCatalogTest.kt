package com.irhunter.core.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class BundledCatalogTest {
    @Test fun everyPackagedCatalogFileHasUsableCommands() {
        val roots = listOf(File("src/main/assets/codes"), File("src/main/assets/codes_flipper_mit"))
        assertThat(roots.all { it.isDirectory }).isTrue()
        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "ir" }.toList() }
        val results = files.map { it to runCatching { FlipperImporter.parse(it.readText()) } }
        val failures = results.filter { it.second.isFailure }.map { "${it.first}: ${it.second.exceptionOrNull()?.message}" }
        assertThat(failures).isEmpty()
        val parsed = results.map { it.second.getOrThrow() }

        assertThat(files).hasSize(5_554)
        assertThat(parsed.all { it.commands.isNotEmpty() }).isTrue()
        assertThat(parsed.sumOf { it.commands.size }).isEqualTo(177_992)
    }
}
