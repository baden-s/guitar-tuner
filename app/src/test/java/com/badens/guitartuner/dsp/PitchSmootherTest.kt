package com.badens.guitartuner.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitchSmootherTest {

    @Test
    fun `first reading passes straight through`() {
        assertEquals(110.0, PitchSmoother().add(110.0), 1e-9)
    }

    @Test
    fun `a single outlier is rejected by the median`() {
        val smoother = PitchSmoother()
        smoother.add(110.0)
        smoother.add(110.0)
        assertEquals(110.0, smoother.add(113.0), 1e-9)
    }

    @Test
    fun `small changes are eased toward`() {
        val smoother = PitchSmoother(medianWindow = 1, alpha = 0.5)
        smoother.add(110.0)
        val next = smoother.add(111.0)
        assertEquals(true, next > 110.0 && next < 111.0)
    }

    @Test
    fun `big jumps snap immediately`() {
        val smoother = PitchSmoother(medianWindow = 1)
        smoother.add(110.0)
        assertEquals(146.8, smoother.add(146.8), 1e-9)
    }

    @Test
    fun `reset forgets history`() {
        val smoother = PitchSmoother()
        smoother.add(110.0)
        smoother.reset()
        assertNull(smoother.current)
        assertEquals(82.4, smoother.add(82.4), 1e-9)
    }
}
