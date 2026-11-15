package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.runtime.Composable
import com.whitemason.okaynow.app.domain.voice.VoiceState
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.VoiceStatus

/**
 * C2 부상 확인. 무응답이면 [severe](에어백·고충격)일 때만 C3 긴급 모드로, 아니면 처음으로 돌아간다.
 * "있어요"(119)는 빨간색, "없어요"는 브랜드 색.
 */
@Composable
fun AskInjuryScreen(secondsLeft: Int?, severe: Boolean, voice: VoiceState, onInjured: () -> Unit, onNotInjured: () -> Unit) {
    StepScaffold(
        title = "다친 사람이 있나요?",
        subtitle = secondsLeft?.let { if (severe) "${it}초 후 119 안내" else "${it}초 후 처음으로 돌아갑니다" },
        actions = {
            BigButton("있어요 · 119", onInjured, danger = true, icon = Icons.Filled.PersonalInjury)
            BigButton("없어요", onNotInjured, icon = Icons.Filled.Check)
        },
    ) {
        VoiceStatus(voice)
    }
}
