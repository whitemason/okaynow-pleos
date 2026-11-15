package com.whitemason.okaynow.app.domain.flow

import com.whitemason.okaynow.app.domain.detection.ImpactConfig
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.Back
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.Finish
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.ImpactDetected
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.ManualStart
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.Next
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.No
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.NoResponse
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.RequestEmergency
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.Yes
import com.whitemason.okaynow.app.domain.flow.AccidentState.AskInjury
import com.whitemason.okaynow.app.domain.flow.AccidentState.ConfirmAccident
import com.whitemason.okaynow.app.domain.flow.AccidentState.ContactInsurer
import com.whitemason.okaynow.app.domain.flow.AccidentState.DamageMap
import com.whitemason.okaynow.app.domain.flow.AccidentState.Done
import com.whitemason.okaynow.app.domain.flow.AccidentState.Emergency
import com.whitemason.okaynow.app.domain.flow.AccidentState.Idle
import com.whitemason.okaynow.app.domain.flow.AccidentState.PhotoCapture
import com.whitemason.okaynow.app.domain.flow.AccidentState.SecureScene
import com.whitemason.okaynow.app.domain.flow.AccidentState.VehicleCheck

/**
 * 사고 처리 흐름(PLAN.md 5절). 순수 함수 [reduce]로 전이를 정의한다.
 *
 * 안전 규칙(CLAUDE.md 4): 부상 확인이 먼저이고, 119(C3)는 보험사(C8)보다 항상 앞선다.
 * 무응답: C1 → 90점 이상이면 C3, 아니면 C2로 다시 묻는다.
 * C2 → 심각한 사고(에어백 전개·90점 이상)면 C3, 아니면 처음(C0)으로 돌아간다(대장 결정, 2026-11-05).
 * 버튼 수동 시작도 심각하지 않은 쪽으로 본다(운전자가 직접 눌렀으니 의식이 있다).
 * 무응답 판단 시간은 [noResponseTimeoutMillis]가 정하고, 시간이 지나면 호출 측이 [AccidentEvent.NoResponse]를 보낸다.
 */
class AccidentFlowStateMachine(
    private val config: ImpactConfig = ImpactConfig(),
    private val timeouts: NoResponseTimeouts = NoResponseTimeouts(),
) {

    /** 이 상태에서 답을 기다리는 시간. 질문 상태가 아니면 null. */
    fun noResponseTimeoutMillis(state: AccidentState): Long? = when (state) {
        is ConfirmAccident -> timeouts.confirmMillis
        is AskInjury -> timeouts.injuryMillis
        else -> null
    }

    fun reduce(state: AccidentState, event: AccidentEvent): Transition {
        if (event == Back) return Transition(state.previousStep() ?: state)
        if (event == RequestEmergency && state != Idle && state != Done) return Transition(Emergency)

        val next: AccidentState = when (state) {
            Idle -> when (event) {
                is ImpactDetected -> when {
                    event.airbagDeployed -> AskInjury(severe = true)
                    event.score >= config.confirmThreshold -> ConfirmAccident(event.score)
                    else -> Idle
                }
                ManualStart -> AskInjury()
                else -> Idle
            }
            is ConfirmAccident -> when (event) {
                Yes, ManualStart -> AskInjury(severe = state.score >= config.emergencyThreshold)
                No -> return Transition(Idle, AccidentEffect.LogFalsePositive(state.score))
                NoResponse -> if (state.score >= config.emergencyThreshold) Emergency else AskInjury()
                is ImpactDetected -> when {
                    event.airbagDeployed -> AskInjury(severe = true)
                    else -> ConfirmAccident(maxOf(state.score, event.score))
                }
                else -> state
            }
            is AskInjury -> when (event) {
                Yes -> Emergency
                NoResponse -> if (state.severe) Emergency else Idle
                No -> SecureScene
                // 묻는 중에 에어백이 터지면 무응답 시 119로 가게 바꾼다.
                is ImpactDetected -> if (event.airbagDeployed || event.score >= config.emergencyThreshold) AskInjury(severe = true) else state
                else -> state
            }
            Emergency -> if (event == Next) SecureScene else state
            // C4 "처음으로": 부상 확인을 마친 뒤라 흐름을 끝내도 안전 규칙을 어기지 않는다.
            SecureScene -> when (event) {
                Next -> VehicleCheck
                Finish -> Idle
                else -> state
            }
            VehicleCheck -> if (event == Next) PhotoCapture else state
            PhotoCapture -> if (event == Next) DamageMap else state
            DamageMap -> if (event == Next) ContactInsurer else state
            ContactInsurer -> if (event == Next) Done else state
            Done -> if (event == Finish) Idle else state
        }
        return Transition(next)
    }
}

/**
 * 뒤로가기로 돌아갈 단계. 없으면 null(툴바에 뒤로 버튼을 두지 않는다).
 * C1~C4는 null: 부상 확인·119 안내를 건너뛰거나 되돌리지 않는다.
 */
fun AccidentState.previousStep(): AccidentState? = when (this) {
    VehicleCheck -> SecureScene
    PhotoCapture -> VehicleCheck
    DamageMap -> PhotoCapture
    ContactInsurer -> DamageMap
    Done -> ContactInsurer
    else -> null
}

/** C1·C2 무응답 시간. */
data class NoResponseTimeouts(
    /** C1 카운트다운. */
    val confirmMillis: Long = 10_000,
    /** C2 무응답(디자인 프로토타입과 같은 12초). */
    val injuryMillis: Long = 12_000,
)
