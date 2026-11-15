package com.whitemason.okaynow.app.domain.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * 음성 안내·응답. SDK 타입을 노출하지 않는 경계다(구현: [PleosVoiceGuide]).
 * 한 번에 하나의 [speak]·[listen]만 진행하고, 호출한 코루틴이 취소되면 말하기·듣기를 멈춘다.
 */
interface VoiceGuide {

    val state: StateFlow<VoiceState>

    /** [text]를 읽는다. 다 읽었거나 실패하면 돌아온다. */
    suspend fun speak(text: String)

    /**
     * [expected] 중 하나로 알아들을 때까지 듣는다. 시간 제한은 없고, 그만 들으려면 호출한 코루틴을 취소한다.
     * 음성을 쓸 수 없으면 바로 [VoiceAnswer.Unavailable].
     */
    suspend fun listen(expected: List<VoiceChoice>): VoiceAnswer
}

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Speaking : VoiceState
    /** [partial]: 지금까지 알아들은 말. */
    data class Listening(val partial: String = "") : VoiceState
    /** 초기화 실패 등. 화면 버튼으로만 응답한다. */
    data class Unavailable(val reason: String) : VoiceState
}

sealed interface VoiceAnswer {
    data class Matched(val choice: VoiceChoice, val heard: String) : VoiceAnswer
    /** 알아들은 답이 없다(테스트 대역용. 실제 구현은 맞을 때까지 듣는다). */
    data object NoResponse : VoiceAnswer
    data object Unavailable : VoiceAnswer
}

/** 기대하는 대답 하나와 그렇게 알아들을 표현들. */
data class VoiceChoice(val id: String, val phrases: List<String>) {
    companion object {
        val YES = VoiceChoice("응", listOf("응", "어", "네", "예", "맞아", "맞아요", "그래", "사고 났어", "사고야", "사고예요"))
        val NO = VoiceChoice("아니", listOf("아니", "아니요", "아니오", "아뇨", "아니야", "괜찮아", "괜찮아요", "사고 아니야"))

        /** C2 "다친 사람이 있나요?" */
        val INJURED = VoiceChoice("있어", YES.phrases + listOf("있어", "있어요", "다쳤어", "다쳤어요", "다친 사람 있어", "아파", "119", "일일구"))
        val NOT_INJURED = VoiceChoice("없어", NO.phrases + listOf("없어", "없어요", "아무도 안 다쳤어", "안 다쳤어", "안 다쳤어요", "다친 사람 없어"))
    }
}
