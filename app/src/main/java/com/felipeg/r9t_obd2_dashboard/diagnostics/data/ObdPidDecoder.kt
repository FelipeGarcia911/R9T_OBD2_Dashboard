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
        val payload = payload(response, "410C", 2) ?: return null
        return (payload[0] * 256 + payload[1]) / 4.0
    }

    fun speedKmh(response: String): Int? = payload(response, "410D", 1)?.single()

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
