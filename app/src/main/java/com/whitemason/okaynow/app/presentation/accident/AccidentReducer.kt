package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.damage.damageSummary
import com.whitemason.okaynow.app.domain.damage.planShots
import com.whitemason.okaynow.app.domain.damage.shotSummary
import com.whitemason.okaynow.app.domain.detection.ImpactAssessment
import com.whitemason.okaynow.app.domain.flow.AccidentEffect
import com.whitemason.okaynow.app.domain.flow.AccidentEvent
import com.whitemason.okaynow.app.domain.flow.AccidentFlowStateMachine
import com.whitemason.okaynow.app.domain.flow.AccidentState

/** 상태 + 의도 → 새 상태 + 부수 효과. 순수 함수이고 Android·SDK에 의존하지 않는다. */
data class Reduction(val state: AccidentUiState, val effects: List<AccidentSideEffect> = emptyList())

/**
 * 단계 전이 규칙(안전 규칙 포함)은 [AccidentFlowStateMachine]이 정하고, 여기서는
 * 단계에 들어설 때 할 일(질문·안내·타이머)과 화면 상태 갱신을 정한다.
 */
class AccidentReducer(private val machine: AccidentFlowStateMachine = AccidentFlowStateMachine()) {

    fun reduce(state: AccidentUiState, intent: AccidentIntent): Reduction =
        // "차를 세우세요" 화면에서는 119만 누를 수 있다. 화면에 없는 버튼을 말로 눌러 단계가 몰래 넘어가지 않게 막는다.
        if (state.needsPark && intent in PARK_BLOCKED) Reduction(state) else reduceIntent(state, intent)

