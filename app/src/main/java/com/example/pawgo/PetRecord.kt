package com.example.pawgo

import androidx.annotation.ColorRes
import androidx.annotation.StringRes

/** 운전자가 버튼이나 음성으로 기록하거나 소리로 감지하는 반려동물 행동. 지도에는 색과 짧은 이름의 마커로 표시한다. */
enum class PetBehavior(
    @StringRes val labelRes: Int,
    @StringRes val markerLabelRes: Int,
    @ColorRes val markerColorRes: Int,
    /** 소리로 감지했을 때 운전자에게 읽어 줄 문장. 소리로 감지하지 않는 행동은 null. */
    @StringRes val soundAlertRes: Int? = null,
    /** 행동 버튼을 보여 줄지. 소리와 음성으로만 기록하는 행동은 false. */
    val hasButton: Boolean = true,
) {
    BARKING(R.string.behavior_barking, R.string.marker_barking, R.color.marker_barking, R.string.sound_alert_barking),
    WHINING(R.string.behavior_whining, R.string.marker_whining, R.color.marker_whining, R.string.sound_alert_whining),
    CARSICK(R.string.behavior_carsick, R.string.marker_carsick, R.color.marker_carsick),
    SLEEPING(R.string.behavior_sleeping, R.string.marker_sleeping, R.color.marker_sleeping),
    TOILET(R.string.behavior_toilet, R.string.marker_toilet, R.color.marker_toilet),
    EXCITED(R.string.behavior_excited, R.string.marker_excited, R.color.marker_excited),
    HOWLING(R.string.behavior_howling, R.string.marker_howling, R.color.marker_howling, R.string.sound_alert_howling, hasButton = false),
    GROWLING(R.string.behavior_growling, R.string.marker_growling, R.color.marker_growling, R.string.sound_alert_growling, hasButton = false),
    DOG_SOUND(R.string.behavior_dog_sound, R.string.marker_dog_sound, R.color.marker_dog_sound, R.string.sound_alert_dog_sound, hasButton = false),
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
    /** 사람이 남긴 기록이 아니라 소리 감지로 남은 기록인지. */
    val detected: Boolean = false,
)
