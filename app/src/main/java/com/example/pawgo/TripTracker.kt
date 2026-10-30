package com.example.pawgo

import android.content.Context

/** 끝난 주행 한 번의 결과. 도착 정보는 내비 도착 이벤트로 끝났을 때만 있다. */
data class TripResult(
    val startMillis: Long,
    val endMillis: Long,
    val destinationName: String?,
    val durationSeconds: Int?,
    val distanceMeters: Int?,
)

/**
 * 주행 구간을 추적한다. 앱이 다시 만들어지거나 재시작돼도 이어지도록 SharedPreferences에 저장한다.
 *
 * 주행 시작은 경로 안내 시작, 끝은 목적지 도착, 내비의 안내 종료, "주행 종료" 버튼이다.
 * 경로 안내 없이 기록했다면 직전 주행이 끝난 뒤부터를 이번 주행으로 본다.
 */
class TripTracker(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 경로 안내로 시작한 주행이 진행 중인지. */
    val isActive: Boolean
        get() = prefs.contains(KEY_ACTIVE_START)

    /** 경로 안내가 시작되면 호출한다. 이미 진행 중인 주행이 있으면 그대로 둔다. */
    fun onRouteStarted(now: Long) {
        if (!prefs.contains(KEY_ACTIVE_START)) {
            prefs.edit().putLong(KEY_ACTIVE_START, now).apply()
        }
    }

    /** 주행을 끝내고, 결과 화면을 보여 줄 때까지 [pendingResult]로 남겨 둔다. */
    fun endTrip(
        now: Long,
        destinationName: String? = null,
        durationSeconds: Int? = null,
        distanceMeters: Int? = null,
    ): TripResult {
        val start = prefs.getLong(KEY_ACTIVE_START, prefs.getLong(KEY_LAST_END, 0L))
        val result = TripResult(start, now, destinationName, durationSeconds, distanceMeters)
        prefs.edit()
            .remove(KEY_ACTIVE_START)
            .putLong(KEY_LAST_END, now)
            .putLong(KEY_PENDING_START, result.startMillis)
            .putLong(KEY_PENDING_END, result.endMillis)
            .putString(KEY_PENDING_NAME, destinationName)
            .putInt(KEY_PENDING_DURATION, durationSeconds ?: NONE)
            .putInt(KEY_PENDING_DISTANCE, distanceMeters ?: NONE)
            .apply()
        return result
    }

    /** 아직 보여 주지 않은 주행 결과. */
    val pendingResult: TripResult?
        get() {
            if (!prefs.contains(KEY_PENDING_END)) return null
            return TripResult(
                startMillis = prefs.getLong(KEY_PENDING_START, 0L),
                endMillis = prefs.getLong(KEY_PENDING_END, 0L),
                destinationName = prefs.getString(KEY_PENDING_NAME, null),
                durationSeconds = prefs.getInt(KEY_PENDING_DURATION, NONE).takeIf { it != NONE },
                distanceMeters = prefs.getInt(KEY_PENDING_DISTANCE, NONE).takeIf { it != NONE },
            )
        }

    fun clearPendingResult() {
        prefs.edit()
            .remove(KEY_PENDING_START)
            .remove(KEY_PENDING_END)
            .remove(KEY_PENDING_NAME)
            .remove(KEY_PENDING_DURATION)
            .remove(KEY_PENDING_DISTANCE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "trip"
        private const val KEY_ACTIVE_START = "activeStart"
        private const val KEY_LAST_END = "lastEnd"
        private const val KEY_PENDING_START = "pendingStart"
        private const val KEY_PENDING_END = "pendingEnd"
        private const val KEY_PENDING_NAME = "pendingName"
        private const val KEY_PENDING_DURATION = "pendingDuration"
        private const val KEY_PENDING_DISTANCE = "pendingDistance"
        private const val NONE = -1
    }
}
