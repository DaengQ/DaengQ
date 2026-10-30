package com.example.pawgo

import java.util.Locale

fun formatDistance(meters: Int): String =
    if (meters >= 1000) String.format(Locale.getDefault(), "%.1fkm", meters / 1000.0) else "${meters}m"

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}
