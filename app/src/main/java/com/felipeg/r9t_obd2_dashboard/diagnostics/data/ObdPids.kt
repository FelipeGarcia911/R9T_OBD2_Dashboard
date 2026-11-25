package com.felipeg.r9t_obd2_dashboard.diagnostics.data

/** Mode 01 PIDs the session handles by name, and how they are written on the wire. */
internal object ObdPids {
    const val ENGINE_RPM = 0x0C
    const val VEHICLE_SPEED = 0x0D
    const val ACTUAL_GEAR = 0xA4
    const val ODOMETER = 0xA6

    /** Data bytes each primary PID returns. */
    const val ENGINE_RPM_BYTES = 2
    const val VEHICLE_SPEED_BYTES = 1

    /** RPM and speed in one CAN exchange. */
    val RPM_AND_SPEED: String = request(ENGINE_RPM, VEHICLE_SPEED)

    /** A byte as two uppercase hex digits, the way the ELM327 writes it. */
    fun hex(value: Int): String = value.toString(16).uppercase().padStart(2, '0')

    /** The Mode 01 request for [pids], such as `010C` or `010C0D`. */
    fun request(vararg pids: Int): String = "01" + pids.joinToString("") { hex(it) }

    /**
     * [request] telling the ELM327 to return after one ECU reply, such as `010C0D1`. Without the
     * count the adapter keeps listening for other ECUs until its timeout before answering.
     */
    fun singleResponse(request: String): String = request + "1"

    /** [command] without a response count, so `010C0D1` and `010C0D` read as the same request. */
    fun baseRequest(command: String): String =
        if (command.startsWith("01") && command.length % 2 == 1) command.dropLast(1) else command

    /** The prefix of a positive Mode 01 response for [pid], such as `410C`. */
    fun responsePrefix(pid: Int): String = "41" + hex(pid)
}
