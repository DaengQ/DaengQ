package com.example.pawgo

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * TextToSpeech SDK(v3.3.1) 래퍼. 운전자에게 반려동물 상태를 묻고 답을 확인해 준다.
 *
 * 문서의 사용 순서(생성 → initialize → addEventListener → setLocale → speak → removeEventListener/release)를 따른다.
 * ON_DEVICE 모드는 차량 내장 엔진을 쓰므로 네트워크와 앱 등록(Client ID/Secret)이 필요 없다.
 */
class PetVoice(
    context: Context,
    private val onSpeechDone: () -> Unit,
    private val onSpeechError: (String) -> Unit,
) {

    private val textToSpeech = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isReady = false
        private set

    private val eventListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onDone() {
            mainHandler.post { onSpeechDone() }
        }

        override fun onStop() = Unit

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "TTS error: $errorType")
            mainHandler.post { onSpeechError(errorType.name) }
        }
    }

    fun connect() {
        textToSpeech.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                textToSpeech.addEventListener(eventListener)
                textToSpeech.setLocale(Locale.KOREA)
                mainHandler.post { isReady = true }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "TTS initialize failed: $errorType")
                mainHandler.post { onSpeechError(errorType.name) }
            }
        })
    }

    /** 읽고 있던 문장은 끊고 새 문장을 읽는다. 다 읽으면 onDone이 불린다. */
    fun speak(text: String) {
        if (!isReady) return
        textToSpeech.stop()
        textToSpeech.speak(text, true)
    }

    fun stop() {
        if (isReady) textToSpeech.stop()
    }

    fun release() {
        textToSpeech.removeEventListener(eventListener)
        textToSpeech.release()
        mainHandler.removeCallbacksAndMessages(null)
        isReady = false
    }

    companion object {
        private const val TAG = "PetVoice"
    }
}
