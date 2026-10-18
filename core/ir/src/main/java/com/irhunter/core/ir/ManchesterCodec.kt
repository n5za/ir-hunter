package com.irhunter.core.ir

import com.irhunter.core.model.IrProtocol
import kotlin.math.abs

/**
 * Manchester / biphase codecs for RC5, RC5X and RC6, derived from Flipper's protocol specs.
 *
 * RC5 and RC5X: 14 bits, half-unit 888us, starts from space, no preamble.
 * RC5 uses space->mark for logic 1; RC6 uses mark->space. Adjacent same-level half-units merge into a double-length pulse.
 *
 * RC6: leader 2666/889, then a start bit, a 3-bit mode field, a double-width toggle bit,
 * then 8-bit address + 8-bit command in biphase. Half-unit 444us.
 */
object ManchesterCodec {

    private const val RC5_HALF = 888
    private const val RC6_HALF = 444
    private const val RC6_LEADER_MARK = 2666
    private const val RC6_LEADER_SPACE = 889
    private const val TOL = 250

    // ---------------------------------------------------------------------
    // RC5 / RC5X
    // ---------------------------------------------------------------------

    /**
     * RC5 frame bits (MSB-first on the wire):
     *   S1(always 1) | S2(1 for RC5, 0 for RC5X) | toggle | addr(5, MSB-first) | cmd(6, MSB-first)
     * Both variants contain 14 wire bits; RC5X carries command bit 6 in the inverted S2 field.
     * We build the wire-order bit list directly.
     */
    fun encodeRc5(address: Long, command: Long, isRc5: Boolean, toggle: Boolean = false): IntArray {
        val s1 = 1
        val s2 = if (isRc5 || command and 0x40L == 0L) 1 else 0
        // Wire order (first bit transmitted first):
        val wire = ArrayList<Boolean>(14)
        wire += s1 == 1
        wire += s2 == 1
        wire += toggle
        // address MSB-first (5 bits)
        for (i in 4 downTo 0) wire += ((address shr i) and 1L) == 1L
        // command: RC5 uses 6 bits, RC5X uses 7 (top bit replaces S2 semantics); MSB-first
        val cmdBits = 6
        for (i in cmdBits - 1 downTo 0) wire += ((command shr i) and 1L) == 1L
        return biphaseToTimings(wire, RC5_HALF, startFromMark = false)
    }

    fun decodeRc5(timings: IntArray, protocol: IrProtocol): IrSignalProtocolOrNull? {
        val captured = timingsToHalfUnits(timings, RC5_HALF, TOL) ?: return null
        val halfUnits = booleanArrayOf(false) + captured
        // Skip leading silence already handled; decode Manchester into bits.
        val isRc5 = protocol == IrProtocol.RC5
        val expectedBits = 14
        val bits = decodeManchester(halfUnits.map { !it }.toBooleanArray(), expectedBits) ?: return null
        // bits[0]=S1 must be 1
        if (!bits[0]) return null
        val s2 = bits[1]
        var addr = 0L
        for (i in 0 until 5) if (bits[3 + i]) addr = addr or (1L shl (4 - i))
        val cmdBits = 6
        var cmd = if (!isRc5 && !s2) 64L else 0L
        if (isRc5 && !s2) return null
        for (i in 0 until cmdBits) if (bits[8 + i]) cmd = cmd or (1L shl (cmdBits - 1 - i))
        // command bit index: RC5 command occupies bits 8..13 (6 bits) for RC5.
        return IrSignalProtocolOrNull(protocol, addr, cmd)
    }

    // ---------------------------------------------------------------------
    // RC6
    // ---------------------------------------------------------------------

    fun encodeRc6(address: Long, command: Long, toggle: Boolean = false): IntArray {
        val wire = ArrayList<Boolean>(21)
        // Leader handled separately. Start bit = 1.
        wire += true
        // Mode (3 bits) = 0 for RC6 Mode 0.
        wire += false; wire += false; wire += false
        // Toggle bit (double width), flipped for each independent press.
        wire += toggle
        // Address (8 bits) MSB-first.
        for (i in 7 downTo 0) wire += ((address shr i) and 1L) == 1L
        // Command (8 bits) MSB-first.
        for (i in 7 downTo 0) wire += ((command shr i) and 1L) == 1L

        val levels = ArrayList<Boolean>()
        wire.forEachIndexed { index, bit ->
            repeat(if (index == 4) 2 else 1) { levels += bit }
            repeat(if (index == 4) 2 else 1) { levels += !bit }
        }
        return intArrayOf(RC6_LEADER_MARK, RC6_LEADER_SPACE) + mergeToTimings(levels, RC6_HALF)
    }

