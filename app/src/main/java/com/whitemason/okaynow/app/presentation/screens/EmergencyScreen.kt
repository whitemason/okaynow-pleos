package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.whitemason.okaynow.app.domain.location.LocationFix
import com.whitemason.okaynow.app.domain.location.coordinates
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.InfoTile
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.Tile

/** C3 긴급 모드. 보험사 연락보다 항상 앞서고, 어떤 번호도 자동으로 걸지 않는다. */
@Composable
fun EmergencyScreen(location: LocationFix?, onCall119: () -> Unit, onRepeatLocation: () -> Unit, onDone: () -> Unit) {
    StepScaffold(
        title = "119에 전화하세요",
        actions = {
            BigButton("119 전화", onCall119, danger = true, icon = Icons.Filled.Call)
            BigButton("위치 다시 듣기", onRepeatLocation, primary = false, icon = Icons.AutoMirrored.Filled.VolumeUp)
            BigButton("통화 끝", onDone, icon = Icons.Filled.CallEnd)
        },
    ) {
        // 주소를 보여 줄 때도 좌표를 함께 둔다(구조대가 좌표를 물을 때 대비).
        // 지도는 앱에 두지 않는다: Pleos 내비(드라이빙 뷰)가 이미 차 위치를 보여 준다.
        InfoTile(
            Tile(
                label = "현재 위치",
                value = location?.let { it.address ?: it.coordinates() } ?: "찾는 중",
                caption = listOfNotNull(location?.takeIf { it.address != null }?.coordinates(), MAP_GUIDE).joinToString("\n"),
                icon = Icons.Filled.LocationOn,
            ),
            Modifier.fillMaxSize(),
        )
    }
}

/** 주소가 보이는 곳에 붙이는 안내. 지도는 Pleos 내비가 보여 주므로 앱에는 두지 않는다(2026-11-09 대장 결정). */
const val MAP_GUIDE = "지도 위치는 Pleos 내비에서 확인하세요"
