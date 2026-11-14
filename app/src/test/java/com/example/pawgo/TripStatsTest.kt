package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripStatsTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun bucketWidthKeepsBarsAtTwelveOrFewer() {
        assertEquals(5, TripStats.bucketMinutes(hour))
        assertEquals(10, TripStats.bucketMinutes(2 * hour))
        assertEquals(15, TripStats.bucketMinutes(3 * hour))
        assertEquals(30, TripStats.bucketMinutes(6 * hour))
        assertEquals(5, TripStats.bucketMinutes(3 * minute))
        assertEquals(10, TripStats.bucketMinutes(61 * minute))
    }

    @Test
    fun lastBucketEndsAtArrival() {
        val buckets = TripStats.buckets(0, 23 * minute, emptyList())
        assertEquals(5, buckets.size)
        assertEquals(20 * minute, buckets.last().startMillis)
        assertEquals(23 * minute, buckets.last().endMillis)
    }

    @Test
    fun countsRecordsAndKeepsEveryTiedPeak() {
        val times = listOf(1 * minute, 12 * minute, 13 * minute, 22 * minute, 23 * minute, 30 * minute)
        val buckets = TripStats.buckets(0, 30 * minute, times)
        assertEquals(listOf(1, 0, 2, 0, 2, 1), buckets.map { it.count })
        // 끝 시각의 기록은 마지막 막대에 들어가고, 같은 건수인 고점은 모두 남긴다
        assertEquals(listOf(2, 4), TripStats.peakIndices(buckets))
    }

    @Test
    fun noRecordsMeansNoPeakOrTopType() {
        val buckets = TripStats.buckets(0, 10 * minute, emptyList())
        assertEquals(emptyList<Int>(), TripStats.peakIndices(buckets))
        assertNull(TripStats.topTypes(TripStats.typeCounts(emptyList())))
    }

    @Test
    fun topTypeHasMostRecords() {
        val records = listOf(PetBehavior.CRYING, PetBehavior.WHINING, PetBehavior.WHINING).mapIndexed { i, b ->
            PetRecord(timeMillis = i.toLong(), behavior = b, naviStatus = null, address = null)
        }
        val counts = TripStats.typeCounts(records)
        assertEquals(0, counts[PetBehavior.HOWLING])
        assertEquals(listOf(PetBehavior.WHINING) to 2, TripStats.topTypes(counts))
    }

    @Test
    fun topTypesKeepsEveryTiedType() {
        val counts = mapOf(PetBehavior.CRYING to 2, PetBehavior.WHINING to 1, PetBehavior.HOWLING to 2)
        assertEquals(listOf(PetBehavior.CRYING, PetBehavior.HOWLING) to 2, TripStats.topTypes(counts))
        val even = PetBehavior.entries.associateWith { 1 }
        assertEquals(PetBehavior.entries.toList() to 1, TripStats.topTypes(even))
    }
}
