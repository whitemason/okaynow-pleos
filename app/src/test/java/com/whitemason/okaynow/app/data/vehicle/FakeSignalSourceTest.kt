package com.whitemason.okaynow.app.data.vehicle

import com.whitemason.okaynow.app.domain.vehicle.Gear
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning
import com.whitemason.okaynow.app.domain.vehicle.SpeedSample
import com.whitemason.okaynow.app.domain.vehicle.TireStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeSignalSourceTest {

    private class Recording {
        val speed = mutableListOf<SpeedSample>()
        val brake = mutableListOf<Boolean>()
        val hazard = mutableListOf<Boolean>()
        val gear = mutableListOf<Gear>()
        val door = mutableListOf<Boolean>()
        val warnings = mutableListOf<Set<SafetyWarning>>()
        val tire = mutableListOf<TireStatus>()

        /** 연속한 두 샘플 사이의 최대 감속도(g). 같은 시각의 샘플 쌍은 건너뛴다. */
        fun maxDecelerationG(): Float = speed.zipWithNext()
            .filter { (a, b) -> b.timestampMillis > a.timestampMillis }
            .maxOfOrNull { (a, b) ->
                (a.metersPerSecond - b.metersPerSecond) / ((b.timestampMillis - a.timestampMillis) / 1000f) / G
            } ?: 0f
    }

    private fun TestScope.play(scenario: FakeScenario): Recording {
        val source = FakeSignalSource(backgroundScope) { testScheduler.currentTime }
        val rec = Recording()
        backgroundScope.launch { source.speed.toList(rec.speed) }
        backgroundScope.launch { source.brakePedal.toList(rec.brake) }
        backgroundScope.launch { source.hazardLight.toList(rec.hazard) }
        backgroundScope.launch { source.gear.toList(rec.gear) }
        backgroundScope.launch { source.doorOpen.toList(rec.door) }
        backgroundScope.launch { source.safetyWarnings.toList(rec.warnings) }
        backgroundScope.launch { source.tireStatus.toList(rec.tire) }
        runCurrent()
        source.play(scenario)
        // 재생은 backgroundScope에서 돌기 때문에 advanceUntilIdle()로는 진행되지 않는다.
        testScheduler.advanceTimeBy(scenario.script.durationMillis + FakeSignalSource.TICK_MILLIS)
        runCurrent()
        return rec
    }

    @Test
    fun normalDriving_hasNoTriggerSignals() = runTest {
        val rec = play(FakeScenario.NORMAL_DRIVING)

        assertTrue("max decel ${rec.maxDecelerationG()}", rec.maxDecelerationG() < 0.3f)
        assertFalse(rec.hazard.any { it })
        assertFalse(rec.door.any { it })
        assertTrue(rec.warnings.all { it.isEmpty() })
        assertFalse(Gear.PARK in rec.gear.drop(1))
    }

    @Test
    fun hardBrake_staysBelowImpactThreshold_withBrakePressed() = runTest {
        val scenario = FakeScenario.HARD_BRAKE_FALSE_ALARM
        val rec = play(scenario)

        assertTrue("max decel ${rec.maxDecelerationG()}", rec.maxDecelerationG() in 0.8f..IMPACT_G)
        assertTrue(scenario.script.snapshotAt(6_000).brakePedal)
        assertFalse(rec.hazard.any { it })
        assertFalse(rec.door.any { it })
        assertEquals(Gear.DRIVE, rec.gear.last())
    }

    @Test
    fun collision_exceedsImpactThreshold_withoutBrake_thenSecondarySignals() = runTest {
        val scenario = FakeScenario.COLLISION
        val rec = play(scenario)

        assertTrue("max decel ${rec.maxDecelerationG()}", rec.maxDecelerationG() >= IMPACT_G)
        assertFalse("brake must be released at impact", scenario.script.snapshotAt(5_300).brakePedal)
        assertTrue(SafetyWarning.MASTER_WARNING in rec.warnings.last())
        assertEquals(TireStatus.LOW_PRESSURE_WARNING, rec.tire.last())
        assertTrue("hazard within 30s of impact", scenario.script.snapshotAt(5_000 + 30_000).hazardLight)
        assertEquals(Gear.PARK, rec.gear.last())
        assertTrue(rec.door.last())
    }

    @Test
    fun lowSpeedContact_staysBelowImpactThreshold_butShowsAftermath() = runTest {
        val rec = play(FakeScenario.LOW_SPEED_CONTACT)

        assertTrue("max decel ${rec.maxDecelerationG()}", rec.maxDecelerationG() < IMPACT_G)
        assertTrue(rec.hazard.last())
        assertEquals(Gear.PARK, rec.gear.last())
        assertTrue(rec.door.last())
        assertTrue(rec.warnings.all { it.isEmpty() })
    }

    @Test
    fun stop_returnsToParkedState() = runTest {
        val source = FakeSignalSource(backgroundScope) { testScheduler.currentTime }
        val gears = mutableListOf<Gear>()
        backgroundScope.launch { source.gear.toList(gears) }
        source.play(FakeScenario.COLLISION)
        testScheduler.advanceTimeBy(1_000)
        source.stop()
        runCurrent()

        assertEquals(listOf(Gear.PARK, Gear.DRIVE, Gear.PARK), gears)
        assertEquals(null, source.playing.value)
    }

    private companion object {
        const val G = 9.80665f
        const val IMPACT_G = 1.2f // PLAN.md 4절
    }
}
