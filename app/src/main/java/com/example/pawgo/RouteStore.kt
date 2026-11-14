package com.example.pawgo

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sqrt

/** 지나온 길의 한 점. */
data class RoutePoint(val timeMillis: Long, val latitude: Double, val longitude: Double)

/**
 * 주행 중 받은 위치를 이어 경로로 남긴다. 위치는 몇 초마다 오므로
 * 한 줄("시각,위도,경도")씩 파일 끝에 덧붙이고, 주행 구간은 시각으로 골라 읽는다.
 */
class RouteStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    /**
     * 이번 주행의 앞 점 [previous]에서 [MIN_STEP_METERS] 이상 움직였을 때만 남긴다. 서 있을 때 같은 자리가 쌓이지 않게 한다.
     * 주행의 첫 점([previous]가 null)은 언제나 남긴다. 지난 주행이 끝난 자리에서 다시 출발해도 경로가 출발점부터 이어진다.
     */
    fun add(point: RoutePoint, previous: RoutePoint?): Boolean {
        if (previous != null && RouteMath.distanceMeters(previous, point) < MIN_STEP_METERS) return false
        return try {
            file.appendText("${point.timeMillis},${point.latitude},${point.longitude}\n")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save route point", e)
            false
        }
    }

    /** [fromMillis]부터 [toMillis]까지의 경로, 시간순. */
    fun load(fromMillis: Long, toMillis: Long): List<RoutePoint> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull(::parse).filter { it.timeMillis in fromMillis..toMillis }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read route", e)
            emptyList()
        }
    }

    /** [beforeMillis]보다 오래된 점을 지운다. */
    fun prune(beforeMillis: Long) {
        if (!file.exists()) return
        try {
            val kept = file.readLines().filter { line -> (parse(line)?.timeMillis ?: 0L) >= beforeMillis }
            file.writeText(kept.joinToString("") { "$it\n" })
        } catch (e: IOException) {
            Log.e(TAG, "Failed to prune route", e)
        }
    }

    private fun parse(line: String): RoutePoint? {
        val parts = line.split(',')
        if (parts.size != 3) return null
        return RoutePoint(
            parts[0].toLongOrNull() ?: return null,
            parts[1].toDoubleOrNull() ?: return null,
            parts[2].toDoubleOrNull() ?: return null,
        )
    }

    companion object {
        private const val TAG = "RouteStore"
        private const val FILE_NAME = "route.txt"
        private const val MIN_STEP_METERS = 5.0
    }
}

object RouteMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** 두 점 사이 거리(m). 몇 km 안의 짧은 거리라 평면으로 근사한다. */
    fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val latitude = Math.toRadians((a.latitude + b.latitude) / 2)
        val dx = Math.toRadians(b.longitude - a.longitude) * cos(latitude)
        val dy = Math.toRadians(b.latitude - a.latitude)
        return sqrt(dx * dx + dy * dy) * EARTH_RADIUS_METERS
    }
}
