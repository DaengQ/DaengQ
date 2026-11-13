package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun writesElapsedRangeWithOneUnitUnderAnHour() {
        assertEquals("5-10분", formatElapsedRange(5, 10))
        assertEquals("0-5분", formatElapsedRange(0, 5))
        assertEquals("50분-1시간", formatElapsedRange(50, 60))
        assertEquals("1시간-1시간 5분", formatElapsedRange(60, 65))
    }
}
