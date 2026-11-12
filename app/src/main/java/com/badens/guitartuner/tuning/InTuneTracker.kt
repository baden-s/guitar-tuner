package com.badens.guitartuner.tuning

import kotlin.math.abs

/**
 * Decides when a string counts as "in tune": its offset must stay within [toleranceCents]
 * for [holdMs] without a break. Switching strings restarts the hold.
 */
class InTuneTracker(
    private val toleranceCents: Double = 5.0,
    private val holdMs: Long = 500,
) {
    private var string: GuitarString? = null
    private var withinSinceMs: Long? = null

    /** Records a reading and returns true once the hold time has been met. */
    fun update(string: GuitarString, cents: Double, timeMs: Long): Boolean {
        if (string != this.string) {
            this.string = string
            withinSinceMs = null
        }
        if (abs(cents) > toleranceCents) {
            withinSinceMs = null
            return false
        }
        val since = withinSinceMs ?: timeMs.also { withinSinceMs = it }
        return timeMs - since >= holdMs
    }

    fun reset() {
        string = null
        withinSinceMs = null
    }
}
