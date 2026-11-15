package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.detection.ImpactAssessment
import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.domain.flow.previousStep
import com.whitemason.okaynow.app.domain.location.LocationFix
import com.whitemason.okaynow.app.domain.report.AccidentReport
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.domain.report.ReportSummary
import com.whitemason.okaynow.app.domain.vehicle.DemoScenario
import com.whitemason.okaynow.app.domain.vehicle.VehicleStatus
import com.whitemason.okaynow.app.domain.voice.VoiceState

/*
 * MVI 계약. 단방향: View → [AccidentIntent] → [AccidentReducer] → [AccidentUiState] → View.
 * Reducer가 낸 [AccidentSideEffect]는 [AccidentStore]가 실행하고, 결과는 다시 Intent로 들어온다.
 */

/** 화면이 그리는 모든 것. 불변이고 하나뿐이다. */
data class AccidentUiState(
    /** 사고 처리 단계(C0~C9). */
    val step: AccidentState = AccidentState.Idle,
    /** C1·C2 무응답까지 남은 초. 질문 중이 아니면 null. */
    val secondsLeft: Int? = null,
    /** 진행 중인 급정차 에피소드의 충격 평가. */
    val assessment: ImpactAssessment? = null,
    val vehicle: VehicleStatus = VehicleStatus(),
    val voice: VoiceState = VoiceState.Idle,
    /** C3에서 마지막으로 확인한 위치. */
    val location: LocationFix? = null,
    /** C6에서 고른 충돌 부위. 처음(C0)으로 돌아오면 비운다. */
    val damageZones: Set<CarZone> = emptySet(),
    val simulation: Boolean = false,
    val scenarios: List<DemoScenario> = emptyList(),
    val playing: DemoScenario? = null,
    /** C0 설정에서 등록한 보험사. */
    val insurer: Insurer? = null,
    /** 앱 화면이 앞에 보이는지. C0은 보일 때만 "사고 났어요"를 듣는다(마이크가 늘 켜지지 않게). */
    val appVisible: Boolean = false,

    // 이번 사고 처리 기록(C8 접수 요약·C9 체크리스트). 처음(C0)으로 돌아오면 비운다.
    /** 처리를 시작한 벽시계 시각(epoch ms). */
    val startedAtMillis: Long? = null,
    /** C2 답. null이면 응답 없음(또는 아직 묻지 않음). */
    val injured: Boolean? = null,
    val emergencyGuided: Boolean = false,
    val secureSceneDone: Boolean = false,
    /** C8에서 만든 원자료와 요약. 만드는 중이면 null. */
    val report: AccidentReport? = null,
    val summary: ReportSummary? = null,
    /** C8에서 보험사 다이얼 화면을 열었는지. */
    val insurerDialed: Boolean = false,
    /** Gleo LLM 요약을 기다리는 중(그동안은 템플릿 문장을 보여 준다). */
    val summarizing: Boolean = false,

    /** 차 안에 보관 중인 사고 기록 수(C0 개인정보 안내). 처음으로 돌아와도 유지한다. */
    val savedReports: Int = 0,
) {
    /** 툴바 뒤로가기를 보일지. C5~C9만. */
    val canGoBack: Boolean get() = step.previousStep() != null && !needsPark

    /** C4~C9인데 P단이 아니다: "차를 세우세요"만 보이고, P단이 되면 그 단계가 이어진다(CLAUDE.md 규칙 5). */
    val needsPark: Boolean get() = step in PARK_ONLY_STEPS && !vehicle.isParked
}

/** P단에서만 여는 단계(C4~C9). 주행 중에는 C1~C3만. */
val PARK_ONLY_STEPS: Set<AccidentState> = setOf(
    AccidentState.SecureScene,
    AccidentState.VehicleCheck,
    AccidentState.PhotoCapture,
    AccidentState.DamageMap,
    AccidentState.ContactInsurer,
    AccidentState.Done,
)

/** 상태를 바꾸려는 모든 입력. 사용자 입력과 시스템 결과(음성 답·타이머·신호)가 같은 길로 들어온다. */
sealed interface AccidentIntent {
    // 사용자: 단계 이동
    data object ManualStart : AccidentIntent
    data object AnswerYes : AccidentIntent
    data object AnswerNo : AccidentIntent
    data object Next : AccidentIntent
    data object Back : AccidentIntent
    data object Finish : AccidentIntent
    data object RequestEmergency : AccidentIntent

