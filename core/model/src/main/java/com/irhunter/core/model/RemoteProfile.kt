package com.irhunter.core.model

data class RemoteCommand(val name: String, val signal: IrSignal, val confirmedOff: Boolean = false) {
    val normalized: String get() = name.lowercase().filter(Char::isLetterOrDigit)
    val isOff: Boolean get() = normalized in setOf("off", "poweroff", "standby", "discreteoff")
    val isToggle: Boolean get() = normalized in setOf("power", "powertoggle", "onoff", "poweronoff")
}

data class RemoteProfile(
    val id: String,
    val name: String,
    val category: DeviceCategory,
    val brand: String,
    val model: String,
    val source: String,
    val license: String,
    val originalText: String,
    val commands: List<RemoteCommand>,
    val saved: Boolean = false,
    val commandCount: Int = commands.size,
) {
    fun offCommand(): RemoteCommand? = commands.firstOrNull { it.isOff && it.confirmedOff }
        ?: commands.firstOrNull { it.isOff } ?: commands.firstOrNull { it.isToggle }
    fun scanCommand(): RemoteCommand? = commands.firstOrNull { it.isToggle } ?: commands.firstOrNull { it.isOff }
}
