package com.whitemason.okaynow.app.domain.detection

/**
 * 충격 추정 가중치·임계값·시간창. PLAN.md 4절 표를 그대로 옮겼고, 튜닝은 여기서만 한다.
 */
data class ImpactConfig(
    /** 이 감속도 이상이면 충격으로 본다(ABS 풀브레이킹 ≈ 1g). */
    val impactDecelerationG: Float = 1.2f,
    /** 이 감속도 이상이면 "급정차" 에피소드를 시작한다. 사후 신호(비상등·P단·도어)는 에피소드 안에서만 센다. */
    val hardStopDecelerationG: Float = 0.5f,
    /** 감속도를 계산하는 구간. 속도 샘플 간격이 이보다 길면 직전 샘플(최대 [maxSampleGapMillis])과 비교한다. */
    val decelerationWindowMillis: Long = 300,
    val maxSampleGapMillis: Long = 1_000,
    /** 이 속도 이하면 정차로 본다. */
    val stoppedSpeedMps: Float = 1.0f,
    /** 정차 후 이 속도를 넘으면 다시 주행한 것으로 보고 에피소드를 끝낸다. */
    val resumeDrivingSpeedMps: Float = 5.0f,

    val hazardWindowMillis: Long = 30_000,
    val aftermathWindowMillis: Long = 60_000,
    /** 에피소드 시작 직전에 들어온 안전 경고도 같은 사고로 본다. */
    val safetyWarningLeadMillis: Long = 2_000,

    val severeDecelerationScore: Int = 50,
    val decelerationWithoutBrakeScore: Int = 30,
    val hazardAfterStopScore: Int = 15,
    val parkAfterStopScore: Int = 15,
    val doorAfterStopScore: Int = 15,
    val safetyWarningScore: Int = 20,
    /** 에어백 전개는 단독으로 확정 수준(2026-11-04 결정: 즉시 100, C1 생략). */
    val airbagDeployedScore: Int = 100,

    /** 이 점수 이상이면 C1 음성 확인. */
    val confirmThreshold: Int = 60,
    /** C1 무응답일 때 이 점수 이상이면 C3 긴급 모드. */
    val emergencyThreshold: Int = 90,
)
