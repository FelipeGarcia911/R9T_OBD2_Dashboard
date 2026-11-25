package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorRotationTest {
    private fun pid(id: Int) = ObdTelemetryPid(id, "PID $id", 1..1) { emptyList() }

    @Test
    fun givesPrioritySensorsThreeOfEveryFourSlots() {
        val rotation = SensorRotation(listOf(pid(1), pid(5), pid(0x5C), pid(9)), setOf(5, 0x5C))
        val picks = List(8) { rotation.next().id }
        assertEquals(listOf(5, 0x5C, 5, 1, 0x5C, 5, 0x5C, 9), picks)
    }

    @Test
    fun usesWhicheverGroupExistsWhenTheOtherIsEmpty() {
        assertEquals(listOf(1, 2, 1), SensorRotation(listOf(pid(1), pid(2)), emptySet()).let { r -> List(3) { r.next().id } })
        assertEquals(listOf(5, 5), SensorRotation(listOf(pid(5)), setOf(5)).let { r -> List(2) { r.next().id } })
        assertTrue(SensorRotation(emptyList(), setOf(5)).isEmpty)
    }
}