    // 사용자: 화면 안의 동작
    data class ToggleZone(val zone: CarZone) : AccidentIntent
    /** C6 "음성 안내": 고른 부위로 촬영 요약을 읽는다. */
    data object SpeakShotGuide : AccidentIntent
    /** C3 "위치 다시 듣기". */
    data object RepeatLocation : AccidentIntent
    /** C8 "보험사 전화": 등록한 번호로 다이얼 화면을 연다(자동 발신 아님). */
    data object CallInsurer : AccidentIntent
    /** C0 설정: 보험사 등록·삭제(null). */
    data class SaveInsurer(val insurer: Insurer?) : AccidentIntent
    /** C0 개인정보 안내: 보관한 사고 기록을 모두 지운다. */
    data object ClearReports : AccidentIntent
    /** 119 다이얼 화면을 연다(자동 발신 아님). */
    data object Call119 : AccidentIntent
    data class SetSimulation(val enabled: Boolean) : AccidentIntent
    data class PlayScenario(val scenario: DemoScenario) : AccidentIntent

    // 시스템: 부수 효과와 입력 스트림의 결과
    data class ImpactScored(val assessment: ImpactAssessment?) : AccidentIntent
    data class ImpactTriggered(val assessment: ImpactAssessment) : AccidentIntent
    data class ImpactEscalated(val assessment: ImpactAssessment) : AccidentIntent
    data class TimerTick(val secondsLeft: Int?) : AccidentIntent
    data object NoResponse : AccidentIntent
    data class VehicleChanged(val vehicle: VehicleStatus) : AccidentIntent
    data class VoiceChanged(val voice: VoiceState) : AccidentIntent
    data class LocationResolved(val location: LocationFix?) : AccidentIntent
    data class SimulationChanged(val enabled: Boolean, val playing: DemoScenario?) : AccidentIntent
    data class InsurerChanged(val insurer: Insurer?) : AccidentIntent
    /** 보관 중인 사고 기록 수를 셌다. */
    data class ReportsCounted(val count: Int) : AccidentIntent
    /** 앱 화면이 앞에 나오거나(onStart) 가려졌다(onStop). */
    data class AppVisibilityChanged(val visible: Boolean) : AccidentIntent
    /** 처리 시작 시각(벽시계). */
    data class Started(val atMillis: Long) : AccidentIntent
    /** C8 접수 원자료와 요약이 준비됐다. [final]이 아니면 템플릿 문장이고 LLM 요약을 기다린다. */
    data class ReportReady(val report: AccidentReport, val summary: ReportSummary, val final: Boolean = true) : AccidentIntent

    /** 단위 테스트용. 전이 규칙을 거치지 않고 단계를 연다(앱 화면에서는 보내지 않는다). */
    data class DebugForceStep(val step: AccidentState) : AccidentIntent
}

/** Reducer가 요청하고 [AccidentStore]가 실행하는 바깥 일. */
sealed interface AccidentSideEffect {
    /** 지금 하던 음성(질문·안내)을 멈춘다. */
    data object CancelVoice : AccidentSideEffect
    data object CancelTimer : AccidentSideEffect
    /** 무응답 타이머. 끝나면 [AccidentIntent.NoResponse]. */
    data class StartNoResponseTimer(val millis: Long) : AccidentSideEffect
    /**
     * 지금 단계의 버튼 이름을 듣는다([voiceCommands]). 하던 말이 있으면 다 말한 뒤에 듣는다.
     * 알아들으면 그 버튼의 Intent를 보내고, 단계가 그대로면 계속 듣는다. 단계가 바뀌면 [CancelVoice]로 멈춘다.
     */
    data object Listen : AccidentSideEffect
    data class Speak(val text: String) : AccidentSideEffect
    /** C3: 119 안내를 읽고, 그동안 구한 위치를 읽는다. */
    data object EmergencyGuide : AccidentSideEffect
    data object ReadLocation : AccidentSideEffect
    data class LogFalsePositive(val score: Int) : AccidentSideEffect
    /** 처리 시작 시각을 찍는다 → [AccidentIntent.Started]. */
    data object StampStart : AccidentSideEffect
    /** C8: 위치를 구하고 원자료를 만들어 요약한다(Gleo LLM, 실패하면 템플릿) → [AccidentIntent.ReportReady]. */
    data object PrepareReport : AccidentSideEffect
    /** 원자료 + 요약을 보관한다. */
    data class ArchiveReport(val report: AccidentReport, val summary: ReportSummary) : AccidentSideEffect
    data class SaveInsurer(val insurer: Insurer?) : AccidentSideEffect
    /** 보관한 사고 기록을 지우고 다시 센다 → [AccidentIntent.ReportsCounted]. */
    data object ClearReports : AccidentSideEffect
    data class SetSimulation(val enabled: Boolean) : AccidentSideEffect
    data class PlayScenario(val scenario: DemoScenario) : AccidentSideEffect
    /** 화면만 할 수 있는 일(Activity 실행 등). */
    data class Ui(val effect: AccidentUiEffect) : AccidentSideEffect
}

/** 화면이 한 번만 처리하는 일. */
sealed interface AccidentUiEffect {
    data class OpenDialer(val number: String) : AccidentUiEffect
}
