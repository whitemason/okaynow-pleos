package com.whitemason.okaynow.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitemason.okaynow.app.presentation.theme.Palette

// 디자인안 선정 전 골격. 색은 ui/theme(그레이스케일 + 브랜드 색 1개)을 따른다.
// 배치는 컨트롤 센터·위젯처럼 화면을 큰 타일로 채운다. 놀란 상황에서도 정밀하게 누를 필요가 없게.

/** 타일·버튼 공통 모서리. */
val TileShape = RoundedCornerShape(32.dp)

/**
 * 사고 처리 단계 공통 틀.
 * - 상단 툴바: 높이 고정([HEADER_HEIGHT]). 뒤로가기([LocalOnBack]이 있을 때만), 제목(필요할 때만 부제), 119만 둔다.
 *   다른 버튼은 여기로 올라오지 않는다.
 * - 가로 창(Landscape 2:0, 1700×1184): 왼쪽(넓음)은 안내 타일, 오른쪽은 대형 버튼 타일이 툴바 아래부터 바닥까지 채운다.
 * - 세로 창(Portrait 1:1, 840×1184): 위는 안내 타일, 아래는 대형 버튼 타일을 세로로 쌓는다.
 * - 어느 쪽이든 다음 단계로 가는 파란(주된) 버튼은 버튼 열 맨 아래(오른쪽 아래)에 둔다. 보조 버튼은 그 위.
 *   빨간 버튼(119·부상)은 안전 우선이라 순서와 상관없이 맨 위에 둔다.
 * 창 방향은 [LocalPortrait]로 하위 화면에 알린다(차 그림 + 목록 같은 안쪽 배치도 [SplitPane]으로 같이 바뀐다).
 * @param onEmergency 이 화면의 주된 행동이 119가 아닐 때 툴바 오른쪽에 회색 119 버튼을 둔다. null이면 숨긴다.
 * @param wide 차 그림 + 목록처럼 안내가 넓어야 하는 화면은 안내 영역을 더 넓힌다.
 */
@Composable
fun StepScaffold(
    title: String,
    subtitle: String? = null,
    onEmergency: (() -> Unit)? = null,
    wide: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val portrait = maxWidth < maxHeight
            CompositionLocalProvider(LocalPortrait provides portrait) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = GAP).padding(bottom = GAP)) {
                    Toolbar(title, subtitle, onEmergency, titleSize = if (portrait) PORTRAIT_TITLE_SIZE else TITLE_SIZE)
                    // 세로 창에서 차 그림이 작아지지 않게 넓은 화면은 안내 영역을 더 키운다.
                    val contentWeight = when { wide && portrait -> 3f; wide -> 2.2f; else -> 1.4f }
                    if (portrait) {
                        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GAP)) {
                            Column(modifier = Modifier.weight(contentWeight).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GAP), content = content)
                            Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GAP), content = actions)
                        }
                    } else {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                            Column(modifier = Modifier.weight(contentWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GAP), content = content)
                            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GAP), content = actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toolbar(title: String, subtitle: String?, onEmergency: (() -> Unit)?, titleSize: TextUnit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalOnBack.current?.let { BackToolbarButton(it) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.displayMedium.copy(fontSize = titleSize, lineHeight = titleSize * 1.15f), fontWeight = FontWeight.Bold, maxLines = 1)
            subtitle?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        onEmergency?.let { EmergencyToolbarButton(it) }
    }
}

/** 지금 창이 세로(Portrait 1:1)인지. [StepScaffold]가 창 크기를 보고 정한다. */
val LocalPortrait = staticCompositionLocalOf { false }

/**
 * 안내 영역 안을 둘로 나눈다. 가로 창이면 좌우, 세로 창이면 위아래([LocalPortrait]).
 * [first]·[second]에는 비율(weight)이 들어간 Modifier를 넘긴다.
 */
