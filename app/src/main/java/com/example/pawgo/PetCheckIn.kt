package com.example.pawgo

import android.content.Context

/**
 * 음성으로 반려동물 상태를 묻고, 대답을 행동 기록으로 바꾼다.
 *
 * 질문 → (다 읽으면) 듣기 → 해석 → 확인 한마디. 알아듣지 못하면 한 번만 다시 묻는다.
 */
class PetCheckIn(
    private val context: Context,
    private val onRecorded: (PetBehavior) -> Unit,
    private val onEvent: (String) -> Unit,
    private val onBusyChanged: () -> Unit,
) {

    /** 말하거나 듣는 동안 [isBusy]가 true다. 앱의 음성이 소리 감지에 잡히지 않도록 그동안 감지를 멈춘다. */
    private enum class Step { IDLE, ASKING, CLOSING }

    private val voice = PetVoice(context, ::onSpeechDone) { error -> reportError(error) }
    private val ears = PetEars(context) { error -> reportError(error) }

    private var step = Step.IDLE
        set(value) {
            val wasBusy = field != Step.IDLE
            field = value
            if (wasBusy != (value != Step.IDLE)) onBusyChanged()
        }
    private var attempt = 0

    val isBusy: Boolean
        get() = step != Step.IDLE

    fun connect() {
        voice.connect()
        ears.connect()
    }

    /** 상태를 묻는다. 이미 대화 중이거나 음성 엔진이 준비되지 않았으면 false. */
    fun ask(): Boolean {
        if (isBusy || !voice.isReady || !ears.isReady) return false
        attempt = 1
        say(Step.ASKING, context.getString(R.string.check_in_question, petName()))
        return true
    }

    /** 한 문장을 읽어 준다. 대화 중이거나 음성 엔진이 준비되지 않았으면 false. */
    fun announce(text: String): Boolean {
        if (isBusy || !voice.isReady) return false
        say(Step.CLOSING, text)
        return true
    }

    fun cancel() {
        step = Step.IDLE
        voice.stop()
        ears.stop()
    }

    fun release() {
        step = Step.IDLE
        voice.release()
        ears.release()
    }

    private fun onSpeechDone() {
        when (step) {
            Step.ASKING -> ears.listen(::onAnswer)
            Step.CLOSING -> step = Step.IDLE
            Step.IDLE -> Unit
        }
    }

    private fun onAnswer(text: String?) {
        if (step != Step.ASKING) return
        onEvent(context.getString(R.string.check_in_heard, text ?: "-"))
        when (val answer = text?.let { PetAnswer.parse(it) } ?: PetAnswer.Unknown) {
            is PetAnswer.Behavior -> {
                onRecorded(answer.behavior)
                say(Step.CLOSING, context.getString(R.string.check_in_recorded, context.getString(answer.behavior.labelRes)))
            }
            PetAnswer.Fine -> say(Step.CLOSING, context.getString(R.string.check_in_fine))
            PetAnswer.Unknown -> {
                if (attempt < MAX_ATTEMPTS) {
                    attempt++
                    say(Step.ASKING, context.getString(R.string.check_in_retry))
                } else {
                    say(Step.CLOSING, context.getString(R.string.check_in_give_up))
                }
            }
        }
    }

    private fun say(next: Step, text: String) {
        step = next
        voice.speak(text)
    }

    private fun reportError(error: String) {
        onEvent(context.getString(R.string.voice_error, error))
        // 음성 엔진 오류로 대화가 멈추면 다음 질문을 할 수 있도록 풀어 둔다
        step = Step.IDLE
    }

    private fun petName(): String = context.getString(R.string.pet_name)

    companion object {
        private const val MAX_ATTEMPTS = 2
    }
}
