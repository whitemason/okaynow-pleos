package com.whitemason.okaynow.app.domain.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 충격 추정·차량 점검에 쓰는 차량 신호. SDK 타입을 노출하지 않는 순수 Kotlin 경계다.
 *
 * 미지원 신호의 Flow는 아무 값도 내보내지 않는다. 어떤 신호가 실제로 들어오는지는
 * [supportedSignals]로 확인하고, 미지원 기능은 UI에서 숨긴다(CLAUDE.md 규칙 3).
 */
interface VehicleSignalSource {

    /** 지원이 확인된 신호. capability 확인이 끝나기 전에는 비어 있을 수 있다. */
    val supportedSignals: StateFlow<Set<SignalType>>

    /** 차량 속도(m/s). */
    val speed: Flow<SpeedSample>

    /** 브레이크 페달을 밟고 있으면 true. */
    val brakePedal: Flow<Boolean>

    /** 비상등이 켜져 있으면 true. */
    val hazardLight: Flow<Boolean>

    val gear: Flow<Gear>

    /** 하나라도 열린 도어가 있으면 true. */
    val doorOpen: Flow<Boolean>

    /** 현재 켜져 있는 안전 경고 전체. 경고가 없으면 빈 집합. */
    val safetyWarnings: Flow<Set<SafetyWarning>>

    val tireStatus: Flow<TireStatus>

    /** 켜져 있는 외부 등화(비상등 제외). 모두 꺼져 있으면 빈 집합. */
    val exteriorLights: Flow<Set<ExteriorLight>>
}

enum class SignalType { SPEED, BRAKE_PEDAL, HAZARD_LIGHT, GEAR, DOOR_OPEN, SAFETY_WARNINGS, TIRE_STATUS, EXTERIOR_LIGHTS }

/** 감속도 계산을 위해 측정 시각을 함께 둔다. [timestampMillis]는 단조 증가 시각. */
data class SpeedSample(val metersPerSecond: Float, val timestampMillis: Long)

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class SafetyWarning {
    AIRBAG_DEPLOYED,
    /** 엔진 이상(파워다운) 경고 */
    POWER_DOWN,
    /** 에어백 시스템 경고등 */
    AIRBAG_TELLTALE,
    ABS,
    ESC,
    MASTER_WARNING,
}

enum class TireStatus { NORMAL, LOW_PRESSURE_WARNING }

enum class ExteriorLight { LOW_BEAM, HIGH_BEAM, TAIL_LAMP, FRONT_FOG, REAR_FOG }
