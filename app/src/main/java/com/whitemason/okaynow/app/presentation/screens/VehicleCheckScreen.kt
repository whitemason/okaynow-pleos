package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import com.whitemason.okaynow.app.domain.vehicle.ExteriorLight
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning
import com.whitemason.okaynow.app.domain.vehicle.SignalType
import com.whitemason.okaynow.app.domain.vehicle.TireStatus
import com.whitemason.okaynow.app.domain.vehicle.VehicleStatus
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TileGrid

/** C5 차량 점검. 지원되는 신호만 보이고(CLAUDE.md 규칙 3), 이상 항목은 강조해 맨 위로 올린다. */
@Composable
fun VehicleCheckScreen(vehicle: VehicleStatus, onNext: () -> Unit, onEmergency: () -> Unit) {
    val cards = checkCards(vehicle).sortedByDescending { it.warning }
    StepScaffold(
        title = if (cards.any { it.warning }) "확인할 곳이 있어요" else "차량 이상 없음",
        onEmergency = onEmergency,
        actions = { BigButton("다음", onNext, icon = Icons.AutoMirrored.Filled.ArrowForward) },
    ) {
        TileGrid(cards.map { Tile(it.label, it.value, warning = it.warning, icon = it.label.checkIcon()) }.ifEmpty { listOf(Tile("차량 신호", "확인 불가", icon = Icons.Filled.DirectionsCar)) })
    }
}

private fun String.checkIcon() = when (this) {
    "타이어" -> Icons.Filled.TireRepair
    "도어" -> Icons.Filled.SensorDoor
    "비상등" -> Icons.Filled.Warning
    "외부 등화" -> Icons.Filled.Lightbulb
    "경고" -> Icons.Filled.ReportProblem
    else -> Icons.Filled.DirectionsCar
}

data class CheckCard(val label: String, val value: String, val warning: Boolean)

/** 지원되고 값을 받은 신호만 카드로 만든다. 값은 짧게, 이상이면 할 일을 붙인다. */
fun checkCards(v: VehicleStatus): List<CheckCard> = buildList {
    fun has(type: SignalType) = type in v.supported
    if (has(SignalType.TIRE_STATUS) && v.tireStatus != null) {
        val low = v.tireStatus == TireStatus.LOW_PRESSURE_WARNING
        add(CheckCard("타이어", if (low) "공기압 낮음 · 주행 금지" else "정상", low))
    }
    if (has(SignalType.DOOR_OPEN) && v.doorOpen != null) {
        add(CheckCard("도어", if (v.doorOpen) "열림" else "닫힘", v.doorOpen))
    }
    if (has(SignalType.HAZARD_LIGHT) && v.hazardLight != null) {
        add(CheckCard("비상등", if (v.hazardLight) "켜짐" else "꺼짐 · 켜세요", !v.hazardLight))
    }
    if (has(SignalType.EXTERIOR_LIGHTS) && v.exteriorLights != null) {
        add(CheckCard("외부 등화", if (v.exteriorLights.isEmpty()) "꺼짐" else v.exteriorLights.joinToString(" · ") { it.label() }, false))
    }
    if (has(SignalType.SAFETY_WARNINGS) && v.safetyWarnings != null) {
        val w = v.safetyWarnings
        add(CheckCard("경고", if (w.isEmpty()) "없음" else w.joinToString(" · ") { it.label() }, w.isNotEmpty()))
    }
}

private fun ExteriorLight.label() = when (this) {
    ExteriorLight.LOW_BEAM -> "하향등"
    ExteriorLight.HIGH_BEAM -> "상향등"
    ExteriorLight.TAIL_LAMP -> "미등"
    ExteriorLight.FRONT_FOG -> "앞 안개등"
    ExteriorLight.REAR_FOG -> "뒤 안개등"
}

private fun SafetyWarning.label() = when (this) {
    SafetyWarning.AIRBAG_DEPLOYED -> "에어백 전개"
    SafetyWarning.POWER_DOWN -> "엔진 이상"
    SafetyWarning.AIRBAG_TELLTALE -> "에어백 경고"
    SafetyWarning.ABS -> "ABS"
    SafetyWarning.ESC -> "ESC"
    SafetyWarning.MASTER_WARNING -> "마스터 경고"
}
