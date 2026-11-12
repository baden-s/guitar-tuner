package com.badens.guitartuner.tuning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneTrackerTest {

    private val tracker = InTuneTracker(toleranceCents = 5.0, holdMs = 500)

    @Test
    fun `in tune only after holding within tolerance for the hold time`() {
        assertFalse(tracker.update(GuitarString.A, 2.0, 0))
        assertFalse(tracker.update(GuitarString.A, -3.0, 400))
        assertTrue(tracker.update(GuitarString.A, 1.0, 500))
    }

    @Test
    fun `drifting out restarts the hold`() {
        tracker.update(GuitarString.A, 2.0, 0)
        tracker.update(GuitarString.A, 8.0, 300)
        assertFalse(tracker.update(GuitarString.A, 2.0, 600))
        assertTrue(tracker.update(GuitarString.A, 2.0, 1100))
    }

    @Test
    fun `switching strings restarts the hold`() {
        tracker.update(GuitarString.A, 0.0, 0)
        assertFalse(tracker.update(GuitarString.D, 0.0, 600))
        assertTrue(tracker.update(GuitarString.D, 0.0, 1100))
    }
}
