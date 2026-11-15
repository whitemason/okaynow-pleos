package com.whitemason.okaynow.app.presentation.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitemason.okaynow.app.domain.vehicle.DemoScenario
import com.whitemason.okaynow.app.domain.vehicle.SignalSourceRepository
import com.whitemason.okaynow.app.domain.vehicle.VehicleStatus
import com.whitemason.okaynow.app.domain.vehicle.status
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** 개발용: 선택된 신호원의 값을 그대로 보여 준다(MVI 흐름 밖). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SignalMonitorViewModel @Inject constructor(
    private val signals: SignalSourceRepository,
) : ViewModel() {

    val simulation: StateFlow<Boolean> = signals.simulation
    val scenarios: List<DemoScenario> = signals.scenarios
    val playing: StateFlow<DemoScenario?> = signals.playing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val status: StateFlow<VehicleStatus> = signals.active
        .flatMapLatest { it.status() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleStatus())

    fun setSimulation(enabled: Boolean) = signals.setSimulation(enabled)

    fun play(scenario: DemoScenario) = signals.play(scenario)
}
