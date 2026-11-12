package com.badens.guitartuner.tuning

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/** Concert pitch reference that every target frequency is derived from. */
const val A4_HZ = 440.0

/** Offset of [frequencyHz] from [referenceHz] in cents (1/100 of an equal-tempered semitone). */
fun centsBetween(frequencyHz: Double, referenceHz: Double): Double =
    1200.0 * log2(frequencyHz / referenceHz)

/** The six open strings of a guitar in standard tuning, thickest first. */
enum class GuitarString(
    /** Guitarists' string number: 6 is the thickest, 1 the thinnest. */
    val number: Int,
    val noteName: String,
    val octave: Int,
    midiNote: Int,
) {
    LOW_E(6, "E", 2, 40),
    A(5, "A", 2, 45),
    D(4, "D", 3, 50),
    G(3, "G", 3, 55),
    B(2, "B", 3, 59),
    HIGH_E(1, "E", 4, 64),
    ;

    /** Target frequency in Hz, equal temperament relative to [A4_HZ]. */
    val frequency: Double = A4_HZ * 2.0.pow((midiNote - 69) / 12.0)

    companion object {
        /** The string whose target is closest to [frequencyHz] on a log (cents) scale. */
        fun nearest(frequencyHz: Double): GuitarString =
            entries.minBy { abs(centsBetween(frequencyHz, it.frequency)) }
    }
}
