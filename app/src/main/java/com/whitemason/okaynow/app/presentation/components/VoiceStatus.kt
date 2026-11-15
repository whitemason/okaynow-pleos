package com.whitemason.okaynow.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitemason.okaynow.app.domain.voice.VoiceState
import com.whitemason.okaynow.app.presentation.theme.Palette

/** 음성 상태 타일. 듣는 중에는 알아들은 말을 크게 보여 준다. */
@Composable
fun VoiceStatus(state: VoiceState, modifier: Modifier = Modifier.fillMaxSize()) {
    val (label, text) = when (state) {
        VoiceState.Speaking -> "음성" to "질문을 읽는 중"
        is VoiceState.Listening -> "듣고 있어요" to state.partial.ifBlank { "말로 답해도 돼요" }.let { if (state.partial.isBlank()) it else "\"$it\"" }
        is VoiceState.Unavailable -> "음성" to "버튼을 눌러 주세요"
        VoiceState.Idle -> "음성" to "말로 답해도 돼요"
    }
    val icon = when (state) {
        VoiceState.Speaking -> Icons.Filled.RecordVoiceOver
        is VoiceState.Listening -> Icons.Filled.Mic
        is VoiceState.Unavailable -> Icons.Filled.TouchApp
        VoiceState.Idle -> Icons.Filled.Mic
    }
    Surface(color = Palette.Surface, shape = TileShape, modifier = modifier) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(52.dp))
                Text(label, style = MaterialTheme.typography.headlineMedium, color = Palette.TextSecondary)
            }
            Text(text, style = MaterialTheme.typography.displayMedium.copy(fontSize = TILE_VALUE_SIZE), fontWeight = FontWeight.Bold)
        }
    }
}
