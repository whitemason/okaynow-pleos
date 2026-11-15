package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whitemason.okaynow.app.BuildConfig
import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.damage.ShotKind
import com.whitemason.okaynow.app.domain.damage.encodeSteps
import com.whitemason.okaynow.app.domain.damage.planShots
import com.whitemason.okaynow.app.domain.damage.shotCountText
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.GAP
import com.whitemason.okaynow.app.presentation.components.InfoTile
import com.whitemason.okaynow.app.presentation.components.QrCode
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.SplitPane
import com.whitemason.okaynow.app.presentation.components.LocalPortrait
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TileShape
import com.whitemason.okaynow.app.presentation.components.TopViewCar
import com.whitemason.okaynow.app.presentation.components.CAMERA_MARGIN
import com.whitemason.okaynow.app.presentation.components.COMPACT_CAMERA_MARGIN
import com.whitemason.okaynow.app.presentation.theme.Palette
import java.net.URLEncoder

/**
 * C6 충돌 부위 선택 + 촬영 순서. 운전자가 차 밖에서 찍으므로 차 화면은 한눈에 보는 동선만 보이고,
 * 단계별 안내는 QR(오른쪽 버튼 칸에 크게)로 연 폰 페이지(whitemason/okaynow-pages)가 맡는다. 사진은 폰에만 저장한다(앱으로 받지 않음).
 */
@Composable
fun PhotoCaptureScreen(
    zones: Set<CarZone>,
    onToggleZone: (CarZone) -> Unit,
    onNext: () -> Unit,
    onEmergency: () -> Unit,
) {
    val steps = remember(zones) { planShots(zones) }
    var highlighted by remember(zones) { mutableStateOf<Int?>(null) }
    StepScaffold(
        title = if (zones.isEmpty()) "부딪힌 곳을 누르세요" else "이 순서로 찍으세요",
        onEmergency = onEmergency,
        wide = true,
        actions = {
            // 폰이 멀리서도 읽게 QR을 버튼 칸 크기로 크게(2026-11-10). 음성 요약은 "음성 안내"·"읽어줘"라고 말하면 읽는다.
            GuideQrTile(steps.takeIf { it.isNotEmpty() }?.let { guideUrl(encodeSteps(it)) })
            BigButton("촬영 완료", onNext, enabled = steps.isNotEmpty(), caption = steps.takeIf { it.isNotEmpty() }?.let(::shotCountText), icon = Icons.Filled.CameraAlt)
        },
    ) {
        val portrait = LocalPortrait.current // StepScaffold 안에서 읽어야 창 방향이 들어온다.
        // 차 그림을 넓게(놀란 상황에서도 부위를 쉽게 누르도록). 세로 창이면 차 그림 위, 순서 목록 아래.
        SplitPane(modifier = Modifier.weight(1f).fillMaxWidth(), firstWeight = if (portrait) 2.6f else 1.3f, first = { m ->
            Box(m, contentAlignment = Alignment.Center) {
                // 세로 창은 여백을 줄여 차를 키운다(부위를 고르기 전후로 같은 여백이라 크기가 바뀌지 않는다).
                TopViewCar(selected = zones, onZoneTap = onToggleZone, steps = steps, highlightedStep = highlighted, margin = if (portrait) COMPACT_CAMERA_MARGIN else CAMERA_MARGIN)
            }
        }) { m ->
            Column(
                modifier = m.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (steps.isEmpty()) {
                    InfoTile(Tile("안내", "여러 곳 선택 가능", icon = Icons.Filled.TouchApp), Modifier.fillMaxWidth())
                    return@Column
                }
                steps.forEach { step ->
                    val on = highlighted == step.number
                    Surface(
                        color = if (on) Palette.SurfaceAccent else Palette.Surface,
                        shape = TileShape,
                        modifier = Modifier.fillMaxWidth().clickable { highlighted = if (on) null else step.number },
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${step.number}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                            Icon(step.kind.icon(), contentDescription = null, modifier = Modifier.padding(end = 16.dp).size(36.dp))
                            Text(step.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            Text(step.distanceText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 버튼 칸에 들어가는 큰 QR. 가로 창은 QR 위·문구 아래, 세로 창(버튼 칸이 낮음)은 QR 왼쪽·문구 오른쪽.
 * [url]이 null이면(부위를 아직 안 고름) 안내 문구만.
 */
@Composable
private fun ColumnScope.GuideQrTile(url: String?) {
    Surface(color = Palette.Surface, shape = TileShape, modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (url == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("부위를 고르면\nQR이 나와요", style = MaterialTheme.typography.headlineMedium, color = Palette.TextSecondary, textAlign = TextAlign.Center)
            }
            return@Surface
        }
        val caption = "QR을 찍으면\n폰에서 안내해요"
        if (LocalPortrait.current) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                QrCode(url, Modifier.fillMaxHeight().aspectRatio(1f))
                Text(caption, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(url, Modifier.weight(1f, fill = false).aspectRatio(1f))
                Text(caption, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 촬영 종류 아이콘: 현장 전체 · 대각선 · 근접 · 번호판 · 영상. */
private fun ShotKind.icon() = when (this) {
    ShotKind.SCENE -> Icons.Filled.Landscape
    ShotKind.MEDIUM -> Icons.Filled.PhotoCamera
    ShotKind.CLOSE_UP -> Icons.Filled.ZoomIn
    ShotKind.OTHER_PLATE -> Icons.Filled.Pin
    ShotKind.VIDEO -> Icons.Filled.Videocam
}

private fun guideUrl(encodedSteps: String) =
    BuildConfig.GUIDE_BASE_URL + "?steps=" + URLEncoder.encode(encodedSteps, "UTF-8")
