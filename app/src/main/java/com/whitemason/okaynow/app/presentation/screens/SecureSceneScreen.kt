package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import com.whitemason.okaynow.app.domain.vehicle.SignalType
import com.whitemason.okaynow.app.domain.vehicle.VehicleStatus
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TileGrid

/** C4 안전 확보: 비상등, 삼각대, 대피. */
@Composable
fun SecureSceneScreen(vehicle: VehicleStatus, onNext: () -> Unit, onHome: () -> Unit, onEmergency: () -> Unit) {
    StepScaffold(
        title = "2차 사고를 막으세요",
        onEmergency = onEmergency,
        actions = {
            // 부상 확인을 마친 뒤라 여기서 끝내도 된다(가벼운 접촉 등).
            BigButton("처음으로", onHome, primary = false, icon = Icons.Filled.Home)
            BigButton("다음", onNext, icon = Icons.AutoMirrored.Filled.ArrowForward)
        },
    ) {
        val hazardOn = SignalType.HAZARD_LIGHT in vehicle.supported && vehicle.hazardLight == true
        // TODO: 체크 상태 저장, 음성으로 하나씩 안내
        TileGrid(
            listOf(
                Tile("비상등", if (hazardOn) "켜짐" else "켜세요", warning = !hazardOn && SignalType.HAZARD_LIGHT in vehicle.supported, icon = Icons.Filled.Warning),
                Tile("대피", "가드레일 밖", icon = Icons.AutoMirrored.Filled.DirectionsRun),
                Tile("삼각대", "차 뒤 100m", caption = "고속도로 200m", icon = Icons.Filled.ChangeHistory),
            ),
        )
    }
}
