package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.common.Clock
import com.whitemason.okaynow.app.domain.common.FlowLogger
import com.whitemason.okaynow.app.domain.detection.ImpactConfig
import com.whitemason.okaynow.app.domain.detection.ImpactScoreEvent
import com.whitemason.okaynow.app.domain.detection.SignalEvent
import com.whitemason.okaynow.app.domain.detection.impactScores
import com.whitemason.okaynow.app.domain.detection.signalEvents
import com.whitemason.okaynow.app.domain.location.LocationSource
import com.whitemason.okaynow.app.domain.location.spoken
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.domain.report.InsurerRepository
import com.whitemason.okaynow.app.domain.report.ReportArchive
import com.whitemason.okaynow.app.domain.report.ReportSummary
import com.whitemason.okaynow.app.domain.report.SummaryWriter
import com.whitemason.okaynow.app.domain.report.llmPrompt
import com.whitemason.okaynow.app.domain.report.templateSummary
import com.whitemason.okaynow.app.domain.vehicle.SignalSourceRepository
import com.whitemason.okaynow.app.domain.vehicle.VehicleSignalSource
import com.whitemason.okaynow.app.domain.vehicle.status
import com.whitemason.okaynow.app.domain.voice.VoiceAnswer
import com.whitemason.okaynow.app.domain.voice.VoiceGuide
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 앱 전체에서 하나인 MVI Store. [DetectionService]가 프로세스를 살려 두는 동안 감시가 이어지고,
 * 화면(AccidentViewModel)은 같은 [state]를 보여 준다.
 *
 * - 상태는 [dispatch] → [AccidentReducer]로만 바뀐다.
 * - Reducer가 낸 [AccidentSideEffect]를 여기서 실행한다(음성·타이머·위치·신호원 전환).
 *   결과는 다시 [AccidentIntent]로 들어온다.
 * - 차량 신호·음성 상태·시뮬레이션 설정 스트림도 Intent로 바꿔 넣는다.
 *
 * 상태 변경은 한 스레드([scope]의 디스패처)에서만 일어난다고 가정한다. Android에 의존하지 않는다.
 */
