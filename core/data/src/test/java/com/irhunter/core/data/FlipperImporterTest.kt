package com.irhunter.core.data

import com.irhunter.core.model.*
import org.junit.Assert.*
import org.junit.Test

class FlipperImporterTest {
    private val header = "Filetype: IR signals file\nVersion: 1\n"
    private val command = "name: Power\ntype: parsed\nprotocol: NECext\naddress: EE 87 00 00\ncommand: 5D A0 00 00\n"
    @Test fun importsLittleEndianAndDeduplicates() {
        val result = FlipperImporter.parse(header + command + command)
        assertEquals(1, result.commands.size)
        assertEquals(1, result.warnings.size)
        val signal = result.commands.single().signal as IrSignal.Protocol
        assertEquals(0x87EEL, signal.address); assertEquals(0xA05DL, signal.command)
    }
    @Test fun malformedAndUnsupportedCommandsReportedWithoutDroppingValidCommands() {
        val result = FlipperImporter.parse(header + command + "name: Bad\ntype: raw\nfrequency: 38000\ndata: 1 -2 3\n")
        assertEquals(1, result.commands.size); assertTrue(result.warnings.single().contains("positive"))
    }
    @Test fun rejectsWrongFormatAndEmptyAndOversizedFiles() {
        assertThrows(IllegalArgumentException::class.java) { FlipperImporter.parse(command) }
        assertThrows(IllegalArgumentException::class.java) { FlipperImporter.parse(header) }
        assertThrows(IllegalArgumentException::class.java) { FlipperImporter.parse(header + "#".repeat(1_048_576)) }
    }
}
