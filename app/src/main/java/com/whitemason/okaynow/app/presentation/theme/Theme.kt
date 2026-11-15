package com.whitemason.okaynow.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 사고 직후 운전자는 시야가 좁다. 화면은 그레이스케일(Pleos basic 토큰, Dark)로 두고,
 * 브랜드 색(Pleos informative/positive 파랑)은 지금 눌러야 할 것과 사용자가 고른 것에만 쓴다.
 * 빨강·초록 같은 다른 색은 쓰지 않는다. 출처: docs/PLEOS_DESIGN_NOTES.md 2절.
 */
object Palette {
    val Brand = Color(0xFF0082FF)
    val OnBrand = Color(0xFFFFFFFF)
    // 위험(119·부상). Pleos regulation/red (Dark). 응급 버튼에만 쓴다.
    val Danger = Color(0xFFFF4339)
    val Background = Color(0xFF131417) // basic/00 (Dark)
    val Surface = Color(0xFF1D1E21) // basic/50
    val SurfaceHigh = Color(0xFF313236) // basic/100
    val SurfaceAccent = Color(0xFF44464E) // basic/200
    val Line = Color(0xFF686A72) // basic/300
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFC7C9CE) // basic/500 (Dark)
}

@Composable
fun AccidentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Brand,
            onPrimary = Palette.OnBrand,
            primaryContainer = Palette.SurfaceAccent,
            onPrimaryContainer = Palette.TextPrimary,
            secondary = Palette.TextSecondary,
            onSecondary = Palette.Background,
            tertiary = Palette.TextSecondary,
            background = Palette.Background,
            onBackground = Palette.TextPrimary,
            surface = Palette.Surface,
            onSurface = Palette.TextPrimary,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.TextSecondary,
            outline = Palette.Line,
            // 오류 색도 그레이스케일로 둔다(강조는 브랜드 색 하나만).
            error = Palette.TextPrimary,
            onError = Palette.Background,
            errorContainer = Palette.SurfaceAccent,
            onErrorContainer = Palette.TextPrimary,
        ),
        content = content,
    )
}
