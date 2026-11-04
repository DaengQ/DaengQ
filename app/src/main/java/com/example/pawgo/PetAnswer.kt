package com.example.pawgo

/** 운전자의 대답을 해석한 결과. */
sealed interface PetAnswer {
    data class Behavior(val behavior: PetBehavior) : PetAnswer
    data object Fine : PetAnswer
    data object Unknown : PetAnswer

    companion object {
        /** 대답에 들어 있는 낱말로 행동을 고른다. 앞에 나온 행동일수록 먼저 고른다. */
        private val KEYWORDS = listOf(
            PetBehavior.CARSICK to listOf("멀미", "토해", "토했", "구토", "토할"),
            PetBehavior.TOILET to listOf("쉬", "똥", "오줌", "화장실", "배변"),
            PetBehavior.BARKING to listOf("짖"),
            PetBehavior.GROWLING to listOf("으르렁"),
            PetBehavior.HOWLING to listOf("하울링"),
            PetBehavior.WHINING to listOf("낑낑", "불안", "떨", "울어", "울고"),
            PetBehavior.EXCITED to listOf("흥분", "신나", "신났", "날뛰"),
            PetBehavior.SLEEPING to listOf("자요", "자고", "잔다", "잠", "졸"),
        )
        private val FINE_WORDS = listOf("괜찮", "얌전", "좋아", "편안", "조용", "별일")

        fun parse(text: String): PetAnswer {
            val compact = text.replace(" ", "")
            KEYWORDS.forEach { (behavior, words) ->
                if (words.any { compact.contains(it) }) return Behavior(behavior)
            }
            return if (FINE_WORDS.any { compact.contains(it) }) Fine else Unknown
        }
    }
}
