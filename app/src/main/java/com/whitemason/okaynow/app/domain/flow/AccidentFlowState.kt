package com.whitemason.okaynow.app.domain.flow

/** 사고 처리 단계. 화면 ID(C1~C9)는 PLAN.md 6절. */
sealed interface AccidentState {
    /** 주행 중 백그라운드 감지 (C0 홈). */
    data object Idle : AccidentState

    /** C1 충격 확인. 무응답이면 [score]로 다음 단계를 정한다. */
    data class ConfirmAccident(val score: Int) : AccidentState

    /**
     * C2 부상 확인. [severe]면(에어백 전개, 충격 점수 90점 이상) 무응답 시 119 안내(C3)로,
     * 아니면 오탐이나 가벼운 접촉으로 보고 처음(C0)으로 돌아간다.
     */
    data class AskInjury(val severe: Boolean = false) : AccidentState

    /** C3 긴급 모드: 119 안내, 위치 낭독. */
    data object Emergency : AccidentState

    /** C4 안전 확보. */
    data object SecureScene : AccidentState

    /** C5 차량 점검. */
    data object VehicleCheck : AccidentState

    /** C6 촬영 연결(QR). */
    data object PhotoCapture : AccidentState

    /** C7 파손 맵. */
    data object DamageMap : AccidentState

    /** C8 보험사 연결. 자동 발신하지 않는다. */
    data object ContactInsurer : AccidentState

    /** C9 완료. */
    data object Done : AccidentState
}

sealed interface AccidentEvent {
    /** 충격 점수가 확인 임계값을 넘었다. [airbagDeployed]면 C1을 건너뛴다. */
    data class ImpactDetected(val score: Int, val airbagDeployed: Boolean = false) : AccidentEvent

    /** 음성 "사고 났어" 또는 수동 시작 버튼. */
    data object ManualStart : AccidentEvent

    data object Yes : AccidentEvent
    data object No : AccidentEvent

    /** 질문에 정해진 시간 안에 답이 없다. */
    data object NoResponse : AccidentEvent

    /** 어느 단계에서든 119 버튼. */
    data object RequestEmergency : AccidentEvent

    /** 현재 단계를 마치고 다음으로. C3에서는 "통화 끝". */
    data object Next : AccidentEvent

    /**
     * 툴바 뒤로가기. C5~C9에서만 한 단계 앞으로 돌아간다.
     * C1~C4에서는 무시한다(부상 확인·119 안내를 건너뛰지 않게, CLAUDE.md 규칙 4).
     */
    data object Back : AccidentEvent

    /** 처음으로: C4·C9에서 흐름을 끝낸다. 시연 초기화에도 쓴다. */
    data object Finish : AccidentEvent
}

/** 상태 전이와 함께 처리해야 할 부수 효과. */
sealed interface AccidentEffect {
    /** 운전자가 C1에서 "아니오" → 오탐 기록. */
    data class LogFalsePositive(val score: Int) : AccidentEffect
}

data class Transition(val state: AccidentState, val effect: AccidentEffect? = null)
