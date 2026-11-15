package com.whitemason.okaynow.app.domain.detection

import com.whitemason.okaynow.app.domain.vehicle.Gear
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning

/** 시각이 붙은 차량 신호 하나. [timestampMillis]는 단조 증가 시각. */
sealed interface SignalEvent {
    val timestampMillis: Long

    data class Speed(val metersPerSecond: Float, override val timestampMillis: Long) : SignalEvent
    data class Brake(val pressed: Boolean, override val timestampMillis: Long) : SignalEvent
    data class Hazard(val on: Boolean, override val timestampMillis: Long) : SignalEvent
    data class GearChanged(val gear: Gear, override val timestampMillis: Long) : SignalEvent
    data class Door(val open: Boolean, override val timestampMillis: Long) : SignalEvent
    data class Safety(val warnings: Set<SafetyWarning>, override val timestampMillis: Long) : SignalEvent
}

enum class ImpactReason {
    SEVERE_DECELERATION,
    DECELERATION_WITHOUT_BRAKE,
    HAZARD_AFTER_STOP,
    PARK_AFTER_STOP,
    DOOR_AFTER_STOP,
    SAFETY_WARNING,
    AIRBAG_DEPLOYED,
}

/**
 * 진행 중인 에피소드(급정차 또는 에어백 전개 1건)의 평가. 감속 중에 브레이크가 들어와
 * "브레이크 미입력" 점수가 빠지는 경우를 빼면 점수는 에피소드 안에서 줄지 않는다.
 * @property episodeId 같은 사고를 한 번만 트리거하도록 호출 측에서 중복을 거르는 데 쓴다.
 */
data class ImpactAssessment(
    val episodeId: Long,
    val startedAtMillis: Long,
    val score: Int,
    val reasons: Set<ImpactReason>,
    val maxDecelerationG: Float,
) {
    val airbagDeployed: Boolean get() = ImpactReason.AIRBAG_DEPLOYED in reasons
}

/**
 * 다중 신호로 충격을 추정한다(PLAN.md 4절). SDK에 의존하지 않는 순수 Kotlin이며 스레드 안전하지 않다.
 *
 * 1. 감속도가 [ImpactConfig.hardStopDecelerationG] 이상이면 급정차 에피소드를 연다.
 * 2. 에피소드 안에서 감속도·브레이크·비상등·P단·도어·안전 경고 점수를 더한다.
 * 3. 시간창이 지나거나, 정차 후 다시 주행하면 에피소드를 닫는다.
 * 에어백 전개는 급정차 없이도 에피소드를 열고 즉시 [ImpactConfig.airbagDeployedScore]를 준다.
 */
class ImpactScorer(private val config: ImpactConfig = ImpactConfig()) {

    private class Episode(val id: Long, val startedAt: Long) {
        val reasons = mutableSetOf<ImpactReason>()
        var maxDecelerationG = 0f
        var brakeDuringDeceleration = false
        var stopped = false
    }

    private val speeds = ArrayDeque<SignalEvent.Speed>()
    private var brakePressed = false
    private var hazardOn = false
    private var gear = Gear.UNKNOWN
    private var doorOpen = false
    private var lastSafetyWarningAt: Long? = null
    private var episode: Episode? = null
    private var nextEpisodeId = 1L

    /** 신호를 반영하고, 진행 중인 에피소드가 있으면 그 평가를 돌려준다. */
    fun onEvent(event: SignalEvent): ImpactAssessment? {
        expireEpisode(event.timestampMillis)
        when (event) {
            is SignalEvent.Speed -> onSpeed(event)
            is SignalEvent.Brake -> {
                brakePressed = event.pressed
                episode?.takeIf { event.pressed && !it.stopped }?.brakeDuringDeceleration = true
            }
            is SignalEvent.Hazard -> hazardOn = event.on
            is SignalEvent.GearChanged -> gear = event.gear
            is SignalEvent.Door -> doorOpen = event.open
            is SignalEvent.Safety -> onSafety(event)
        }
        episode?.let { scoreAftermath(it, event.timestampMillis) }
        return episode?.toAssessment()
    }

    private fun onSpeed(sample: SignalEvent.Speed) {
        // 늦게 도착한 과거 샘플은 버린다(시간이 거꾸로 가면 Δt가 음수가 된다).
        if (speeds.isNotEmpty() && sample.timestampMillis < speeds.last().timestampMillis) return
        val deceleration = decelerationG(sample)
        speeds.addLast(sample)
        while (speeds.first().timestampMillis < sample.timestampMillis - config.maxSampleGapMillis) speeds.removeFirst()

        val current = episode
        if (current == null) {
            if (deceleration >= config.hardStopDecelerationG) {
                episode = Episode(nextEpisodeId++, sample.timestampMillis).also { trackDeceleration(it, deceleration) }
            }
            return
        }
        if (!current.stopped) {
            if (deceleration >= config.hardStopDecelerationG) trackDeceleration(current, deceleration)
            if (sample.metersPerSecond <= config.stoppedSpeedMps) current.stopped = true
        } else if (sample.metersPerSecond > config.resumeDrivingSpeedMps) {
            episode = null // 다시 주행 → 단순 급정차로 본다
        }
    }

