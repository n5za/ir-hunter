package com.irhunter.core.model

/**
 * A single IR frame: either a structured protocol frame (address + command) or a raw
 * timing capture. This is the canonical in-memory representation used by the encoder,
 * the scan engine, and the transmitter.
 */
sealed interface IrSignal {
    /** The carrier frequency to transmit at, in Hz. */
    val carrierHz: Int

    /**
     * A protocol-encoded frame. [address] and [command] are packed per the protocol's own
     * bit layout (see :core:ir encoders); they are NOT pre-inverted or pre-shifted here —
     * the encoder applies the protocol's complement/parity rules at transmit time.
     */
    data class Protocol(
        val protocol: IrProtocol,
        val address: Long,
        val command: Long,
        /** How many frames to send back-to-back (SIRC=3, Pioneer=2, NEC=1...). */
        val repeat: Int = protocol.repeatCount,
        override val carrierHz: Int = protocol.carrierHz,
    ) : IrSignal

    /**
     * A raw timing capture. [timings] alternate mark/space durations in microseconds and
     * always begin with a MARK (index 0 = mark). This matches what
     * `android.hardware.ConsumerIrManager.transmit()` expects.
     */
    data class Raw(
        val timings: IntArray,
        override val carrierHz: Int,
    ) : IrSignal {
        override fun equals(other: Any?): Boolean =
            other is Raw && other.carrierHz == carrierHz && other.timings.contentEquals(timings)

        override fun hashCode(): Int = 31 * timings.contentHashCode() + carrierHz
    }
}

/**
 * A parsed Flipper `.ir` file signal, before it is normalized into an [IrSignal].
 * Keeps the raw text provenance so importers can attribute + dedupe faithfully.
 */
data class FlipperSignal(
    val name: String,
    val protocol: String?,
    val address: Long?,
    val command: Long?,
    val rawTimings: IntArray?,
    val carrierHz: Int,
    val repeat: Int,
) {
    val isRaw: Boolean get() = rawTimings != null

    override fun equals(other: Any?): Boolean =
        other is FlipperSignal &&
            other.name == name && other.protocol == protocol &&
            other.address == address && other.command == command &&
            other.carrierHz == carrierHz && other.repeat == repeat &&
            ((rawTimings == null && other.rawTimings == null) ||
                (rawTimings != null && other.rawTimings != null && rawTimings.contentEquals(other.rawTimings)))

    override fun hashCode(): Int {
        var h = name.hashCode()
        h = 31 * h + (protocol?.hashCode() ?: 0)
        h = 31 * h + (address?.hashCode() ?: 0)
        h = 31 * h + (command?.hashCode() ?: 0)
        h = 31 * h + (rawTimings?.contentHashCode() ?: 0)
        return h
    }
}

/**
 * The result of encoding an [IrSignal] into a transmit-ready pattern: alternating
 * mark/space durations (index 0 = mark) plus the carrier to use.
 */
data class TransmissionPacket(
    val timings: IntArray,
    val carrierHz: Int,
) {
    /** Total on-air duration in microseconds (sum of all mark + space durations). */
    val totalDurationUs: Long get() = timings.sumOf { it.toLong() }

    override fun equals(other: Any?): Boolean =
        other is TransmissionPacket && other.carrierHz == carrierHz && timings.contentEquals(timings)

    override fun hashCode(): Int = 31 * timings.contentHashCode() + carrierHz
}
