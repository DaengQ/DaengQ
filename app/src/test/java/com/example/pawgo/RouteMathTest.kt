package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteMathTest {

    @Test
    fun measuresShortDistances() {
        val a = RoutePoint(0L, 37.4127, 127.0946)
        // 위도 0.001도는 약 111m
        assertEquals(111.2, RouteMath.distanceMeters(a, a.copy(latitude = 37.4137)), 0.5)
        assertEquals(0.0, RouteMath.distanceMeters(a, a), 0.0)
    }
}
