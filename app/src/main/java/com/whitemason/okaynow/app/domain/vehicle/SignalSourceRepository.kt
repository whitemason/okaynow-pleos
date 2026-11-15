package com.whitemason.okaynow.app.domain.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 시연 시나리오. 재생 데이터는 data 계층이 가진다. */
data class DemoScenario(val id: String, val label: String)

/**
 * 지금 쓰는 차량 신호원. 시뮬레이션 모드면 시연 신호원, 아니면 실차(Pleos SDK) 신호원.
 * 구현은 data 계층(SignalSourceSelector).
 */
interface SignalSourceRepository {
    /** 바뀔 때마다 새 신호원. 구독 측은 이전 신호원 감시를 취소한다. */
    val active: Flow<VehicleSignalSource>
    val simulation: StateFlow<Boolean>
    val scenarios: List<DemoScenario>
    /** 재생 중인 시나리오. 없으면 null. */
    val playing: Flow<DemoScenario?>

    fun setSimulation(enabled: Boolean)
    fun play(scenario: DemoScenario)
}
