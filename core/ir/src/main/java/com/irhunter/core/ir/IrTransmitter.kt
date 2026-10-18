package com.irhunter.core.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.irhunter.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun validatePacket(packet: TransmissionPacket) {
    require(packet.carrierHz in 1..1_000_000) { "Invalid carrier frequency" }
    require(packet.timings.isNotEmpty() && packet.timings.size <= 10000) { "Invalid pattern length" }
    require(packet.timings.all { it > 0 }) { "Durations must be positive" }
    require(packet.totalDurationUs < 2_000_000) { "Pattern must be shorter than two seconds" }
}

fun packetFor(signal: IrSignal): TransmissionPacket = when (signal) {
    is IrSignal.Protocol -> IrCodec.encode(signal)
    is IrSignal.Raw -> TransmissionPacket(signal.timings, signal.carrierHz)
}.also(::validatePacket)

fun interface IrTransmitter {
    /** Returning means sent, never proof that the target responded. */
    suspend fun send(signal: IrSignal)
}

class AndroidIrTransmitter(context: Context) : IrTransmitter {
    private val manager = context.getSystemService(ConsumerIrManager::class.java)
    private val mutex = Mutex()
    private val toggles = mutableMapOf<Pair<IrProtocol, Long>, Boolean>()
    val available: Boolean get() = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)
    val hardwareMessage: String get() = if (available) "IR emitter detected. Point it at your device." else
        "No compatible built-in IR emitter detected. You can import and manage remotes, but this phone cannot transmit. USB and Bluetooth blasters are not supported."

    override suspend fun send(signal: IrSignal) = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(available) { hardwareMessage }
            val key = (signal as? IrSignal.Protocol)?.let { it.protocol to it.address }
            val toggle = key?.let { toggles[it] } ?: false
            val packet = if (signal is IrSignal.Protocol) IrCodec.encode(signal, toggle) else packetFor(signal)
            val ranges = manager?.carrierFrequencies
            check(!ranges.isNullOrEmpty()) { "The phone did not report supported IR carrier frequencies." }
            check(ranges.any { packet.carrierHz in it.minFrequency..it.maxFrequency }) {
                "The phone does not support ${packet.carrierHz} Hz."
            }
            currentCoroutineContext().ensureActive()
            manager!!.transmit(packet.carrierHz, packet.timings)
            if (key != null) toggles[key] = !toggle
        }
    }
}
