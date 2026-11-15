package com.whitemason.okaynow.app.data.report

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.ResultListener
import android.content.Context
import android.util.Log
import com.whitemason.okaynow.app.domain.report.SummaryWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gleo LLM v2.1.3.2, ON_DEVICE 모드(앱 등록 불필요). 릴리스 노트상 "LLM SDK 제공 종료"라 실패할 수 있으므로
 * 실패·시간 초과면 null을 돌려주고 호출 측이 템플릿 문장을 쓴다(docs/SDK_NOTES.md 3-3).
 * API는 AAR 시그니처로 확인: LLM(context, Mode), initialize(), generateContent(prompt, ResultListener),
 * ResultListener.onResponse(response, completed)·onError(message). 권한 pleos.car.permission.LLM_SERVICE(normal).
 */
@Singleton
class PleosSummaryWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : SummaryWriter {

    private var llm: LLM? = null

    private fun client(): LLM = llm ?: LLM(context, Mode.ON_DEVICE).also {
        it.initialize() // 서비스 바인딩은 비동기. 연결 전 요청은 "not running" 오류로 오고 아래에서 다시 시도한다.
        llm = it
    }

    override suspend fun summarize(prompt: String): String? = withTimeoutOrNull(TIMEOUT_MS) {
        repeat(ATTEMPTS) { attempt ->
            // SecurityException(권한 없음) 등 SDK 예외도 실패로 본다.
            val text = runCatching { generate(client(), prompt) }.getOrElse { Result.failure(it) }.getOrNull()?.trim()
            if (!text.isNullOrEmpty()) return@withTimeoutOrNull text
            Log.i(TAG, "attempt ${attempt + 1} failed, retrying")
            delay(RETRY_DELAY_MS)
        }
        null
    }

    private suspend fun generate(llm: LLM, prompt: String): Result<String> = suspendCancellableCoroutine { cont ->
        val partial = StringBuilder()
        llm.generateContent(prompt, object : ResultListener {
            override fun onResponse(response: String, completed: Boolean) {
                Log.d(TAG, "response completed=$completed length=${response.length}")
                if (!completed) { partial.append(response); return }
                val text = response.ifBlank { partial.toString() }
                if (cont.isActive) cont.resume(Result.success(text))
            }
            override fun onError(message: String) {
                Log.w(TAG, "llm error: $message")
                if (cont.isActive) cont.resume(Result.failure(IllegalStateException(message)))
            }
        })
    }

    private companion object {
        const val TAG = "GleoLLM"
        const val TIMEOUT_MS = 15_000L
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_500L
    }
}
