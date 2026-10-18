package com.irhunter.core.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class BundledCatalogTest {
    @Test fun everyPackagedCatalogFileHasUsableCommands() {
        val root = File("src/main/assets/codes")
        assertThat(root.isDirectory).isTrue()
        val files = root.walkTopDown().filter { it.isFile && it.extension == "ir" }.toList()
        val parsed = files.map { FlipperImporter.parse(it.readText()) }

        assertThat(files).hasSize(121)
        assertThat(parsed.all { it.commands.isNotEmpty() }).isTrue()
        assertThat(parsed.sumOf { it.commands.size }).isEqualTo(2_586)
    }
}
