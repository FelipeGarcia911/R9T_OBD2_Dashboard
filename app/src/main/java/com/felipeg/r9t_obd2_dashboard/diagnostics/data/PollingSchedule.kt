package com.felipeg.r9t_obd2_dashboard.diagnostics.data

/** Minimum time between primary requests. Field captures show ~75 ms per exchange. */
internal const val PRIMARY_PERIOD_MS = 50L

/** Only used when the ECU rejects the combined request and speed must be polled separately. */
internal const val SPEED_PERIOD_MS = 250L

/**
 * RPM and speed own the bus: one secondary reading every two seconds fits between them, so each
 * takes under 3 % of the RPM and speed requests. Engine vitals take three of every four of those
 * slots, so each refreshes about every 10 s; temperatures and voltage change slowly.
 */
internal const val SECONDARY_PERIOD_MS = 2_000L

/**
 * How long a live session waits without any RPM or speed reading before it treats the ECU as
 * gone (the ignition was switched off) and stops sending requests.
 */
internal const val ECU_SILENCE_LIMIT_MS = 2_000L

/** The request that may follow a cycle's primary read. */
internal enum class SecondarySlot { SPEED, SENSOR, NONE }

/**
 * Decides what fits after each primary read. Every cycle reads RPM (with speed, when the ECU
 * answers both at once); at most one secondary request follows. Separate speed reads win over
 * sensors when both are due. Times are monotonic milliseconds; the caller reports each read.
 */
internal class PollingSchedule(pollSpeedSeparately: Boolean, hasSensors: Boolean, startedAt: Long) {
    private var nextSpeedAt = if (pollSpeedSeparately) startedAt + SPEED_PERIOD_MS else Long.MAX_VALUE
    private var nextSensorAt = if (hasSensors) startedAt + SECONDARY_PERIOD_MS else Long.MAX_VALUE

    fun secondaryAt(now: Long): SecondarySlot = when {
        nextSpeedAt <= now && nextSpeedAt <= nextSensorAt -> SecondarySlot.SPEED
        nextSensorAt <= now -> SecondarySlot.SENSOR
        else -> SecondarySlot.NONE
    }

    fun speedRead(at: Long) {
        nextSpeedAt = at + SPEED_PERIOD_MS
    }

    fun sensorRead(at: Long) {
        nextSensorAt = at + SECONDARY_PERIOD_MS
    }

    companion object {
        /** The pause that keeps cycles [PRIMARY_PERIOD_MS] apart; none when a cycle ran long. */
        fun delayAfterCycle(cycleStartedAt: Long, now: Long): Long =
            (PRIMARY_PERIOD_MS - (now - cycleStartedAt)).coerceAtLeast(0)
    }
}

/**
 * Notices when a live ECU stops answering: no RPM or speed reading for [limitMillis]. That is what
 * switching the ignition off looks like, and the session must then stop sending requests.
 * Times are monotonic milliseconds.
 */
internal class EcuSilenceWatch(startedAt: Long, private val limitMillis: Long = ECU_SILENCE_LIMIT_MS) {
    private var lastHeardAt = startedAt

    /** Call with the time of every reply that carried RPM or speed. */
    fun heard(at: Long) {
        lastHeardAt = at
    }

    fun isSilent(now: Long): Boolean = now - lastHeardAt >= limitMillis
}
