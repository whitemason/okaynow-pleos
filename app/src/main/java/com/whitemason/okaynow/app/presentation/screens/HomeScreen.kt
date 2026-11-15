package com.whitemason.okaynow.app.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whitemason.okaynow.app.domain.detection.ImpactAssessment
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.domain.report.KNOWN_INSURERS
import com.whitemason.okaynow.app.domain.vehicle.DemoScenario
import com.whitemason.okaynow.app.domain.vehicle.VehicleStatus
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.GAP
import com.whitemason.okaynow.app.presentation.components.InfoTile
import com.whitemason.okaynow.app.presentation.components.LocalOnBack
import com.whitemason.okaynow.app.presentation.components.SplitPane
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TileShape
import com.whitemason.okaynow.app.presentation.theme.Palette

/**
 * C0 홈 + 사전 설정. 다른 단계처럼 위젯(큰 타일)으로 채운다.
 * 주행 중에는 수동 시작 버튼 하나와 음성 안내 타일만 보인다. 설정 타일은 P단에서만(CLAUDE.md 규칙 5).
 */
@Composable
fun HomeScreen(
    vehicle: VehicleStatus,
    simulation: Boolean,
    scenarios: List<DemoScenario>,
    playing: DemoScenario?,
    assessment: ImpactAssessment?,
    onManualStart: () -> Unit,
    onSimulationChange: (Boolean) -> Unit,
    onPlay: (DemoScenario) -> Unit,
    onOpenMonitor: () -> Unit,
    insurer: Insurer?,
    onSaveInsurer: (Insurer?) -> Unit,
    savedReports: Int,
    onClearReports: () -> Unit,
) {
    if (!vehicle.isParked) {
        StepScaffold(
            title = "사고 감지 중",
            actions = { BigButton("사고 났어요", onManualStart, icon = Icons.Filled.CarCrash) },
        ) {
            InfoTile(Tile("음성", "\"사고 났어\"라고\n말해도 돼요", icon = Icons.Filled.Mic), Modifier.fillMaxSize())
        }
        return
    }
    var editInsurer by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    if (showPrivacy) {
        val close = { showPrivacy = false }
        BackHandler(onBack = close)
        CompositionLocalProvider(LocalOnBack provides close) {
            PrivacyScreen(savedReports, onClearReports)
        }
        return
    }
    if (editInsurer) {
        val close = { editInsurer = false }
        BackHandler(onBack = close)
        CompositionLocalProvider(LocalOnBack provides close) {
            InsurerSettingsScreen(insurer, KNOWN_INSURERS, onSaveInsurer, onClose = close)
        }
        return
    }
    StepScaffold(
        title = "OkayNow",
        subtitle = "사고 순간, 안심 ON",
        wide = true,
        actions = { BigButton("사고 났어요", onManualStart, icon = Icons.Filled.CarCrash) },
    ) {
        SplitPane(firstWeight = 1.3f, secondWeight = 1f, first = { m ->
            // 설정 위젯 2×2: 사고 감지·보험사 / 음성·개인정보
            Column(m, verticalArrangement = Arrangement.spacedBy(GAP)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    InfoTile(
                        Tile("사고 감지", if (simulation) "시연 신호" else "켜짐", caption = assessment?.let { "충격 점수 ${it.score}" } ?: "충격 없음", icon = Icons.Filled.Sensors, compact = true),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                    InfoTile(
                        Tile("보험사", insurer?.name ?: "등록하기", caption = insurer?.phone ?: "눌러서 등록", icon = Icons.Filled.Shield, compact = true),
                        Modifier.weight(1f).fillMaxHeight(),
                        onClick = { editInsurer = true },
                    )
                }
                // 비상 연락처·감지 감도 칸은 동작하지 않아 뺐다(2026-11-10). 연락처 문자 발송은 미승인 권한이 필요해 PT 이후 검토
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    InfoTile(
                        Tile("음성", "\"사고 났어요\"라고\n말하면 시작해요", caption = "화면이 보일 때만 들어요", icon = Icons.Filled.Mic, compact = true),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                    InfoTile(
                        Tile("개인정보", "차 안에만 저장", caption = "눌러서 자세히", icon = Icons.Filled.Lock, compact = true),
                        Modifier.weight(1f).fillMaxHeight(),
                        onClick = { showPrivacy = true },
                    )
                }
            }
        }) { m ->
            SimulationTile(simulation, scenarios, playing, onSimulationChange, onPlay, onOpenMonitor, m)
        }
    }
}

/** 시연용 시뮬레이션 위젯: 켜면 시나리오 버튼이 나온다. 개발용 신호 모니터 링크는 아래에 작게. */
@Composable
private fun SimulationTile(
    simulation: Boolean,
    scenarios: List<DemoScenario>,
    playing: DemoScenario?,
    onSimulationChange: (Boolean) -> Unit,
    onPlay: (DemoScenario) -> Unit,
    onOpenMonitor: () -> Unit,
    modifier: Modifier,
) {
    Surface(color = Palette.Surface, shape = TileShape, modifier = modifier) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(52.dp))
                Text("시뮬레이션", style = MaterialTheme.typography.headlineMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Switch(checked = simulation, onCheckedChange = onSimulationChange)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (simulation) {
                    scenarios.forEach { scenario ->
                        OutlinedButton(onClick = { onPlay(scenario) }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                            Text(if (playing == scenario) "${scenario.label} (재생 중)" else scenario.label, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                } else {
                    Text("시연 영상용. 켜면 충돌 시나리오를 재생해요", style = MaterialTheme.typography.titleLarge, color = Palette.TextSecondary)
                }
            }
            TextButton(onClick = onOpenMonitor) { Text("신호 모니터 (개발용)", color = Palette.TextSecondary) }
        }
    }
}
