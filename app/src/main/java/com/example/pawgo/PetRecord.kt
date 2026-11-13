package com.example.pawgo

import androidx.annotation.ColorRes
import androidx.annotation.StringRes

/**
 * 기록하는 강아지 소리 세 종류. 소리 감지와 버튼 기록, 음성 수정이 모두 이 셋만 쓴다.
 * 지도에는 종류별 색의 동그라미로 표시한다. 화면에는 이 순서(울음·낑낑·하울링)로 보여 준다.
 */
enum class PetBehavior(
    @StringRes val labelRes: Int,
    @ColorRes val markerColorRes: Int,
    /** 소리로 감지했을 때 운전자에게 읽어 줄 문장. */
    @StringRes val soundAlertRes: Int,
) {
    CRYING(R.string.behavior_crying, R.color.marker_crying, R.string.sound_alert_crying),
    WHINING(R.string.behavior_whining, R.color.marker_whining, R.string.sound_alert_whining),
    HOWLING(R.string.behavior_howling, R.color.marker_howling, R.string.sound_alert_howling);

    companion object {
        /**
         * 저장된 이름을 종류로 바꾼다. 종류가 9개였던 예전 기록도 읽는다.
         * 짖음·으르렁·강아지 소리는 울음으로 옮기고, 소리가 아닌 기록(멀미·잠·배변·흥분)은 버린다.
         */
        fun fromStoredName(name: String): PetBehavior? = when (name) {
            "WHINING" -> WHINING
            "CRYING", "BARKING", "GROWLING", "DOG_SOUND" -> CRYING
            "HOWLING" -> HOWLING
            else -> null
        }
    }
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
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null
}
