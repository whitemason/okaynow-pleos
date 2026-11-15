package com.whitemason.okaynow.app.domain.detection

import com.whitemason.okaynow.app.domain.vehicle.VehicleSignalSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * 신호원의 Flow들을 시각이 붙은 [SignalEvent] 하나의 스트림으로 합친다.
 * 속도는 샘플 시각을, 나머지 신호는 [nowMillis]로 잰 수신 시각을 쓴다. 두 시계는 같은 기준이어야 한다.
 */
fun VehicleSignalSource.signalEvents(nowMillis: () -> Long): Flow<SignalEvent> = merge(
    speed.map { SignalEvent.Speed(it.metersPerSecond, it.timestampMillis) },
    brakePedal.map { SignalEvent.Brake(it, nowMillis()) },
    hazardLight.map { SignalEvent.Hazard(it, nowMillis()) },
    gear.map { SignalEvent.GearChanged(it, nowMillis()) },
    doorOpen.map { SignalEvent.Door(it, nowMillis()) },
    safetyWarnings.map { SignalEvent.Safety(it, nowMillis()) },
)
