package com.irhunter.core.ir

import com.irhunter.core.model.IrProtocol
import com.irhunter.core.model.IrSignal
import com.irhunter.core.model.TransmissionPacket
import kotlin.math.abs

/** Thrown when a signal cannot be encoded (e.g. address/command exceed the protocol's bit width). */
class IrEncodingException(message: String) : Exception(message)

/**
 * Encodes structured [IrSignal.Protocol] values into transmit-ready [TransmissionPacket]s and
 * decodes raw timing captures back into address/command pairs. Implements the Flipper Zero
 * protocol set (NEC family, Samsung32, SIRC, RCA, Kaseikyo, Pioneer, RC5/RC5X/RC6).
 *
 * Encoding rules follow protocol layouts. Physical device compatibility requires hardware testing.
 */
object IrCodec {

    // ---------------------------------------------------------------------
    // Encoding
    // ---------------------------------------------------------------------

    /** Encode a protocol signal into an alternating mark/space timing array (index 0 = mark). */
    fun encode(signal: IrSignal.Protocol, toggle: Boolean = false): TransmissionPacket {
        val protocol = signal.protocol
        require(signal.address in 0 until (1L shl protocol.addressBits)) { "Address exceeds protocol width" }
        require(signal.command in 0 until (1L shl protocol.commandBits)) { "Command exceeds protocol width" }
        require(signal.repeat in 1..10) { "Repeat must be 1–10" }
        val timings = when {
            protocol == IrProtocol.RC5 || protocol == IrProtocol.RC5X ->
                ManchesterCodec.encodeRc5(signal.address, signal.command, isRc5 = protocol == IrProtocol.RC5, toggle = toggle)
            protocol == IrProtocol.RC6 -> ManchesterCodec.encodeRc6(signal.address, signal.command, toggle)
            else -> encodePulseDistance(protocol, signal.address, signal.command)
        }
        val repeated = ArrayList<Int>()
        repeat(signal.repeat) { index ->
            if (index > 0) {
                val gap = when (protocol) {
                    IrProtocol.SIRC, IrProtocol.SIRC_15, IrProtocol.SIRC_20 -> (45000 - timings.sum()).coerceAtLeast(1000)
                    IrProtocol.PIONEER -> 26000
                    else -> 40000
                }
                if (repeated.size % 2 == 1) repeated += gap else repeated[repeated.lastIndex] += gap
            }
            repeated.addAll(timings.toList())
        }
        return TransmissionPacket(repeated.toIntArray(), signal.carrierHz).also { validatePacket(it) }
    }

    private fun encodePulseDistance(protocol: IrProtocol, address: Long, command: Long): IntArray {
        val t = ProtocolTimings.forProtocol(protocol)
            ?: throw IrEncodingException("No pulse timings for $protocol")
        val bits = packBits(protocol, address, command)
        require(bits.size <= protocolTotalBits(protocol)) {
            "$protocol frame exceeds bit width"
        }

        val out = ArrayList<Int>(2 + bits.size * 2 + 1)
        out += t.preambleMark
        out += t.preambleSpace
        for (bit in bits) {
            out += if (bit) t.bit1Mark else t.bit0Mark
            out += if (bit) t.bit1Space else t.bit0Space
        }
        if (t.isPwm) {
            // PWM (SIRC): the final space is omitted — frame ends on the last mark.
            out.removeAt(out.size - 1)
        } else if (protocol.hasStopBit) {
            // Pulse-distance: append the stop-bit mark.
            out += t.bit0Mark
        }
        return out.toIntArray()
    }

    // ---------------------------------------------------------------------
    // Decoding
    // ---------------------------------------------------------------------

    /**
     * Decode a mark-first raw timing capture into a protocol signal. [timings] must begin with a
     * mark and contain at least one full frame. Returns null if the timings do not match [protocol].
     */
    fun decode(protocol: IrProtocol, timings: IntArray, carrierHz: Int = protocol.carrierHz): IrSignal.Protocol? {
        return when {
            protocol == IrProtocol.RC5 || protocol == IrProtocol.RC5X -> ManchesterCodec.decodeRc5(timings, protocol)?.let { IrSignal.Protocol(it.protocol, it.address, it.command) }
            protocol == IrProtocol.RC6 -> ManchesterCodec.decodeRc6(timings)?.let { IrSignal.Protocol(it.protocol, it.address, it.command) }
            else -> decodePulseDistance(protocol, timings)
        }?.copy(carrierHz = carrierHz)
    }

