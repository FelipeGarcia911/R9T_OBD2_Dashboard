package com.felipeg.r9t_obd2_dashboard.diagnostics.data

internal object ObdPidDecoder {
    fun supportedPids(response: String, basePid: Int = 0): Set<Int>? {
        val prefix = "41" + basePid.toString(16).uppercase().padStart(2, '0')
        val payload = payload(response, prefix, 4) ?: return null
        return buildSet {
            payload.forEachIndexed { byteIndex, byte ->
                for (bitIndex in 0..7) {
                    if (byte and (0x80 shr bitIndex) != 0) {
                        add(basePid + byteIndex * 8 + bitIndex + 1)
                    }
                }
            }
        }
    }

    fun engineRpm(response: String): Double? {
        return engineRpm(payload(response, "410C", 2) ?: return null)
    }

    fun speedKmh(response: String): Int? = payload(response, "410D", 1)?.single()

    /**
     * Decodes a multi-PID Mode 01 response such as `41 0C 1A F8 0D 58`, keyed by PID.
     * [lengths] gives the data length of each requested PID. The ECU may answer in any order,
     * in one frame or one frame per PID; PIDs it omits are absent from the result.
     */
    fun multiplePids(response: String, lengths: Map<Int, Int>): Map<Int, List<Int>> = buildMap {
        response.substringBefore('>').lineSequence().forEach { line ->
            val frame = line.filterNot(Char::isWhitespace).uppercase()
            if (!frame.startsWith("41") || frame.length % 2 != 0) return@forEach
            if (frame.any { it !in "0123456789ABCDEF" }) return@forEach
            val bytes = frame.chunked(2).drop(1).map { it.toInt(16) }
            var index = 0
            while (index < bytes.size) {
                val pid = bytes[index]
                val length = lengths[pid] ?: return@forEach
                if (index + 1 + length > bytes.size) return@forEach
                putIfAbsent(pid, bytes.subList(index + 1, index + 1 + length))
                index += 1 + length
            }
        }
    }

    fun engineRpm(payload: List<Int>): Double = (payload[0] * 256 + payload[1]) / 4.0

    fun telemetryPayload(response: String, pid: ObdTelemetryPid): List<Int>? =
        payload(response, "41${pid.hex}", pid.lengths)

    private fun payload(response: String, prefix: String, size: Int): List<Int>? =
        payload(response, prefix, size..size)

    // The ELM327 session requests headerless output. Each line is a separate ECU response.
    private fun payload(response: String, prefix: String, sizes: IntRange): List<Int>? =
        response.substringBefore('>').lineSequence().firstNotNullOfOrNull { line ->
            val frame = line.filterNot(Char::isWhitespace).uppercase()
            if (!frame.startsWith(prefix)) return@firstNotNullOfOrNull null
            val length = frame.length - prefix.length
            if (length % 2 != 0 || length / 2 !in sizes) {
                return@firstNotNullOfOrNull null
            }

            val bytes = frame.drop(prefix.length).chunked(2)
            if (bytes.any { byte -> byte.any { it !in "0123456789ABCDEF" } }) {
                return@firstNotNullOfOrNull null
            }
            bytes.map { it.toInt(16) }
        }
}
