package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState

/** What the handshake learned: the protocol the adapter chose and every PID the ECU advertises. */
internal data class EcuLink(val protocol: String?, val advertisedPids: Set<Int>)

/**
 * Opens the Bluetooth link, prepares the adapter and asks the ECU what it supports, moving the
 * recorder through the BLUETOOTH, ADAPTER and ECU stages. [showProgress] reports each wait.
 */
internal class ElmHandshake(
    private val channel: ElmChannel,
    private val recorder: SessionRecorder,
    private val showProgress: (DiagnosticState) -> Unit,
) {
    suspend fun connect(adapterId: String): EcuLink {
        recorder.record("Opening direct Bluetooth link to ELM327")
        channel.open(adapterId)
        recorder.record("Bluetooth link connected")

        recorder.stage = DiagnosticStage.ADAPTER
        showProgress(DiagnosticState.WaitingForAdapter)
        channel.exchange("ATZ", "Reset adapter")
        val identification = channel.exchange("ATI", "Identify adapter")
        if (ElmResponse(identification).answer == null) invalidResponse()
        channel.configure("ATE0", "Disable echo")
        channel.configure("ATH0", "Disable headers")
        // Battery voltage at the diagnostic socket. The adapter answers it even with the ignition off,
        // so session logs show the battery's state. Informational only: a clone without it still works.
        channel.exchange("ATRV", "Read battery voltage")
        selectProtocol()

        recorder.stage = DiagnosticStage.ECU
        showProgress(DiagnosticState.WaitingForEcu)
        val support = channel.exchange("0100", "Read supported PIDs")
        val supported = ObdPidDecoder.supportedPids(support) ?: run {
            if (ElmResponse(support).ecuDidNotAnswer) throw ElmTransportException(DiagnosticError.NO_ECU_DATA)
            invalidResponse()
        }
        recordPidSupport(supported, ObdPids.ENGINE_RPM, "Engine speed")
        recordPidSupport(supported, ObdPids.VEHICLE_SPEED, "Vehicle speed")
        val protocol = ElmResponse(channel.exchange("ATDP", "Read active protocol")).answer
        return EcuLink(protocol, discoverSupportedPids(supported))
    }

    /**
     * Searches automatically but tries ISO 15765-4 CAN 11/500 first, the protocol the reference
     * motorcycle uses. Adapters that reject `ATSPA6` fall back to a plain automatic search.
     */
    private suspend fun selectProtocol() {
        if (!ElmResponse(channel.exchange("ATSPA6", "Select protocol automatically, trying CAN 11/500 first")).isOk) {
            channel.configure("ATSP0", "Select protocol automatically")
        }
    }

    private suspend fun discoverSupportedPids(initial: Set<Int>): Set<Int> {
        val supported = initial.toMutableSet()
        for (page in 0x20..0xA0 step 0x20) {
            if (page !in supported) break
            val command = ObdPids.request(page)
            val response = channel.exchange(command, "Read extended PID support")
            val pagePids = ObdPidDecoder.supportedPids(response, page)
            if (pagePids == null) {
                recorder.record("Extended PID discovery stopped: no valid response for $command")
                return supported.toSet()
            }
            supported += pagePids
        }
        recordPidSupport(supported, ObdPids.ACTUAL_GEAR, "Actual gear")
        recordPidSupport(supported, ObdPids.ODOMETER, "Odometer")
        if (ObdPids.ACTUAL_GEAR in supported) {
            channel.exchange(ObdPids.request(ObdPids.ACTUAL_GEAR), "Capture actual gear for field validation")
        }
        if (ObdPids.ODOMETER in supported) {
            channel.exchange(ObdPids.request(ObdPids.ODOMETER), "Capture odometer for field validation")
        }
        return supported.toSet()
    }

    private suspend fun recordPidSupport(supported: Set<Int>, pid: Int, label: String) {
        val availability = if (pid in supported) "supported" else "not advertised by the ECU"
        recorder.record("$label (${ObdPids.request(pid)}) · $availability")
    }
}
