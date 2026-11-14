package com.example.pawgo

/** '말로 고치기'에서 운전자가 한 말을 해석한 결과. */
sealed interface PetCommand {
    /** [from] 종류의 가장 최근 기록을 [to]로 바꾼다. [from]이 null이면 종류와 상관없이 가장 최근 기록. */
    data class Change(val from: PetBehavior?, val to: PetBehavior) : PetCommand

    /** [target] 종류의 가장 최근 기록을 지운다. [target]이 null이면 가장 최근 기록. */
    data class Delete(val target: PetBehavior?) : PetCommand

    /** 새 기록을 남긴다. */
    data class Add(val behavior: PetBehavior) : PetCommand

    /** 아무것도 바꾸지 않는다. */
    data object Cancel : PetCommand

    data object Unknown : PetCommand

    companion object {
        private val KEYWORDS = mapOf(
            PetBehavior.WHINING to listOf("낑낑", "끙끙", "칭얼"),
            PetBehavior.CRYING to listOf("울음", "울어", "울고", "울지", "운다", "울었", "짖"),
            PetBehavior.HOWLING to listOf("하울"),
        )
        /** 지우라는 말. "지운다"의 "운다"가 울음으로 읽히지 않도록 종류를 찾기 전에 빼낸다. */
        private val DELETE = Regex("지우|지워|지울|지운|삭제|없애|없앨|없앤")

        /** 하지 말라는 말: "지우지 마", "삭제하지 말고", "바꾸면 안 돼". 이 부분은 명령에서 빼고 읽는다. */
        private val PROHIBITED = Regex("(지우|지워|삭제하|삭제|없애|바꾸|바꿔|변경하|변경)(지마|지말|지않|면안)")

        /** 그 소리를 안 냈다는 말: "안 울었어", "낑낑 안 했어", "울지 않았어". 잘못 남은 기록을 지우라는 뜻이다. */
        private val DENIED_SOUND = Regex("안(울|짖|낑|끙|칭얼|하울|했)|(울|짖)지않")

        private val NEGATE_WORDS = listOf("아니", "말고")
        private val CHANGE_WORDS = listOf("바꿔", "바꾸", "변경", "으로")
        private val CANCEL_WORDS = listOf("됐어", "됐다", "괜찮", "그만")

        /**
         * 예: "아까 거 울음으로 바꿔줘", "하울링 아니고 울음이야", "방금 거 지워줘",
         * "하울링 기록 삭제해 줘", "지금 낑낑거려", "됐어", "방금 안 울었어", "지우지 마". 앞에 "댕큐"를 붙여도 된다.
         */
        fun parse(text: String): PetCommand {
            val compact = text.replace(" ", "").replace("댕큐", "")
            val prohibited = PROHIBITED.containsMatchIn(compact)
            val allowed = PROHIBITED.replace(compact, "")
            val deleteAsked = DELETE.containsMatchIn(allowed)
            val mentioned = mentionedInOrder(DELETE.replace(allowed, ""))
            val changeAsked = CHANGE_WORDS.any { it in allowed } && mentioned.isNotEmpty()
            return when {
                deleteAsked -> Delete(mentioned.firstOrNull())
                DENIED_SOUND.containsMatchIn(allowed) -> mentioned.firstOrNull()?.let { Delete(it) } ?: Unknown
                // 하지 말라고만 했으면 아무것도 바꾸지 않는다. 예: "지우지 마", "하울링 바꾸지 마"
                prohibited && !changeAsked -> Cancel
                NEGATE_WORDS.any { it in allowed } -> when {
                    mentioned.size >= 2 -> Change(mentioned.first(), mentioned.last())
                    CANCEL_WORDS.any { it in allowed } -> Cancel
                    // "울음 아니야"처럼 무엇으로 바꿀지 없으면 되묻는다
                    else -> Unknown
                }
                changeAsked ->
                    if (mentioned.size >= 2) Change(mentioned.first(), mentioned.last()) else Change(null, mentioned.first())
                CANCEL_WORDS.any { it in allowed } -> Cancel
                mentioned.size == 1 -> Add(mentioned.first())
                else -> Unknown
            }
        }

        /** 말에 나온 종류를 처음 나온 순서대로. */
        private fun mentionedInOrder(text: String): List<PetBehavior> =
            KEYWORDS.mapNotNull { (behavior, words) ->
                words.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()?.let { behavior to it }
            }.sortedBy { it.second }.map { it.first }
    }
}
