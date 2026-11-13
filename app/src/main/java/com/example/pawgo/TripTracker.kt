package com.example.pawgo

import android.content.Context

/** 끝난 주행 한 번의 결과. */
data class TripResult(
    val startMillis: Long,
    val endMillis: Long,
    /** 출발 지점. 주행 중 위치를 한 번도 받지 못했으면 null. */
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
)

/**
 * 주행 구간을 추적한다. 앱이 다시 만들어지거나 재시작돼도 이어지도록 SharedPreferences에 저장한다.
 *
 * 주행은 "주행 시작" 버튼이나 첫 기록으로 시작하고, "주행 종료" 버튼으로 끝난다.
 */
class TripTracker(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isActive: Boolean
        get() = prefs.contains(KEY_ACTIVE_START)

    /** 진행 중인 주행의 시작 시각. 주행 중이 아니면 null. */
    val activeStartMillis: Long?
        get() = if (isActive) prefs.getLong(KEY_ACTIVE_START, 0L) else null

    /** 진행 중인 주행의 출발 지점(위도, 경도). 아직 위치를 받지 못했으면 null. */
    val activeStartLocation: Pair<Double, Double>?
        get() = readLocation(KEY_START_LAT, KEY_START_LNG)

    /** 주행 중 처음 받은 위치를 출발 지점으로 남긴다. 이미 있으면 그대로 둔다. */
    fun recordStartLocation(latitude: Double, longitude: Double) {
        if (!isActive || prefs.contains(KEY_START_LAT)) return
        prefs.edit()
            .putLong(KEY_START_LAT, latitude.toRawBits())
            .putLong(KEY_START_LNG, longitude.toRawBits())
            .apply()
    }

    /** 주행을 시작한다. 이미 진행 중인 주행이 있으면 그대로 둔다. */
    fun startTrip(now: Long) {
        if (!isActive) prefs.edit().putLong(KEY_ACTIVE_START, now).apply()
    }

    /** 주행을 끝내고, 결과 화면을 보여 줄 때까지 [pendingResult]로 남겨 둔다. */
    fun endTrip(now: Long) {
        val editor = prefs.edit()
            .putLong(KEY_PENDING_START, prefs.getLong(KEY_ACTIVE_START, now))
            .putLong(KEY_PENDING_END, now)
            .remove(KEY_ACTIVE_START)
            .remove(KEY_START_LAT)
            .remove(KEY_START_LNG)
        activeStartLocation?.let { (latitude, longitude) ->
            editor.putLong(KEY_PENDING_START_LAT, latitude.toRawBits())
                .putLong(KEY_PENDING_START_LNG, longitude.toRawBits())
        }
        editor.apply()
    }

    /** 아직 보여 주지 않은 주행 결과. */
    val pendingResult: TripResult?
        get() {
            if (!prefs.contains(KEY_PENDING_END)) return null
            val start = readLocation(KEY_PENDING_START_LAT, KEY_PENDING_START_LNG)
            return TripResult(
                startMillis = prefs.getLong(KEY_PENDING_START, 0L),
                endMillis = prefs.getLong(KEY_PENDING_END, 0L),
                startLatitude = start?.first,
                startLongitude = start?.second,
            )
        }

    fun clearPendingResult() {
        prefs.edit()
            .remove(KEY_PENDING_START)
            .remove(KEY_PENDING_END)
            .remove(KEY_PENDING_START_LAT)
            .remove(KEY_PENDING_START_LNG)
            .apply()
    }

    private fun readLocation(latKey: String, lngKey: String): Pair<Double, Double>? {
        if (!prefs.contains(latKey) || !prefs.contains(lngKey)) return null
        return Double.fromBits(prefs.getLong(latKey, 0L)) to Double.fromBits(prefs.getLong(lngKey, 0L))
    }

    companion object {
        private const val PREFS_NAME = "trip"
        private const val KEY_ACTIVE_START = "activeStart"
        private const val KEY_PENDING_START = "pendingStart"
        private const val KEY_PENDING_END = "pendingEnd"
        private const val KEY_START_LAT = "startLat"
        private const val KEY_START_LNG = "startLng"
        private const val KEY_PENDING_START_LAT = "pendingStartLat"
        private const val KEY_PENDING_START_LNG = "pendingStartLng"
    }
}
