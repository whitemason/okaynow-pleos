package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.GAP
import com.whitemason.okaynow.app.presentation.components.LocalPortrait
import com.whitemason.okaynow.app.presentation.components.SplitPane
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.TileShape
import com.whitemason.okaynow.app.presentation.theme.Palette

/**
 * C0 사전 설정: 보험사 등록(전체 화면). 왼쪽은 보험사 목록 타일(누르면 이름·번호가 채워진다),
 * 오른쪽은 직접 입력. 입력 화면이라 P단에서만 연다(CLAUDE.md 규칙 5, HomeScreen이 주차 중에만 보여 준다).
 * 뒤로가기는 툴바 버튼(LocalOnBack)과 하드웨어 키로 한다(HomeScreen).
 */
@Composable
fun InsurerSettingsScreen(
    current: Insurer?,
    presets: List<Insurer>,
    onSave: (Insurer?) -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current?.name.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(current?.phone.orEmpty()) }
    val valid = name.isNotBlank() && phone.count(Char::isDigit) >= 3
    StepScaffold(
        title = "보험사 등록",
        subtitle = current?.let { "지금: ${it.name} ${it.phone}" } ?: "목록에서 고르거나 직접 입력하세요",
        wide = true,
        actions = {
            BigButton("등록 지우기", { onSave(null); onClose() }, primary = false, enabled = current != null, icon = Icons.Filled.Delete)
            BigButton("저장", { onSave(Insurer(name.trim(), phone.trim())); onClose() }, enabled = valid, caption = if (valid) null else "이름과 번호를 넣어요", icon = Icons.Filled.Check)
        },
    ) {
        SplitPane(firstWeight = 1.5f, secondWeight = 1f, first = { m ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (LocalPortrait.current) 2 else 3),
                modifier = m,
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                items(presets) { preset ->
                    val selected = preset.name == name && preset.phone == phone
                    Surface(
                        onClick = { name = preset.name; phone = preset.phone },
                        color = if (selected) MaterialTheme.colorScheme.primary else Palette.Surface,
                        contentColor = Palette.TextPrimary,
                        shape = TileShape,
                        modifier = Modifier.fillMaxWidth().height(132.dp),
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(preset.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(preset.phone, style = MaterialTheme.typography.titleLarge, color = if (selected) Palette.TextPrimary else Palette.TextSecondary)
                        }
                    }
                }
            }
        }) { m ->
            Surface(color = Palette.Surface, shape = TileShape, modifier = m) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("직접 입력", style = MaterialTheme.typography.headlineMedium, color = Palette.TextSecondary)
                    Text("목록에 없거나 번호가 바뀌었으면 여기에 적어요", style = MaterialTheme.typography.titleLarge, color = Palette.TextSecondary)
                    OutlinedTextField(
                        name, { name = it }, label = { Text("보험사 이름") }, singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        phone, { input -> phone = input.filter { it.isDigit() || it == '-' } },
                        label = { Text("사고접수 번호") }, singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
