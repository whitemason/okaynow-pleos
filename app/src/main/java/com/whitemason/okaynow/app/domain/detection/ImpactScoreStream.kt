package com.whitemason.okaynow.app.domain.detection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** [impactScores]가 내는 점수 이벤트. */
sealed interface ImpactScoreEvent {
    /** 신호 하나를 반영한 뒤의 평가. 진행 중인 에피소드가 없으면 null. */
    data class Scored(val assessment: ImpactAssessment?) : ImpactScoreEvent

    /** 에피소드가 확인 임계값을 처음 넘었다(또는 에어백 전개). 에피소드당 한 번. */
    data class Triggered(val assessment: ImpactAssessment) : ImpactScoreEvent

    /** 이미 트리거된 에피소드의 점수가 더 올랐다. */
    data class Escalated(val assessment: ImpactAssessment) : ImpactScoreEvent
}

/** 신호 스트림을 [ImpactScorer]로 점수화해 점수 이벤트 스트림으로 바꾼다. */
fun Flow<SignalEvent>.impactScores(config: ImpactConfig = ImpactConfig()): Flow<ImpactScoreEvent> = flow {
    val scorer = ImpactScorer(config)
    var triggeredEpisode: Long? = null
    var triggeredScore = 0
    collect { event ->
        val assessment = scorer.onEvent(event)
        emit(ImpactScoreEvent.Scored(assessment))
        if (assessment == null) return@collect
        if (!assessment.airbagDeployed && assessment.score < config.confirmThreshold) return@collect
        if (triggeredEpisode != assessment.episodeId) {
            triggeredEpisode = assessment.episodeId
            triggeredScore = assessment.score
            emit(ImpactScoreEvent.Triggered(assessment))
        } else if (assessment.score > triggeredScore) {
            triggeredScore = assessment.score
            emit(ImpactScoreEvent.Escalated(assessment))
        }
    }
}
