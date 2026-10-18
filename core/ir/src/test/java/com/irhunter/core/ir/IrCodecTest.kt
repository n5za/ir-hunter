package com.irhunter.core.ir

import com.irhunter.core.model.*
import org.junit.Assert.*
import org.junit.Test

class IrCodecTest {
    @Test fun necKnownFrameIncludesComplements() {
        val packet = IrCodec.encode(IrSignal.Protocol(IrProtocol.NEC, 0, 0))
        val expected = mutableListOf(9000, 4500)
        listOf(0, 255, 0, 255).forEach { byte -> repeat(8) { bit -> expected += 560; expected += if (byte and (1 shl bit) != 0) 1690 else 560 } }
        expected += 560
        assertArrayEquals(expected.toIntArray(), packet.timings)
    }
    @Test fun everyProtocolRetainsHighAndLowAddressAndCommandBits() {
        IrProtocol.entries.forEach { p ->
            val address = (1L shl p.addressBits) - 1
            val command = (1L shl p.commandBits) - 1
            val original = IrSignal.Protocol(p, address, command, repeat = 1)
            val decoded = IrCodec.decode(p, IrCodec.encode(original).timings)
            assertNotNull(p.name, decoded)
            assertEquals(p.name, address, decoded!!.address)
            assertEquals(p.name, command, decoded.command)
        }
    }
    @Test fun sircContainsThreeFramesAt45MillisecondIntervals() {
        val p = IrCodec.encode(IrSignal.Protocol(IrProtocol.SIRC, 1, 21))
        assertEquals(3, p.timings.count { it == 2400 })
        val first = p.timings.indexOf(2400)
        val second = p.timings.indexOfFirstAfter(first + 1) { it == 2400 }
        assertEquals(45000, p.timings.slice(first until second).sum())
    }
    @Test fun rc6ToggleHasDoubleWidthAndChangesBetweenPresses() {
        val a = ManchesterCodec.encodeRc6(0, 0, false)
        val b = ManchesterCodec.encodeRc6(0, 0, true)
        assertEquals(2666 + 889 + 44 * 444, a.sum())
        assertFalse(a.contentEquals(b))
        assertEquals(0L, ManchesterCodec.decodeRc6(b)!!.command)
    }
    @Test fun rc5UsesSpaceToMarkStartAndToggleChanges() {
        val a = ManchesterCodec.encodeRc5(0, 0, true, false)
        assertEquals(888, a[0])
        assertEquals(27 * 888, a.sum())
        assertFalse(a.contentEquals(ManchesterCodec.encodeRc5(0, 0, true, true)))
    }
    @Test fun rejectsOversizedFieldsAndUnsafePatterns() {
        assertThrows(IllegalArgumentException::class.java) { IrCodec.encode(IrSignal.Protocol(IrProtocol.NEC, 256, 0)) }
        assertThrows(IllegalArgumentException::class.java) { packetFor(IrSignal.Raw(intArrayOf(1, -1), 38000)) }
        assertThrows(IllegalArgumentException::class.java) { packetFor(IrSignal.Raw(intArrayOf(2_000_000), 38000)) }
        assertThrows(IllegalArgumentException::class.java) { packetFor(IrSignal.Raw(intArrayOf(), 38000)) }
    }
    @Test fun rejectsCorruptNecComplementAndTimings() {
        val good = IrCodec.encode(IrSignal.Protocol(IrProtocol.NEC, 0, 0)).timings
        val bad = good.copyOf(); bad[19] = 560
        assertNull(IrCodec.decode(IrProtocol.NEC, bad))
        val noise = good.copyOf(); noise[4] = 9000
        assertNull(IrCodec.decode(IrProtocol.NEC, noise))
    }
    private fun IntArray.indexOfFirstAfter(start: Int, predicate: (Int) -> Boolean) = (start until size).first { predicate(this[it]) }
}
