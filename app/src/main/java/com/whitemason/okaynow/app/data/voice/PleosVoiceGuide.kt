package com.whitemason.okaynow.app.data.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import android.content.Context
import android.util.Log
import com.whitemason.okaynow.app.domain.voice.VoiceAnswer
import com.whitemason.okaynow.app.domain.voice.VoiceChoice
import com.whitemason.okaynow.app.domain.voice.VoiceGuide
import com.whitemason.okaynow.app.domain.voice.VoiceState
import com.whitemason.okaynow.app.domain.voice.matchAnswer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gleo TextToSpeech·SpeechToText v3.3.1, ON_DEVICE 모드. 사용 신청이 "준비 중"이라 앱 등록이 필요 없는
 * ON_DEVICE만 쓴다(docs/SDK_NOTES.md 0절 5). API는 문서와 AAR 시그니처로 확인한 것만 쓴다.
 */
@Singleton
class PleosVoiceGuide @Inject constructor(
    @ApplicationContext context: Context,
) : VoiceGuide {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private val ttsReady = CompletableDeferred<Boolean>()
    private val sttReady = CompletableDeferred<Boolean>()

    /** 지금 진행 중인 말하기의 끝(onDone/onStop/onError). */
    @Volatile private var speechDone: CompletableDeferred<Unit>? = null

    /** STT 이벤트. 듣기를 시작할 때마다 비운다. */
    private val heard = Channel<SttEvent>(Channel.UNLIMITED)

    private sealed interface SttEvent {
        data class Text(val text: String, val completed: Boolean) : SttEvent
        data object Ended : SttEvent
        data class Error(val type: SttError) : SttEvent
    }

    private val ttsListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { speechDone?.complete(Unit) }
        override fun onStop() { speechDone?.complete(Unit) }
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "tts error: $errorType")
            speechDone?.complete(Unit)
        }
    }

    private val sttListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) { heard.trySend(SttEvent.Text(stt, completed)) }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() { heard.trySend(SttEvent.Ended) }
        override fun onError(errorType: SttError) { heard.trySend(SttEvent.Error(errorType)) }
    }

    init {
        tts.initialize(object : TtsInit {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                tts.addEventListener(ttsListener)
                ttsReady.complete(true)
            }
            override fun onFailure(errorType: TtsError) {
                Log.w(TAG, "tts init failed: $errorType")
                ttsReady.complete(false)
            }
        })
        stt.initialize(object : SttInit {
            override fun onSuccess() {
                stt.setLocale(Locale.KOREA)
                stt.addListener(sttListener)
                sttReady.complete(true)
            }
            override fun onFailure(errorType: SttError) {
                Log.w(TAG, "stt init failed: $errorType")
                sttReady.complete(false)
            }
        })
    }

    override suspend fun speak(text: String) = mutex.withLock { speakLocked(text) }

    override suspend fun listen(expected: List<VoiceChoice>): VoiceAnswer =
        mutex.withLock {
            if (!awaitReady(sttReady, "STT")) return@withLock VoiceAnswer.Unavailable
            try {
                listenUntilMatched(expected)
            } finally {
                if (_state.value !is VoiceState.Unavailable) _state.value = VoiceState.Idle
            }
        }

    private suspend fun speakLocked(text: String) {
        if (!awaitReady(ttsReady, "TTS")) return
        val done = CompletableDeferred<Unit>().also { speechDone = it }
        _state.value = VoiceState.Speaking
        try {
            tts.speak(text, true)
            done.await()
        } finally {
            if (!done.isCompleted) tts.stop()
            speechDone = null
            if (_state.value == VoiceState.Speaking) _state.value = VoiceState.Idle
        }
    }

    /** 알아들은 답이 나올 때까지 듣고, 한 번 인식이 끝나면 다시 듣는다(그만 듣는 건 호출 측 취소). */
    private suspend fun listenUntilMatched(expected: List<VoiceChoice>): VoiceAnswer {
        while (true) {
            while (heard.tryReceive().isSuccess) Unit
            _state.value = VoiceState.Listening()
            stt.request()
            try {
                while (true) {
                    when (val event = heard.receive()) {
                        is SttEvent.Text -> {
                            _state.value = VoiceState.Listening(event.text)
                            matchAnswer(event.text, expected)?.let { return VoiceAnswer.Matched(it, event.text) }
                            if (event.completed) break
                        }
                        // 실측(2026-11-05): 말 끝(EPD) 신호가 최종 문장보다 먼저 온다("처음으" → 끝 → "처음으로").
                        // 바로 멈추면 최종 문장을 놓치므로 잠깐 더 기다려 받는다.
                        SttEvent.Ended -> {
                            awaitFinalText(expected)?.let { return it }
                            break
                        }
                        is SttEvent.Error -> {
                            Log.w(TAG, "stt error: ${event.type}")
                            break
                        }
                    }
                }
            } finally {
                stt.stop()
            }
            // 마이크를 못 쓰는 동안(통화 중 등) 오류가 연달아 나도 바로 다시 요청하지 않게 쉰다.
            delay(RETRY_DELAY_MS)
        }
    }

    /** 말 끝 신호 뒤에 오는 최종 문장을 [FINAL_TEXT_WAIT_MS]까지 기다려 맞춰 본다. */
    private suspend fun awaitFinalText(expected: List<VoiceChoice>): VoiceAnswer? = withTimeoutOrNull(FINAL_TEXT_WAIT_MS) {
        var answer: VoiceAnswer? = null
        var done = false
        while (!done) {
            when (val event = heard.receive()) {
                is SttEvent.Text -> {
                    _state.value = VoiceState.Listening(event.text)
                    matchAnswer(event.text, expected)?.let { answer = VoiceAnswer.Matched(it, event.text) }
                    done = answer != null || event.completed
                }
                SttEvent.Ended -> Unit
                is SttEvent.Error -> done = true
            }
        }
        answer
    }

    private suspend fun awaitReady(ready: CompletableDeferred<Boolean>, name: String): Boolean {
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } == true
        if (!ok) _state.value = VoiceState.Unavailable("$name 초기화 실패")
        return ok
    }

    private companion object {
        const val TAG = "VoiceGuide"
        const val INIT_TIMEOUT_MS = 3_000L
        const val RETRY_DELAY_MS = 300L
        const val FINAL_TEXT_WAIT_MS = 1_000L
    }
}
