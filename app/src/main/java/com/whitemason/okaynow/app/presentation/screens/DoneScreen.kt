package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whitemason.okaynow.app.BuildConfig
import com.whitemason.okaynow.app.domain.report.AccidentReport
import com.whitemason.okaynow.app.domain.report.ReportSummary
import com.whitemason.okaynow.app.domain.report.reportUrl
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.QrCode
import com.whitemason.okaynow.app.presentation.components.SplitPane
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TileGrid
import com.whitemason.okaynow.app.presentation.components.TileShape
import com.whitemason.okaynow.app.presentation.theme.Palette

/**
 * C9 완료. 처리 체크리스트와, 같은 기록(원자료 + 요약)을 폰으로 넘기는 QR.
 * QR은 폰 페이지 report.html(whitemason/okaynow-pages)을 열고 기록은 주소의 # 뒤에만 담는다(서버로 보내지 않음).
 */
@Composable
fun DoneScreen(report: AccidentReport?, summary: ReportSummary?, onFinish: () -> Unit) {
    StepScaffold(
        title = "모두 마쳤어요",
        subtitle = "QR을 폰으로 찍으면 처리 기록을 받아요",
        wide = true,
        actions = { BigButton("처음으로", onFinish, icon = Icons.Filled.Home) },
    ) {
        SplitPane(firstWeight = 1.7f, secondWeight = 1f, first = { m -> TileGrid(checklist(report), m) }) { m ->
            val url = remember(report, summary) { if (report != null && summary != null) reportUrl(BuildConfig.GUIDE_BASE_URL, report, summary) else null }
            Surface(color = Palette.Surface, shape = TileShape, modifier = m) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    if (url != null) {
                        QrCode(url, Modifier.weight(1f, fill = false).aspectRatio(1f), description = "처리 기록 QR")
                        Text("폰 카메라로 찍으세요", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text(QR_PRIVACY_NOTE, style = MaterialTheme.typography.titleLarge, color = Palette.TextSecondary, textAlign = TextAlign.Center)
                    } else {
                        Text("처리 기록이 없어요", style = MaterialTheme.typography.headlineSmall, color = Palette.TextSecondary)
                    }
                }
            }
        }
    }
}

/** 단계별 처리 결과. 기록이 없으면(C8을 거치지 않음) "-". */
private fun checklist(r: AccidentReport?): List<Tile> = listOf(
    Tile("부상 확인", r?.injuryText ?: "-", icon = Icons.Filled.PersonalInjury, compact = true),
    Tile("119 안내", if (r?.emergencyGuided == true) "안내받음" else "받지 않음", icon = Icons.Filled.LocalHospital, compact = true),
    Tile("안전 확보", if (r?.secureSceneDone == true) "완료" else "-", icon = Icons.Filled.Warning, compact = true),
    Tile("차량 점검", r?.vehicleText ?: "-", warning = r?.vehicleIssues?.isNotEmpty() == true, icon = Icons.Filled.DirectionsCar, compact = true),
    Tile("파손 부위", r?.damageZones?.size?.takeIf { it > 0 }?.let { "${it}곳" } ?: "-", icon = Icons.Filled.CarCrash, compact = true),
    Tile("보험사 연락", if (r?.insurerDialed == true) "전화 연결함" else "하지 않음", icon = Icons.Filled.Call, compact = true),
)
