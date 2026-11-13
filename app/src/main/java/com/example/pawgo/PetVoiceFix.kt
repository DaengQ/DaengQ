package com.example.pawgo

import android.content.Context

/**
 * 음성 안내와 '말로 고치기'를 맡는다.
 *
 * 말로 고치기: "네, 어떻게 고칠까요?" → (다 읽으면) 듣기 → 해석 → 결과 한마디.
 * 8초 동안 말이 없거나 알아듣지 못하면 한 번 더 묻고, 그래도 안 되면 버튼으로 고치라고 안내한다.
 */
class PetVoiceFix(
    private val context: Context,
    /** 해석한 명령을 기록에 반영하고, 운전자에게 읽어 줄 결과 문장을 돌려준다. */
    private val onCommand: (PetCommand) -> String,
    /** 들은 말(못 들었으면 null)을 화면에 잠깐 보여 준다. */
    private val onHeard: (String?) -> Unit,
    private val onError: (String) -> Unit,
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

    /** 어떻게 고칠지 묻고 듣는다. 이미 대화 중이거나 음성 엔진이 준비되지 않았으면 false. */
    fun start(): Boolean {
        if (isBusy || !voice.isReady || !ears.isReady) return false
        attempt = 1
        say(Step.ASKING, context.getString(R.string.voice_fix_question))
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
        onHeard(text)
        val command = text?.let { PetCommand.parse(it) } ?: PetCommand.Unknown
        if (command != PetCommand.Unknown) {
            say(Step.CLOSING, onCommand(command))
        } else if (attempt < MAX_ATTEMPTS) {
            attempt++
            say(Step.ASKING, context.getString(R.string.voice_fix_retry))
        } else {
            say(Step.CLOSING, context.getString(R.string.voice_fix_give_up))
        }
    }

    private fun say(next: Step, text: String) {
        step = next
        voice.speak(text)
    }

    private fun reportError(error: String) {
        onError(error)
        // 음성 엔진 오류로 대화가 멈추면 다시 시작할 수 있도록 풀어 둔다
        step = Step.IDLE
    }

    companion object {
        private const val MAX_ATTEMPTS = 2
    }
}
