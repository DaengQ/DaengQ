package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun writesElapsedRangeWithOneUnitUnderAnHour() {
        assertEquals("5-10분", formatElapsedRange(5, 10))
        assertEquals("0-5분", formatElapsedRange(0, 5))
        assertEquals("50분-1시간", formatElapsedRange(50, 60))
        assertEquals("1시간 0-5분", formatElapsedRange(60, 65))
        assertEquals("1시간 15-30분", formatElapsedRange(75, 90))
        assertEquals("1-2시간", formatElapsedRange(60, 120))
        assertEquals("0-2시간", formatElapsedRange(0, 120))
        assertEquals("1시간 45분-2시간", formatElapsedRange(105, 120))
        assertEquals("4시간-5시간 7분", formatElapsedRange(240, 307))
    }
}
