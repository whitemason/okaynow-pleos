package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.domain.voice.VoiceChoice

/** 말로 누를 수 있는 버튼 하나: 이렇게 알아들으면([choice]) 버튼과 같은 [intent]를 보낸다. */
data class VoiceCommand(val choice: VoiceChoice, val intent: AccidentIntent)

/**
 * 지금 단계에서 말로 누를 수 있는 버튼들. 버튼 글자를 그대로 말하면 그 버튼을 누른 것과 같다.
 * C0(홈)은 앱 화면이 앞에 보일 때만 "사고 났어요"를 듣는다(대장 결정, 2026-11-05). 백그라운드 감시 중에는 마이크를 켜지 않는다.
 * 툴바 119는 C4~C9 어디서든 "119"로 부른다. 화면 버튼을 바꾸면 여기도 같이 바꾼다.
 */
fun AccidentUiState.voiceCommands(): List<VoiceCommand> = if (needsPark) emergency() else when (step) {
    AccidentState.Idle -> if (appVisible) listOf(VoiceCommand(ACCIDENT, AccidentIntent.ManualStart)) else emptyList()
    is AccidentState.ConfirmAccident -> listOf(
        VoiceCommand(VoiceChoice.YES, AccidentIntent.AnswerYes),
        VoiceCommand(VoiceChoice.NO, AccidentIntent.AnswerNo),
        VoiceCommand(EMERGENCY, AccidentIntent.RequestEmergency),
    )
    // "119"는 INJURED에 들어 있다(있어요 · 119 버튼).
    is AccidentState.AskInjury -> listOf(
        VoiceCommand(VoiceChoice.INJURED, AccidentIntent.AnswerYes),
        VoiceCommand(VoiceChoice.NOT_INJURED, AccidentIntent.AnswerNo),
    )
    // C3의 119는 툴바가 아니라 다이얼 화면을 여는 버튼이다.
    AccidentState.Emergency -> listOf(
        VoiceCommand(EMERGENCY, AccidentIntent.Call119),
        VoiceCommand(button("위치 다시 듣기", "위치 다시", "위치 알려줘", "다시 듣기"), AccidentIntent.RepeatLocation),
        VoiceCommand(button("통화 끝", "통화 끝났어", "전화 끊었어", "통화 했어"), AccidentIntent.Next),
    )
    AccidentState.SecureScene -> listOf(
        VoiceCommand(button("다음", "다음으로", "넘어가", "완료", "다 했어"), AccidentIntent.Next),
        VoiceCommand(button("처음으로", "처음으로 가", "처음 화면"), AccidentIntent.Finish),
    ) + emergency()
    AccidentState.VehicleCheck -> listOf(VoiceCommand(button("다음", "다음으로", "넘어가"), AccidentIntent.Next)) + back() + emergency()
    AccidentState.PhotoCapture -> listOf(
        // 부위를 안 골랐으면 Reducer가 무시한다(버튼이 꺼진 것과 같게).
        VoiceCommand(button("촬영 완료", "다 찍었어", "찍었어요"), AccidentIntent.Next),
        VoiceCommand(button("음성 안내", "읽어줘", "안내해줘"), AccidentIntent.SpeakShotGuide),
    ) + back() + emergency()
    // "확인"은 온디바이스 STT가 "하긴"으로 알아듣는 일이 많아(2026-11-05 실측) 버튼을 "다음"으로 바꿨다.
    AccidentState.DamageMap -> listOf(VoiceCommand(button("다음", "다음으로", "넘어가", "확인", "맞아요"), AccidentIntent.Next)) + back() + emergency()
    AccidentState.ContactInsurer -> listOf(
        // 보험사를 등록하지 않았으면 Reducer가 무시한다(버튼이 꺼진 것과 같게).
        VoiceCommand(button("보험사 전화", "보험사에 전화", "보험사 연결"), AccidentIntent.CallInsurer),
        VoiceCommand(button("접수 완료", "접수했어", "접수 했어요"), AccidentIntent.Next),
    ) + back() + emergency()
    AccidentState.Done -> listOf(VoiceCommand(button("처음으로", "처음으로 가", "처음 화면"), AccidentIntent.Finish)) + back()
}

/** 버튼 이름(첫 표현)과 같은 뜻의 말. id는 버튼 이름. */
private fun button(vararg phrases: String) = VoiceChoice(phrases.first(), phrases.toList())

// VoiceChoice.YES에도 "사고 났어"가 있다(C1 질문의 답).
private val ACCIDENT = button("사고 났어요", "사고 났어", "사고 났다", "사고야", "사고 났습니다", "사고 처리")
private val EMERGENCY = button("119 전화", "119", "일일구", "119 불러줘")
private fun emergency() = listOf(VoiceCommand(EMERGENCY, AccidentIntent.RequestEmergency))
private fun back() = listOf(VoiceCommand(button("뒤로", "뒤로 가", "이전으로", "이전 단계"), AccidentIntent.Back))