@Composable
fun SplitPane(
    modifier: Modifier = Modifier.fillMaxSize(),
    firstWeight: Float = 1f,
    secondWeight: Float = 1f,
    spacing: Dp = GAP,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    if (LocalPortrait.current) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.weight(firstWeight).fillMaxWidth())
            second(Modifier.weight(secondWeight).fillMaxWidth())
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.weight(firstWeight).fillMaxHeight())
            second(Modifier.weight(secondWeight).fillMaxHeight())
        }
    }
}

/** 상단 툴바 높이. 화면마다 같게 고정한다(제목 + 부제 한 줄까지). */
val HEADER_HEIGHT = 128.dp

/** 타일 사이 간격(화면 가장자리 여백도 같게). */
val GAP = 20.dp

// 놀란 상황에서도 한눈에 읽히게 크게(Material display보다 크게).
private val TITLE_SIZE = 52.sp
// 세로 창(840폭)은 뒤로 + 제목 + 119가 한 줄에 들어가게 줄인다(Pleos Title/Large 40).
private val PORTRAIT_TITLE_SIZE = 40.sp
private val PORTRAIT_BUTTON_SIZE = 44.sp
private val BUTTON_SIZE = 56.sp
/** 타일 값 글자 크기. */
val TILE_VALUE_SIZE = 48.sp
/** 긴 값(주소 등) 글자 크기. */
val COMPACT_VALUE_SIZE = 32.sp

/**
 * 대형 버튼 타일. 버튼 영역 높이를 버튼끼리 나눠 채운다.
 * 세로 창에서는 버튼 높이가 낮으니 아이콘을 글자 왼쪽에 둔다.
 * [primary]면 브랜드 색(이 화면에서 할 일), 아니면 회색 타일. [danger]면 빨간색(119·부상처럼 위급한 선택). [caption]은 버튼을 누르기 전에 알아야 할 조건(한 줄).
 * [icon]은 글자 위에 크게 둔다. 글자를 읽기 전에 모양으로 먼저 알아보게.
 */
@Composable
fun ColumnScope.BigButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = true,
    enabled: Boolean = true,
    caption: String? = null,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        enabled = enabled,
        shape = TileShape,
        colors = when {
            danger -> ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.TextPrimary)
            primary -> ButtonDefaults.buttonColors()
            else -> ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.TextPrimary)
        },
    ) {
        if (LocalPortrait.current) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(TILE_ICON_SIZE)) }
                Column {
                    Text(text, style = MaterialTheme.typography.displayMedium.copy(fontSize = PORTRAIT_BUTTON_SIZE), fontWeight = FontWeight.Bold)
                    caption?.let { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(BUTTON_ICON_SIZE)) }
                Text(text, style = MaterialTheme.typography.displayMedium.copy(fontSize = BUTTON_SIZE), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                caption?.let { Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
            }
        }
    }
}

/** 툴바 뒤로가기. null이면 버튼을 숨긴다. 어느 단계에서 뒤로 갈 수 있는지는 AccidentApp이 정한다. */
val LocalOnBack = staticCompositionLocalOf<(() -> Unit)?> { null }

/** 툴바 뒤로가기 버튼. 119와 같은 높이, 회색 테두리. */
@Composable
private fun BackToolbarButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(TOOLBAR_BUTTON_HEIGHT).semantics { contentDescription = "뒤로" },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(2.dp, Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.TextPrimary),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(TOOLBAR_ICON_SIZE))
    }
}

private val TOOLBAR_BUTTON_HEIGHT = 88.dp
// Pleos 아이콘 크기(level10 48 · level11 52 · level16 72). docs/PLEOS_DESIGN_NOTES.md 4절.
private val TOOLBAR_ICON_SIZE = 48.dp
private val TILE_ICON_SIZE = 52.dp
private val BUTTON_ICON_SIZE = 72.dp

