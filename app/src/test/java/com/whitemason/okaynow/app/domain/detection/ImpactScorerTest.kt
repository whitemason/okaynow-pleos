package com.whitemason.okaynow.app.domain.detection

import com.whitemason.okaynow.app.data.vehicle.FakeScenario
import com.whitemason.okaynow.app.data.vehicle.VehicleSnapshot
import com.whitemason.okaynow.app.domain.vehicle.Gear
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpactScorerTest {

    private val config = ImpactConfig()

    /** 시나리오를 100ms 간격으로 재생해 모든 평가를 모은다. 속도는 매 틱, 나머지는 바뀔 때만 보낸다. */
    private fun run(scenario: FakeScenario, scorer: ImpactScorer = ImpactScorer(config)): List<ImpactAssessment?> {
        val results = mutableListOf<ImpactAssessment?>()
        var previous: VehicleSnapshot? = null
        var t = 0L
        while (t <= scenario.script.durationMillis) {
            val s = scenario.script.snapshotAt(t)
            val p = previous
            if (p == null || s.brakePedal != p.brakePedal) results += scorer.onEvent(SignalEvent.Brake(s.brakePedal, t))
            if (p == null || s.hazardLight != p.hazardLight) results += scorer.onEvent(SignalEvent.Hazard(s.hazardLight, t))
            if (p == null || s.gear != p.gear) results += scorer.onEvent(SignalEvent.GearChanged(s.gear, t))
            if (p == null || s.doorOpen != p.doorOpen) results += scorer.onEvent(SignalEvent.Door(s.doorOpen, t))
            if (p == null || s.safetyWarnings != p.safetyWarnings) {
                results += scorer.onEvent(SignalEvent.Safety(s.safetyWarnings, t))
            }
            results += scorer.onEvent(SignalEvent.Speed(s.speedMps, t))
            previous = s
            t += 100
        }
        return results
    }

    private fun List<ImpactAssessment?>.maxScore() = filterNotNull().maxOfOrNull { it.score } ?: 0

    @Test
    fun normalDriving_neverOpensEpisode() {
        assertTrue(run(FakeScenario.NORMAL_DRIVING).all { it == null })
    }

    @Test
    fun hardBrakeFalseAlarm_scoresZero_andEndsWhenDrivingResumes() {
        val results = run(FakeScenario.HARD_BRAKE_FALSE_ALARM)

        assertTrue("episode should open on hard stop", results.any { it != null })
        assertEquals(0, results.maxScore())
        assertNull("episode should close after resuming", results.last())
    }

    @Test
    fun collision_reachesEmergencyThreshold_withAllReasons() {
        val final = run(FakeScenario.COLLISION).filterNotNull().last()

        assertTrue("score ${final.score}", final.score >= config.emergencyThreshold)
        assertEquals(
            setOf(
                ImpactReason.SEVERE_DECELERATION,
                ImpactReason.DECELERATION_WITHOUT_BRAKE,
                ImpactReason.HAZARD_AFTER_STOP,
                ImpactReason.PARK_AFTER_STOP,
                ImpactReason.DOOR_AFTER_STOP,
                ImpactReason.SAFETY_WARNING,
            ),
            final.reasons,
        )
    }

    @Test
    fun collision_crossesConfirmThreshold_atImpact() {
        val firstOverThreshold = run(FakeScenario.COLLISION).filterNotNull().first { it.score >= config.confirmThreshold }
        // 충돌 시각(5,000ms) 직후 감속도만으로 넘는다.
        assertTrue(firstOverThreshold.startedAtMillis in 5_000..5_700)
    }

    @Test
    fun lowSpeedContact_staysBelowConfirmThreshold_withAftermathOnly() {
        val final = run(FakeScenario.LOW_SPEED_CONTACT).filterNotNull().last()

        assertTrue("score ${final.score}", final.score < config.confirmThreshold)
        assertEquals(
            setOf(ImpactReason.HAZARD_AFTER_STOP, ImpactReason.PARK_AFTER_STOP, ImpactReason.DOOR_AFTER_STOP),
            final.reasons,
        )
    }

    @Test
    fun airbagDeployed_aloneOpensEpisodeAtFullScore() {
        val scorer = ImpactScorer(config)
        val result = scorer.onEvent(SignalEvent.Safety(setOf(SafetyWarning.AIRBAG_DEPLOYED), 1_000))!!

        assertTrue(ImpactReason.AIRBAG_DEPLOYED in result.reasons)
        assertTrue(result.score >= config.airbagDeployedScore)
    }

    @Test
    fun aftermathOutsideWindow_isNotCounted() {
        val scorer = ImpactScorer(config)
        scorer.onEvent(SignalEvent.Brake(false, 0))
        scorer.onEvent(SignalEvent.Speed(10f, 0))
        scorer.onEvent(SignalEvent.Speed(0f, 500)) // ≈2g, 브레이크 없음 → 80점
        val late = scorer.onEvent(SignalEvent.Hazard(true, 500 + config.hazardWindowMillis + 1))

        assertTrue(late == null || ImpactReason.HAZARD_AFTER_STOP !in late.reasons)
    }

    @Test
    fun parkWhileStillMoving_doesNotCount() {
        val scorer = ImpactScorer(config)
        scorer.onEvent(SignalEvent.Brake(true, 0))
        scorer.onEvent(SignalEvent.Speed(20f, 0))
        scorer.onEvent(SignalEvent.Speed(17f, 300)) // ≈1g, 아직 주행 중
        val result = scorer.onEvent(SignalEvent.GearChanged(Gear.PARK, 400))!!

        assertTrue(ImpactReason.PARK_AFTER_STOP !in result.reasons)
    }

    @Test
    fun irregularSampleIntervals_stillDetectImpact() {
        // 16.7 → 0 m/s를 0.7초에 (≈2.4g), 간격 30~260ms로 들쭉날쭉
        val gaps = listOf(0L, 30, 260, 45, 120, 75, 170)
        val scorer = ImpactScorer(config)
        scorer.onEvent(SignalEvent.Brake(false, 0))
        var t = 1_000L
        var last: ImpactAssessment? = null
        for (gap in gaps) {
            t += gap
            val speed = 16.7f * (1f - (t - 1_000L) / 700f).coerceAtLeast(0f)
            last = scorer.onEvent(SignalEvent.Speed(speed, t))
        }

        assertTrue("decel ${last?.maxDecelerationG}", ImpactReason.SEVERE_DECELERATION in last!!.reasons)
    }

    @Test
    fun irregularSampleIntervals_gentleBrakingStaysQuiet() {
        // 0.3g 감속, 간격 20~600ms
        val gaps = listOf(0L, 20, 600, 90, 350, 40, 510, 200, 15, 480)
        val scorer = ImpactScorer(config)
        var t = 0L
        var v = 20f
        for (gap in gaps) {
            t += gap
            v -= 0.3f * 9.80665f * gap / 1000f
            assertNull("t=$t", scorer.onEvent(SignalEvent.Speed(v, t)))
        }
    }

    @Test
    fun sampleGapLongerThanLimit_isNotTreatedAsDeceleration() {
        val scorer = ImpactScorer(config)
        scorer.onEvent(SignalEvent.Speed(20f, 0))
        // 신호가 끊겼다 돌아온 경우: 1.5초 사이 20 → 0은 계산하지 않는다.
        assertNull(scorer.onEvent(SignalEvent.Speed(0f, config.maxSampleGapMillis + 500)))
    }

    @Test
    fun outOfOrderAndDuplicateSamples_areIgnored() {
        val scorer = ImpactScorer(config)
        scorer.onEvent(SignalEvent.Speed(20f, 1_000))
        assertNull(scorer.onEvent(SignalEvent.Speed(0f, 900))) // 과거 샘플
        assertNull(scorer.onEvent(SignalEvent.Speed(0f, 1_000))) // 같은 시각
        assertNull(scorer.onEvent(SignalEvent.Speed(19.9f, 1_100)))
    }

    @Test
    fun scoreStream_triggersOncePerEpisode_thenEscalates() = runTest {
        val events = flowOf(
            SignalEvent.Brake(false, 0),
            SignalEvent.Speed(16.7f, 0),
            SignalEvent.Speed(8f, 300), // ≈3g, 브레이크 없음 → 80점 → 트리거
            SignalEvent.Speed(0f, 600),
            SignalEvent.Hazard(true, 2_000), // +15 → 상승
            SignalEvent.Hazard(false, 3_000),
            SignalEvent.Hazard(true, 4_000), // 이미 반영 → 이벤트 없음
        )
        val out = events.impactScores(config).toList()

        val triggers = out.filterIsInstance<ImpactScoreEvent.Triggered>()
        val escalations = out.filterIsInstance<ImpactScoreEvent.Escalated>()
        assertEquals(1, triggers.size)
        assertEquals(80, triggers.single().assessment.score)
        assertEquals(listOf(95), escalations.map { it.assessment.score })
        assertEquals(events.toList().size, out.filterIsInstance<ImpactScoreEvent.Scored>().size)
    }
}
