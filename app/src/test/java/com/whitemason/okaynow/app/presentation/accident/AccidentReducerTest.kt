package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.detection.ImpactAssessment
import com.whitemason.okaynow.app.domain.detection.ImpactReason
import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.presentation.accident.AccidentSideEffect.CancelTimer
import com.whitemason.okaynow.app.presentation.accident.AccidentSideEffect.CancelVoice
import com.whitemason.okaynow.app.presentation.accident.AccidentSideEffect.EmergencyGuide
import com.whitemason.okaynow.app.presentation.accident.AccidentSideEffect.Listen
import com.whitemason.okaynow.app.presentation.accident.AccidentSideEffect.Speak
import com.whitemason.okaynow.app.presentation.accident.AccidentSideEffect.StartNoResponseTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reducer는 순수 함수: 상태 + 의도 → 새 상태 + 부수 효과. */
class AccidentReducerTest {

    private val reducer = AccidentReducer()

    private fun AccidentUiState.after(vararg intents: AccidentIntent): AccidentUiState =
        intents.fold(this) { s, i -> reducer.reduce(s, i).state }

    private fun assessment(score: Int, airbag: Boolean = false) = ImpactAssessment(
        episodeId = 1, startedAtMillis = 0, score = score,
        reasons = if (airbag) setOf(ImpactReason.AIRBAG_DEPLOYED) else emptySet(), maxDecelerationG = 1.5f,
    )

    @Test
    fun impactTriggered_entersConfirm_asksAndStartsTimer() {
        val result = reducer.reduce(AccidentUiState(), AccidentIntent.ImpactTriggered(assessment(80)))

        assertEquals(AccidentState.ConfirmAccident(80), result.state.step)
        assertEquals(80, result.state.assessment?.score)
        assertEquals(
            listOf(CancelVoice, CancelTimer, AccidentSideEffect.StampStart, StartNoResponseTimer(10_000), Speak(VoicePrompts.CONFIRM_QUESTION), Listen),
            result.effects,
        )
    }

