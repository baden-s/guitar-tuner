package com.badens.guitartuner.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** A pitch reading: the fundamental frequency and how periodic the frame was (0..1). */
data class PitchEstimate(val frequencyHz: Double, val confidence: Double)

/**
 * Monophonic pitch detection with the YIN algorithm (de Cheveigné & Kawahara, 2002).
 *
 * YIN finds the lag at which the signal best matches a shifted copy of itself. Unlike FFT
 * peak-picking it locks onto the fundamental even when a harmonic is louder, which matters for
 * the low E string, whose fundamental is weak.
 *
 * The lag search is lazy: it stops just past the first dip below [threshold], so high strings
 * cost far less than the worst case. Not thread-safe; use one instance per audio stream.
 */
class YinPitchDetector(
    val sampleRate: Int,
    val frameSize: Int,
    minFrequencyHz: Double = 60.0,
    maxFrequencyHz: Double = 500.0,
    private val threshold: Double = 0.15,
) {
    private val tauMin = max(2, floor(sampleRate / maxFrequencyHz).toInt())
    private val tauMax = ceil(sampleRate / minFrequencyHz).toInt()

    /** Samples compared at each lag; constant so the difference function has no slope. */
    private val windowLength = frameSize - tauMax - 1

    private val difference = DoubleArray(tauMax + 2)
    private val normalized = DoubleArray(tauMax + 2)
    private var computedUpTo = 0
    private var runningSum = 0.0

    init {
        require(windowLength >= tauMax) {
            "frameSize $frameSize is too short to hold two periods of $minFrequencyHz Hz"
        }
    }

    /** Returns the pitch of [frame], or null if the frame isn't clearly periodic. */
    fun detect(frame: FloatArray): PitchEstimate? {
        require(frame.size >= frameSize) { "frame has ${frame.size} samples, need $frameSize" }
        computedUpTo = 0
        runningSum = 0.0

        // Absolute threshold: take the first dip below the threshold, then walk down to the
        // bottom of that dip. Taking the first dip (not the global minimum) avoids reporting
        // a multiple of the period, i.e. a note an octave or more too low.
        var tau = tauMin
        var candidate = -1
        while (tau <= tauMax) {
            computeUpTo(frame, tau + 1)
            if (normalized[tau] < threshold) {
                while (tau < tauMax && normalized[tau + 1] < normalized[tau]) {
                    tau++
                    computeUpTo(frame, tau + 1)
                }
                candidate = tau
                break
            }
            tau++
        }
        if (candidate < 0) return null

        // The normalized function is slightly biased, so refine on the raw difference function.
        var best = candidate
        while (best > tauMin && difference[best - 1] < difference[best]) best--
        while (best < tauMax) {
            computeUpTo(frame, best + 1)
            if (difference[best + 1] < difference[best]) best++ else break
        }
        computeUpTo(frame, best + 1)

        val period = parabolicMinimum(best)
        return PitchEstimate(
            frequencyHz = sampleRate / period,
            confidence = (1.0 - normalized[candidate]).coerceIn(0.0, 1.0),
        )
    }

    /** Fills the difference and cumulative-mean-normalized functions for lags 1..[tau]. */
    private fun computeUpTo(frame: FloatArray, tau: Int) {
        while (computedUpTo < tau) {
            val lag = ++computedUpTo
            var sum = 0.0
            for (j in 0 until windowLength) {
                val delta = (frame[j] - frame[j + lag]).toDouble()
                sum += delta * delta
            }
            difference[lag] = sum
            runningSum += sum
            normalized[lag] = if (runningSum > 0.0) sum * lag / runningSum else 1.0
        }
    }

    /** Sub-sample position of the minimum near [tau], from a parabola through 3 points. */
    private fun parabolicMinimum(tau: Int): Double {
        val left = difference[tau - 1]
        val mid = difference[tau]
        val right = difference[tau + 1]
        val curvature = left - 2 * mid + right
        if (curvature <= 0.0) return tau.toDouble()
        val shift = 0.5 * (left - right) / curvature
        return tau + shift.coerceIn(-1.0, 1.0)
    }
}