    fun decodeRc6(timings: IntArray): IrSignalProtocolOrNull? {
        if (timings.size < 4) return null
        if (abs(timings[0] - RC6_LEADER_MARK) > 600) return null
        if (abs(timings[1] - RC6_LEADER_SPACE) > 400) return null
        val body = timings.copyOfRange(2, timings.size)
        val halfUnits = timingsToHalfUnits(body, RC6_HALF, TOL) ?: return null
        // 1 start + 3 mode + 1 toggle(double) + 8 addr + 8 cmd.
        // Toggle is double-width => account when decoding half-unit stream.
        val bits = decodeRc6Bits(halfUnits) ?: return null
        if (bits.size < 21 || !bits[0] || bits[1] || bits[2] || bits[3]) return null
        // bits: [0]=start, [1..3]=mode, [4]=toggle, [5..12]=addr, [13..20]=cmd
        var addr = 0L
        for (i in 0 until 8) if (bits[5 + i]) addr = addr or (1L shl (7 - i))
        var cmd = 0L
        for (i in 0 until 8) if (bits[13 + i]) cmd = cmd or (1L shl (7 - i))
        return IrSignalProtocolOrNull(IrProtocol.RC6, addr, cmd)
    }

    // ---------------------------------------------------------------------
    // Biphase helpers
    // ---------------------------------------------------------------------

    /**
     * Convert a wire-order bit list to an alternating mark/space timing array.
     * Biphase: logic 1 = mark then space, logic 0 = space then mark (mark-first variant),
     * with adjacent equal levels merged into a 2x pulse. Index 0 = mark.
     */
    private fun biphaseToTimings(bits: List<Boolean>, half: Int, startFromMark: Boolean): IntArray {
        // Build half-unit level stream (true=mark).
        val levels = ArrayList<Boolean>(bits.size * 2)
        for (bit in bits) {
            levels += if (startFromMark) bit else !bit
            levels += if (startFromMark) !bit else bit
        }
        // Merge runs of equal levels into durations, ensuring index 0 is a mark.
        return mergeToTimings(levels, half)
    }

    private fun mergeToTimings(levels: List<Boolean>, half: Int): IntArray {
        if (levels.isEmpty()) return IntArray(0)
        val durations = ArrayList<Int>()
        var current = levels[0]
        var count = 1
        for (i in 1 until levels.size) {
            if (levels[i] == current) count++ else { durations += count * half; current = levels[i]; count = 1 }
        }
        durations += count * half
        // If the stream starts with a space, omit idle time so ConsumerIrManager sees mark-first.
        val startsMark = levels[0]
        return if (startsMark) durations.toIntArray() else {
            // Consumer IR starts with a mark; omit the initial idle half-unit.
            durations.drop(1).toIntArray()
        }
    }

    /** Expand a mark-first timing array into a boolean half-unit level stream (true=mark). */
    private fun timingsToHalfUnits(timings: IntArray, half: Int, tol: Int): BooleanArray? {
        if (timings.isEmpty()) return null
        val out = ArrayList<Boolean>()
        for ((i, d) in timings.withIndex()) {
            val units = Math.round(d.toFloat() / half)
            if (units <= 0 || units > 4 || abs(d - units * half) > tol) return null
            val isMark = i % 2 == 0
            repeat(units) { out += isMark }
        }
        return out.toBooleanArray()
    }

    /** Decode a level stream into bits (mark-first biphase, logic1 = mark->space). */
    private fun decodeManchester(levels: BooleanArray, expectedBits: Int): List<Boolean>? {
        val bits = ArrayList<Boolean>(expectedBits)
        var i = 0
        while (bits.size < expectedBits && i + 1 < levels.size) {
            val a = levels[i]
            val b = levels[i + 1]
            bits += if (a && !b) true else if (!a && b) false else {
                // merged pulse: a double-length first half means the bit is the opposite phase
                return null
            }
            i += 2
        }
        return if (bits.size == expectedBits) bits else null
    }

    private fun decodeRc6Bits(levels: BooleanArray): List<Boolean>? {
        // RC6 biphase with a double-width toggle bit. Decode start(1) + mode(3) normally,
        // then handle toggle as a double unit, then addr+cmd.
        val bits = ArrayList<Boolean>()
        var i = 0
        // start + 3 mode bits = 4 bits, each 2 half-units
        repeat(4) {
            if (i + 1 >= levels.size) return null
            val a = levels[i]; val b = levels[i + 1]
            bits += if (a && !b) true else if (!a && b) false else return null
            i += 2
        }
        // toggle: double-width. Consume 4 half-units, decode value from first two levels' phase.
        if (i + 3 >= levels.size) return null
        val ta = levels[i]; val tb = levels[i + 2]
        if (levels[i + 1] != ta || levels[i + 3] != tb) return null
        bits += if (ta && !tb) true else if (!ta && tb) false else return null
        i += 4 // skip double-width
        // addr (8) + cmd (8)
        repeat(16) {
            if (i + 1 >= levels.size) return null
            val a = levels[i]; val b = levels[i + 1]
            bits += if (a && !b) true else if (!a && b) false else return null
            i += 2
        }
        return bits
    }
}

/** Lightweight decode result (address, command, protocol) to avoid a circular model import. */
data class IrSignalProtocolOrNull(val protocol: IrProtocol, val address: Long, val command: Long)