    private fun decodePulseDistance(protocol: IrProtocol, timings: IntArray): IrSignal.Protocol? {
        val t = ProtocolTimings.forProtocol(protocol) ?: return null
        var i = 0
        if (!match(timings, i, t.preambleMark, PREAMBLE_TOL)) return null
        if (!match(timings, i + 1, t.preambleSpace, PREAMBLE_TOL)) return null
        i = 2

        val totalBits = protocolTotalBits(protocol)
        val bits = BooleanArray(totalBits)
        var bitIndex = 0
        while (bitIndex < totalBits && i < timings.size) {
            val mark = timings[i]
            val space = if (t.isPwm && bitIndex == totalBits - 1) t.bit0Space else timings.getOrNull(i + 1) ?: return null
            if (minOf(abs(mark - t.bit1Mark), abs(mark - t.bit0Mark)) > 250 ||
                minOf(abs(space - t.bit1Space), abs(space - t.bit0Space)) > 350) return null
            val one = if (t.isPwm) {
                abs(mark - t.bit1Mark) < abs(mark - t.bit0Mark)
            } else {
                abs(space - t.bit1Space) < abs(space - t.bit0Space)
            }
            bits[bitIndex] = one
            bitIndex++
            i += 2
        }
        if (bitIndex < totalBits) return null
        if (protocol.hasStopBit && !match(timings, i, t.bit0Mark, 250)) return null

        val (address, command) = unpackBits(protocol, bits)
        if (!packBits(protocol, address, command).contentEquals(bits)) return null
        return IrSignal.Protocol(protocol, address, command)
    }

    // ---------------------------------------------------------------------
    // Bit packing (per-protocol, mirroring Flipper's encoder layouts)
    // ---------------------------------------------------------------------

    private fun protocolTotalBits(protocol: IrProtocol): Int = when (protocol) {
        IrProtocol.NEC, IrProtocol.NEC_EXT -> 32
        IrProtocol.NEC_42, IrProtocol.NEC_42_EXT -> 42
        IrProtocol.SAMSUNG32 -> 32
        IrProtocol.RCA -> 24
        IrProtocol.KASEIKYO -> 48
        IrProtocol.PIONEER -> 32
        IrProtocol.SIRC -> 12
        IrProtocol.SIRC_15 -> 15
        IrProtocol.SIRC_20 -> 20
        else -> 0
    }

    /** Pack (address, command) into the LSB-first bit list Flipper transmits for [protocol]. */
    fun packBits(protocol: IrProtocol, address: Long, command: Long): BooleanArray {
        val bits = when (protocol) {
            IrProtocol.NEC -> {
                // addr | ~addr<<8 | cmd<<16 | ~cmd<<24
                val v = (address and 0xFF) or
                    ((address.inv() and 0xFF) shl 8) or
                    ((command and 0xFF) shl 16) or
                    ((command.inv() and 0xFF) shl 24)
                bitsFromLong(v, 32)
            }
            IrProtocol.NEC_EXT -> {
                val v = (address and 0xFFFF) or ((command and 0xFFFF) shl 16)
                bitsFromLong(v, 32)
            }
            IrProtocol.NEC_42 -> {
                val v = (address and 0x1FFF) or
                    ((address.inv() and 0x1FFF) shl 13) or
                    ((command and 0xFF) shl 26) or ((command.inv() and 0xFF) shl 34)
                bitsFromLong(v, 42)
            }
            IrProtocol.NEC_42_EXT -> {
                val v = (address and 0x3FFFFFF) or ((command and 0xFFFF) shl 26)
                bitsFromLong(v, 42)
            }
            IrProtocol.SAMSUNG32 -> {
                val v = (address and 0xFF) or ((address and 0xFF) shl 8) or
                    ((command and 0xFF) shl 16) or ((command.inv() and 0xFF) shl 24)
                bitsFromLong(v, 32)
            }
            IrProtocol.RCA -> {
                val v = (address and 0xF) or ((command and 0xFF) shl 4) or
                    ((address.inv() and 0xF) shl 12) or ((command.inv() and 0xFF) shl 16)
                bitsFromLong(v, 24)
            }
            IrProtocol.PIONEER -> {
                // addr8 | ~addr8 | cmd8 | ~cmd8; the encoder appends the stop mark.
                val v = (address and 0xFF) or ((address.inv() and 0xFF) shl 8) or
                    ((command and 0xFF) shl 16) or ((command.inv() and 0xFF) shl 24)
                bitsFromLong(v, 32)
            }
            IrProtocol.KASEIKYO -> kaseikyoBits(address, command)
            IrProtocol.SIRC -> sircBits(address, command, 12)
            IrProtocol.SIRC_15 -> sircBits(address, command, 15)
            IrProtocol.SIRC_20 -> sircBits(address, command, 20)
            else -> throw IrEncodingException("packBits unsupported for $protocol")
        }
        return bits
    }

