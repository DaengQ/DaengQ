package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayerTest {
    private val tmapDefaultPriority = 9999f

    @Test
    fun layersStackFromLocationDownToRoute() {
        val topToBottom = listOf(
            "location" to MapLayer.LOCATION,
            "newest record" to MapLayer.RECORD_TOP,
            "oldest record" to MapLayer.RECORD_BOTTOM,
            "start and end squares" to MapLayer.SQUARE,
            "markers without a priority" to tmapDefaultPriority,
            "route line" to MapLayer.ROUTE,
        )
        topToBottom.zipWithNext().forEach { (above, below) ->
            assertTrue("${above.first} must be drawn above ${below.first}", above.second < below.second)
        }
    }

    @Test
    fun laterRecordsGoOnTopAndStayAboveSquares() {
        val priorities = generateSequence(MapLayer.RECORD_BOTTOM, MapLayer::nextRecord).take(20_000).toList()
        assertTrue("a later record is never below an earlier one", priorities.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("the second record goes above the first", priorities[1] < priorities[0])
        assertEquals(MapLayer.RECORD_TOP, priorities.last())
        assertTrue(priorities.all { it < MapLayer.SQUARE && it > MapLayer.LOCATION })
    }
}
