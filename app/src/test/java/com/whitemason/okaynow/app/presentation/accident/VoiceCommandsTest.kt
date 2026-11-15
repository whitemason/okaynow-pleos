package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.domain.voice.matchAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 단계마다 버튼 이름을 말하면 그 버튼으로 알아듣는지(다른 버튼과 헷갈리지 않는지). */
class VoiceCommandsTest {

    private val steps = listOf(
        AccidentState.ConfirmAccident(80), AccidentState.AskInjury(), AccidentState.Emergency, AccidentState.SecureScene,
        AccidentState.VehicleCheck, AccidentState.PhotoCapture, AccidentState.DamageMap, AccidentState.ContactInsurer, AccidentState.Done,
    )

    @Test
    fun home_listensOnlyWhileVisible() {
        assertTrue(AccidentUiState(step = AccidentState.Idle, appVisible = false).voiceCommands().isEmpty())
        val commands = AccidentUiState(step = AccidentState.Idle, appVisible = true).voiceCommands()
        assertEquals(listOf("사고 났어요"), commands.map { it.choice.id })
        assertEquals(AccidentIntent.ManualStart, commands.single().intent)
        for (said in listOf("사고 났어요", "사고 났어", "사고야")) assertEquals(said, commands.single().choice, matchAnswer(said, commands.map { it.choice }))
    }

    @Test
    fun everyPhrase_matchesOwnButton() {
        for (step in steps) {
            val commands = AccidentUiState(step = step).voiceCommands()
            val choices = commands.map { it.choice }
            assertTrue("$step", commands.isNotEmpty())
            for (command in commands) for (phrase in command.choice.phrases) {
                assertEquals("$step \"$phrase\"", command.choice, matchAnswer(phrase, choices))
            }
        }
    }

    @Test
    fun buttonsByStep() {
        fun ids(step: AccidentState) = AccidentUiState(step = step).voiceCommands().map { it.choice.id }
        assertEquals(listOf("119 전화", "위치 다시 듣기", "통화 끝"), ids(AccidentState.Emergency))
        assertEquals(listOf("다음", "처음으로", "119 전화"), ids(AccidentState.SecureScene))
        assertEquals(listOf("다음", "뒤로", "119 전화"), ids(AccidentState.DamageMap))
        assertEquals(listOf("보험사 전화", "접수 완료", "뒤로", "119 전화"), ids(AccidentState.ContactInsurer))
    }

    @Test
    fun spokenButtons_sendSameIntentAsTap() {
        fun intentFor(step: AccidentState, said: String): AccidentIntent? {
            val commands = AccidentUiState(step = step).voiceCommands()
            val choice = matchAnswer(said, commands.map { it.choice })
            return commands.firstOrNull { it.choice == choice }?.intent
        }
        assertEquals(AccidentIntent.Next, intentFor(AccidentState.SecureScene, "완료"))
        assertEquals(AccidentIntent.Finish, intentFor(AccidentState.SecureScene, "처음으로"))
        assertEquals(AccidentIntent.Call119, intentFor(AccidentState.Emergency, "119 전화"))
        assertEquals(AccidentIntent.RequestEmergency, intentFor(AccidentState.VehicleCheck, "119"))
        assertEquals(AccidentIntent.AnswerYes, intentFor(AccidentState.AskInjury(), "있어요"))
        assertEquals(AccidentIntent.AnswerNo, intentFor(AccidentState.AskInjury(), "없어요"))
    }
}
