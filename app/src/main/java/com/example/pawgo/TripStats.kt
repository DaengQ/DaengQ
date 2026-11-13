package com.example.pawgo

import kotlin.math.ceil

/** 시간대 막대 하나. [endMillis]는 포함하지 않는다(마지막 막대만 도착 시각까지 포함). */
data class TimeBucket(val startMillis: Long, val endMillis: Long, val count: Int)

/** 주행 결과 요약 규칙(PRD '주행 결과 요약 규칙'). */
object TripStats {
    /** 막대 폭 후보(분). 막대가 [MAX_BARS]개를 넘지 않는 가장 짧은 폭을 고른다. */
    private val BUCKET_MINUTES = listOf(5, 10, 15, 30, 60, 120, 180, 360, 720, 1440)
    const val MAX_BARS = 12
    private const val MINUTE_MS = 60_000L

    fun bucketMinutes(durationMillis: Long): Int =
        BUCKET_MINUTES.firstOrNull { barCount(durationMillis, it) <= MAX_BARS } ?: BUCKET_MINUTES.last()

    /** 출발 시각부터 같은 폭으로 나눈 막대. 마지막 막대는 도착 시각에서 끝난다. */
    fun buckets(startMillis: Long, endMillis: Long, recordTimes: List<Long>): List<TimeBucket> {
        val duration = (endMillis - startMillis).coerceAtLeast(0)
        val widthMs = bucketMinutes(duration) * MINUTE_MS
        val bars = barCount(duration, bucketMinutes(duration))
        val counts = IntArray(bars)
        recordTimes.filter { it in startMillis..endMillis }.forEach { time ->
            counts[((time - startMillis) / widthMs).toInt().coerceAtMost(bars - 1)]++
        }
        return List(bars) { i ->
            TimeBucket(
                startMillis = startMillis + i * widthMs,
                endMillis = minOf(startMillis + (i + 1) * widthMs, endMillis),
                count = counts[i],
            )
        }
    }

    /** 건수가 가장 많은 막대. 같으면 이른 막대. 기록이 없으면 null. */
    fun peakIndex(buckets: List<TimeBucket>): Int? {
        val max = buckets.maxOfOrNull { it.count } ?: return null
        return if (max == 0) null else buckets.indexOfFirst { it.count == max }
    }

    /** 세 종류 모두의 건수. 기록이 없는 종류는 0. */
    fun typeCounts(records: List<PetRecord>): Map<PetBehavior, Int> {
        val counts = records.groupingBy { it.behavior }.eachCount()
        return PetBehavior.entries.associateWith { counts[it] ?: 0 }
    }

    /** 가장 많은 종류와 건수. 같으면 앞 종류. 기록이 없으면 null. */
    fun topType(counts: Map<PetBehavior, Int>): Pair<PetBehavior, Int>? =
        PetBehavior.entries
            .map { it to (counts[it] ?: 0) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }

    private fun barCount(durationMillis: Long, minutes: Int): Int =
        ceil(durationMillis.toDouble() / (minutes * MINUTE_MS)).toInt().coerceAtLeast(1)
}
