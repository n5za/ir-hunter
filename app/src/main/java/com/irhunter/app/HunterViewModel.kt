package com.irhunter.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irhunter.core.data.FlipperImporter
import com.irhunter.core.database.*
import com.irhunter.core.ir.AndroidIrTransmitter
import com.irhunter.core.model.*
import com.irhunter.core.scan.*
import kotlinx.coroutines.*
import java.util.UUID

class HunterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RemoteDatabase.open(application)
    val transmitter = AndroidIrTransmitter(application)
    private val runner = CommandRunner(transmitter)
    var profiles by mutableStateOf<List<RemoteProfile>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var message by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var scanProgress by mutableStateOf(""); private set
    var lastCandidate by mutableStateOf<Candidate?>(null); private set
    var offStatuses by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var importText by mutableStateOf<String?>(null); private set
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            try {
                database.profiles().observe().collect { rows ->
                    profiles = rows.map(ProfileJson::decode)
                    loading = false
                }
            } catch (e: Exception) { loading = false; message = "Unable to read local database: ${e.message}" }
        }
    }
    fun notice(text: String) { message = text }
    fun readImport(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 1_048_576) { "File exceeds 1 MiB" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    } ?: error("Cannot open file")
                }
                importText = text
            } catch (e: Exception) { message = "Import failed: ${e.message}" }
        }
    }
    fun dismissImport() { importText = null }
    fun importProfile(name: String, category: DeviceCategory, brand: String, model: String, source: String, license: String) {
        val text = importText ?: return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { FlipperImporter.parse(text) }
                val existing = profiles.firstOrNull { it.originalText == text && it.category == category &&
                    it.brand == brand.ifBlank { "Unknown Brand" } && it.model == model.ifBlank { "Unknown Model" } }
                if (existing != null) { message = "Already imported: ${existing.name}"; importText = null; return@launch }
                val profile = RemoteProfile(UUID.randomUUID().toString(), name.trim().ifBlank { "Imported remote" }, category,
                    brand.trim().ifBlank { "Unknown Brand" }, model.trim().ifBlank { "Unknown Model" },
                    source.trim().ifBlank { "User-imported file" }, license.trim().ifBlank { "Not specified by source" }, text, result.commands)
                database.profiles().put(ProfileJson.encode(profile))
                importText = null
                message = "Imported ${result.commands.size} commands." + if (result.warnings.isEmpty()) "" else
                    " Skipped records: ${result.warnings.joinToString("; ")}"
            } catch (e: Exception) { message = "Import failed: ${e.message}"; importText = null }
        }
    }
    fun update(profile: RemoteProfile) {
        viewModelScope.launch {
            try { database.profiles().put(ProfileJson.encode(profile)); message = "Saved ${profile.name}" }
            catch (e: Exception) { message = "Save failed: ${e.message}" }
        }
    }
    fun delete(profile: RemoteProfile) {
        viewModelScope.launch {
            try { database.profiles().delete(profile.id); message = "Deleted ${profile.name}" }
            catch (e: Exception) { message = "Delete failed: ${e.message}" }
        }
    }
    private fun runOperation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        operation = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { message = "Stopped. No further commands will be sent."; throw e }
            catch (e: Exception) { message = "Transmission failed: ${e.message}" }
            finally { busy = false }
        }
    }
    fun send(command: RemoteCommand) = runOperation {
        transmitter.send(command.signal)
        message = "Sent ${command.name}. Check the physical device for a response."
    }
    fun scan(candidates: List<Candidate>) {
        if (busy) return
        lastCandidate = null
        runOperation {
            runner.scan(candidates, { index, c ->
                lastCandidate = null
                scanProgress = "${index + 1}/${candidates.size}: ${c.profile.brand} · ${c.profile.model} · ${c.command.name}"
            }, { lastCandidate = it })
            message = "Scan finished. Confirm only if the displayed candidate caused a response."
        }
    }
    fun confirmCandidate() {
        val c = lastCandidate ?: return
        stop()
        update(c.profile.copy(saved = true))
    }
    fun allOff(selected: List<RemoteProfile>) {
        if (busy) return
        offStatuses = selected.associate { it.id to "Pending" }
        runOperation {
            try { runner.allOff(selected) { id, status -> offStatuses = offStatuses + (id to status) } }
            finally { offStatuses = offStatuses.mapValues { (_, s) -> if (s == "Pending" || s.startsWith("Sending")) "Cancelled / result unknown" else s } }
        }
    }
    fun stop() { operation?.cancel() }
    override fun onCleared() { operation?.cancel(); database.close() }
}
