package com.example.pawgo

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale

/**
 * SpeechToText SDK(v3.3.1) 래퍼. 운전자의 대답 한 번을 듣고 최종 인식 결과를 돌려준다.
 *
 * 문서의 사용 순서(생성 → initialize → addListener → setLocale → request → stop → removeListener/release)를 따른다.
 * ON_DEVICE 모드는 차량 내장 엔진을 쓰므로 네트워크와 앱 등록이 필요 없다.
 */
class PetEars(
    context: Context,
    private val onError: (String) -> Unit,
) {

    private val speechToText = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isReady = false
        private set

    /** 듣는 중일 때만 있다. 결과가 오면 한 번 부르고 비운다. */
    private var pendingAnswer: ((String?) -> Unit)? = null
    private var lastText: String? = null

    private val resultListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onUpdated(stt: String, completed: Boolean) {
            mainHandler.post {
                lastText = stt
                if (completed) deliver(stt)
            }
        }

        override fun onEndedRecognition() {
            // 최종 결과 없이 끝나면 들은 데까지를, 아무 말도 없었으면 null을 넘긴다
            mainHandler.post { deliver(lastText) }
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "STT error: $errorType")
            mainHandler.post {
                onError(errorType.name)
                deliver(null)
            }
        }
    }

    fun connect() {
        speechToText.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                speechToText.addListener(resultListener)
                speechToText.setLocale(Locale.KOREA)
                mainHandler.post { isReady = true }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "STT initialize failed: $errorType")
                mainHandler.post { onError(errorType.name) }
            }
        })
    }

    /** 대답을 한 번 듣는다. 인식된 글이 없으면 null이 넘어온다. */
    fun listen(onAnswer: (String?) -> Unit) {
        if (!isReady) {
            onAnswer(null)
            return
        }
        pendingAnswer = onAnswer
        lastText = null
        speechToText.request()
        // 대답이 없어도 마이크를 오래 열어 두지 않는다
        mainHandler.postAtTime({ deliver(lastText) }, LISTEN_TOKEN, SystemClock.uptimeMillis() + LISTEN_TIMEOUT_MS)
    }

    fun stop() {
        if (pendingAnswer == null) return
        pendingAnswer = null
        mainHandler.removeCallbacksAndMessages(LISTEN_TOKEN)
        speechToText.stop()
    }

    fun release() {
        pendingAnswer = null
        speechToText.removeListener(resultListener)
        speechToText.release()
        mainHandler.removeCallbacksAndMessages(null)
        isReady = false
    }

    private fun deliver(text: String?) {
        val callback = pendingAnswer ?: return
        pendingAnswer = null
        mainHandler.removeCallbacksAndMessages(LISTEN_TOKEN)
        speechToText.stop()
        callback(text?.takeIf { it.isNotBlank() })
    }

    companion object {
        private const val TAG = "PetEars"
        private const val LISTEN_TIMEOUT_MS = 8_000L
        private val LISTEN_TOKEN = Any()
    }
}
