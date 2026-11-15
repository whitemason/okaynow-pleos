package com.whitemason.okaynow.app.data.vehicle

import com.whitemason.okaynow.app.domain.vehicle.ExteriorLight
import com.whitemason.okaynow.app.domain.vehicle.Gear
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning
import com.whitemason.okaynow.app.domain.vehicle.TireStatus

/**
 * 시연·테스트용 차량 상태 시나리오. 수치는 PLAN.md 4절 트리거 표(감속도 1.2g, 30초 내 비상등 등)를 기준으로 잡았다.
 *
 * - [NORMAL_DRIVING]: 가속·완만한 제동(≈0.15g)만 있는 일상 주행. 어떤 트리거도 걸리면 안 된다.
 * - [HARD_BRAKE_FALSE_ALARM]: 80km/h에서 브레이크로 급정지(≈0.95g) 후 다시 출발. 오탐 검증용.
 * - [COLLISION]: 60km/h에서 브레이크 없이 0.7초 만에 정지(≈2.4g) + 마스터 경고·TPMS 경고, 이후 비상등 → P단 → 도어.
 *   에어백 전개는 넣지 않는다(에뮬레이터 미지원, C1 확인 요청 경로를 시연하기 위해). 에어백 경로는 단위 테스트로 검증.
 * - [LOW_SPEED_CONTACT]: 15km/h 접촉(≈0.7g). 자동 감지 점수는 임계값 아래로 남고, 사후 패턴(비상등·P단·도어)만 나온다.
 */
enum class FakeScenario(val label: String, internal val script: ScenarioScript) {
    NORMAL_DRIVING(
        "정상 주행",
        ScenarioScript(
            durationMillis = 60_000,
            speedPoints = listOf(0L to 0f, 10_000L to 16.7f, 20_000L to 16.7f, 24_000L to 11.1f, 30_000L to 16.7f, 50_000L to 16.7f, 58_000L to 0f),
            events = listOf(
                0L to { s -> s.copy(gear = Gear.DRIVE, exteriorLights = DRIVING_LIGHTS) },
                20_000L to { s -> s.copy(brakePedal = true) },
                24_000L to { s -> s.copy(brakePedal = false) },
                50_000L to { s -> s.copy(brakePedal = true) },
            ),
        ),
    ),
    HARD_BRAKE_FALSE_ALARM(
        "급정차 오탐",
        ScenarioScript(
            durationMillis = 30_000,
            speedPoints = listOf(0L to 22.2f, 5_000L to 22.2f, 7_400L to 0f, 10_000L to 0f, 20_000L to 13.9f, 30_000L to 13.9f),
            events = listOf(
                0L to { s -> s.copy(gear = Gear.DRIVE, exteriorLights = DRIVING_LIGHTS) },
                4_800L to { s -> s.copy(brakePedal = true) },
                10_000L to { s -> s.copy(brakePedal = false) },
            ),
        ),
    ),
    COLLISION(
        "충돌",
        ScenarioScript(
            durationMillis = 60_000,
            speedPoints = listOf(0L to 16.7f, 5_000L to 16.7f, 5_700L to 0f, 60_000L to 0f),
            events = listOf(
                0L to { s -> s.copy(gear = Gear.DRIVE, exteriorLights = DRIVING_LIGHTS) },
                5_000L to { s ->
                    s.copy(safetyWarnings = setOf(SafetyWarning.MASTER_WARNING))
                },
                6_000L to { s -> s.copy(brakePedal = true, tireStatus = TireStatus.LOW_PRESSURE_WARNING) },
                9_000L to { s -> s.copy(hazardLight = true) },
                15_000L to { s -> s.copy(gear = Gear.PARK, brakePedal = false) },
                25_000L to { s -> s.copy(doorOpen = true) },
            ),
        ),
    ),
    LOW_SPEED_CONTACT(
        "저속 접촉",
        ScenarioScript(
            durationMillis = 60_000,
            speedPoints = listOf(0L to 4.2f, 5_000L to 4.2f, 5_600L to 0f, 60_000L to 0f),
            events = listOf(
                0L to { s -> s.copy(gear = Gear.DRIVE, exteriorLights = DRIVING_LIGHTS) },
                5_300L to { s -> s.copy(brakePedal = true) },
                12_000L to { s -> s.copy(hazardLight = true) },
                20_000L to { s -> s.copy(gear = Gear.PARK, brakePedal = false) },
                30_000L to { s -> s.copy(doorOpen = true) },
            ),
        ),
    ),
}

private val DRIVING_LIGHTS = setOf(ExteriorLight.LOW_BEAM, ExteriorLight.TAIL_LAMP)

/** 한 시점의 차량 상태. */
data class VehicleSnapshot(
    val speedMps: Float = 0f,
    val brakePedal: Boolean = false,
    val hazardLight: Boolean = false,
    val gear: Gear = Gear.PARK,
    val doorOpen: Boolean = false,
    val safetyWarnings: Set<SafetyWarning> = emptySet(),
    val tireStatus: TireStatus = TireStatus.NORMAL,
    val exteriorLights: Set<ExteriorLight> = emptySet(),
) {
    companion object {
        /** 시나리오 밖의 대기 상태: 정차, P단, 경고 없음. */
        val PARKED = VehicleSnapshot()
    }
}

/**
 * @property speedPoints (시각 ms, 속도 m/s). 사이 값은 선형 보간한다.
 * @property events (시각 ms, 상태 변경). 시각 순으로 누적 적용한다.
 */
internal class ScenarioScript(
    val durationMillis: Long,
    private val speedPoints: List<Pair<Long, Float>>,
    private val events: List<Pair<Long, (VehicleSnapshot) -> VehicleSnapshot>>,
) {
    fun snapshotAt(timeMillis: Long): VehicleSnapshot {
        val state = events
            .filter { (at, _) -> at <= timeMillis }
            .fold(VehicleSnapshot.PARKED) { acc, (_, apply) -> apply(acc) }
        return state.copy(speedMps = speedAt(timeMillis))
    }

    private fun speedAt(t: Long): Float {
        val next = speedPoints.indexOfFirst { (at, _) -> at >= t }
        if (next == -1) return speedPoints.last().second
        if (next == 0) return speedPoints.first().second
        val (t0, v0) = speedPoints[next - 1]
        val (t1, v1) = speedPoints[next]
        return v0 + (v1 - v0) * (t - t0) / (t1 - t0)
    }
}
