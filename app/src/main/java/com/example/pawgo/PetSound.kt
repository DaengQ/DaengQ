package com.example.pawgo

/**
 * YAMNet 분류 결과를 기록 종류로 바꾼다. 기준값 0.3 이상인 분류만 본다(PRD: 실차 테스트에서 조정).
 * "Animal"처럼 강아지와 상관없는 넓은 분류만으로는 감지하지 않는다.
 */
object PetSound {
    const val SCORE_THRESHOLD = 0.3f

    /** 구체적인 소리 분류. 이름은 YAMNet class map의 display_name이다. */
    private val SPECIFIC = mapOf(
        "Whimper (dog)" to PetBehavior.WHINING,
        "Howl" to PetBehavior.HOWLING,
        "Bark" to PetBehavior.CRYING,
        "Yip" to PetBehavior.CRYING,
        "Bow-wow" to PetBehavior.CRYING,
    )

    /** 소리 종류는 모르고 강아지라는 것만 아는 분류. PRD에 따라 울음으로 기록한다. */
    private const val GENERIC_DOG = "Dog"

    /** 기준값을 넘은 구체적인 소리 중 점수가 가장 높은 것을 고른다. 없으면 일반 "Dog" 분류를 본다. */
    fun classify(scores: List<Pair<String, Float>>): PetBehavior? {
        val specific = scores
            .filter { (name, score) -> name in SPECIFIC && score >= SCORE_THRESHOLD }
            .maxByOrNull { (_, score) -> score }
        if (specific != null) return SPECIFIC.getValue(specific.first)
        return if (scores.any { (name, score) -> name == GENERIC_DOG && score >= SCORE_THRESHOLD }) {
            PetBehavior.CRYING
        } else {
            null
        }
    }
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
