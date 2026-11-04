package com.example.pawgo

/**
 * YAMNet 분류 결과를 행동으로 바꾼다.
 *
 * 기준값 0.3과 대상 분류는 참고 프로젝트(jatacid/dogbarkingdetector)와 같다.
 * 단, "Animal"처럼 강아지와 상관없는 넓은 분류만으로는 감지하지 않는다.
 */
object PetSound {
    const val SCORE_THRESHOLD = 0.3f

    /**
     * 낑낑 소리가 Growling으로 최대 0.5까지 나온 시험 결과가 있어 으르렁만 기준을 높였다.
     * 시험 녹음 한 개에서 정한 값이고, 실제 으르렁 소리로는 아직 확인하지 못했다.
     */
    const val GROWLING_THRESHOLD = 0.6f

    /** 구체적인 소리 분류. 이름은 YAMNet class map의 display_name이다. */
    private const val GROWLING = "Growling"

    private val SPECIFIC = mapOf(
        "Bark" to PetBehavior.BARKING,
        "Yip" to PetBehavior.BARKING,
        "Bow-wow" to PetBehavior.BARKING,
        "Whimper (dog)" to PetBehavior.WHINING,
        "Howl" to PetBehavior.HOWLING,
        GROWLING to PetBehavior.GROWLING,
    )

    /** 소리 종류는 모르고 강아지라는 것만 아는 분류. 종류를 단정하지 않고 "강아지 소리"로 기록한다. */
    private const val GENERIC_DOG = "Dog"

    /** 기준값을 넘은 구체적인 소리 중 점수가 가장 높은 것을 고른다. 없으면 일반 "Dog" 분류를 본다. */
    fun classify(scores: List<Pair<String, Float>>): PetBehavior? {
        val specific = scores
            .filter { (name, score) -> name in SPECIFIC && score >= thresholdOf(name) }
            .maxByOrNull { (_, score) -> score }
        if (specific != null) return SPECIFIC.getValue(specific.first)
        return if (scores.any { (name, score) -> name == GENERIC_DOG && score >= SCORE_THRESHOLD }) {
            PetBehavior.DOG_SOUND
        } else {
            null
        }
    }

    private fun thresholdOf(name: String): Float =
        if (name == GROWLING) GROWLING_THRESHOLD else SCORE_THRESHOLD
}

/**
 * 같은 소리가 이어질 때 기록과 음성 알림이 쏟아지지 않게 걸러 낸다.
 * 짖는 동안 1초마다 감지되므로, 소리 종류별로 일정 시간에 한 번만 통과시킨다.
 */
class PetSoundFilter(
    private val recordCooldownMs: Long = RECORD_COOLDOWN_MS,
    private val alertCooldownMs: Long = ALERT_COOLDOWN_MS,
) {
    data class Decision(val record: Boolean, val alert: Boolean)

    private val lastRecord = mutableMapOf<PetBehavior, Long>()
    private val lastAlert = mutableMapOf<PetBehavior, Long>()

    fun onDetected(behavior: PetBehavior, now: Long): Decision {
        val record = lastRecord[behavior]?.let { now - it >= recordCooldownMs } ?: true
        if (record) lastRecord[behavior] = now
        val alert = lastAlert[behavior]?.let { now - it >= alertCooldownMs } ?: true
        if (alert) lastAlert[behavior] = now
        return Decision(record, alert)
    }

    companion object {
        const val RECORD_COOLDOWN_MS = 30_000L
        const val ALERT_COOLDOWN_MS = 60_000L
    }
}
