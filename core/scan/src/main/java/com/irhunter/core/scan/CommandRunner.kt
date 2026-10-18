package com.irhunter.core.scan

import com.irhunter.core.ir.IrTransmitter
import com.irhunter.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

data class Candidate(val profile: RemoteProfile, val command: RemoteCommand)

object Candidates {
    fun ordered(profiles: List<RemoteProfile>, category: DeviceCategory, brand: String, model: String): List<Candidate> =
        profiles.filter { it.category == category && (brand.isBlank() || it.brand.contains(brand, true)) &&
            (model.isBlank() || it.model.contains(model, true) || it.name.contains(model, true)) }
            .sortedWith(compareBy({ it.brand.lowercase() }, { it.model.lowercase() }, { it.id }))
            .mapNotNull { p -> p.scanCommand()?.let { Candidate(p, it) } }
            .distinctBy { it.command.signal }
}

class CommandRunner(private val transmitter: IrTransmitter) {
    suspend fun scan(candidates: List<Candidate>, onCandidate: (Int, Candidate) -> Unit, onSent: (Candidate) -> Unit) {
        candidates.forEachIndexed { index, c ->
            currentCoroutineContext().ensureActive()
            onCandidate(index, c)
            transmitter.send(c.command.signal)
            currentCoroutineContext().ensureActive()
            onSent(c)
            delay(3000)
        }
    }
    suspend fun allOff(profiles: List<RemoteProfile>, status: (String, String) -> Unit) {
        profiles.forEach { p ->
            currentCoroutineContext().ensureActive()
            val c = p.offCommand()
            if (c == null) status(p.id, "Skipped: no OFF or Power Toggle command")
            else {
                status(p.id, "Sending ${c.name}…")
                try {
                    transmitter.send(c.signal)
                    currentCoroutineContext().ensureActive()
                    status(p.id, "Sent ${c.name}; device OFF is not confirmed")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status(p.id, "Failed: ${e.message}") }
                delay(1000)
            }
        }
    }
}
