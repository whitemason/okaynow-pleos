package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.domain.report.AccidentReport
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.domain.report.ReportArchive
import com.whitemason.okaynow.app.domain.report.ReportSummary
import com.whitemason.okaynow.app.domain.report.SummaryWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C8 접수 요약(Gleo LLM → 템플릿 대체)·보관·보험사 전화, C9·처음으로 돌아올 때 기록 처리. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccidentStoreReportTest {

    private val samsung = Insurer("테스트 보험", "1588-0000")

    /** 다치지 않은 사고로 C8까지 버튼으로 간다(C6에서 앞 범퍼 왼쪽 선택). */
    private fun TestScope.reachContactInsurer(store: AccidentStore) {
        store.dispatch(AccidentIntent.ManualStart)
        store.dispatch(AccidentIntent.AnswerNo)
        store.dispatch(AccidentIntent.Next) // C4 완료
        store.dispatch(AccidentIntent.Next) // C5
        store.dispatch(AccidentIntent.ToggleZone(CarZone.entries.first()))
        store.dispatch(AccidentIntent.Next) // C6
        store.dispatch(AccidentIntent.Next) // C7
        runCurrent()
    }

    @Test
    fun contactInsurer_usesLlmSummary_andArchivesRawWithSummary() = runTest {
        val archived = mutableListOf<Pair<AccidentReport, ReportSummary>>()
        val prompts = mutableListOf<String>()
        val store = testStore(
            summaryWriter = SummaryWriter { prompts += it; "요약 문장입니다." },
            archive = ReportArchive { r, s -> archived += r to s },
        ).also { runCurrent() }

        reachContactInsurer(store)

        val state = store.state.value
        assertEquals(AccidentState.ContactInsurer, state.step)
        assertEquals(ReportSummary("요약 문장입니다.", ReportSummary.Source.GLEO_LLM), state.summary)
        val report = state.report!!
        assertEquals(WALL_START, report.occurredAtMillis)
        assertEquals(false, report.injured)
        assertFalse(report.emergencyGuided)
        assertTrue(report.secureSceneDone)
        assertEquals(listOf(CarZone.entries.first()), report.damageZones)
        assertEquals(SEOUL, report.location) // C3을 거치지 않아도 위치를 구한다
        assertFalse(state.summarizing)
        // 템플릿으로 먼저 보관하고, LLM 요약이 오면 다시 보관한다.
        assertEquals(listOf(ReportSummary.Source.TEMPLATE, ReportSummary.Source.GLEO_LLM), archived.map { it.second.source })
        assertEquals(report to state.summary, archived.last())
        assertTrue(prompts.single().contains("\"injury\":\"no\""))
    }

    @Test
    fun templateShownFirst_whileLlmIsSlow() = runTest {
        val store = testStore(summaryWriter = SummaryWriter { kotlinx.coroutines.delay(10_000); "늦은 요약" }).also { runCurrent() }

        reachContactInsurer(store)
        assertEquals(ReportSummary.Source.TEMPLATE, store.state.value.summary?.source)
        assertTrue(store.state.value.summarizing)

        testScheduler.advanceTimeBy(10_001)
        runCurrent()
        assertEquals(ReportSummary("늦은 요약", ReportSummary.Source.GLEO_LLM), store.state.value.summary)
        assertFalse(store.state.value.summarizing)
    }

    @Test
    fun llmFailure_fallsBackToTemplate() = runTest {
        val store = testStore(summaryWriter = SummaryWriter { null }).also { runCurrent() }

        reachContactInsurer(store)

        val summary = store.state.value.summary!!
        assertEquals(ReportSummary.Source.TEMPLATE, summary.source)
        assertTrue(summary.text.contains("다친 사람 없음"))
    }

    @Test
    fun callInsurer_opensDialerWithRegisteredNumber_andIsRecorded() = runTest {
        val archived = mutableListOf<AccidentReport>()
        val store = testStore(insurers = MemoryInsurers(samsung), archive = ReportArchive { r, _ -> archived += r }).also { runCurrent() }
        val effects = mutableListOf<AccidentUiEffect>()
        backgroundScope.launch { store.uiEffects.collect { effects += it } }
        reachContactInsurer(store)

        store.dispatch(AccidentIntent.CallInsurer)
        store.dispatch(AccidentIntent.Next) // C9
        runCurrent()

        assertEquals(listOf<AccidentUiEffect>(AccidentUiEffect.OpenDialer("15880000")), effects)
        assertEquals(AccidentState.Done, store.state.value.step)
        assertTrue(store.state.value.report!!.insurerDialed)
        assertTrue(archived.last().insurerDialed) // C9에서 다시 보관
    }

    @Test
    fun callInsurer_withoutRegistration_isIgnored() = runTest {
        val store = testStore().also { runCurrent() }
        val effects = mutableListOf<AccidentUiEffect>()
        backgroundScope.launch { store.uiEffects.collect { effects += it } }
        reachContactInsurer(store)

        store.dispatch(AccidentIntent.CallInsurer)
        runCurrent()

        assertTrue(effects.isEmpty())
        assertFalse(store.state.value.insurerDialed)
    }

    @Test
    fun saveInsurer_updatesState() = runTest {
        val store = testStore().also { runCurrent() }

        store.dispatch(AccidentIntent.SaveInsurer(samsung))
        runCurrent()

        assertEquals(samsung, store.state.value.insurer)
    }

    @Test
    fun finish_clearsAccidentRecord_butKeepsInsurer() = runTest {
        val store = testStore(insurers = MemoryInsurers(samsung)).also { runCurrent() }
        reachContactInsurer(store)
        store.dispatch(AccidentIntent.Next)
        store.dispatch(AccidentIntent.Finish)
        runCurrent()

        val state = store.state.value
        assertEquals(AccidentState.Idle, state.step)
        assertNull(state.report)
        assertNull(state.startedAtMillis)
        assertNull(state.injured)
        assertTrue(state.damageZones.isEmpty())
        assertEquals(samsung, state.insurer)
    }

    @Test
    fun injuredAnswer_andEmergency_areRecorded() = runTest {
        val store = testStore().also { runCurrent() }
        store.dispatch(AccidentIntent.ManualStart)
        store.dispatch(AccidentIntent.AnswerYes)
        runCurrent()

        assertEquals(true, store.state.value.injured)
        assertTrue(store.state.value.emergencyGuided)
    }

    /** 사고 시각별 파일처럼 기록을 쌓는 보관소(같은 사고는 덮어쓴다, FileReportArchive와 같게). */
    private class MemoryArchive(initial: Int) : ReportArchive {
        private val keys = (1..initial).map { -it.toLong() }.toMutableSet()
        val files get() = keys.size
        override suspend fun save(report: AccidentReport, summary: ReportSummary) { keys += report.occurredAtMillis }
        override suspend fun count(): Int = keys.size
        override suspend fun clear() { keys.clear() }
    }

    @Test
    fun savedReports_countedAtStart_andAfterArchive() = runTest {
        val archive = MemoryArchive(initial = 2)
        val store = testStore(archive = archive).also { runCurrent() }
        assertEquals(2, store.state.value.savedReports)

        reachContactInsurer(store)
        runCurrent()
        assertEquals(3, store.state.value.savedReports)

        // 처음(C0)으로 돌아와도 기록 수는 남는다(C0 개인정보 안내에서 보여 준다).
        store.dispatch(AccidentIntent.Next) // C9
        store.dispatch(AccidentIntent.Finish)
        runCurrent()
        assertEquals(AccidentState.Idle, store.state.value.step)
        assertEquals(3, store.state.value.savedReports)
    }

    @Test
    fun clearReports_deletesAll_andResetsCount() = runTest {
        val archive = MemoryArchive(initial = 3)
        val store = testStore(archive = archive).also { runCurrent() }

        store.dispatch(AccidentIntent.ClearReports)
        runCurrent()

        assertEquals(0, archive.files)
        assertEquals(0, store.state.value.savedReports)
    }
}
