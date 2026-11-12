package com.badens.guitartuner.tuning

import org.junit.Assert.assertEquals
import org.junit.Test

class GuitarStringTest {

    @Test
    fun `target frequencies match standard tuning at A4 = 440 Hz`() {
        assertEquals(82.41, GuitarString.LOW_E.frequency, 0.01)
        assertEquals(110.00, GuitarString.A.frequency, 0.01)
        assertEquals(146.83, GuitarString.D.frequency, 0.01)
        assertEquals(196.00, GuitarString.G.frequency, 0.01)
        assertEquals(246.94, GuitarString.B.frequency, 0.01)
        assertEquals(329.63, GuitarString.HIGH_E.frequency, 0.01)
    }

    @Test
    fun `cents are 1200 per octave and signed`() {
        assertEquals(0.0, centsBetween(440.0, 440.0), 1e-9)
        assertEquals(1200.0, centsBetween(880.0, 440.0), 1e-9)
        assertEquals(-100.0, centsBetween(440.0 / 2.0.pow(1.0 / 12), 440.0), 1e-9)
    }

    /** Boundaries from the plan's detection table (geometric midpoints). */
    @Test
    fun `nearest string follows the detection table`() {
        val expectations = listOf(
            70.0 to GuitarString.LOW_E,
            95.0 to GuitarString.LOW_E,
            95.4 to GuitarString.A,
            127.0 to GuitarString.A,
            127.2 to GuitarString.D,
            169.5 to GuitarString.D,
            169.7 to GuitarString.G,
            219.9 to GuitarString.G,
            220.1 to GuitarString.B,
            285.2 to GuitarString.B,
            285.4 to GuitarString.HIGH_E,
            400.0 to GuitarString.HIGH_E,
        )
        for ((frequency, expected) in expectations) {
            assertEquals("$frequency Hz", expected, GuitarString.nearest(frequency))
        }
    }

    private fun Double.pow(x: Double) = Math.pow(this, x)
}