    private fun reduceIntent(state: AccidentUiState, intent: AccidentIntent): Reduction = when (intent) {
        AccidentIntent.ManualStart -> step(state, AccidentEvent.ManualStart)
        // C2 답은 접수 기록에 남긴다.
        AccidentIntent.AnswerYes -> step(if (state.step is AccidentState.AskInjury) state.copy(injured = true) else state, AccidentEvent.Yes)
        AccidentIntent.AnswerNo -> step(if (state.step is AccidentState.AskInjury) state.copy(injured = false) else state, AccidentEvent.No)
        // C6에서 부위를 고르지 않았으면 "촬영 완료"는 꺼져 있다(음성으로 말해도 같게 무시).
        AccidentIntent.Next ->
            if (state.step == AccidentState.PhotoCapture && state.damageZones.isEmpty()) Reduction(state)
            else step(state, AccidentEvent.Next)
        AccidentIntent.Back -> step(state, AccidentEvent.Back)
        AccidentIntent.Finish -> step(state, AccidentEvent.Finish)
        AccidentIntent.RequestEmergency -> step(state, AccidentEvent.RequestEmergency)
        AccidentIntent.NoResponse -> step(state, AccidentEvent.NoResponse)

        is AccidentIntent.ImpactScored -> Reduction(state.copy(assessment = intent.assessment))
        is AccidentIntent.ImpactTriggered -> step(state.copy(assessment = intent.assessment), intent.assessment.toEvent())
        // 같은 사고에서 점수가 오르면 C1의 무응답 판단 기준만 갱신한다(사용자가 이미 답했으면 무시).
        is AccidentIntent.ImpactEscalated -> {
            val scored = state.copy(assessment = intent.assessment)
            if (state.step is AccidentState.ConfirmAccident) step(scored, intent.assessment.toEvent()) else Reduction(scored)
        }

        is AccidentIntent.ToggleZone -> Reduction(
            state.copy(damageZones = state.damageZones.let { if (intent.zone in it) it - intent.zone else it + intent.zone }),
        )
        AccidentIntent.SpeakShotGuide -> {
            val steps = planShots(state.damageZones)
            Reduction(state, if (steps.isEmpty()) emptyList() else listOf(AccidentSideEffect.Speak(shotSummary(steps))))
        }
        AccidentIntent.RepeatLocation -> Reduction(state, listOf(AccidentSideEffect.ReadLocation))
        AccidentIntent.Call119 -> Reduction(state, listOf(AccidentSideEffect.Ui(AccidentUiEffect.OpenDialer("119"))))
        // 등록한 보험사가 없으면 버튼이 꺼져 있다(음성도 같게 무시).
        AccidentIntent.CallInsurer -> state.insurer?.takeIf { state.step == AccidentState.ContactInsurer }?.let { insurer ->
            Reduction(
                state.copy(insurerDialed = true, report = state.report?.copy(insurerDialed = true)),
                listOf(AccidentSideEffect.Ui(AccidentUiEffect.OpenDialer(insurer.dialNumber))),
            )
        } ?: Reduction(state)
        is AccidentIntent.SaveInsurer -> Reduction(state, listOf(AccidentSideEffect.SaveInsurer(intent.insurer)))
        is AccidentIntent.InsurerChanged -> Reduction(state.copy(insurer = intent.insurer))
        AccidentIntent.ClearReports -> Reduction(state, listOf(AccidentSideEffect.ClearReports))
        is AccidentIntent.ReportsCounted -> Reduction(state.copy(savedReports = intent.count))
        // C0은 화면이 보일 때만 듣는다. 다른 단계는 화면과 상관없이 계속 듣는다(사고 처리 중).
        is AccidentIntent.AppVisibilityChanged -> {
            val next = state.copy(appVisible = intent.visible)
            val effects = when {
                state.step != AccidentState.Idle || state.appVisible == intent.visible -> emptyList()
                intent.visible -> listOf(AccidentSideEffect.Listen)
                else -> listOf(AccidentSideEffect.CancelVoice)
            }
            Reduction(next, effects)
        }
        is AccidentIntent.Started -> Reduction(state.copy(startedAtMillis = intent.atMillis))
        // C8을 떠난 뒤 늦게 온 결과는 버린다(C9에서는 받아 둔다: 뒤로 왔다 다시 넘어간 경우).
        is AccidentIntent.ReportReady ->
            if (state.step != AccidentState.ContactInsurer && state.step != AccidentState.Done) Reduction(state)
            else {
                // 기다리는 동안 보험사 전화를 걸었을 수 있다.
                val report = intent.report.copy(insurerDialed = state.insurerDialed)
                Reduction(
                    state.copy(report = report, summary = intent.summary, summarizing = !intent.final),
                    listOf(AccidentSideEffect.ArchiveReport(report, intent.summary)),
                )
            }
        is AccidentIntent.SetSimulation -> Reduction(state, listOf(AccidentSideEffect.SetSimulation(intent.enabled)))
        is AccidentIntent.PlayScenario -> Reduction(state, listOf(AccidentSideEffect.PlayScenario(intent.scenario)))

        is AccidentIntent.TimerTick -> Reduction(state.copy(secondsLeft = intent.secondsLeft))
        is AccidentIntent.VehicleChanged -> {
            val next = state.copy(vehicle = intent.vehicle)
            val effects = when {
                // C4~C9 중에 P단에서 벗어나면 한 번 알린다(듣는 말은 119만).
                next.needsPark && !state.needsPark -> listOf(AccidentSideEffect.Speak(VoicePrompts.PARK_FIRST))
                // P단이 되면 그 단계의 버튼 이름으로 다시 듣는다.
                !next.needsPark && state.needsPark -> listOf(AccidentSideEffect.CancelVoice, AccidentSideEffect.Listen)
                else -> emptyList()
            }
            Reduction(next, effects)
        }
        is AccidentIntent.VoiceChanged -> Reduction(state.copy(voice = intent.voice))
        is AccidentIntent.LocationResolved -> Reduction(state.copy(location = intent.location))
        is AccidentIntent.SimulationChanged -> Reduction(state.copy(simulation = intent.enabled, playing = intent.playing))

        is AccidentIntent.DebugForceStep -> moveTo(state, intent.step)
    }

    private fun step(state: AccidentUiState, event: AccidentEvent): Reduction {
        val transition = machine.reduce(state.step, event)
        val moved = moveTo(state, transition.state)
        val extra = when (val effect = transition.effect) {
            is AccidentEffect.LogFalsePositive -> listOf(AccidentSideEffect.LogFalsePositive(effect.score))
            null -> emptyList()
        }
        return moved.copy(effects = moved.effects + extra)
    }

