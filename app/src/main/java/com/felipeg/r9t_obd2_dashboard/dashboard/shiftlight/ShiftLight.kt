package com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight

/**
 * The rider's shift light settings, modeled on BMW Motorrad's: on or off, the engine speed where
 * the light starts warning, the engine speed to shift at, and the flashing frequency there
 * (0 Hz is a steady light).
 *
 * Defaults suit the R nineT's 1,170 cc boxer (EU5): peak power at 7,250 rpm, peak torque at
 * 6,000 rpm, maximum engine speed 8,500 rpm. The light warns just before peak power and asks
 * for the shift a little past it, well short of the limit. Ride captures with gear data can
 * refine these points later.
 */
internal data class ShiftLightSettings(
    val enabled: Boolean = true,
    val startRpm: Int = 7_000,
    val shiftRpm: Int = 7_750,
    val frequencyHz: Int = 4,
) {
    init {
        require(startRpm in RPM_RANGE && shiftRpm in RPM_RANGE) { "RPM outside $RPM_RANGE" }
        require(shiftRpm - startRpm >= RPM_STEP) { "Shift RPM must be above the start RPM" }
        require(frequencyHz in FREQUENCY_RANGE) { "Frequency outside $FREQUENCY_RANGE" }
    }

    /** A copy with [startRpm] moved, pushing the shift RPM up when they would cross. */
    fun withStart(rpm: Int): ShiftLightSettings {
        val start = snap(rpm).coerceAtMost(RPM_RANGE.last - RPM_STEP)
        return copy(startRpm = start, shiftRpm = shiftRpm.coerceAtLeast(start + RPM_STEP))
    }

    /** A copy with [shiftRpm] moved, pulling the start RPM down when they would cross. */
    fun withShift(rpm: Int): ShiftLightSettings {
        val shift = snap(rpm).coerceAtLeast(RPM_RANGE.first + RPM_STEP)
        return copy(shiftRpm = shift, startRpm = startRpm.coerceAtMost(shift - RPM_STEP))
    }

    fun withFrequency(hz: Int): ShiftLightSettings =
        copy(frequencyHz = hz.coerceIn(FREQUENCY_RANGE.first, FREQUENCY_RANGE.last))

    companion object {
        val RPM_RANGE = 3_000..8_500
        const val RPM_STEP = 250
        val FREQUENCY_RANGE = 0..10

        private fun snap(rpm: Int): Int =
            (Math.round(rpm / RPM_STEP.toDouble()) * RPM_STEP).toInt().coerceIn(RPM_RANGE.first, RPM_RANGE.last)
    }
}

/** What the shift light shows. */
internal enum class ShiftLightPhase {
    /** Below the start RPM, disabled, or no reading. */
    OFF,

    /** Between the start and shift RPM: the shift point is near. */
    APPROACHING,

    /** At or above the shift RPM: shift up now. */
    SHIFT,
}

/**
 * Decides the shift light phase from each RPM reading. A phase is only left once the engine
 * speed falls [HYSTERESIS_RPM] below its threshold, so readings jittering around a threshold
 * do not make the light flicker.
 */
internal object ShiftLightRules {
    const val HYSTERESIS_RPM = 150

    fun next(previous: ShiftLightPhase, rpm: Double?, settings: ShiftLightSettings): ShiftLightPhase {
        if (!settings.enabled || rpm == null) return ShiftLightPhase.OFF
        val shiftFrom = settings.shiftRpm - if (previous == ShiftLightPhase.SHIFT) HYSTERESIS_RPM else 0
        val startFrom = settings.startRpm - if (previous != ShiftLightPhase.OFF) HYSTERESIS_RPM else 0
        return when {
            rpm >= shiftFrom -> ShiftLightPhase.SHIFT
            rpm >= startFrom -> ShiftLightPhase.APPROACHING
            else -> ShiftLightPhase.OFF
        }
    }

    /**
     * Whether the light is on at [elapsedMillis] into a phase: steady while approaching, and at
     * the shift point flashing at [ShiftLightSettings.frequencyHz] full on/off cycles a second,
     * starting lit so the warning shows at once. 0 Hz stays lit.
     */
    fun isLit(phase: ShiftLightPhase, frequencyHz: Int, elapsedMillis: Long): Boolean = when (phase) {
        ShiftLightPhase.OFF -> false
        ShiftLightPhase.APPROACHING -> true
        ShiftLightPhase.SHIFT -> frequencyHz == 0 || (elapsedMillis * frequencyHz * 2 / 1_000) % 2 == 0L
    }
}
