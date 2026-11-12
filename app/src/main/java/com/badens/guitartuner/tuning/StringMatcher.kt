package com.badens.guitartuner.tuning

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Maps a pitch to a guitar string, with hysteresis.
 *
 * Once a string is matched it stays matched until the pitch moves [hysteresisCents] past the
 * boundary (the geometric midpoint) between that string and its neighbour. This stops the
 * display flickering between two strings when a badly detuned string sits near a boundary.
 */
class StringMatcher(private val hysteresisCents: Double = 30.0) {

    var current: GuitarString? = null
        private set

    fun match(frequencyHz: Double): GuitarString {
        val nearest = GuitarString.nearest(frequencyHz)
        val held = current
        if (held == null || held == nearest) {
            current = nearest
            return nearest
        }
        // `nearest` differs from `held`, so the pitch is on the far side of the boundary between
        // `held` and its neighbour in that direction, and that neighbour exists.
        val step = if (frequencyHz > held.frequency) 1 else -1
        val neighbour = GuitarString.entries[held.ordinal + step]
        val boundary = sqrt(held.frequency * neighbour.frequency)
        return if (abs(centsBetween(frequencyHz, boundary)) >= hysteresisCents) {
            current = nearest
            nearest
        } else {
            held
        }
    }

    fun reset() {
        current = null
    }
}
