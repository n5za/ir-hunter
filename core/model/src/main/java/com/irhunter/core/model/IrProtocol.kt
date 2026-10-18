package com.irhunter.core.model

/**
 * The IR protocols IR Hunter can encode and decode. These mirror the Flipper Zero
 * protocol set (the authoritative offline dataset we curate from). Bit-packing and
 * timing rules live in :core:ir; this is only the identifier + metadata.
 */
enum class IrProtocol(
    val displayName: String,
    /** Address field width in bits ( Flipper `address_length`). */
    val addressBits: Int,
    /** Command field width in bits ( Flipper `command_length`). */
    val commandBits: Int,
    /** Carrier frequency in Hz used when encoding. */
    val carrierHz: Int,
    /** Frames transmitted per press (stateless protocols repeat; SIRC repeats 3x, etc.). */
    val repeatCount: Int,
    /** Whether the encoder appends a trailing stop-bit mark (pulse-distance does, PWM does not). */
    val hasStopBit: Boolean,
) {
    NEC("NEC", 8, 8, 38_000, 1, true),
    NEC_EXT("NECext", 16, 16, 38_000, 1, true),
    NEC_42("NEC42", 13, 8, 38_000, 1, true),
    NEC_42_EXT("NEC42ext", 26, 16, 38_000, 1, true),
    SAMSUNG32("Samsung32", 8, 8, 38_000, 1, true),
    RC5("RC5", 5, 6, 36_000, 1, false),
    RC5X("RC5X", 5, 7, 36_000, 1, false),
    RC6("RC6", 8, 8, 36_000, 1, false),
    SIRC("SIRC", 5, 7, 40_000, 3, false),
    SIRC_15("SIRC15", 8, 7, 40_000, 3, false),
    SIRC_20("SIRC20", 13, 7, 40_000, 3, false),
    KASEIKYO("Kaseikyo", 26, 10, 37_000, 1, true),
    RCA("RCA", 4, 8, 38_000, 1, true),
    PIONEER("Pioneer", 8, 8, 40_000, 2, true),
    ;

    /** True for pulse-distance / pulse-width frame protocols (everything except RC5/RC6 Manchester). */
    val isPulseDistance: Boolean
        get() = this != RC5 && this != RC5X && this != RC6

    companion object {
        /**
         * Tolerant lookup used by importers: Flipper `.ir` files and irdb CSVs use slightly
         * different spellings. Returns null when the protocol is genuinely unsupported.
         */
        fun fromWireName(raw: String?): IrProtocol? {
            if (raw.isNullOrBlank()) return null
            return when (raw.trim().lowercase().replace("_", "").replace("-", "").replace(" ", "")) {
                "nec" -> NEC
                "necext", "necextended", "necext32" -> NEC_EXT
                "nec42" -> NEC_42
                "nec42ext", "nec42extended" -> NEC_42_EXT
                "samsung32", "samsung" -> SAMSUNG32
                "rc5" -> RC5
                "rc5x" -> RC5X
                "rc6" -> RC6
                "sirc", "sony12", "sony" -> SIRC
                "sirc15", "sony15" -> SIRC_15
                "sirc20", "sony20" -> SIRC_20
                "kaseikyo", "panasonic" -> KASEIKYO
                "rca" -> RCA
                "pioneer" -> PIONEER
                else -> null
            }
        }
    }
}
