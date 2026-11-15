package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.data.vehicle.FakeSignalSource
import com.whitemason.okaynow.app.domain.common.Clock
import com.whitemason.okaynow.app.domain.detection.SignalEvent
import com.whitemason.okaynow.app.domain.detection.signalEvents
import com.whitemason.okaynow.app.domain.location.LocationFix
import com.whitemason.okaynow.app.domain.location.LocationSource
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.domain.report.InsurerRepository
import com.whitemason.okaynow.app.domain.report.ReportArchive
import com.whitemason.okaynow.app.domain.report.SummaryWriter
import com.whitemason.okaynow.app.domain.vehicle.DemoScenario
import com.whitemason.okaynow.app.domain.vehicle.SignalSourceRepository
import com.whitemason.okaynow.app.domain.vehicle.VehicleSignalSource
import com.whitemason.okaynow.app.domain.voice.VoiceAnswer
import com.whitemason.okaynow.app.domain.voice.VoiceChoice
import com.whitemason.okaynow.app.domain.voice.VoiceGuide
import com.whitemason.okaynow.app.domain.voice.VoiceState
import com.whitemason.okaynow.app.domain.voice.matchAnswer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

/** 시연 신호원 하나만 쓰는 저장소. */
internal class TestSignals(source: VehicleSignalSource) : SignalSourceRepository {
    override val active = MutableStateFlow(source)
    override val simulation = MutableStateFlow(true)
    override val scenarios = emptyList<DemoScenario>()
    override val playing = MutableStateFlow<DemoScenario?>(null)
    override fun setSimulation(enabled: Boolean) { simulation.value = enabled }
    override fun play(scenario: DemoScenario) = Unit
}

internal class FixedLocation(private val fix: LocationFix?) : LocationSource {
    override suspend fun current() = fix
}

/**
 * 들을 때마다 [answers]에서 답을 하나 꺼낸다. 비었으면 대답 없이 취소될 때까지 기다린다.
 * 답은 [say]처럼 지금 기대하는 선택지를 받아 고른다.
 */
internal class ScriptedVoice(private val answers: MutableList<(List<VoiceChoice>) -> VoiceAnswer> = mutableListOf()) : VoiceGuide {
    override val state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val spoken = mutableListOf<String>()
    /** 들을 때마다 기대한 선택지 id들. */
    val listened = mutableListOf<List<String>>()

    override suspend fun speak(text: String) {
        spoken += text
    }

    override suspend fun listen(expected: List<VoiceChoice>): VoiceAnswer {
        listened += expected.map { it.id }
        val answer = answers.removeFirstOrNull()?.invoke(expected) ?: awaitCancellation()
        delay(500) // 말하는 데 걸리는 시간
        return answer
    }
}

/** [text]라고 말했을 때 실제와 같은 규칙([matchAnswer])으로 알아듣는다. 못 알아들으면 NoResponse. */
internal fun say(text: String): (List<VoiceChoice>) -> VoiceAnswer = { choices ->
    matchAnswer(text, choices)?.let { VoiceAnswer.Matched(it, text) } ?: VoiceAnswer.NoResponse
}

internal val SEOUL = LocationFix(37.56650, 126.97800, 0)

/** 2026-11-05 15:10 KST(벽시계 시작값). */
internal const val WALL_START = 1_793_859_000_000L

internal class MemoryInsurers(initial: Insurer? = null) : InsurerRepository {
    override val insurer = MutableStateFlow(initial)
    override suspend fun save(insurer: Insurer?) { this.insurer.value = insurer }
}

/** 가상 시간으로 도는 Store. 신호원은 [source](기본: 주차 상태로 멈춰 있는 시연 신호원). */
internal fun TestScope.testStore(
    voice: VoiceGuide = ScriptedVoice(),
    location: LocationFix? = SEOUL,
    source: VehicleSignalSource = FakeSignalSource(backgroundScope) { testScheduler.currentTime },
    transform: (Flow<SignalEvent>) -> Flow<SignalEvent> = { it },
    insurers: InsurerRepository = MemoryInsurers(),
    summaryWriter: SummaryWriter = SummaryWriter { null },
    archive: ReportArchive = ReportArchive { _, _ -> },
): AccidentStore {
    val clock = Clock { testScheduler.currentTime }
    return AccidentStore(
        scope = backgroundScope,
        signals = TestSignals(source),
        voice = voice,
        location = FixedLocation(location),
        clock = clock,
        logger = {},
        events = { transform(it.signalEvents(clock::nowMillis)) },
        insurers = insurers,
        summaryWriter = summaryWriter,
        archive = archive,
        wallClock = { WALL_START + testScheduler.currentTime },
    )
}
