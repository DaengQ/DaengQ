package com.example.pawgo

import android.content.Context
import android.media.AudioRecord
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions

/**
 * 마이크 소리를 MediaPipe Audio Classifier(YAMNet)로 분류해 강아지 소리를 찾는다.
 *
 * 공식 가이드의 AUDIO_STREAM 방식을 따른다. 모든 처리는 기기 안에서 하고 소리를 저장하거나 보내지 않는다.
 * 콜백은 메인 스레드로 전달한다.
 */
class PetSoundDetector(
    context: Context,
    private val onDetected: (PetBehavior) -> Unit,
    private val onError: (String) -> Unit,
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var thread: HandlerThread? = null
    private var worker: Handler? = null

    @Volatile
    private var isRunning = false

    // 아래 세 값은 작업 스레드에서만 쓴다
    private var classifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var audioData: AudioData? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        val newThread = HandlerThread("PetSoundDetector").apply { start() }
        thread = newThread
        worker = Handler(newThread.looper).also { it.post(::open) }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        worker?.removeCallbacksAndMessages(null)
        worker?.post(::close)
        thread?.quitSafely()
        worker = null
        thread = null
    }

    private fun open() {
        try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.AUDIO_STREAM)
                .setMaxResults(MAX_RESULTS)
                .setResultListener(::onResult)
                .setErrorListener { e -> fail(e) }
                .build()
            val newClassifier = AudioClassifier.createFromOptions(appContext, options)
            classifier = newClassifier
            val record = newClassifier.createAudioRecord(CHANNELS, SAMPLE_RATE, MODEL_INPUT_SAMPLES)
            audioRecord = record
            record.startRecording()
            audioData = AudioData.create(
                AudioData.AudioDataFormat.builder()
                    .setNumOfChannels(CHANNELS)
                    .setSampleRate(SAMPLE_RATE.toFloat())
                    .build(),
                MODEL_INPUT_SAMPLES
            )
            scheduleNext()
        } catch (e: RuntimeException) {
            fail(e)
        }
    }

    private fun classifyNext() {
        val record = audioRecord ?: return
        val data = audioData ?: return
        try {
            data.load(record)
            classifier?.classifyAsync(data, SystemClock.uptimeMillis())
            scheduleNext()
        } catch (e: RuntimeException) {
            fail(e)
        }
    }

    private fun scheduleNext() {
        if (isRunning) worker?.postDelayed(::classifyNext, INTERVAL_MS)
    }

    private fun onResult(result: AudioClassifierResult) {
        val scores = result.classificationResults()
            .flatMap { it.classifications() }
            .flatMap { it.categories() }
            .map { it.categoryName() to it.score() }
        val behavior = PetSound.classify(scores) ?: return
        mainHandler.post { if (isRunning) onDetected(behavior) }
    }

    private fun close() {
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord was not recording", e)
        }
        audioRecord?.release()
        audioRecord = null
        audioData = null
        classifier?.close()
        classifier = null
    }

    private fun fail(e: Throwable) {
        Log.e(TAG, "Sound detection failed", e)
        mainHandler.post {
            stop()
            onError(e.message.orEmpty())
        }
    }

    companion object {
        private const val TAG = "PetSoundDetector"
        private const val MODEL_ASSET = "yamnet.tflite"
        private const val MAX_RESULTS = 5
        private const val CHANNELS = 1
        private const val SAMPLE_RATE = 16_000

        /** YAMNet 입력 크기(15600 샘플, 16kHz에서 0.975초)와 그 길이만큼의 간격. */
        private const val MODEL_INPUT_SAMPLES = 15_600
        private const val INTERVAL_MS = 975L
    }
}
