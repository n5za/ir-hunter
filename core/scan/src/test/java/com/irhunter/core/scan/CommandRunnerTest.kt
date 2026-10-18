package com.irhunter.core.scan

import com.irhunter.core.ir.IrTransmitter
import com.irhunter.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRunnerTest {
    private val toggle = RemoteCommand("Power", IrSignal.Protocol(IrProtocol.NEC, 1, 1))
    private val off = RemoteCommand("Off", IrSignal.Protocol(IrProtocol.NEC, 1, 2), true)
    private fun profile(id: String, commands: List<RemoteCommand> = listOf(toggle)) =
        RemoteProfile(id, id, DeviceCategory.TV, id, "Model", "Test", "Test only", "", commands, true)
    @Test fun scanStopsDuringDelayAndKeepsCandidateOrder() = runTest {
        val sent = mutableListOf<IrSignal>()
        val candidates = Candidates.ordered(listOf(profile("Z", listOf(off)), profile("A")), DeviceCategory.TV, "", "")
        assertEquals(listOf("A", "Z"), candidates.map { it.profile.id })
        val runner = CommandRunner(IrTransmitter { sent += it })
        val job = launch { runner.scan(candidates, { _, _ -> }, {}) }
        runCurrent(); assertEquals(1, sent.size)
        job.cancelAndJoin(); advanceUntilIdle(); assertEquals(1, sent.size)
    }
    @Test fun scanFiltersAndDeduplicatesSignals() {
        val candidates = Candidates.ordered(listOf(profile("A"), profile("B"), profile("C").copy(category = DeviceCategory.AC)), DeviceCategory.TV, "", "")
        assertEquals(1, candidates.size)
        assertTrue(Candidates.ordered(listOf(profile("A")), DeviceCategory.TV, "unknown", "").isEmpty())
    }
    @Test fun bulkPrefersVerifiedOffAndContinuesAfterFailure() = runTest {
        val sent = mutableListOf<IrSignal>()
        val statuses = mutableMapOf<String, String>()
        val runner = CommandRunner(IrTransmitter { if (it == toggle.signal) error("mock failure"); sent += it })
        runner.allOff(listOf(profile("A"), profile("B", listOf(toggle, off)), profile("C", emptyList()))) { id, s -> statuses[id] = s }
        assertEquals(listOf(off.signal), sent)
        assertTrue(statuses.getValue("A").startsWith("Failed"))
        assertTrue(statuses.getValue("B").contains("not confirmed"))
        assertTrue(statuses.getValue("C").startsWith("Skipped"))
    }
    @Test fun bulkCancellationDoesNotSendNextDevice() = runTest {
        var sent = 0
        val runner = CommandRunner(IrTransmitter { sent++ })
        val job = launch { runner.allOff(listOf(profile("A"), profile("B"))) { _, _ -> } }
        runCurrent(); job.cancelAndJoin(); advanceUntilIdle()
        assertEquals(1, sent)
    }
}