@Singleton
class AccidentStore internal constructor(
    private val scope: CoroutineScope,
    private val signals: SignalSourceRepository,
    private val voice: VoiceGuide,
    private val location: LocationSource,
    private val clock: Clock,
    private val logger: FlowLogger,
    private val reducer: AccidentReducer = AccidentReducer(),
    private val impactConfig: ImpactConfig = ImpactConfig(),
    /** 신호원 → 신호 이벤트. 테스트에서 일부 신호를 뺄 때 바꾼다. */
    private val events: (VehicleSignalSource) -> Flow<SignalEvent> = { it.signalEvents(clock::nowMillis) },
    private val insurers: InsurerRepository = NoInsurers,
    /** C8 접수 요약(Gleo LLM). null을 돌려주면 템플릿 문장을 쓴다. */
    private val summaryWriter: SummaryWriter = SummaryWriter { null },
    private val archive: ReportArchive = ReportArchive { _, _ -> },
    /** 접수 기록용 벽시계(epoch ms). [clock]은 신호용 단조 시계라 따로 둔다. */
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        signals: SignalSourceRepository,
        voice: VoiceGuide,
        location: LocationSource,
        clock: Clock,
        logger: FlowLogger,
        insurers: InsurerRepository,
        summaryWriter: SummaryWriter,
        archive: ReportArchive,
    ) : this(
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), signals, voice, location, clock, logger,
        insurers = insurers, summaryWriter = summaryWriter, archive = archive,
    )

    private val _state = MutableStateFlow(AccidentUiState(scenarios = signals.scenarios))
    val state: StateFlow<AccidentUiState> = _state.asStateFlow()

    private val _uiEffects = MutableSharedFlow<AccidentUiEffect>(extraBufferCapacity = 8)
    val uiEffects: SharedFlow<AccidentUiEffect> = _uiEffects.asSharedFlow()

    private var voiceJob: Job? = null
    private var timerJob: Job? = null
    private var reportJob: Job? = null

    init {
        // 시뮬레이션 모드를 바꾸면 이전 신호원 감시를 취소하고 새 신호원을 감시한다.
        // TODO(verify-sdk): CarPropertyValue.timestamp가 elapsedRealtimeNanos 기준인지 실차에서 확인.
        scope.launch {
            signals.active.collectLatest { source ->
                coroutineScope {
                    launch { source.status().collect { dispatch(AccidentIntent.VehicleChanged(it)) } }
                    events(source).impactScores(impactConfig).collect { dispatch(it.toIntent()) }
                }
            }
        }
        scope.launch { voice.state.collect { dispatch(AccidentIntent.VoiceChanged(it)) } }
        scope.launch { insurers.insurer.collect { dispatch(AccidentIntent.InsurerChanged(it)) } }
        scope.launch { countReports() }
        scope.launch {
            combine(signals.simulation, signals.playing) { sim, playing -> AccidentIntent.SimulationChanged(sim, playing) }
                .collect(::dispatch)
        }
    }

    fun dispatch(intent: AccidentIntent) {
        val before = _state.value
        val (after, effects) = reducer.reduce(before, intent)
        _state.value = after
        if (after.step != before.step) logger.log("$intent: ${before.step} → ${after.step} (gear=${after.vehicle.gear})")
        effects.forEach(::run)
    }

    private fun run(effect: AccidentSideEffect) {
        when (effect) {
            AccidentSideEffect.CancelVoice -> voiceJob?.cancel().also { voiceJob = null }
            AccidentSideEffect.CancelTimer -> timerJob?.cancel().also { timerJob = null }
            is AccidentSideEffect.StartNoResponseTimer -> {
                timerJob?.cancel()
                timerJob = scope.launch { countDown(effect.millis) }
            }
            // 말하는 중이면 말이 끝난 뒤 voiceTask가 이어서 듣는다.
            AccidentSideEffect.Listen -> if (voiceJob?.isActive != true) voiceTask {}
            is AccidentSideEffect.Speak -> voiceTask { voice.speak(effect.text) }
            AccidentSideEffect.EmergencyGuide -> voiceTask {
                coroutineScope {
                    // 위치는 안내를 읽는 동안 미리 구해 화면에 바로 보이게 한다.
                    val fix = async { location.current().also { dispatch(AccidentIntent.LocationResolved(it)) } }
                    voice.speak(VoicePrompts.EMERGENCY_GUIDE)
                    voice.speak(fix.await()?.spoken() ?: VoicePrompts.LOCATION_UNKNOWN)
                }
            }
            AccidentSideEffect.ReadLocation -> voiceTask {
                val fix = location.current().also { dispatch(AccidentIntent.LocationResolved(it)) }
                voice.speak(fix?.spoken() ?: VoicePrompts.LOCATION_UNKNOWN)
            }
            // TODO: data 계층에 오탐 로그를 남긴다(감도 튜닝용).
            is AccidentSideEffect.LogFalsePositive -> logger.log("effect: $effect")
            AccidentSideEffect.StampStart -> dispatch(AccidentIntent.Started(wallClock()))
            AccidentSideEffect.PrepareReport -> {
                reportJob?.cancel()
                reportJob = scope.launch { prepareReport() }
            }
            is AccidentSideEffect.ArchiveReport -> scope.launch {
                runCatching { archive.save(effect.report, effect.summary) }.onFailure { logger.log("archive failed: $it") }
                countReports()
            }
            AccidentSideEffect.ClearReports -> scope.launch {
                runCatching { archive.clear() }.onFailure { logger.log("clear reports failed: $it") }
                countReports()
            }
            is AccidentSideEffect.SaveInsurer -> scope.launch { insurers.save(effect.insurer) }
            is AccidentSideEffect.SetSimulation -> signals.setSimulation(effect.enabled)
            is AccidentSideEffect.PlayScenario -> signals.play(effect.scenario)
            is AccidentSideEffect.Ui -> _uiEffects.tryEmit(effect.effect)
        }
    }

    private suspend fun countReports() {
        runCatching { archive.count() }.onSuccess { dispatch(AccidentIntent.ReportsCounted(it)) }
    }

    /**
     * 음성은 한 번에 하나: 새 안내가 오면 하던 것을 멈춘다.
     * 안내를 마치면 지금 단계의 버튼 이름을 듣는다([listenForCommands]). C0처럼 들을 말이 없으면 끝난다.
     */
    private fun voiceTask(block: suspend CoroutineScope.() -> Unit) {
        voiceJob?.cancel()
        voiceJob = scope.launch {
            block()
            listenForCommands()
        }
    }

    /**
     * 버튼 이름을 알아들으면 그 버튼의 Intent를 보낸다. 단계가 바뀌거나 새 안내가 시작되면
     * dispatch 안에서 이 코루틴이 취소되므로 거기서 끝나고, 그렇지 않으면(부위 미선택 "촬영 완료" 등) 계속 듣는다.
     * 무응답 판단은 타이머가 한다.
     */
    private suspend fun listenForCommands() {
        while (true) {
            currentCoroutineContext().ensureActive()
            val commands = _state.value.voiceCommands()
            if (commands.isEmpty()) return
            when (val answer = voice.listen(commands.map { it.choice })) {
                is VoiceAnswer.Matched -> {
                    val command = commands.firstOrNull { it.choice == answer.choice } ?: continue
                    logger.log("voice \"${answer.heard}\" → ${command.intent}")
                    dispatch(command.intent)
                }
                VoiceAnswer.NoResponse -> Unit
                VoiceAnswer.Unavailable -> return
            }
        }
    }

    /**
     * C8 접수 원자료와 요약. 위치는 C3을 거치지 않았으면 여기서 구한다.
     * 요약은 Gleo LLM이 만들고, 실패하거나 늦으면 템플릿 문장을 쓴다(어느 쪽인지 [ReportSummary.source]에 남긴다).
     */
    private suspend fun prepareReport() {
        if (_state.value.location == null) dispatch(AccidentIntent.LocationResolved(location.current()))
        val report = _state.value.toReport(wallClock())
        // 템플릿 문장을 먼저 보여 주고(LLM은 길면 20초), LLM 요약이 오면 바꾼다.
        dispatch(AccidentIntent.ReportReady(report, ReportSummary(report.templateSummary(), ReportSummary.Source.TEMPLATE), final = false))
        val text = runCatching { summaryWriter.summarize(report.llmPrompt()) }.getOrNull()
        logger.log("report summary: ${if (text != null) "gleo-llm" else "template (llm unavailable)"}")
        if (text != null) dispatch(AccidentIntent.ReportReady(report, ReportSummary(text, ReportSummary.Source.GLEO_LLM)))
        else dispatch(AccidentIntent.ReportReady(report, ReportSummary(report.templateSummary(), ReportSummary.Source.TEMPLATE)))
    }

    private suspend fun countDown(millis: Long) {
        var remaining = millis
        while (remaining > 0) {
            dispatch(AccidentIntent.TimerTick(((remaining + 999) / 1000).toInt()))
            val step = minOf(1_000L, remaining)
            delay(step)
            remaining -= step
        }
        dispatch(AccidentIntent.TimerTick(null))
        dispatch(AccidentIntent.NoResponse)
    }

    private fun ImpactScoreEvent.toIntent(): AccidentIntent = when (this) {
        is ImpactScoreEvent.Scored -> AccidentIntent.ImpactScored(assessment)
        is ImpactScoreEvent.Triggered -> AccidentIntent.ImpactTriggered(assessment)
        is ImpactScoreEvent.Escalated -> AccidentIntent.ImpactEscalated(assessment)
    }
}

/** 보험사를 등록하지 않은 저장소(테스트·기본값). */
private object NoInsurers : InsurerRepository {
    override val insurer: Flow<Insurer?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun save(insurer: Insurer?) = Unit
}
