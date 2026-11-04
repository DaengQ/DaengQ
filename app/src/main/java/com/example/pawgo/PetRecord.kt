package com.example.pawgo

import androidx.annotation.ColorRes
import androidx.annotation.StringRes

/** 운전자가 버튼이나 음성으로 기록하는 반려동물 행동. 지도에는 색과 짧은 이름의 마커로 표시한다. */
enum class PetBehavior(
    @StringRes val labelRes: Int,
    @StringRes val markerLabelRes: Int,
    @ColorRes val markerColorRes: Int,
) {
    BARKING(R.string.behavior_barking, R.string.marker_barking, R.color.marker_barking),
    WHINING(R.string.behavior_whining, R.string.marker_whining, R.color.marker_whining),
    CARSICK(R.string.behavior_carsick, R.string.marker_carsick, R.color.marker_carsick),
    SLEEPING(R.string.behavior_sleeping, R.string.marker_sleeping, R.color.marker_sleeping),
    TOILET(R.string.behavior_toilet, R.string.marker_toilet, R.color.marker_toilet),
    EXCITED(R.string.behavior_excited, R.string.marker_excited, R.color.marker_excited),
}

data class PetRecord(
    val timeMillis: Long,
    val behavior: PetBehavior,
    /** 기록 당시 내비 상태 문자열. 아직 상태를 받지 못했으면 null. */
    val naviStatus: String?,
    /** 내비가 알려 준 현재 위치 주소. 아직 받지 못했으면 null. */
    val address: String?,
    /** 내비가 알려 준 현재 위치 좌표. 아직 받지 못했으면 null. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)
