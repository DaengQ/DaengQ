package com.example.pawgo

import androidx.annotation.StringRes

/** 운전자가 한 번의 터치로 기록하는 반려동물 행동. */
enum class PetBehavior(@StringRes val labelRes: Int) {
    BARKING(R.string.behavior_barking),
    WHINING(R.string.behavior_whining),
    CARSICK(R.string.behavior_carsick),
    SLEEPING(R.string.behavior_sleeping),
    TOILET(R.string.behavior_toilet),
    EXCITED(R.string.behavior_excited),
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