    private fun trackDeceleration(episode: Episode, deceleration: Float) {
        episode.maxDecelerationG = maxOf(episode.maxDecelerationG, deceleration)
        if (brakePressed) episode.brakeDuringDeceleration = true
        if (episode.maxDecelerationG >= config.impactDecelerationG) episode.reasons += ImpactReason.SEVERE_DECELERATION
    }

    private fun onSafety(event: SignalEvent.Safety) {
        if (event.warnings.isNotEmpty()) lastSafetyWarningAt = event.timestampMillis
        if (SafetyWarning.AIRBAG_DEPLOYED in event.warnings) {
            val current = episode ?: Episode(nextEpisodeId++, event.timestampMillis).also { episode = it }
            current.reasons += ImpactReason.AIRBAG_DEPLOYED
        }
    }

    private fun scoreAftermath(episode: Episode, now: Long) {
        val elapsed = now - episode.startedAt
        if (episode.maxDecelerationG >= config.hardStopDecelerationG && !episode.brakeDuringDeceleration) {
            episode.reasons += ImpactReason.DECELERATION_WITHOUT_BRAKE
        } else {
            episode.reasons -= ImpactReason.DECELERATION_WITHOUT_BRAKE
        }
        if (hazardOn && elapsed <= config.hazardWindowMillis) episode.reasons += ImpactReason.HAZARD_AFTER_STOP
        if (episode.stopped && gear == Gear.PARK && elapsed <= config.aftermathWindowMillis) {
            episode.reasons += ImpactReason.PARK_AFTER_STOP
        }
        if (episode.stopped && doorOpen && elapsed <= config.aftermathWindowMillis) {
            episode.reasons += ImpactReason.DOOR_AFTER_STOP
        }
        val warningAt = lastSafetyWarningAt
        if (warningAt != null && warningAt >= episode.startedAt - config.safetyWarningLeadMillis) {
            episode.reasons += ImpactReason.SAFETY_WARNING
        }
    }

    private fun expireEpisode(now: Long) {
        val current = episode ?: return
        val window = maxOf(config.hazardWindowMillis, config.aftermathWindowMillis)
        if (now - current.startedAt > window) episode = null
    }

    /**
     * Δv/Δt 감속도(g). 샘플 간격이 불규칙해도 되도록, 구간 안 가장 오래된 샘플과 비교하고
     * 구간 안에 다른 샘플이 없으면 직전 샘플과 비교한다. 직전 샘플이 [ImpactConfig.maxSampleGapMillis]보다
     * 오래됐거나 같은 시각이면 계산하지 않는다(0).
     */
    private fun decelerationG(sample: SignalEvent.Speed): Float {
        val windowStart = sample.timestampMillis - config.decelerationWindowMillis
        val reference = speeds.firstOrNull { it.timestampMillis >= windowStart && it.timestampMillis < sample.timestampMillis }
            ?: speeds.lastOrNull()
            ?: return 0f
        val dtMillis = sample.timestampMillis - reference.timestampMillis
        if (dtMillis <= 0 || dtMillis > config.maxSampleGapMillis) return 0f
        val dtSeconds = dtMillis / 1000f
        return (reference.metersPerSecond - sample.metersPerSecond) / dtSeconds / STANDARD_GRAVITY
    }

    private fun Episode.toAssessment() = ImpactAssessment(
        episodeId = id,
        startedAtMillis = startedAt,
        score = reasons.sumOf { config.scoreOf(it) },
        reasons = reasons.toSet(),
        maxDecelerationG = maxDecelerationG,
    )

    private companion object {
        const val STANDARD_GRAVITY = 9.80665f

        fun ImpactConfig.scoreOf(reason: ImpactReason) = when (reason) {
            ImpactReason.SEVERE_DECELERATION -> severeDecelerationScore
            ImpactReason.DECELERATION_WITHOUT_BRAKE -> decelerationWithoutBrakeScore
            ImpactReason.HAZARD_AFTER_STOP -> hazardAfterStopScore
            ImpactReason.PARK_AFTER_STOP -> parkAfterStopScore
            ImpactReason.DOOR_AFTER_STOP -> doorAfterStopScore
            ImpactReason.SAFETY_WARNING -> safetyWarningScore
            ImpactReason.AIRBAG_DEPLOYED -> airbagDeployedScore
        }
    }
}
