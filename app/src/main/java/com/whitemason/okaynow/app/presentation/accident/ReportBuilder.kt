package com.whitemason.okaynow.app.presentation.accident

import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.report.AccidentReport
import com.whitemason.okaynow.app.presentation.screens.checkCards

/** 지금까지의 처리 기록으로 접수 원자료를 만든다. 시작 시각이 없으면 [nowMillis]를 쓴다. */
fun AccidentUiState.toReport(nowMillis: Long): AccidentReport {
    val cards = checkCards(vehicle)
    return AccidentReport(
        occurredAtMillis = startedAtMillis ?: nowMillis,
        location = location,
        injured = injured,
        emergencyGuided = emergencyGuided,
        secureSceneDone = secureSceneDone,
        vehicleIssues = cards.filter { it.warning }.map { "${it.label}: ${it.value}" },
        vehicleChecked = cards.map { it.label },
        damageZones = CarZone.entries.filter { it in damageZones },
        insurer = insurer,
        insurerDialed = insurerDialed,
    )
}