/** 툴바의 119 버튼. 주된 행동과 겹치지 않게 회색 테두리. */
@Composable
private fun EmergencyToolbarButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(TOOLBAR_BUTTON_HEIGHT).widthIn(min = 220.dp),
        border = BorderStroke(2.dp, Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.TextPrimary),
    ) {
        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(TOOLBAR_ICON_SIZE * 0.8f))
        Spacer(Modifier.width(12.dp))
        Text("119 전화", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

/** 정보 타일 한 칸. [warning]이면 흰 바탕·검은 글씨로 뒤집는다(색은 쓰지 않는다). */
data class Tile(
    val label: String,
    val value: String,
    val caption: String? = null,
    val warning: Boolean = false,
    val icon: ImageVector? = null,
    /** 주소·문장처럼 긴 값은 글자를 줄인다. */
    val compact: Boolean = false,
)

/** 큰 정보 타일(위젯처럼). 이름은 작게 위에, 값은 크게 아래에 붙인다. [onClick]이 있으면 눌러서 설정을 바꾼다. */
@Composable
fun InfoTile(tile: Tile, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val bg = if (tile.warning) Palette.TextPrimary else Palette.Surface
    val fg = if (tile.warning) Palette.Background else Palette.TextPrimary
    val sub = if (tile.warning) Palette.SurfaceAccent else Palette.TextSecondary
    if (onClick != null) Surface(onClick = onClick, color = bg, contentColor = fg, shape = TileShape, modifier = modifier) { InfoTileContent(tile, sub) }
    else Surface(color = bg, contentColor = fg, shape = TileShape, modifier = modifier) { InfoTileContent(tile, sub) }
}

@Composable
private fun InfoTileContent(tile: Tile, sub: androidx.compose.ui.graphics.Color) {
    run {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                tile.icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(TILE_ICON_SIZE)) }
                Text(tile.label, style = MaterialTheme.typography.headlineMedium, color = sub)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val size = if (tile.compact) COMPACT_VALUE_SIZE else TILE_VALUE_SIZE
                Text(tile.value, style = MaterialTheme.typography.displayMedium.copy(fontSize = size, lineHeight = size * 1.2f), fontWeight = FontWeight.Bold)
                tile.caption?.let { Text(it, style = MaterialTheme.typography.headlineMedium, color = sub) }
            }
        }
    }
}

/** 타일을 [columns]칸 격자로 깔아 남은 영역을 채운다. */
@Composable
fun TileGrid(tiles: List<Tile>, modifier: Modifier = Modifier.fillMaxSize(), columns: Int = 2) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GAP)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.weight(1f)) {
                // 남는 칸은 비워 두지 않고 마지막 타일이 줄을 채운다.
                row.forEach { InfoTile(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** 한 줄 정보(설정 목록용). [onClick]이 있으면 눌러서 바꿀 수 있다. */
@Composable
fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val m = Modifier.fillMaxWidth()
    if (onClick != null) Surface(onClick = onClick, color = Palette.Surface, shape = TileShape, modifier = m) { InfoRowContent(label, value) }
    else Surface(color = Palette.Surface, shape = TileShape, modifier = m) { InfoRowContent(label, value) }
}

@Composable
private fun InfoRowContent(label: String, value: String) {
    run {
        Row(modifier = Modifier.padding(24.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * CLAUDE.md 규칙 5: 목록·지도·입력 화면(C4~C9)은 P단에서만. 주행 중이면 이 화면만 보이고,
 * P단으로 바꾸면 [nextStep] 화면이 저절로 이어진다(누를 버튼이 없어도 되게 그렇게 알린다).
 */
@Composable
fun ParkRequired(nextStep: String, onEmergency: () -> Unit) {
    StepScaffold(
        title = "차를 세우세요",
        subtitle = "P단으로 바꾸면 바로 이어서 안내해요",
        actions = { BigButton("119 전화", onEmergency, primary = false, icon = Icons.Filled.Call) },
    ) {
        InfoTile(Tile("기어", "P단으로 바꾸세요", caption = "안전한 곳에 세운 뒤", icon = Icons.Filled.LocalParking), Modifier.weight(1f).fillMaxWidth())
        InfoTile(Tile("다음 안내", nextStep, caption = "P단이 되면 저절로 넘어가요", icon = Icons.AutoMirrored.Filled.ArrowForward), Modifier.weight(1f).fillMaxWidth())
    }
}

/** CLAUDE.md 규칙 6. */
@Composable
fun ReferenceOnlyNotice() {
    Text("참고용 추정 · 손해사정 아님", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
