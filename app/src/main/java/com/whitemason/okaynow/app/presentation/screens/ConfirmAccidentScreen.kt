package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import com.whitemason.okaynow.app.domain.voice.VoiceState
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.VoiceStatus

/** C1 충격 확인. 주행 중에도 표시한다(음성 + 대형 버튼 2개). */
@Composable
fun ConfirmAccidentScreen(
    secondsLeft: Int?,
    voice: VoiceState,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onEmergency: () -> Unit,
) {
    StepScaffold(
        title = "사고가 났나요?",
        subtitle = secondsLeft?.let { "${it}초 후 다음 단계로" },
        onEmergency = onEmergency,
        actions = {
            BigButton("아니요", onNo, primary = false, icon = Icons.Filled.Close)
            BigButton("네", onYes, icon = Icons.Filled.Check)
        },
    ) {
        VoiceStatus(voice)
    }
}
