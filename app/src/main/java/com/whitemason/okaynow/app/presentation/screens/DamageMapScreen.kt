package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.damage.damagedZoneNames
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.InfoTile
import com.whitemason.okaynow.app.presentation.components.ReferenceOnlyNotice
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.SplitPane
import com.whitemason.okaynow.app.presentation.components.LocalPortrait
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TopViewCar
import com.whitemason.okaynow.app.presentation.components.CAMERA_MARGIN
import com.whitemason.okaynow.app.presentation.components.TIGHT_MARGIN

/**
 * C7 파손 부위 확인. C6에서 고른 부위를 보여 주고 탭으로 고칠 수 있다.
 * TODO: 사진 분석(analyze-damage)을 붙이면 구역별 정도(severe/minor)를 미리 채운다.
 */
@Composable
fun DamageMapScreen(zones: Set<CarZone>, onToggleZone: (CarZone) -> Unit, onNext: () -> Unit, onEmergency: () -> Unit) {
    StepScaffold(
        title = "파손 부위가 맞나요?",
        subtitle = "누르면 고칠 수 있어요",
        onEmergency = onEmergency,
        wide = true,
        actions = { BigButton("다음", onNext, icon = Icons.AutoMirrored.Filled.ArrowForward) },
    ) {
        val portrait = LocalPortrait.current
        SplitPane(
            firstWeight = if (portrait) 2.4f else 1f,
            spacing = 24.dp,
            first = { m ->
                Box(m, contentAlignment = Alignment.Center) {
                    TopViewCar(selected = zones, onZoneTap = onToggleZone, margin = if (portrait) TIGHT_MARGIN else CAMERA_MARGIN)
                }
            },
            second = { m ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp, if (portrait) Alignment.Top else Alignment.CenterVertically), modifier = m) {
                    InfoTile(Tile("파손 부위", damagedZoneNames(zones) ?: "선택 없음", icon = Icons.Filled.CarCrash), Modifier.fillMaxWidth())
                    ReferenceOnlyNotice()
                }
            },
        )
    }
}
