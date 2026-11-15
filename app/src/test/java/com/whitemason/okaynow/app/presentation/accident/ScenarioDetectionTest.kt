package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.data.vehicle.FakeScenario
import com.whitemason.okaynow.app.data.vehicle.FakeSignalSource
import com.whitemason.okaynow.app.domain.detection.SignalEvent
import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.domain.vehicle.SignalType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시연 시나리오 → 신호 이벤트 → 점수 → Intent → Reducer(무응답 타이머 포함)를 가상 시간으로 끝까지 돌린다. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioDetectionTest {

    private class Run(val store: AccidentStore, val visited: List<AccidentState>)

    private fun TestScope.play(
        scenario: FakeScenario,
        untilMillis: Long = scenario.script.durationMillis,
        transform: (Flow<SignalEvent>) -> Flow<SignalEvent> = { it },
    ): Run {
        val source = FakeSignalSource(backgroundScope) { testScheduler.currentTime }
        val store = testStore(source = source, transform = transform)
        val visited = mutableListOf<AccidentState>()
        backgroundScope.launch { store.state.collect { visited += it.step } }
        runCurrent()
        source.play(scenario)
        testScheduler.advanceTimeBy(untilMillis)
        runCurrent()
        return Run(store, visited)
    }

    private val Run.step get() = store.state.value.step

    @Test
    fun collision_requestsConfirmation() = runTest {
        // 충돌 시각 5,000ms 직후 C1이 떠야 한다(무응답 타이머 10초 전에 확인).
        val run = play(FakeScenario.COLLISION, untilMillis = 6_000)

        val state = run.step
        assertTrue("state=$state", state is AccidentState.ConfirmAccident)
        assertTrue("score=${(state as AccidentState.ConfirmAccident).score}", state.score >= 60)
    }

    @Test
    fun collision_noResponseWithScoreOver90_goesToEmergency() = runTest {
        val run = play(FakeScenario.COLLISION, untilMillis = 5_000 + 10_000 + 1_000)

        // C1에 머무는 동안 비상등·P단이 더해져 점수가 오른다(타이머는 다시 시작하지 않는다).
        val confirm = run.visited.filterIsInstance<AccidentState.ConfirmAccident>().last()
        assertTrue("score=${confirm.score}", confirm.score >= 90)
        assertEquals(AccidentState.Emergency, run.step)
        assertEquals(
            listOf(AccidentState.Idle::class, AccidentState.ConfirmAccident::class, AccidentState.Emergency::class),
            run.visited.map { it::class }.distinct(),
        )
    }

    @Test
    fun hardBrakeWithBrakePedal_doesNotTrigger() = runTest {
        val run = play(FakeScenario.HARD_BRAKE_FALSE_ALARM)

        assertEquals(listOf<AccidentState>(AccidentState.Idle), run.visited.distinct())
    }

    @Test
    fun lowSpeedContact_doesNotTrigger_untilManualStart() = runTest {
        val run = play(FakeScenario.LOW_SPEED_CONTACT)

        assertEquals(listOf<AccidentState>(AccidentState.Idle), run.visited.distinct())
        assertTrue("aftermath should be scored", (run.store.state.value.assessment?.score ?: 0) in 1..59)

        run.store.dispatch(AccidentIntent.ManualStart)
        assertEquals(AccidentState.AskInjury(), run.step)
    }

    @Test
    fun collisionWithoutSafetySignals_stillRequestsConfirmation() = runTest {
        // 실차에서 안전 경고를 못 받는 경우(SDK_NOTES 6절): 감속도·브레이크만으로 80점
        val run = play(FakeScenario.COLLISION, untilMillis = 6_000) { events ->
            events.filterNot { it is SignalEvent.Safety || it is SignalEvent.Hazard }
        }

        val state = run.step
        assertTrue("state=$state", state is AccidentState.ConfirmAccident)
        assertTrue((state as AccidentState.ConfirmAccident).score in 60..89)
    }

    @Test
    fun vehicleStatus_flowsIntoUiState() = runTest {
        val run = play(FakeScenario.COLLISION, untilMillis = 6_000)
        assertEquals(SignalType.entries.toSet(), run.store.state.value.vehicle.supported)
    }
}
