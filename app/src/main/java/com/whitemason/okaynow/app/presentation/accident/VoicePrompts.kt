package com.whitemason.okaynow.app.presentation.accident

/** 단계별 음성 문구. */
object VoicePrompts {
    // 질문을 읽는 시간도 무응답 타이머(C1 10초, C2 12초)에 들어간다. 에뮬레이터 실측에서 긴 질문은
    // 8.5초가 걸려 들을 시간이 거의 남지 않았으므로 짧게 둔다. 대답 방법은 화면에 보인다.
    const val CONFIRM_QUESTION = "사고가 났나요?"
    const val INJURY_QUESTION = "다친 사람이 있나요?"
    const val EMERGENCY_GUIDE = "119에 전화하세요."
    const val LOCATION_UNKNOWN = "위치를 못 찾았어요. 주변 표지판을 알려 주세요."
    const val PARK_FIRST = "안전한 곳에 차를 세우고 P단으로 바꿔 주세요. 그다음 안내를 이어갈게요."
}