    @Test
    fun escalation_inConfirm_updatesScore_withoutRestartingTimer() {
        val confirm = AccidentUiState().after(AccidentIntent.ImpactTriggered(assessment(80)))
        val result = reducer.reduce(confirm, AccidentIntent.ImpactEscalated(assessment(95)))

        assertEquals(AccidentState.ConfirmAccident(95), result.state.step)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun escalation_afterAnswer_isIgnored() {
        val injury = AccidentUiState().after(AccidentIntent.ImpactTriggered(assessment(80)), AccidentIntent.AnswerYes)
        val result = reducer.reduce(injury, AccidentIntent.ImpactEscalated(assessment(95)))

        assertEquals(AccidentState.AskInjury(), result.state.step)
        assertEquals(95, result.state.assessment?.score)
    }

    @Test
    fun injured_goesToEmergency_withGuide_beforeInsurer() {
        val result = reducer.reduce(AccidentUiState(step = AccidentState.AskInjury()), AccidentIntent.AnswerYes)

        assertEquals(AccidentState.Emergency, result.state.step)
        assertEquals(listOf(CancelVoice, CancelTimer, EmergencyGuide, Listen), result.effects)
    }

    @Test
    fun falseAlarm_returnsHome_andLogs() {
        val confirm = AccidentUiState().after(AccidentIntent.ImpactTriggered(assessment(70)))
        val result = reducer.reduce(confirm, AccidentIntent.AnswerNo)

        assertEquals(AccidentState.Idle, result.state.step)
        assertTrue(AccidentSideEffect.LogFalsePositive(70) in result.effects)
    }

    @Test
    fun zones_toggle_andClearWhenBackHome() {
        val photo = AccidentUiState(step = AccidentState.PhotoCapture)
            .after(AccidentIntent.ToggleZone(CarZone.FRONT_BUMPER), AccidentIntent.ToggleZone(CarZone.LEFT_FRONT_DOOR), AccidentIntent.ToggleZone(CarZone.FRONT_BUMPER))
        assertEquals(setOf(CarZone.LEFT_FRONT_DOOR), photo.damageZones)

        val home = photo.copy(step = AccidentState.SecureScene).after(AccidentIntent.Finish)
        assertEquals(AccidentState.Idle, home.step)
        assertTrue(home.damageZones.isEmpty())
    }

    @Test
    fun shotGuide_speaksOnlyWithZones() {
        assertTrue(reducer.reduce(AccidentUiState(), AccidentIntent.SpeakShotGuide).effects.isEmpty())
        val effects = reducer.reduce(AccidentUiState(damageZones = setOf(CarZone.FRONT_BUMPER)), AccidentIntent.SpeakShotGuide).effects
        assertTrue(effects.single() is AccidentSideEffect.Speak)
    }

    @Test
    fun back_onlyFromVehicleCheckOnward() {
        assertFalse(AccidentUiState(step = AccidentState.SecureScene).canGoBack)
        assertTrue(AccidentUiState(step = AccidentState.VehicleCheck).canGoBack)
        assertEquals(AccidentState.SecureScene, AccidentUiState(step = AccidentState.VehicleCheck).after(AccidentIntent.Back).step)
        assertEquals(AccidentState.AskInjury(), AccidentUiState(step = AccidentState.AskInjury()).after(AccidentIntent.Back).step)
    }

    @Test
    fun call119_isUiEffect_notStepChange() {
        val result = reducer.reduce(AccidentUiState(step = AccidentState.Emergency), AccidentIntent.Call119)
        assertEquals(AccidentState.Emergency, result.state.step)
        assertEquals(listOf(AccidentSideEffect.Ui(AccidentUiEffect.OpenDialer("119"))), result.effects)
    }

    @Test
    fun leavingQuestion_clearsCountdown() {
        val result = reducer.reduce(AccidentUiState(step = AccidentState.AskInjury(), secondsLeft = 4), AccidentIntent.AnswerNo)
        assertEquals(AccidentState.SecureScene, result.state.step)
        assertEquals(null, result.state.secondsLeft)
    }

    private val driving = com.whitemason.okaynow.app.domain.vehicle.VehicleStatus(gear = com.whitemason.okaynow.app.domain.vehicle.Gear.DRIVE)
    private val parked = com.whitemason.okaynow.app.domain.vehicle.VehicleStatus(gear = com.whitemason.okaynow.app.domain.vehicle.Gear.PARK)

    @Test
    fun callEnded_whileDriving_asksToPark_andBlocksHiddenButtons() {
        val c3 = AccidentUiState(step = AccidentState.Emergency, vehicle = driving)
        val result = reducer.reduce(c3, AccidentIntent.Next)

        assertEquals(AccidentState.SecureScene, result.state.step)
        assertTrue(result.state.needsPark)
        assertTrue(AccidentSideEffect.Speak(VoicePrompts.PARK_FIRST) in result.effects)
        // 화면에 없는 "다음"을 말해도 넘어가지 않는다. 119는 된다.
        assertEquals(AccidentState.SecureScene, result.state.after(AccidentIntent.Next).step)
        assertEquals(AccidentState.Emergency, result.state.after(AccidentIntent.RequestEmergency).step)
        assertEquals(listOf("119 전화"), result.state.voiceCommands().map { it.choice.id })
    }

    @Test
    fun shiftingToPark_continuesStep_andRestartsListening() {
        val waiting = AccidentUiState(step = AccidentState.SecureScene, vehicle = driving)
        val result = reducer.reduce(waiting, AccidentIntent.VehicleChanged(parked))

        assertFalse(result.state.needsPark)
        assertEquals(listOf(CancelVoice, AccidentSideEffect.Listen), result.effects)
        assertEquals(AccidentState.VehicleCheck, result.state.after(AccidentIntent.Next).step)
    }

    @Test
    fun leavingPark_duringParkOnlyStep_announcesOnce() {
        val c5 = AccidentUiState(step = AccidentState.VehicleCheck, vehicle = parked)
        val first = reducer.reduce(c5, AccidentIntent.VehicleChanged(driving))
        assertEquals(listOf<AccidentSideEffect>(AccidentSideEffect.Speak(VoicePrompts.PARK_FIRST)), first.effects)
        assertTrue(reducer.reduce(first.state, AccidentIntent.VehicleChanged(driving.copy(speedKmh = 10f))).effects.isEmpty())
    }
}
