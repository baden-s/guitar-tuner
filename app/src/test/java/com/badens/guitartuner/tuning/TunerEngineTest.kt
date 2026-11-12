package com.badens.guitartuner.tuning

import com.badens.guitartuner.testing.FRAME_SIZE
import com.badens.guitartuner.testing.Recording
import com.badens.guitartuner.testing.SAMPLE_RATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class TunerEngineTest {

    private val aSharp12 = GuitarString.A.frequency * 2.0.pow(12.0 / 1200)
    private val aSharp1 = GuitarString.A.frequency * 2.0.pow(1.0 / 1200)

    /** Runs a recording through a fresh engine and returns its state after each frame. */
    private fun run(recording: Recording, locked: GuitarString? = null): List<Pair<Long, TunerState>> {
        val engine = TunerEngine(SAMPLE_RATE, FRAME_SIZE)
        return recording.frames().map { (frame, time) -> time to engine.process(frame, time, locked) }
    }

    private fun List<Pair<Long, TunerState>>.at(seconds: Double): TunerState =
        last { it.first <= seconds * 1000 }.second

    @Test
    fun `a full tune-up of the A string`() {
        val states = run(
            Recording()
                .silence(0.5)
                .note(aSharp12, 1.0) // plucked 12 cents sharp
                .note(aSharp1, 1.3) // tuned down to within tolerance
                .silence(2.5),
        )

        val idle = states.at(0.4)
        assertEquals(TuningStatus.IDLE, idle.status)
        assertNull(idle.activeString)

        val sharp = states.at(1.4)
        assertEquals(GuitarString.A, sharp.activeString)
        assertEquals(TuningStatus.SHARP, sharp.status)
        assertEquals(12.0, sharp.cents, 1.0)

        val tuned = states.at(2.75)
        assertEquals(TuningStatus.IN_TUNE, tuned.status)
        assertEquals(1.0, tuned.cents, 1.0)
        assertTrue(GuitarString.A in tuned.tunedStrings)

        // A short silence holds the last reading...
        assertEquals(GuitarString.A, states.at(3.8).activeString)
        // ...a long one returns to idle but remembers the tuned string.
        val after = states.at(5.0)
        assertEquals(TuningStatus.IDLE, after.status)
        assertTrue(GuitarString.A in after.tunedStrings)
    }

    @Test
    fun `reading responds within 200 ms of a pluck`() {
        val states = run(Recording().silence(0.5).note(aSharp12, 1.0))
        val firstReading = states.first { it.second.activeString != null }.first
        assertTrue("first reading at $firstReading ms", firstReading - 500 <= 200)
    }

    @Test
    fun `locked string measures against the locked target`() {
        val states = run(Recording().silence(0.3).note(GuitarString.A.frequency, 1.0), locked = GuitarString.LOW_E)
        val last = states.last().second
        assertEquals(GuitarString.LOW_E, last.activeString)
        assertEquals(GuitarString.LOW_E, last.lockedString)
        assertEquals(TuningStatus.SHARP, last.status)
        assertEquals(500.0, last.cents, 1.0)
    }

    @Test
    fun `quiet input stays idle`() {
        val states = run(Recording().note(aSharp1, 1.0, amplitude = 0.0005))
        assertTrue(states.all { it.second.status == TuningStatus.IDLE })
    }

    @Test
    fun `a string that drifts out loses its check mark`() {
        val states = run(
            Recording()
                .silence(0.3)
                .note(GuitarString.A.frequency, 1.2)
                .note(GuitarString.A.frequency * 2.0.pow(-30.0 / 1200), 0.8),
        )
        assertTrue(GuitarString.A in states.at(1.4).tunedStrings)
        assertTrue(GuitarString.A !in states.last().second.tunedStrings)
    }
}
