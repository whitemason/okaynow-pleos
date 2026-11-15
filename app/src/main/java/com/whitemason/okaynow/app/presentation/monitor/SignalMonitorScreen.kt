package com.whitemason.okaynow.app.presentation.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitemason.okaynow.app.domain.vehicle.SignalType
import com.whitemason.okaynow.app.domain.vehicle.VehicleStatus

/** 신호 확인 화면(개발용). C0에서 연다. */
@Composable
fun SignalMonitorScreen(onClose: () -> Unit, viewModel: SignalMonitorViewModel = hiltViewModel()) {
    val state by viewModel.status.collectAsStateWithLifecycle()
    val simulation by viewModel.simulation.collectAsStateWithLifecycle()
    val playing by viewModel.playing.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("차량 신호", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("닫기") }
                }
                SignalRow("속도", SignalType.SPEED, state, state.speedKmh?.let { "%.1f km/h".format(it) })
                SignalRow("브레이크 페달", SignalType.BRAKE_PEDAL, state, state.brakePedal?.let { if (it) "밟음" else "뗌" })
                SignalRow("비상등", SignalType.HAZARD_LIGHT, state, state.hazardLight?.let { if (it) "켜짐" else "꺼짐" })
                SignalRow("기어", SignalType.GEAR, state, state.gear?.name)
                SignalRow("도어", SignalType.DOOR_OPEN, state, state.doorOpen?.let { if (it) "열림" else "닫힘" })
                SignalRow(
                    "안전 경고", SignalType.SAFETY_WARNINGS, state,
                    state.safetyWarnings?.let { if (it.isEmpty()) "없음" else it.joinToString() },
                )
                SignalRow("타이어", SignalType.TIRE_STATUS, state, state.tireStatus?.name)
                SignalRow(
                    "외부 등화", SignalType.EXTERIOR_LIGHTS, state,
                    state.exteriorLights?.let { if (it.isEmpty()) "모두 꺼짐" else it.joinToString() },
                )
            }
            Column(modifier = Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시뮬레이션 모드", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Switch(checked = simulation, onCheckedChange = viewModel::setSimulation)
                }
                if (simulation) {
                    viewModel.scenarios.forEach { scenario ->
                        val label = if (playing == scenario) "${scenario.label} (재생 중)" else scenario.label
                        if (playing == scenario) {
                            Button(onClick = { viewModel.play(scenario) }, Modifier.fillMaxWidth()) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { viewModel.play(scenario) }, Modifier.fillMaxWidth()) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, type: SignalType, state: VehicleStatus, value: String?) {
    val shown = when {
        value != null -> value
        type in state.supported -> "대기 중"
        else -> "미지원 / 확인 중"
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(shown, style = MaterialTheme.typography.titleLarge)
    }
}
