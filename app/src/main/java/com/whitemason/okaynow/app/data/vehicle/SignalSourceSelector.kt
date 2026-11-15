package com.whitemason.okaynow.app.data.vehicle

import com.whitemason.okaynow.app.domain.vehicle.DemoScenario
import com.whitemason.okaynow.app.domain.vehicle.SignalSourceRepository
import com.whitemason.okaynow.app.domain.vehicle.VehicleSignalSource
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * 시뮬레이션 모드에 따라 실제 SDK 신호원과 [FakeSignalSource]를 바꿔 준다.
 * TODO: 시뮬레이션 모드 값은 C0 사전 설정(DataStore)으로 옮긴다.
 */
@Singleton
class SignalSourceSelector @Inject constructor(
    private val pleos: Provider<PleosVehicleSignalSource>,
    private val fake: FakeSignalSource,
) : SignalSourceRepository {
    private val _simulation = MutableStateFlow(false)
    override val simulation: StateFlow<Boolean> = _simulation.asStateFlow()

    override val active: Flow<VehicleSignalSource> = simulation.map { sim -> if (sim) fake else pleos.get() }

    override val scenarios: List<DemoScenario> = FakeScenario.entries.map { it.toDemo() }

    override val playing: Flow<DemoScenario?> = fake.playing.map { it?.toDemo() }

    override fun setSimulation(enabled: Boolean) {
        if (!enabled) fake.stop()
        _simulation.value = enabled
    }

    override fun play(scenario: DemoScenario) = fake.play(FakeScenario.valueOf(scenario.id))

    private fun FakeScenario.toDemo() = DemoScenario(name, label)
}
