package com.badens.guitartuner.tuning

import org.junit.Assert.assertEquals
import org.junit.Test

class StringMatcherTest {

    @Test
    fun `stays on G just past the G-B boundary`() {
        val matcher = StringMatcher(hysteresisCents = 30.0)
        assertEquals(GuitarString.G, matcher.match(196.0))
        // Boundary is ~220.0 Hz; 30 cents past it is ~223.8 Hz.
        assertEquals(GuitarString.G, matcher.match(222.0))
        assertEquals(GuitarString.B, matcher.match(225.0))
    }

    @Test
    fun `once switched, holds the new string back across the boundary`() {
        val matcher = StringMatcher(hysteresisCents = 30.0)
        matcher.match(246.9)
        assertEquals(GuitarString.B, matcher.match(218.0))
        assertEquals(GuitarString.G, matcher.match(214.0))
    }

    @Test
    fun `a far jump switches straight to the nearest string`() {
        val matcher = StringMatcher()
        matcher.match(82.4)
        assertEquals(GuitarString.B, matcher.match(246.9))
    }

    @Test
    fun `reset drops the held string`() {
        val matcher = StringMatcher()
        matcher.match(196.0)
        matcher.reset()
        assertEquals(GuitarString.B, matcher.match(221.0))
    }
}
