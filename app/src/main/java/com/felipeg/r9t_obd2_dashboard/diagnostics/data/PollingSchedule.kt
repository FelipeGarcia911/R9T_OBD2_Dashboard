package com.felipeg.r9t_obd2_dashboard.diagnostics.data

/** Minimum time between primary requests. Field captures show ~75 ms per exchange. */
internal const val PRIMARY_PERIOD_MS = 50L

/** Only used when the ECU rejects the combined request and speed must be polled separately. */
internal const val SPEED_PERIOD_MS = 250L

/**
 * RPM and speed own the bus: only one secondary reading per second fits between them.
 * Engine vitals take three of every four of those slots and may lag by several seconds.
 */
internal const val SECONDARY_PERIOD_MS = 1_000L

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
