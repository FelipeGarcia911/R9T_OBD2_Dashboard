package com.felipeg.r9t_obd2_dashboard.diagnostics.data

/**
 * Chooses the secondary sensor for each polling slot. Priority sensors take most slots in
 * round-robin order; every [otherEvery]th slot goes to the remaining sensors, so diagnostic
 * readings still refresh without delaying what the rider sees.
 */
internal class SensorRotation(
    sensors: List<ObdTelemetryPid>,
    priorityIds: Set<Int>,
    private val otherEvery: Int = 4,
) {
    private val priority = sensors.filter { it.id in priorityIds }
    private val others = sensors.filter { it.id !in priorityIds }
    private var slot = 0
    private var nextPriority = 0
    private var nextOther = 0

    val isEmpty: Boolean get() = priority.isEmpty() && others.isEmpty()

    init {
        require(otherEvery >= 1)
    }

    fun next(): ObdTelemetryPid {
        check(!isEmpty) { "No sensors to rotate" }
        val otherTurn = others.isNotEmpty() && (priority.isEmpty() || slot % otherEvery == otherEvery - 1)
        slot++
        return if (otherTurn) others[nextOther++ % others.size] else priority[nextPriority++ % priority.size]
    }
}
