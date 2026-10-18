package com.irhunter.core.data

import com.irhunter.core.ir.packetFor
import com.irhunter.core.model.*

/** Bounded .ir importer. Keeps the original document in the profile for attribution. */
object FlipperImporter {
    data class Result(val commands: List<RemoteCommand>, val warnings: List<String>)
    fun parse(text: String): Result {
        require(text.toByteArray().size <= 1_048_576) { "File exceeds 1 MiB" }
        require(text.lineSequence().any { it.trim() == "Filetype: IR signals file" }) { "Expected a Flipper IR signals file" }
        require(text.lineSequence().any { it.trim() == "Version: 1" }) { "Only IR file version 1 is supported" }
        val commands = mutableListOf<RemoteCommand>()
        val warnings = mutableListOf<String>()
        var fields = linkedMapOf<String, String>()
        fun flush() {
            if (fields.isEmpty()) return
            val name = fields["name"] ?: "Unnamed"
            try {
                require(name.length <= 100) { "Command name too long" }
                val signal = when (fields["type"]) {
                    "raw" -> {
                        val data = fields.getValue("data").trim().split(Regex("\\s+"))
                        require(data.size <= 10000) { "Too many timings" }
                        IrSignal.Raw(data.map(String::toInt).toIntArray(), fields.getValue("frequency").toInt())
                    }
                    "parsed" -> {
                        val p = IrProtocol.fromWireName(fields["protocol"]) ?: error("Unsupported protocol ${fields["protocol"]}")
                        val command = littleEndian(fields.getValue("command"))
                        IrSignal.Protocol(p, littleEndian(fields.getValue("address")),
                            if (p == IrProtocol.RC5X && command < 64) command or 64 else command)
                    }
                    else -> error("Unsupported signal type")
                }
                packetFor(signal)
                val command = RemoteCommand(name, signal)
                if (commands.any { it.name.equals(name, true) && it.signal == signal }) warnings += "$name: duplicate skipped"
                else {
                    require(commands.none { it.name.equals(name, true) }) { "Conflicting command name" }
                    commands += command
                }
            } catch (e: Exception) { warnings += "$name: ${e.message}" }
            fields = linkedMapOf()
        }
        text.lineSequence().forEach { line ->
            val clean = line.trim()
            if (clean.isNotBlank() && !clean.startsWith("#") && ':' in clean) {
                val key = clean.substringBefore(':').trim()
                val value = clean.substringAfter(':').trim()
                if (key == "name") flush()
                if (key !in setOf("Filetype", "Version")) fields[key] = value
            }
        }
        flush()
        require(commands.isNotEmpty()) { "No usable commands. ${warnings.take(5).joinToString()}" }
        return Result(commands, warnings)
    }
    private fun littleEndian(value: String): Long {
        val bytes = value.trim().split(Regex("\\s+"))
        require(bytes.size == 4 && bytes.all { it.matches(Regex("[0-9a-fA-F]{2}")) }) { "Expected four little-endian hex bytes" }
        return bytes.mapIndexed { i, b -> b.toLong(16) shl (8 * i) }.fold(0L) { a, b -> a or b }
    }
}
