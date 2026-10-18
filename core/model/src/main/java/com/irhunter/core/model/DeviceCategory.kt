package com.irhunter.core.model

/**
 * The 13 device categories IR Hunter organizes codes and scans around.
 * [ALL_DEVICES] is not a physical category but the bulk "everything off" workflow.
 */
enum class DeviceCategory(
    val displayName: String,
    val iconGlyph: String,
    /** Whether a guided scan for this category makes sense (AC/Other use stateful/raw flows). */
    val supportsGuidedScan: Boolean,
) {
    TV("TV", "📺", supportsGuidedScan = true),
    MI_TV("Mi TV / Mi Box", "⬛", supportsGuidedScan = true),
    SET_TOP_BOX("Set-top Box", "📡", supportsGuidedScan = true),
    AC("Air Conditioner", "❄️", supportsGuidedScan = false),
    FAN("Fan", "🌀", supportsGuidedScan = true),
    SMART_BOX("Smart Box", "▶️", supportsGuidedScan = true),
    AV_RECEIVER("A/V Receiver", "🔊", supportsGuidedScan = true),
    DVD("DVD / Blu-ray", "💿", supportsGuidedScan = true),
    PROJECTOR("Projector", "📽️", supportsGuidedScan = true),
    CHINESE_SATELLITE("Chinese Satellite", "🛰️", supportsGuidedScan = true),
    CAMERA("Camera", "📷", supportsGuidedScan = true),
    OTHER("Other", "❔", supportsGuidedScan = true),
    ALL_DEVICES("All Devices", "🔌", supportsGuidedScan = false);

    companion object {
        /** Categories a device profile can actually be created for (excludes ALL_DEVICES). */
        val selectable: List<DeviceCategory> = entries.filter { it != ALL_DEVICES }
    }
}
