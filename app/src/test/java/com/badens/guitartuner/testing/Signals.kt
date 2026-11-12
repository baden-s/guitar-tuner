package com.badens.guitartuner.testing

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

const val SAMPLE_RATE = 48_000
const val FRAME_SIZE = 4096
const val HOP_SIZE = 2048

/** Harmonic amplitudes of a band-limited sawtooth: rich in overtones, like a plucked string. */
val SAWTOOTH: List<Double> = (1..20).map { 1.0 / it }

/** A low E with a fundamental much weaker than its 2nd harmonic, as phone mics often hear it. */
val WEAK_FUNDAMENTAL: List<Double> = listOf(0.15, 1.0, 0.6, 0.4, 0.25)

/**
 * A synthetic tone: a sum of harmonics of [frequencyHz], optionally decaying like a plucked
 * string and with white noise added.
 */
fun tone(
    frequencyHz: Double,
    length: Int = FRAME_SIZE,
    harmonics: List<Double> = listOf(1.0),
    amplitude: Double = 0.3,
    startSeconds: Double = 0.0,
    decaySeconds: Double? = null,
    noiseAmplitude: Double = 0.0,
    seed: Long = 1,
): FloatArray {
    val noise = Random(seed)
    val norm = harmonics.sum()
    return FloatArray(length) { n ->
        val t = startSeconds + n.toDouble() / SAMPLE_RATE
        var v = 0.0
        harmonics.forEachIndexed { i, a -> v += a * sin(2 * PI * frequencyHz * (i + 1) * t) }
        v *= amplitude / norm
        if (decaySeconds != null) v *= exp(-t / decaySeconds)
        (v + noiseAmplitude * noise.nextGaussian()).toFloat()
    }
}

/**
 * Builds a continuous test recording from segments. Frequency changes keep the phase
 * continuous, as a real string being retuned would.
 */
class Recording {
    private val samples = ArrayList<Float>()
    private val noise = Random(7)
    private var phase = 0.0

    fun silence(seconds: Double) = apply {
        repeat((seconds * SAMPLE_RATE).toInt()) { samples += (1e-4 * noise.nextGaussian()).toFloat() }
    }

    fun note(frequencyHz: Double, seconds: Double, amplitude: Double = 0.2) = apply {
        repeat((seconds * SAMPLE_RATE).toInt()) {
            phase += 2 * PI * frequencyHz / SAMPLE_RATE
            val v = (sin(phase) + 0.5 * sin(2 * phase) + 0.25 * sin(3 * phase)) / 1.75
            samples += (amplitude * v + 1e-4 * noise.nextGaussian()).toFloat()
        }
    }

    /** Overlapping frames as the app's capture emits them, each with its capture time in ms. */
    fun frames(): List<Pair<FloatArray, Long>> {
        val all = samples.toFloatArray()
        return (FRAME_SIZE..all.size step HOP_SIZE).map { end ->
            all.copyOfRange(end - FRAME_SIZE, end) to end * 1000L / SAMPLE_RATE
        }
    }
}
