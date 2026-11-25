package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PollingScheduleTest {
    @Test
    fun combinedPollingOnlyLeavesRoomForOneSensorPerSecond() {
        val schedule = PollingSchedule(pollSpeedSeparately = false, hasSensors = true, startedAt = 0)

        assertEquals(SecondarySlot.NONE, schedule.secondaryAt(999))
        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(1_000))
        schedule.sensorRead(at = 1_075)
        assertEquals(SecondarySlot.NONE, schedule.secondaryAt(2_074))
        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(2_075))
    }

    @Test
    fun separateSpeedReadsComeEveryQuarterSecondAndWinTies() {
        val schedule = PollingSchedule(pollSpeedSeparately = true, hasSensors = true, startedAt = 0)

        assertEquals(SecondarySlot.NONE, schedule.secondaryAt(249))
        assertEquals(SecondarySlot.SPEED, schedule.secondaryAt(250))
        schedule.speedRead(at = 750)
        // Speed is due at 1000 together with the first sensor read: speed goes first.
        assertEquals(SecondarySlot.SPEED, schedule.secondaryAt(1_000))
        schedule.speedRead(at = 1_050)
        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(1_100))
    }

    @Test
    fun aSensorThatIsOverdueWaitsForAnEarlierSpeedRead() {
        val schedule = PollingSchedule(pollSpeedSeparately = true, hasSensors = true, startedAt = 0)
        schedule.speedRead(at = 800) // Next speed at 1050, after the sensor's 1000.

        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(1_100))
    }

    @Test
    fun nothingIsScheduledWithoutSensorsOrSeparateSpeed() {
        val schedule = PollingSchedule(pollSpeedSeparately = false, hasSensors = false, startedAt = 0)

        assertEquals(SecondarySlot.NONE, schedule.secondaryAt(Long.MAX_VALUE - 1))
    }

    @Test
    fun cyclesStartEveryPrimaryPeriodUnlessOneRanLong() {
        assertEquals(PRIMARY_PERIOD_MS, PollingSchedule.delayAfterCycle(cycleStartedAt = 100, now = 100))
        assertEquals(PRIMARY_PERIOD_MS - 30, PollingSchedule.delayAfterCycle(cycleStartedAt = 100, now = 130))
        assertEquals(0L, PollingSchedule.delayAfterCycle(cycleStartedAt = 100, now = 175))
    }
}
