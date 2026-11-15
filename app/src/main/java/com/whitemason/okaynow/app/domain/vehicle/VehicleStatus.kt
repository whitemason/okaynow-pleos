package com.whitemason.okaynow.app.domain.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** 화면 표시용 최신 차량 상태. null = 아직 값을 받지 못함(미지원이면 계속 null). */
data class VehicleStatus(
    val supported: Set<SignalType> = emptySet(),
    val speedKmh: Float? = null,
    val brakePedal: Boolean? = null,
    val hazardLight: Boolean? = null,
    val gear: Gear? = null,
    val doorOpen: Boolean? = null,
    val safetyWarnings: Set<SafetyWarning>? = null,
    val tireStatus: TireStatus? = null,
    val exteriorLights: Set<ExteriorLight>? = null,
) {
    /**
     * 목록·입력 화면을 열어도 되는지. 기어를 모르면 막지 않는다.
     * TODO(verify-sdk): 실차에서는 CarUxRestrictionsManager 기준으로 바꾼다(Pleos 주행 규제 개발 가이드).
     */
    val isParked: Boolean get() = gear == null || gear == Gear.PARK
}

fun VehicleSignalSource.status(): Flow<VehicleStatus> = combine(
    listOf(
        supportedSignals,
        speed.map { it.metersPerSecond * MPS_TO_KMH }.orNull(),
        brakePedal.orNull(),
        hazardLight.orNull(),
        gear.orNull(),
        doorOpen.orNull(),
        safetyWarnings.orNull(),
        tireStatus.orNull(),
        exteriorLights.orNull(),
    ),
) { v ->
    @Suppress("UNCHECKED_CAST")
    VehicleStatus(
        supported = v[0] as Set<SignalType>,
        speedKmh = v[1] as Float?,
        brakePedal = v[2] as Boolean?,
        hazardLight = v[3] as Boolean?,
        gear = v[4] as Gear?,
        doorOpen = v[5] as Boolean?,
        safetyWarnings = v[6] as Set<SafetyWarning>?,
        tireStatus = v[7] as TireStatus?,
        exteriorLights = v[8] as Set<ExteriorLight>?,
    )
}

private fun <T> Flow<T>.orNull(): Flow<T?> = map<T, T?> { it }.onStart { emit(null) }

private const val MPS_TO_KMH = 3.6f
