package com.whitemason.okaynow.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.whitemason.okaynow.app.R
import com.whitemason.okaynow.app.domain.damage.CAR_LENGTH_M
import com.whitemason.okaynow.app.domain.damage.CAR_WIDTH_M
import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.damage.Point
import com.whitemason.okaynow.app.domain.damage.ShotStep
import com.whitemason.okaynow.app.presentation.theme.Palette
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 차를 위에서 본 그림(앞이 위, 운전석이 왼쪽). 차 이미지는 res/drawable-nodpi/car_top.png이고,
 * 그 위에 [CarZone] 12구역을 겹쳐 그린다. 구역 좌표와 화면 방향이 같아서 별도 회전은 없다.
 *
 * 구역을 탭하면 [onZoneTap], [steps]가 있으면 번호 붙은 촬영 위치와 바라보는 방향(부채꼴)을 그린다.
 * 그림 밖으로 나가는 촬영 위치는 테두리에 붙여 그린다.
 *
 * [margin]은 차 둘레 여백(미터). 촬영 위치를 그리는 화면은 [CAMERA_MARGIN]을 쓰고,
 * 촬영 위치가 없는 화면은 [TIGHT_MARGIN]으로 줄여 차를 크게 그릴 수 있다.
 */
@Composable
fun TopViewCar(
    selected: Set<CarZone>,
    onZoneTap: ((CarZone) -> Unit)?,
    modifier: Modifier = Modifier,
    steps: List<ShotStep> = emptyList(),
    highlightedStep: Int? = null,
    margin: Float = CAMERA_MARGIN,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val car = ImageBitmap.imageResource(R.drawable.car_top)
    val viewW = CAR_WIDTH_M + margin * 2
    val viewH = CAR_LENGTH_M + margin * 2
    Canvas(
        modifier = modifier
            .aspectRatio(viewW / viewH)
            .pointerInput(onZoneTap, margin) {
                if (onZoneTap == null) return@pointerInput
                detectTapGestures { tap ->
                    val scale = size.width / viewW
                    CarZone.at(tap.x / scale - margin, tap.y / scale - margin)?.let(onZoneTap)
                }
            },
    ) {
        val scale = size.width / viewW
        fun screen(p: Point) = Offset((p.x + margin) * scale, (p.y + margin) * scale)

        drawCarImage(car, screen(Point(0f, 0f)), Size(CAR_WIDTH_M * scale, CAR_LENGTH_M * scale))

        CarZone.entries.forEach { zone ->
            val topLeft = screen(Point(zone.rect.left, zone.rect.top))
            val zoneSize = Size((zone.rect.right - zone.rect.left) * scale, (zone.rect.bottom - zone.rect.top) * scale)
            if (zone in selected) drawRect(colors.primary, topLeft, zoneSize, alpha = 0.6f)
            drawRect(colors.outline, topLeft, zoneSize, alpha = 0.45f, style = Stroke(width = 1.5f))
        }

        steps.forEach { step ->
            val camera = step.camera ?: return@forEach
            val emphasized = highlightedStep == null || highlightedStep == step.number
            // 촬영 위치는 회색조(브랜드 색은 사용자가 고른 부위에만).
            val color = if (emphasized) Palette.TextPrimary else Palette.Line
            // 테두리에 붙인 위치에서 부채꼴을 그려야 그림 밖 촬영 위치도 방향이 보인다.
            val at = clamp(screen(camera), size)
            step.facingDegrees?.let { facing ->
                fun tip(deg: Double): Offset {
                    val r = Math.toRadians(deg)
                    return at + Offset(sin(r).toFloat(), -cos(r).toFloat()) * (CONE_M * scale)
                }
                drawCone(at, tip(facing - 30.0), tip(facing + 30.0), color, if (emphasized) 0.35f else 0.12f)
            }
            drawCircle(color, radius = 0.22f * scale, center = at)
            val label = measurer.measure(step.number.toString(), TextStyle(color = Palette.Background, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            drawText(label, topLeft = at - Offset(label.size.width / 2f, label.size.height / 2f))
        }
    }
}

/** 이미지 속 차체 범위(px, 미러·그림자 제외)를 [bodyTopLeft]·[bodySize]에 맞춰 그린다. */
private fun DrawScope.drawCarImage(image: ImageBitmap, bodyTopLeft: Offset, bodySize: Size) {
    val sx = bodySize.width / IMAGE_BODY_W
    val sy = bodySize.height / IMAGE_BODY_H
    drawImage(
        image,
        dstOffset = IntOffset((bodyTopLeft.x - IMAGE_BODY_LEFT * sx).roundToInt(), (bodyTopLeft.y - IMAGE_BODY_TOP * sy).roundToInt()),
        dstSize = IntSize((image.width * sx).roundToInt(), (image.height * sy).roundToInt()),
    )
}

private fun DrawScope.drawCone(at: Offset, left: Offset, right: Offset, color: Color, alpha: Float) {
    val path = Path().apply {
        moveTo(at.x, at.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
    }
    drawPath(path, color, alpha = alpha)
}

private fun clamp(p: Offset, size: Size): Offset {
    val pad = 18f
    return Offset(p.x.coerceIn(pad, size.width - pad), p.y.coerceIn(pad, size.height - pad))
}

/** 촬영 위치를 그릴 때 차 주변 여백(미터). 현장 전체 촬영 위치(4m)는 테두리에 붙는다. */
const val CAMERA_MARGIN = 1.2f
/** 좁은 세로 창에서 촬영 위치를 그릴 때 여백(미터). 1~2m 촬영 위치도 테두리에 붙지만 방향은 보인다. */
const val COMPACT_CAMERA_MARGIN = 0.6f
/** 촬영 위치 없이 차만 그릴 때 여백(미터). 미러가 잘리지 않을 만큼만 둔다. */
const val TIGHT_MARGIN = 0.3f
private const val CONE_M = 0.9f

// car_top.png(455×858, 앞이 위)에서 잰 차체 범위. 이미지를 바꾸면 다시 잰다(whitemason/okaynow-pages의 public/guide.html도 같은 값).
private const val IMAGE_BODY_LEFT = 104f
private const val IMAGE_BODY_TOP = 5f
private const val IMAGE_BODY_W = 322f
private const val IMAGE_BODY_H = 801f
