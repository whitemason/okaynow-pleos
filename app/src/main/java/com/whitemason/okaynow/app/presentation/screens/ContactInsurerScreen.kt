package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitemason.okaynow.app.domain.report.AccidentReport
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.InfoTile
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TileGrid
import com.whitemason.okaynow.app.presentation.components.TileShape
import com.whitemason.okaynow.app.presentation.theme.Palette

/**
 * C8 보험사 접수. "보험사 전화"는 등록한 번호로 다이얼 화면만 연다(발신은 운전자가, CLAUDE.md 규칙 4).
 * 에뮬레이터 확인(2026-11-05): 다이얼 인텐트로 Pleos 전화 앱이 번호를 채워 열린다. 통화는 블루투스 폰 연결이 필요하다.
 * 연결이 안 돼도 휴대폰으로 걸 수 있게 번호와, 상담원이 묻는 것(시각·장소·대인/대물·경위)을 늘 크게 보여 준다.
 * 통화 가이드(2026-11-10 대장): 문장을 대신 읽어 주기보다 무엇을 말할지 순서만 보여 준다. 알고 있는 값은 아래 칸에.
 * Gleo AI 요약 문장은 C9 QR 기록(폰 페이지)에만 쓴다.
 */
@Composable
fun ContactInsurerScreen(
    report: AccidentReport?,
    insurer: Insurer?,
    onCallInsurer: () -> Unit,
    onNext: () -> Unit,
    onEmergency: () -> Unit,
) {
    StepScaffold(
        title = "보험사에 알리세요",
        subtitle = insurer?.let { "${it.name} ${it.phone}" } ?: "보험사 미등록 · 처음 화면 설정에서 등록해요",
        onEmergency = onEmergency,
        wide = true,
        actions = {
            BigButton("보험사 전화", onCallInsurer, primary = false, enabled = insurer != null, caption = insurer?.phone ?: "번호 미등록", icon = Icons.Filled.Call)
            BigButton("접수 완료", onNext, icon = Icons.Filled.Check)
        },
    ) {
        if (report == null) {
            InfoTile(Tile("접수 내용", "정리하는 중…", icon = Icons.Filled.Description), Modifier.fillMaxSize())
            return@StepScaffold
        }
        CallGuideTile(Modifier.weight(1f).fillMaxWidth())
        // 가이드 1~3번에 바로 답할 값. 4번(경위)의 참고로 파손 부위.
        TileGrid(
            listOf(
                Tile("시각", report.occurredAtText, icon = Icons.Filled.Schedule, compact = true),
                Tile("장소", report.locationText ?: "확인 못 함", caption = MAP_GUIDE, icon = Icons.Filled.LocationOn, compact = true),
                Tile("다친 사람", personText(report.injured), warning = report.injured == true, icon = Icons.Filled.PersonalInjury, compact = true),
                Tile("파손 부위", report.damageText ?: "고르지 않음", icon = Icons.Filled.CarCrash, compact = true),
            ),
            Modifier.weight(1.2f).fillMaxWidth(),
        )
    }
}

/** 상담원에게 말할 순서. 사고 접수 때 보험사가 묻는 4가지. */
val CALL_GUIDE = listOf(
    "사고 시각" to "언제 났는지",
    "사고 장소" to "어디서 났는지",
    "대인 · 대물" to "다친 사람이 있는지, 차·물건만 부서졌는지",
    "사고 경위" to "어떻게 부딪혔는지 직접 설명해요",
)

/** 다친 사람 칸: 대인/대물 구분을 함께. C2 답이 없으면 확인 못 함. */
private fun personText(injured: Boolean?) = when (injured) {
    true -> "있음 · 대인 사고"
    false -> "없음 · 대물 사고"
    null -> "확인 못 함"
}

/** 통화 가이드: 무엇을 어떤 순서로 말할지만. */
@Composable
private fun CallGuideTile(modifier: Modifier) {
    Surface(color = Palette.Surface, shape = TileShape, modifier = modifier) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SupportAgent, null, Modifier.size(40.dp))
                Text("통화 가이드", style = MaterialTheme.typography.headlineMedium, color = Palette.TextSecondary)
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                CALL_GUIDE.forEachIndexed { i, (what, how) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp), fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                        Text(what, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp), fontWeight = FontWeight.Bold, modifier = Modifier.width(250.dp))
                        Text(how, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp), color = Palette.TextSecondary)
                    }
                }
            }
        }
    }
}
