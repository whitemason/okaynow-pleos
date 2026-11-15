package com.whitemason.okaynow.app.domain.voice

import com.whitemason.okaynow.app.domain.voice.VoiceChoice.Companion.INJURED
import com.whitemason.okaynow.app.domain.voice.VoiceChoice.Companion.NO
import com.whitemason.okaynow.app.domain.voice.VoiceChoice.Companion.NOT_INJURED
import com.whitemason.okaynow.app.domain.voice.VoiceChoice.Companion.YES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerMatcherTest {

    private val yesNo = listOf(YES, NO)
    private val injury = listOf(INJURED, NOT_INJURED)

    @Test
    fun yesNo_basic() {
        assertEquals(YES, matchAnswer("응", yesNo))
        assertEquals(YES, matchAnswer("네, 사고 났어요", yesNo))
        assertEquals(NO, matchAnswer("아니요", yesNo))
        assertEquals(NO, matchAnswer("아니 괜찮아", yesNo))
    }

    @Test
    fun ignoresSpacesAndPunctuation() {
        assertEquals(NO, matchAnswer("  아 니 요 !", yesNo))
    }

    @Test
    fun longerPhraseWins_forNegatedInjury() {
        assertEquals(NOT_INJURED, matchAnswer("아무도 안 다쳤어", injury))
        assertEquals(INJURED, matchAnswer("한 명 다쳤어요", injury))
        assertEquals(NOT_INJURED, matchAnswer("다친 사람 없어", injury))
    }

    @Test
    fun unrelatedOrEmpty_isNull() {
        assertNull(matchAnswer("", yesNo))
        assertNull(matchAnswer("음악 틀어줘", yesNo))
        assertNull(matchAnswer("어디야", yesNo))
    }

    @Test
    fun singleSyllableAnswers_onlyWhenShort() {
        assertEquals(YES, matchAnswer("어", yesNo))
        assertEquals(YES, matchAnswer("네네", yesNo))
        assertEquals(YES, matchAnswer("응 맞아", yesNo))
    }
}
