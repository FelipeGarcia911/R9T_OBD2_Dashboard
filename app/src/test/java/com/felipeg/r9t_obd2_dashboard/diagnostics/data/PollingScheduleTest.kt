package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PollingScheduleTest {
    @Test
    fun combinedPollingOnlyLeavesRoomForOneSensorEveryTwoSeconds() {
        val schedule = PollingSchedule(pollSpeedSeparately = false, hasSensors = true, startedAt = 0)

        assertEquals(2_000L, SECONDARY_PERIOD_MS)
        assertEquals(SecondarySlot.NONE, schedule.secondaryAt(1_999))
        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(2_000))
        schedule.sensorRead(at = 2_075)
        assertEquals(SecondarySlot.NONE, schedule.secondaryAt(4_074))
        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(4_075))
    }

    @Test
    fun separateSpeedReadsComeEveryQuarterSecondAndWinTies() {
        val schedule = PollingSchedule(pollSpeedSeparately = true, hasSensors = true, startedAt = 0)

        assertEquals(SecondarySlot.NONE, schedule.secondaryAt(249))
        assertEquals(SecondarySlot.SPEED, schedule.secondaryAt(250))
        schedule.speedRead(at = 1_750)
        // Speed is due at 2000 together with the first sensor read: speed goes first.
        assertEquals(SecondarySlot.SPEED, schedule.secondaryAt(2_000))
        schedule.speedRead(at = 2_050)
        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(2_100))
    }

    @Test
    fun aSensorThatIsOverdueWaitsForAnEarlierSpeedRead() {
        val schedule = PollingSchedule(pollSpeedSeparately = true, hasSensors = true, startedAt = 0)
        schedule.speedRead(at = 1_800) // Next speed at 2050, after the sensor's 2000.

        assertEquals(SecondarySlot.SENSOR, schedule.secondaryAt(2_100))
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

class EcuSilenceWatchTest {
    @Test
    fun aQuietEcuIsSilentAfterTheLimit() {
        val watch = EcuSilenceWatch(startedAt = 1_000, limitMillis = 2_000)
        assertEquals(false, watch.isSilent(2_999))
        assertEquals(true, watch.isSilent(3_000))
    }

    @Test
    fun everyReadingRestartsTheWait() {
        val watch = EcuSilenceWatch(startedAt = 0, limitMillis = 2_000)
        watch.heard(at = 1_500)
        assertEquals(false, watch.isSilent(3_000))
        assertEquals(true, watch.isSilent(3_500))
    }
}
