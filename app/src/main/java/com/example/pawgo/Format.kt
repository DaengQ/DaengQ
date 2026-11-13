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

/** 출발 후 구간. 1시간 안이면 단위를 한 번만 쓴다. 예: 5-10분, 50분-1시간, 1시간-1시간 5분. */
fun formatElapsedRange(startMinutes: Int, endMinutes: Int): String =
    if (endMinutes < 60) "$startMinutes-${endMinutes}분"
    else "${formatElapsed(startMinutes)}-${formatElapsed(endMinutes)}"
