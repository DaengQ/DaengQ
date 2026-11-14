package com.example.pawgo

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

/** 출발 후 지난 시간. 1시간이 넘으면 시간 단위로 줄인다. 예: 20분, 1시간, 1시간 30분. */
fun formatElapsed(minutes: Int): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0 -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

/**
 * 출발 후 구간. 결과 꼬리표 한 줄에 두 구간이 들어가도록 겹치는 단위는 한 번만 쓴다.
 * 예: 5-10분, 1-2시간, 1시간 15-30분, 50분-1시간, 1시간 45분-2시간.
 */
fun formatElapsedRange(startMinutes: Int, endMinutes: Int): String {
    val startHour = startMinutes / 60
    val endHour = endMinutes / 60
    return when {
        endMinutes < 60 -> "$startMinutes-${endMinutes}분"
        startMinutes % 60 == 0 && endMinutes % 60 == 0 -> "$startHour-${endHour}시간"
        startHour == endHour -> "${startHour}시간 ${startMinutes % 60}-${endMinutes % 60}분"
        else -> "${formatElapsed(startMinutes)}-${formatElapsed(endMinutes)}"
    }
}
