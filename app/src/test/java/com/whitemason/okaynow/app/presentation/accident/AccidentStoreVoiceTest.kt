package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.flow.AccidentState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Store가 Reducer의 음성·타이머 효과를 실행하고 결과를 다시 Intent로 넣는지(가상 시간). */
@OptIn(ExperimentalCoroutinesApi::class)
class AccidentStoreVoiceTest {

    @Test
    fun spokenYesThenNoInjury_reachesSecureScene() = runTest {
        val voice = ScriptedVoice(mutableListOf(say("응"), say("안 다쳤어")))
        val store = testStore(voice).also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.ConfirmAccident(80)))
        testScheduler.advanceTimeBy(2_000)
        runCurrent()

        assertEquals(AccidentState.SecureScene, store.state.value.step)
        assertEquals(listOf(VoicePrompts.CONFIRM_QUESTION, VoicePrompts.INJURY_QUESTION), voice.spoken)
    }

    @Test
    fun spokenNo_dismissesFalseAlarm() = runTest {
        val store = testStore(ScriptedVoice(mutableListOf(say("아니요")))).also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.ConfirmAccident(70)))
        testScheduler.advanceTimeBy(1_000)
        runCurrent()

        assertEquals(AccidentState.Idle, store.state.value.step)
    }

    @Test
    fun noSpokenAnswer_timerGoesToEmergency_readsLocation_andShowsIt() = runTest {
        val voice = ScriptedVoice()
        val store = testStore(voice).also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.ConfirmAccident(95)))
        testScheduler.advanceTimeBy(5_000)
        runCurrent()
        assertEquals(5, store.state.value.secondsLeft)

        testScheduler.advanceTimeBy(5_000 + 100)
        runCurrent()

        assertEquals(AccidentState.Emergency, store.state.value.step)
        assertEquals(VoicePrompts.CONFIRM_QUESTION, voice.spoken.first())
        assertEquals(VoicePrompts.EMERGENCY_GUIDE, voice.spoken[1])
        assertTrue(voice.spoken.last().startsWith("현재 위치는"))
        assertEquals(SEOUL, store.state.value.location)
        assertEquals(null, store.state.value.secondsLeft)
    }

    @Test
    fun buttonBeforeVoice_cancelsListening() = runTest {
        val voice = ScriptedVoice()
        val store = testStore(voice).also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.ConfirmAccident(80)))
        runCurrent()
        store.dispatch(AccidentIntent.AnswerYes) // 버튼
        runCurrent()

        assertEquals(AccidentState.AskInjury(), store.state.value.step)
        assertEquals(listOf(VoicePrompts.CONFIRM_QUESTION, VoicePrompts.INJURY_QUESTION), voice.spoken) // C1 듣기는 취소되고 C2 질문으로 넘어감
    }

    @Test
    fun unknownLocation_readsFallback() = runTest {
        val voice = ScriptedVoice()
        val store = testStore(voice, location = null).also { runCurrent() }

        store.dispatch(AccidentIntent.ManualStart)
        store.dispatch(AccidentIntent.AnswerYes)
        runCurrent()

        assertEquals(VoicePrompts.LOCATION_UNKNOWN, voice.spoken.last())
    }

    @Test
    fun injuryNoResponse_notSevere_returnsHome_andStopsListening() = runTest {
        val voice = ScriptedVoice()
        val store = testStore(voice).also { runCurrent() }

        store.dispatch(AccidentIntent.ManualStart)
        testScheduler.advanceTimeBy(12_000 + 100)
        runCurrent()

        assertEquals(AccidentState.Idle, store.state.value.step)
        assertEquals(listOf(VoicePrompts.INJURY_QUESTION), voice.spoken)
        assertEquals(1, voice.listened.size) // C0에서는 다시 듣지 않는다
    }

    @Test
    fun injuryNoResponse_severe_goesToEmergency() = runTest {
        val store = testStore().also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.AskInjury(severe = true)))
        testScheduler.advanceTimeBy(12_000 + 100)
        runCurrent()

        assertEquals(AccidentState.Emergency, store.state.value.step)
    }

    @Test
    fun buttonNameSpoken_pressesThatButton() = runTest {
        val voice = ScriptedVoice(mutableListOf(say("완료"), say("다음"), say("처음으로")))
        val store = testStore(voice).also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.SecureScene))
        testScheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals(AccidentState.VehicleCheck, store.state.value.step) // "완료"

        testScheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals(AccidentState.PhotoCapture, store.state.value.step) // "다음"

        // C6에는 "처음으로" 버튼이 없어 알아듣지 못하고 계속 듣는다.
        testScheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals(AccidentState.PhotoCapture, store.state.value.step)
        assertEquals(listOf("촬영 완료", "음성 안내", "뒤로", "119 전화"), voice.listened.last())
    }

    @Test
    fun spokenShotDone_withoutZones_isIgnored_andKeepsListening() = runTest {
        val voice = ScriptedVoice(mutableListOf(say("촬영 완료"), say("119")))
        val store = testStore(voice).also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.PhotoCapture))
        testScheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals(AccidentState.PhotoCapture, store.state.value.step)

        testScheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals(AccidentState.Emergency, store.state.value.step)
    }

    @Test
    fun spokenRepeatLocation_readsLocation_thenListensAgain() = runTest {
        val voice = ScriptedVoice(mutableListOf(say("위치 다시 듣기"), say("통화 끝")))
        val store = testStore(voice).also { runCurrent() }

        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.Emergency))
        testScheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals(AccidentState.Emergency, store.state.value.step)
        assertEquals(2, voice.spoken.count { it.startsWith("현재 위치는") }) // 처음 안내 + 다시 듣기

        testScheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals(AccidentState.SecureScene, store.state.value.step)
    }

    @Test
    fun home_listensWhileVisible_andStartsOnSpokenAccident() = runTest {
        val voice = ScriptedVoice(mutableListOf(say("사고 났어")))
        val store = testStore(voice).also { runCurrent() }
        assertTrue(voice.listened.isEmpty()) // 화면이 안 보이면(백그라운드 감시) 듣지 않는다

        store.dispatch(AccidentIntent.AppVisibilityChanged(true))
        testScheduler.advanceTimeBy(600)
        runCurrent()

        assertEquals(AccidentState.AskInjury(), store.state.value.step)
    }

    @Test
    fun home_stopsListeningWhenHidden() = runTest {
        val voice = ScriptedVoice()
        val store = testStore(voice).also { runCurrent() }
        store.dispatch(AccidentIntent.AppVisibilityChanged(true))
        runCurrent()
        assertEquals(1, voice.listened.size)

        store.dispatch(AccidentIntent.AppVisibilityChanged(false))
        store.dispatch(AccidentIntent.AppVisibilityChanged(true))
        runCurrent()
        assertEquals(2, voice.listened.size) // 가려지면 멈췄다가 다시 보이면 새로 듣는다
    }

    @Test
    fun returningHome_whileVisible_listensAgain() = runTest {
        val voice = ScriptedVoice()
        val store = testStore(voice).also { runCurrent() }
        store.dispatch(AccidentIntent.AppVisibilityChanged(true))
        store.dispatch(AccidentIntent.DebugForceStep(AccidentState.Done))
        store.dispatch(AccidentIntent.Finish)
        runCurrent()

        assertEquals(AccidentState.Idle, store.state.value.step)
        assertEquals(listOf("사고 났어요"), voice.listened.last())
    }

    @Test
    fun call119_opensDialerOnce() = runTest {
        val store = testStore().also { runCurrent() }
        val effects = mutableListOf<AccidentUiEffect>()
        backgroundScope.launch { store.uiEffects.collect { effects += it } }
        runCurrent()

        store.dispatch(AccidentIntent.Call119)
        runCurrent()

        assertEquals(listOf<AccidentUiEffect>(AccidentUiEffect.OpenDialer("119")), effects)
    }

}
