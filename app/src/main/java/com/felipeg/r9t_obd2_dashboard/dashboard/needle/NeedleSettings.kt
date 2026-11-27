package com.felipeg.r9t_obd2_dashboard.dashboard.needle

import android.content.Context
import androidx.core.content.edit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How the tachometer needle moves toward each reading: a spring with [dampingRatio] (1 = no
 * bounce, lower = bouncier) and [stiffness] (higher = faster). The defaults are the feel confirmed
 * on the bike: critically damped, Compose's StiffnessMediumLow.
 */
internal data class NeedleSettings(
    val dampingRatio: Float = DEFAULT_DAMPING,
    val stiffness: Float = DEFAULT_STIFFNESS,
) {
    init {
        require(dampingRatio in MIN_DAMPING..MAX_DAMPING) { "Damping outside $MIN_DAMPING..$MAX_DAMPING" }
        require(stiffness in MIN_STIFFNESS..MAX_STIFFNESS) { "Stiffness outside $MIN_STIFFNESS..$MAX_STIFFNESS" }
    }

    val isDefault: Boolean get() = this == NeedleSettings()

    fun withDamping(ratio: Float): NeedleSettings =
        copy(dampingRatio = ((ratio / DAMPING_STEP).roundToInt() * DAMPING_STEP).coerceIn(MIN_DAMPING, MAX_DAMPING))

    fun withStiffness(value: Float): NeedleSettings = copy(stiffness = value.roundToInt().toFloat().coerceIn(MIN_STIFFNESS, MAX_STIFFNESS))

    /** The stiffness slider's position, 0–1, on a logarithmic scale: low stiffness gets most of the travel. */
    val stiffnessPosition: Float get() = (ln(stiffness / MIN_STIFFNESS) / ln(MAX_STIFFNESS / MIN_STIFFNESS))

    fun withStiffnessPosition(position: Float): NeedleSettings =
        withStiffness(MIN_STIFFNESS * (MAX_STIFFNESS / MIN_STIFFNESS).pow(position.coerceIn(0f, 1f)))

    /** How far past a sudden change the needle swings before settling, in percent of the change. */
    val overshootPercent: Float
        get() = if (dampingRatio >= 1f) 0f
        else 100f * exp(-PI * dampingRatio / sqrt(1 - dampingRatio * dampingRatio)).toFloat()

    /** About how long the needle takes to come within 2 % of a sudden change. */
    val settleMillis: Int
        get() {
            val omega = sqrt(stiffness)
            val seconds = if (dampingRatio >= 1f) 5.83f / omega else 4f / (dampingRatio * omega)
            return (seconds * 1_000).roundToInt()
        }

    companion object {
        const val DEFAULT_DAMPING = 1f
        const val DEFAULT_STIFFNESS = 400f // Spring.StiffnessMediumLow
        const val MIN_DAMPING = 0.3f
        const val MAX_DAMPING = 1f
        const val DAMPING_STEP = 0.05f
        const val MIN_STIFFNESS = 50f
        const val MAX_STIFFNESS = 3_000f
    }
}

/** Keeps the rider's needle settings between sessions. */
internal interface NeedleSettingsStore {
    fun load(): NeedleSettings
    fun save(settings: NeedleSettings)
}

internal class SharedPreferencesNeedleSettingsStore(context: Context) : NeedleSettingsStore {
    private val preferences = context.getSharedPreferences("needle", Context.MODE_PRIVATE)

    /** Falls back to the defaults when nothing is stored or the stored values are no longer valid. */
    override fun load(): NeedleSettings = runCatching {
        NeedleSettings(
            dampingRatio = preferences.getFloat(DAMPING, NeedleSettings.DEFAULT_DAMPING),
            stiffness = preferences.getFloat(STIFFNESS, NeedleSettings.DEFAULT_STIFFNESS),
        )
    }.getOrDefault(NeedleSettings())

    override fun save(settings: NeedleSettings) {
        preferences.edit {
            putFloat(DAMPING, settings.dampingRatio)
            putFloat(STIFFNESS, settings.stiffness)
        }
    }

    private companion object {
        const val DAMPING = "damping_ratio"
        const val STIFFNESS = "stiffness"
    }
}
