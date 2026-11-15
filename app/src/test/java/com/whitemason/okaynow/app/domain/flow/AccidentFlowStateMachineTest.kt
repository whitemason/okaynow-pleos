package com.whitemason.okaynow.app.domain.flow

import com.whitemason.okaynow.app.domain.flow.AccidentEvent.Finish
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.ImpactDetected
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.ManualStart
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.Next
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.No
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.NoResponse
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.RequestEmergency
import com.whitemason.okaynow.app.domain.flow.AccidentEvent.Yes
import com.whitemason.okaynow.app.domain.flow.AccidentState.AskInjury
import com.whitemason.okaynow.app.domain.flow.AccidentState.ConfirmAccident
import com.whitemason.okaynow.app.domain.flow.AccidentState.ContactInsurer
import com.whitemason.okaynow.app.domain.flow.AccidentState.DamageMap
import com.whitemason.okaynow.app.domain.flow.AccidentState.Done
import com.whitemason.okaynow.app.domain.flow.AccidentState.Emergency
import com.whitemason.okaynow.app.domain.flow.AccidentState.Idle
import com.whitemason.okaynow.app.domain.flow.AccidentState.PhotoCapture
import com.whitemason.okaynow.app.domain.flow.AccidentState.SecureScene
import com.whitemason.okaynow.app.domain.flow.AccidentState.VehicleCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccidentFlowStateMachineTest {

    private val machine = AccidentFlowStateMachine()

    private fun AccidentState.after(vararg events: AccidentEvent): AccidentState =
        events.fold(this) { state, event -> machine.reduce(state, event).state }

    @Test
    fun impactBelowThreshold_staysIdle() {
        assertEquals(Idle, Idle.after(ImpactDetected(59)))
    }

    @Test
    fun impactAtThreshold_asksConfirmation() {
        assertEquals(ConfirmAccident(60), Idle.after(ImpactDetected(60)))
    }

    @Test
    fun airbag_skipsConfirmation() {
        assertEquals(AskInjury(severe = true), Idle.after(ImpactDetected(120, airbagDeployed = true)))
        assertEquals(AskInjury(severe = true), ConfirmAccident(70).after(ImpactDetected(120, airbagDeployed = true)))
    }

    @Test
    fun manualStart_goesStraightToInjuryQuestion() {
        assertEquals(AskInjury(severe = false), Idle.after(ManualStart))
    }

    @Test
    fun confirmNo_returnsIdle_andLogsFalsePositive() {
        val transition = machine.reduce(ConfirmAccident(75), No)

        assertEquals(Idle, transition.state)
        assertEquals(AccidentEffect.LogFalsePositive(75), transition.effect)
    }

    @Test
    fun confirmNoResponse_highScore_goesToEmergency() {
        assertEquals(Emergency, ConfirmAccident(90).after(NoResponse))
    }

    @Test
    fun confirmNoResponse_lowScore_asksInjuryAgain() {
        assertEquals(AskInjury(severe = false), ConfirmAccident(89).after(NoResponse))
    }

    @Test
    fun confirmKeepsHighestScore() {
        assertEquals(ConfirmAccident(95), ConfirmAccident(65).after(ImpactDetected(95), ImpactDetected(70)))
    }

    @Test
    fun injuryYes_goesToEmergency() {
        assertEquals(Emergency, AskInjury().after(Yes))
        assertEquals(Emergency, AskInjury(severe = true).after(Yes))
    }

    @Test
    fun injuryNoResponse_severe_goesToEmergency_otherwiseHome() {
        assertEquals(Emergency, AskInjury(severe = true).after(NoResponse))
        assertEquals(Idle, AskInjury(severe = false).after(NoResponse))
    }

    @Test
    fun confirmYes_carriesSeverity() {
        assertEquals(AskInjury(severe = true), ConfirmAccident(90).after(Yes))
        assertEquals(AskInjury(severe = false), ConfirmAccident(89).after(Yes))
    }

    @Test
    fun airbagDuringInjuryQuestion_makesItSevere() {
        assertEquals(AskInjury(severe = true), AskInjury().after(ImpactDetected(120, airbagDeployed = true)))
        assertEquals(AskInjury(severe = false), AskInjury().after(ImpactDetected(70)))
    }

    @Test
    fun fullFlowWithoutInjury_reachesDoneWithoutEmergency() {
        val visited = mutableListOf<AccidentState>()
        var state: AccidentState = Idle
        for (event in listOf(ImpactDetected(80), Yes, No, Next, Next, Next, Next, Next)) {
            state = machine.reduce(state, event).state
            visited += state
        }

        assertEquals(
            listOf(ConfirmAccident(80), AskInjury(), SecureScene, VehicleCheck, PhotoCapture, DamageMap, ContactInsurer, Done),
            visited,
        )
        assertEquals(Idle, state.after(Finish))
    }

    @Test
    fun emergencyAlwaysPrecedesInsurer_whenInjured() {
        val visited = mutableListOf<AccidentState>()
        var state: AccidentState = Idle
        for (event in listOf(ManualStart, Yes, Next, Next, Next, Next, Next)) {
            state = machine.reduce(state, event).state
            visited += state
        }

        assertTrue(visited.indexOf(Emergency) < visited.indexOf(ContactInsurer))
    }

    @Test
    fun requestEmergency_fromAnyActiveStep() {
        listOf(ConfirmAccident(60), AskInjury(), SecureScene, VehicleCheck, PhotoCapture, DamageMap, ContactInsurer)
            .forEach { assertEquals("from $it", Emergency, it.after(RequestEmergency)) }
        assertEquals(Idle, Idle.after(RequestEmergency))
    }

    @Test
    fun impactDuringFlow_isIgnored() {
        assertEquals(SecureScene, SecureScene.after(ImpactDetected(100)))
        assertNull(machine.reduce(SecureScene, ImpactDetected(100)).effect)
    }

    @Test
    fun back_goesOneStepBack_fromVehicleCheckOnward() {
        assertEquals(AccidentState.SecureScene, AccidentState.VehicleCheck.after(AccidentEvent.Back))
        assertEquals(AccidentState.VehicleCheck, AccidentState.PhotoCapture.after(AccidentEvent.Back))
        assertEquals(AccidentState.PhotoCapture, AccidentState.DamageMap.after(AccidentEvent.Back))
        assertEquals(AccidentState.DamageMap, AccidentState.ContactInsurer.after(AccidentEvent.Back))
        assertEquals(AccidentState.ContactInsurer, AccidentState.Done.after(AccidentEvent.Back))
    }

    @Test
    fun back_isIgnored_duringSafetySteps() {
        val safety = listOf(AccidentState.Idle, AccidentState.ConfirmAccident(80), AccidentState.AskInjury(), AccidentState.Emergency, AccidentState.SecureScene)
        for (state in safety) assertEquals(state, state.after(AccidentEvent.Back))
    }

    @Test
    fun finish_fromSecureScene_returnsHome() {
        assertEquals(AccidentState.Idle, AccidentState.SecureScene.after(AccidentEvent.Finish))
        // 부상 확인 전에는 처음으로 돌아갈 수 없다.
        assertEquals(AccidentState.AskInjury(), AccidentState.AskInjury().after(AccidentEvent.Finish))
        assertEquals(AccidentState.Emergency, AccidentState.Emergency.after(AccidentEvent.Finish))
    }

    @Test
    fun noResponseTimeouts_onlyForQuestions() {
        assertEquals(10_000L, machine.noResponseTimeoutMillis(ConfirmAccident(70)))
        assertEquals(12_000L, machine.noResponseTimeoutMillis(AskInjury()))
        listOf(Idle, Emergency, SecureScene, VehicleCheck, PhotoCapture, DamageMap, ContactInsurer, Done)
            .forEach { assertNull("$it", machine.noResponseTimeoutMillis(it)) }
    }
}