    /** Inverse of [packBits]: recover (address, command) from the LSB-first bit list. */
    fun unpackBits(protocol: IrProtocol, bits: BooleanArray): Pair<Long, Long> = when (protocol) {
        IrProtocol.NEC -> {
            val v = longFromBits(bits, 32)
            val addr = v and 0xFF
            val cmd = (v shr 16) and 0xFF
            addr to cmd
        }
        IrProtocol.NEC_EXT -> {
            val v = longFromBits(bits, 32)
            (v and 0xFFFF) to ((v shr 16) and 0xFFFF)
        }
        IrProtocol.NEC_42 -> {
            val v = longFromBits(bits, 42)
            val addr = v and 0x1FFF
            val cmd = ((v shr 26) and 0x3F) or (((v shr 32) and 0x3) shl 6)
            addr to cmd
        }
        IrProtocol.NEC_42_EXT -> {
            val v = longFromBits(bits, 42)
            val addr = v and 0x3FFFFFF
            val cmd = (v shr 26) and 0xFFFF
            addr to cmd
        }
        IrProtocol.SAMSUNG32 -> {
            val v = longFromBits(bits, 32)
            (v and 0xFF) to ((v shr 16) and 0xFF)
        }
        IrProtocol.RCA -> {
            val v = longFromBits(bits, 24)
            (v and 0xF) to ((v shr 4) and 0xFF)
        }
        IrProtocol.PIONEER -> {
            val v = longFromBits(bits, 32)
            (v and 0xFF) to ((v shr 16) and 0xFF)
        }
        IrProtocol.KASEIKYO -> {
            val bytes = ByteArray(6)
            for (b in 0 until 6) {
                var byte = 0
                for (bit in 0 until 8) if (bits[b * 8 + bit]) byte = byte or (1 shl bit)
                bytes[b] = byte.toByte()
            }
            val vendorId = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)
            val genre1 = (bytes[2].toInt() shr 4) and 0xF
            val genre2 = bytes[3].toInt() and 0xF
            val data = ((bytes[3].toInt() shr 4) and 0xF) or (((bytes[4].toInt() and 0x3F)) shl 4)
            val id = (bytes[4].toInt() shr 6) and 0x3
            val address = (id.toLong() shl 24) or (vendorId.toLong() shl 8) or
                (genre1.toLong() shl 4) or genre2.toLong()
            address to data.toLong()
        }
        IrProtocol.SIRC, IrProtocol.SIRC_15, IrProtocol.SIRC_20 -> {
            val n = protocolTotalBits(protocol)
            val v = longFromBits(bits, n)
            val cmd = v and 0x7F
            val addr = (v shr 7) and ((1L shl protocol.addressBits) - 1)
            addr to cmd
        }
        else -> throw IrEncodingException("unpackBits unsupported for $protocol")
    }

    private fun kaseikyoBits(address: Long, command: Long): BooleanArray {
        val id = (address shr 24) and 0x3
        val vendorId = (address shr 8) and 0xFFFF
        val genre1 = (address shr 4) and 0xF
        val genre2 = address and 0xF
        val b0 = (vendorId and 0xFF).toInt()
        val b1 = ((vendorId shr 8) and 0xFF).toInt()
        var parity = b0 xor b1
        parity = (parity and 0xF) xor ((parity shr 4) and 0xF)
        val b2 = ((parity and 0xF) or ((genre1.toInt() and 0xF) shl 4))
        val b3 = ((genre2.toInt() and 0xF) or (((command and 0xF).toInt()) shl 4))
        val b4 = ((id.toInt() shl 6) or (((command shr 4) and 0xFF).toInt()))
        val b5 = b2 xor b3 xor b4
        val bytes = intArrayOf(b0, b1, b2, b3, b4, b5)
        val bits = BooleanArray(48)
        for (bi in 0 until 6) for (bit in 0 until 8) bits[bi * 8 + bit] = ((bytes[bi] shr bit) and 1) == 1
        return bits
    }

    private fun sircBits(address: Long, command: Long, totalBits: Int): BooleanArray {
        val v = (command and 0x7F) or (address shl 7)
        return bitsFromLong(v, totalBits)
    }

    // ---------------------------------------------------------------------
    // Bit <-> long helpers (LSB-first)
    // ---------------------------------------------------------------------

    private fun bitsFromLong(value: Long, count: Int): BooleanArray {
        val bits = BooleanArray(count)
        for (i in 0 until count) bits[i] = ((value shr i) and 1L) == 1L
        return bits
    }

    private fun longFromBits(bits: BooleanArray, count: Int): Long {
        var v = 0L
        for (i in 0 until count) if (bits[i]) v = v or (1L shl i)
        return v
    }

    private fun match(timings: IntArray, index: Int, nominal: Int, tol: Int): Boolean =
        index < timings.size && abs(timings[index] - nominal) <= tol

    /** Preamble durations tolerate real-world capture jitter (Flipper uses ~200us; we allow more). */
    private const val PREAMBLE_TOL = 700
}
