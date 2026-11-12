package com.badens.guitartuner.dsp

import com.badens.guitartuner.tuning.centsBetween
import kotlin.math.abs
import kotlin.math.pow

/**
 * Steadies a stream of pitch readings so the needle doesn't jitter.
 *
 * A short median rejects one-off outliers, then an exponential moving average (in the log
 * domain, so it is even in cents) smooths what's left. A jump bigger than [snapCents] is taken
 * immediately instead of being eased into, so switching strings isn't sluggish.
 */
class PitchSmoother(
    private val medianWindow: Int = 3,
    private val alpha: Double = 0.35,
    private val snapCents: Double = 50.0,
) {
    private val recent = ArrayDeque<Double>(medianWindow)

    /** The latest smoothed frequency, or null before the first reading. */
    var current: Double? = null
        private set

    fun add(frequencyHz: Double): Double {
        recent.addLast(frequencyHz)
        if (recent.size > medianWindow) recent.removeFirst()
        val median = recent.sorted()[recent.size / 2]

        val previous = current
        val next = if (previous == null || abs(centsBetween(median, previous)) > snapCents) {
            median
        } else {
            previous * (median / previous).pow(alpha)
        }
        current = next
        return next
    }

    fun reset() {
        recent.clear()
        current = null
    }
}
