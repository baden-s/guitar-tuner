package com.badens.guitartuner.tuning

import com.badens.guitartuner.dsp.PitchSmoother
import com.badens.guitartuner.dsp.YinPitchDetector
import kotlin.math.abs
import kotlin.math.log10

/** Level reported for digital silence. */
const val SILENT_DB = -120.0

/** Tuning thresholds and timings. Defaults follow docs/PLAN.md. */
data class TunerConfig(
    /** Pitches outside this range are ignored (open strings span 82–330 Hz). */
    val minFrequencyHz: Double = 70.0,
    val maxFrequencyHz: Double = 400.0,
    /** Frames quieter than this (dBFS RMS) count as silence. */
    val gateDb: Double = -55.0,
    /** A level jump this big (dB) between frames counts as a new pluck. */
    val onsetJumpDb: Double = 6.0,
    /** Frames skipped after a pluck; the attack is noisy and slightly sharp. */
    val transientFrames: Int = 2,
    /** After this long without a pitch, the display returns to idle. */
    val silenceTimeoutMs: Long = 1500,
    val inTuneCents: Double = 5.0,
    val closeCents: Double = 15.0,
    val inTuneHoldMs: Long = 500,
    val hysteresisCents: Double = 30.0,
)

enum class TuningStatus {
    /** No string detected. */
    IDLE,

    /** Below the target by more than the in-tune tolerance. */
    FLAT,

    /** Above the target by more than the in-tune tolerance. */
    SHARP,

    /** Within tolerance, but not yet for the full hold time. */
    CENTERED,

    /** Within tolerance for the full hold time. */
    IN_TUNE,
}

data class TunerState(
    val activeString: GuitarString? = null,
    /** Smoothed offset from [activeString]'s target, unclamped. */
    val cents: Double = 0.0,
    val frequencyHz: Double? = null,
    val status: TuningStatus = TuningStatus.IDLE,
    /** Strings that reached [TuningStatus.IN_TUNE] and haven't drifted off since. */
    val tunedStrings: Set<GuitarString> = emptySet(),
    val lockedString: GuitarString? = null,
    val levelDb: Double = SILENT_DB,
)

/**
 * Turns overlapping audio frames into tuner readings.
 *
 * Pure Kotlin with no Android dependencies, so the whole pipeline is unit-testable on a PC.
 * Call [process] for each frame, in order, from a single thread.
 */
class TunerEngine(
    val sampleRate: Int,
    val frameSize: Int,
    private val config: TunerConfig = TunerConfig(),
    tunedStrings: Set<GuitarString> = emptySet(),
) {
    // Search slightly wider than the accepted range so edge pitches are found, then filtered.
    private val detector = YinPitchDetector(
        sampleRate = sampleRate,
        frameSize = frameSize,
        minFrequencyHz = config.minFrequencyHz * 0.85,
        maxFrequencyHz = config.maxFrequencyHz * 1.15,
    )
    private val smoother = PitchSmoother()
    private val matcher = StringMatcher(config.hysteresisCents)
    private val inTune = InTuneTracker(config.inTuneCents, config.inTuneHoldMs)
    private val tuned = tunedStrings.toMutableSet()

    private var state = TunerState(tunedStrings = tuned.toSet())
    private var previousDb = SILENT_DB
    private var skipFrames = 0
    private var lastTimeMs: Long? = null
    private var lastPitchMs: Long? = null
    private var lastLock: GuitarString? = null

    val tunedStrings: Set<GuitarString> get() = tuned.toSet()

    /**
     * Processes one frame of [frameSize] mono samples in -1..1, captured at [timeMs]
     * (any monotonic clock). With [lockedString] set, cents are measured against that string
     * instead of the auto-detected one.
     */
    fun process(frame: FloatArray, timeMs: Long, lockedString: GuitarString? = null): TunerState {
        lastTimeMs?.let { if (timeMs < it) resetSignal() } // The clock restarted: new capture session.
        lastTimeMs = timeMs
        if (lockedString != lastLock) {
            lastLock = lockedString
            matcher.reset()
            inTune.reset()
        }

        val levelDb = rmsDb(frame)
        val loud = levelDb >= config.gateDb
        if (loud && (previousDb < config.gateDb || levelDb - previousDb >= config.onsetJumpDb)) {
            onPluck()
        }
        previousDb = levelDb

        val pitch = when {
            !loud -> null
            skipFrames > 0 -> {
                skipFrames--
                null
            }
            else -> detector.detect(frame)?.frequencyHz
                ?.takeIf { it in config.minFrequencyHz..config.maxFrequencyHz && !isOctaveJump(it) }
        }

        state = if (pitch != null) {
            reading(pitch, timeMs, lockedString, levelDb)
        } else {
            noReading(timeMs, lockedString, levelDb)
        }
        return state
    }

    private fun reading(pitch: Double, timeMs: Long, lockedString: GuitarString?, levelDb: Double): TunerState {
        val smoothed = smoother.add(pitch)
        val string = lockedString ?: matcher.match(smoothed)
        val cents = centsBetween(smoothed, string.frequency)
        lastPitchMs = timeMs

        val isInTune = inTune.update(string, cents, timeMs)
        if (isInTune) {
            tuned += string
        } else if (abs(cents) > config.closeCents) {
            tuned -= string
        }
        val status = when {
            isInTune -> TuningStatus.IN_TUNE
            abs(cents) <= config.inTuneCents -> TuningStatus.CENTERED
            cents < 0 -> TuningStatus.FLAT
            else -> TuningStatus.SHARP
        }
        return TunerState(string, cents, smoothed, status, tuned.toSet(), lockedString, levelDb)
    }

    private fun noReading(timeMs: Long, lockedString: GuitarString?, levelDb: Double): TunerState {
        val last = lastPitchMs
        if (last != null && timeMs - last < config.silenceTimeoutMs) {
            // Brief gap (between frames, or the pluck transient): hold the last reading.
            return state.copy(lockedString = lockedString, levelDb = levelDb)
        }
        if (last != null) {
            lastPitchMs = null
            smoother.reset()
            inTune.reset()
        }
        return TunerState(tunedStrings = tuned.toSet(), lockedString = lockedString, levelDb = levelDb)
    }

    private fun onPluck() {
        skipFrames = config.transientFrames
        smoother.reset()
        matcher.reset()
    }

    /** Within one note, a sudden exact octave jump is a detection error, not a real change. */
    private fun isOctaveJump(frequencyHz: Double): Boolean {
        val previous = smoother.current ?: return false
        return abs(abs(centsBetween(frequencyHz, previous)) - 1200.0) < 40.0
    }

    private fun resetSignal() {
        smoother.reset()
        matcher.reset()
        inTune.reset()
        skipFrames = 0
        previousDb = SILENT_DB
        lastPitchMs = null
        state = TunerState(tunedStrings = tuned.toSet(), lockedString = lastLock)
    }

    private fun rmsDb(frame: FloatArray): Double {
        var sum = 0.0
        for (i in 0 until frameSize) {
            val s = frame[i].toDouble()
            sum += s * s
        }
        val meanSquare = sum / frameSize
        return if (meanSquare > 0.0) (10.0 * log10(meanSquare)).coerceAtLeast(SILENT_DB) else SILENT_DB
    }
}
