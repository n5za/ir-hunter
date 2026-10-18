package com.irhunter.core.ir

import com.irhunter.core.model.IrProtocol

/**
 * Timing constants for a pulse-distance / pulse-width protocol, taken verbatim from
 * Flipper Zero firmware headers in lib/infrared/encoder_decoder (the *_i.h files). These are
 * documented protocol facts (microsecond durations), not copied source code.
 */
data class PulseTimings(
    val preambleMark: Int,
    val preambleSpace: Int,
    val bit1Mark: Int,
    val bit1Space: Int,
    val bit0Mark: Int,
    val bit0Space: Int,
) {
    /** PWM protocols encode info in the mark length and keep spaces equal. */
    val isPwm: Boolean get() = bit1Space == bit0Space
}

/** Per-protocol encode/decode parameters derived from Flipper's protocol specs. */
object ProtocolTimings {
    private val NEC = PulseTimings(9000, 4500, 560, 1690, 560, 560)
    private val SAMSUNG = PulseTimings(4500, 4500, 550, 1650, 550, 550)
    private val SIRC = PulseTimings(2400, 600, 1200, 600, 600, 600)
    private val RCA = PulseTimings(4000, 4000, 500, 2000, 500, 1000)
    private val KASEIKYO = PulseTimings(8 * 432, 4 * 432, 432, 3 * 432, 432, 432)
    private val PIONEER = PulseTimings(8500, 4225, 500, 1500, 500, 500)

    fun forProtocol(protocol: IrProtocol): PulseTimings? = when (protocol) {
        IrProtocol.NEC, IrProtocol.NEC_EXT, IrProtocol.NEC_42, IrProtocol.NEC_42_EXT -> NEC
        IrProtocol.SAMSUNG32 -> SAMSUNG
        IrProtocol.SIRC, IrProtocol.SIRC_15, IrProtocol.SIRC_20 -> SIRC
        IrProtocol.RCA -> RCA
        IrProtocol.KASEIKYO -> KASEIKYO
        IrProtocol.PIONEER -> PIONEER
        // Manchester protocols are handled by [ManchesterCodec], not pulse tables.
        IrProtocol.RC5, IrProtocol.RC5X, IrProtocol.RC6 -> null
    }
}