    /**
     * 단계가 바뀌면 이전 단계의 음성·타이머를 멈추고 새 단계의 일을 시작한다.
     * 같은 단계 안의 갱신(C1 점수 상승)은 타이머를 다시 시작하지 않는다.
     */
    private fun moveTo(state: AccidentUiState, to: AccidentState): Reduction {
        val from = state.step
        if (to == from) return Reduction(state)
        var next = state.copy(step = to)
        if (from::class == to::class) return Reduction(next)

        next = next.copy(secondsLeft = null)
        // 처음으로 돌아오면 이번 사고 기록을 비운다.
        if (to == AccidentState.Idle) next = AccidentUiState(
            vehicle = state.vehicle, voice = state.voice, assessment = state.assessment, insurer = state.insurer, appVisible = state.appVisible,
            simulation = state.simulation, scenarios = state.scenarios, playing = state.playing,
            savedReports = state.savedReports,
        )
        if (to == AccidentState.Emergency) next = next.copy(emergencyGuided = true)
        if (from == AccidentState.SecureScene && to == AccidentState.VehicleCheck) next = next.copy(secureSceneDone = true)
        // C7에서 부위를 바꾸고 다시 올 수 있으니 C8에 들어올 때마다 새로 만든다.
        if (to == AccidentState.ContactInsurer) next = next.copy(report = null, summary = null, summarizing = false)
        // C8을 떠나면 늦게 오는 LLM 요약은 버린다(ReportReady 참고).
        if (from == AccidentState.ContactInsurer) next = next.copy(summarizing = false)

        val effects = mutableListOf<AccidentSideEffect>(AccidentSideEffect.CancelVoice, AccidentSideEffect.CancelTimer)
        if (from == AccidentState.Idle) effects += AccidentSideEffect.StampStart
        if (to == AccidentState.ContactInsurer) effects += AccidentSideEffect.PrepareReport
        // C9: 보험사 연락 여부까지 더해 다시 보관한다.
        if (to == AccidentState.Done) {
            val report = next.report; val summary = next.summary
            if (report != null && summary != null) effects += AccidentSideEffect.ArchiveReport(report.copy(insurerDialed = next.insurerDialed), summary)
        }
        val timeout = machine.noResponseTimeoutMillis(to)
        timeout?.let { effects += AccidentSideEffect.StartNoResponseTimer(it) }
        // 주행 중에 C4~C9로 오면(예: C3 통화 끝) 그 단계 안내 대신 차를 세우라고 알린다.
        effects += if (next.needsPark) listOf(AccidentSideEffect.Speak(VoicePrompts.PARK_FIRST)) else when (to) {
            is AccidentState.ConfirmAccident -> listOf(AccidentSideEffect.Speak(VoicePrompts.CONFIRM_QUESTION))
            is AccidentState.AskInjury -> listOf(AccidentSideEffect.Speak(VoicePrompts.INJURY_QUESTION))
            AccidentState.Emergency -> listOf(AccidentSideEffect.EmergencyGuide)
            AccidentState.DamageMap -> listOfNotNull(damageSummary(next.damageZones)?.let(AccidentSideEffect::Speak))
            else -> emptyList()
        }
        // 안내를 다 읽은 뒤 이 단계의 버튼 이름을 듣는다(C0은 듣지 않는다).
        if (next.voiceCommands().isNotEmpty()) effects += AccidentSideEffect.Listen
        return Reduction(next, effects)
    }

    private fun ImpactAssessment.toEvent() = AccidentEvent.ImpactDetected(score, airbagDeployed)
}

/** "차를 세우세요" 화면에서 막는 입력(화면에 버튼이 없다). 119와 시스템 입력은 막지 않는다. */
private val PARK_BLOCKED: Set<AccidentIntent> = setOf(
    AccidentIntent.Next, AccidentIntent.Back, AccidentIntent.Finish, AccidentIntent.CallInsurer, AccidentIntent.SpeakShotGuide,
)
