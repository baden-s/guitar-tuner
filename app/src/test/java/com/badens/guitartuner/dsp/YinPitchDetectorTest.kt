package com.badens.guitartuner.dsp

import com.badens.guitartuner.testing.FRAME_SIZE
import com.badens.guitartuner.testing.SAMPLE_RATE
import com.badens.guitartuner.testing.SAWTOOTH
import com.badens.guitartuner.testing.WEAK_FUNDAMENTAL
import com.badens.guitartuner.testing.tone
import com.badens.guitartuner.tuning.GuitarString
import com.badens.guitartuner.tuning.centsBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow

class YinPitchDetectorTest {

    private val detector = YinPitchDetector(SAMPLE_RATE, FRAME_SIZE)
    private val offsets = listOf(-50.0, -10.0, 0.0, 10.0, 50.0)

    /** Plan, phase 2 exit check: every string at -50..+50 cents, error under 1 cent. */
    @Test
    fun `pure sine on every string is within 1 cent`() {
        assertAllStringsWithinOneCent { frequency -> tone(frequency) }
    }

    @Test
    fun `decaying harmonic-rich pluck with noise is within 1 cent`() {
        assertAllStringsWithinOneCent { frequency ->
            tone(
                frequency,
                harmonics = SAWTOOTH,
                startSeconds = 0.3,
                decaySeconds = 1.5,
                noiseAmplitude = 0.005,
            )
        }
    }

    @Test
    fun `weak fundamental on low E is not read an octave high`() {
        val target = GuitarString.LOW_E.frequency
        val estimate = requireEstimate(detector.detect(tone(target, harmonics = WEAK_FUNDAMENTAL)))
        assertEquals(0.0, centsBetween(estimate.frequencyHz, target), 1.0)
    }

    @Test
    fun `white noise has no pitch`() {
        val random = Random(42)
        val noise = FloatArray(FRAME_SIZE) { (0.1 * random.nextGaussian()).toFloat() }
        assertNull(detector.detect(noise))
    }

    @Test
    fun `digital silence has no pitch`() {
        assertNull(detector.detect(FloatArray(FRAME_SIZE)))
    }

    @Test
    fun `clean tone reports high confidence`() {
        val estimate = requireEstimate(detector.detect(tone(GuitarString.A.frequency)))
        assertTrue("confidence ${estimate.confidence}", estimate.confidence > 0.9)
    }

    private fun assertAllStringsWithinOneCent(makeFrame: (Double) -> FloatArray) {
        val failures = mutableListOf<String>()
        for (string in GuitarString.entries) {
            for (offset in offsets) {
                val frequency = string.frequency * 2.0.pow(offset / 1200)
                val estimate = detector.detect(makeFrame(frequency))
                val error = estimate?.let { centsBetween(it.frequencyHz, frequency) }
                if (error == null || abs(error) >= 1.0) {
                    failures += "$string at %+.0f cents: error %s".format(offset, error?.let { "%.3f".format(it) } ?: "no pitch")
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun requireEstimate(value: PitchEstimate?): PitchEstimate {
        assertNotNull("expected a pitch", value)
        return value!!
    }
}
