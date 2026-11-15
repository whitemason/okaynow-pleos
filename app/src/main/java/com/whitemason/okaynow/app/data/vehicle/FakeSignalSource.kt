package com.whitemason.okaynow.app.data.vehicle

import android.os.SystemClock
import com.whitemason.okaynow.app.domain.vehicle.ExteriorLight
import com.whitemason.okaynow.app.domain.vehicle.Gear
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning
import com.whitemason.okaynow.app.domain.vehicle.SignalType
import com.whitemason.okaynow.app.domain.vehicle.SpeedSample
import com.whitemason.okaynow.app.domain.vehicle.TireStatus
import com.whitemason.okaynow.app.domain.vehicle.VehicleSignalSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [FakeScenario]를 [TICK_MILLIS] 간격으로 재생하는 시뮬레이션 신호원. 시연 영상과 단위 테스트에 쓴다.
 * 재생하지 않을 때는 [VehicleSnapshot.PARKED] 상태를 유지한다.
 */
@Singleton
class FakeSignalSource(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) : VehicleSignalSource {

    /** 실제 신호원과 같은 시계(elapsedRealtime)를 써서 다른 신호의 수신 시각과 맞춘다. */
    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default), { SystemClock.elapsedRealtime() })

    private data class Frame(val snapshot: VehicleSnapshot, val timestampMillis: Long)

    private val frame = MutableStateFlow(Frame(VehicleSnapshot.PARKED, nowMillis()))
    private val _playing = MutableStateFlow<FakeScenario?>(null)
    private var playback: Job? = null

    /** 재생 중인 시나리오. 끝나거나 [stop]하면 null. */
    val playing: StateFlow<FakeScenario?> = _playing.asStateFlow()

    override val supportedSignals: StateFlow<Set<SignalType>> = MutableStateFlow(SignalType.entries.toSet())

    override val speed: Flow<SpeedSample> = frame.map { SpeedSample(it.snapshot.speedMps, it.timestampMillis) }
    override val brakePedal: Flow<Boolean> = field { it.brakePedal }
    override val hazardLight: Flow<Boolean> = field { it.hazardLight }
    override val gear: Flow<Gear> = field { it.gear }
    override val doorOpen: Flow<Boolean> = field { it.doorOpen }
    override val safetyWarnings: Flow<Set<SafetyWarning>> = field { it.safetyWarnings }
    override val tireStatus: Flow<TireStatus> = field { it.tireStatus }
    override val exteriorLights: Flow<Set<ExteriorLight>> = field { it.exteriorLights }

    /** 처음부터 재생한다. 재생 중이던 시나리오는 중단된다. 끝나면 마지막 상태를 유지한다. */
    fun play(scenario: FakeScenario) {
        playback?.cancel()
        _playing.value = scenario
        playback = scope.launch {
            val script = scenario.script
            val start = nowMillis()
            var t = 0L
            while (t <= script.durationMillis) {
                frame.value = Frame(script.snapshotAt(t), start + t)
                delay(TICK_MILLIS)
                t += TICK_MILLIS
            }
            _playing.value = null
        }
    }

    /** 재생을 멈추고 대기 상태(정차·P단)로 되돌린다. */
    fun stop() {
        playback?.cancel()
        _playing.value = null
        frame.value = Frame(VehicleSnapshot.PARKED, nowMillis())
    }

    private fun <T> field(select: (VehicleSnapshot) -> T): Flow<T> =
        frame.map { select(it.snapshot) }.distinctUntilChanged()

    companion object {
        const val TICK_MILLIS = 100L
    }
}
