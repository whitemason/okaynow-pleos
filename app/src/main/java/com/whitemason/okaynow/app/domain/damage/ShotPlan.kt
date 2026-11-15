package com.whitemason.okaynow.app.domain.damage

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** 촬영 한 장(또는 영상 하나). */
enum class ShotKind { SCENE, MEDIUM, CLOSE_UP, OTHER_PLATE, VIDEO }

/**
 * @property camera 서 있을 위치(미터, [CarZone] 좌표계). 상대 차 번호판처럼 이 차 기준 위치가 없으면 null.
 * @property facingDegrees 카메라가 바라보는 방향. 0 = 차 앞쪽(위), 시계 방향.
 */
data class ShotStep(
    val number: Int,
    val kind: ShotKind,
    val zone: CarZone?,
    val camera: Point?,
    val facingDegrees: Int?,
    val distanceText: String,
    val title: String,
    val instruction: String,
)

/**
 * 선택한 충돌 부위로 촬영 순서를 만든다. 차 주위를 한 바퀴만 돌도록 부위를 시계 방향으로 정렬한다.
 * 현장 전체 → (부위별) 대각선 → 근접 → 상대 차 번호판 → 한 바퀴 영상.
 *
 * 같은 규칙과 문구를 폰 안내 페이지(whitemason/okaynow-pages의 public/guide.html)도 쓴다. 바꾸면 그쪽 문구도 맞춘다.
 */
fun planShots(zones: Set<CarZone>): List<ShotStep> {
    if (zones.isEmpty()) return emptyList()
    val ordered = zones.sortedBy { clockwiseFromFront(it.center) }
    val first = ordered.first()
    val steps = mutableListOf<ShotStep>()
    fun add(kind: ShotKind, zone: CarZone?, camera: Point?, facing: Int?, distance: String, title: String, text: String) {
        steps += ShotStep(steps.size + 1, kind, zone, camera, facing, distance, title, text)
    }

    // 1. 현장 전체: 첫 부위 쪽 바깥 4m에서, 차 전체와 도로가 나오게
    sceneCamera(first).let { add(ShotKind.SCENE, null, it, facingTo(it, CAR_CENTER), "3~5m", "현장 전체", SCENE_TEXT) }
    for (zone in ordered) {
        val normal = outwardNormal(zone)
        // 2. 대각선 45°, 1.5m: 부위와 차 윤곽이 함께
        val diagonal = rotate(normal, if (zone.center.x < CAR_CENTER.x) 45.0 else -45.0)
        val medium = offset(edgePoint(zone.center, diagonal), diagonal, MEDIUM_M)
        add(ShotKind.MEDIUM, zone, medium, facingTo(medium, zone.center), "1~2m", "${zone.label} 대각선", "대각선에서 차 윤곽과 함께")
        // 3. 근접 0.4m, 약간 비스듬히(반사 줄이기)
        val closeDir = rotate(normal, 20.0)
        val close = offset(edgePoint(zone.center, closeDir), closeDir, CLOSE_M)
        add(ShotKind.CLOSE_UP, zone, close, facingTo(close, zone.center), "30~50cm", "${zone.label} 가까이", "파손 부위를 비스듬히")
    }
    add(ShotKind.OTHER_PLATE, null, null, null, "1~2m", "상대 차 번호판", PLATE_TEXT)
    val videoDir = outwardNormal(first)
    add(ShotKind.VIDEO, first, offset(edgePoint(first.center, videoDir), videoDir, MEDIUM_M), null, "30초", "한 바퀴 영상", VIDEO_TEXT)
    return steps
}

/** 운전자에게 한 번만 읽어 줄 요약. */
fun shotSummary(steps: List<ShotStep>): String =
    "${shotCountText(steps)}. 순서는 폰으로 QR을 찍어 보세요. 차도 쪽은 피하세요."

/** "사진 6장, 영상 1개 이상 필요". C6 촬영 완료 버튼에 넣는 최소 촬영량. */
fun shotCountText(steps: List<ShotStep>): String {
    val photos = steps.count { it.kind != ShotKind.VIDEO }
    val videos = steps.count { it.kind == ShotKind.VIDEO }
    return "사진 ${photos}장, 영상 ${videos}개 이상 필요"
}

/**
 * 폰 안내 페이지 주소의 steps 값. "종류,부위,x,y,방향" 단계를 ";"로 잇는다(좌표는 cm 정수).
 * 예: "S,,-120,40,135;M,fb,-110,-90,45;..."
 */
fun encodeSteps(steps: List<ShotStep>): String = steps.joinToString(";") { s ->
    listOf(
        KIND_CODES.getValue(s.kind),
        s.zone?.code.orEmpty(),
        s.camera?.x?.cm().orEmpty(),
        s.camera?.y?.cm().orEmpty(),
        s.facingDegrees?.toString().orEmpty(),
    ).joinToString(",")
}

private val KIND_CODES = mapOf(
    ShotKind.SCENE to "S", ShotKind.MEDIUM to "M", ShotKind.CLOSE_UP to "C",
    ShotKind.OTHER_PLATE to "P", ShotKind.VIDEO to "V",
)

const val SCENE_TEXT = "차 두 대와 도로가 함께"
const val PLATE_TEXT = "번호판과 파손 부위"
const val VIDEO_TEXT = "차 주위를 천천히 한 바퀴"

private val CAR_CENTER = Point(CAR_WIDTH_M / 2, CAR_LENGTH_M / 2)
private const val SCENE_M = 4f
private const val MEDIUM_M = 1.5f
private const val CLOSE_M = 0.4f

/** 차 중심에서 본 방향(0 = 앞, 시계 방향, 0..360). */
private fun clockwiseFromFront(p: Point): Double {
    // 가운데 줄 부위(앞 범퍼 등)는 x 차이가 부동소수점 오차만큼 음수가 될 수 있어 0으로 맞춘다.
    val dx = (p.x - CAR_CENTER.x).toDouble().let { if (kotlin.math.abs(it) < 1e-4) 0.0 else it }
    val deg = Math.toDegrees(atan2(dx, (CAR_CENTER.y - p.y).toDouble()))
    return (deg + 360) % 360
}

/** 부위에서 차 바깥쪽으로 향하는 단위 벡터. 지붕은 운전석 앞쪽 대각선 위에서 내려다본다. */
private fun outwardNormal(zone: CarZone): Point = when (zone) {
    CarZone.ROOF_GLASS -> unit(-1f, -1f)
    else -> unit(zone.center.x - CAR_CENTER.x, (zone.center.y - CAR_CENTER.y) * WIDTH_TO_LENGTH)
}

/** 차가 길쭉해서 앞뒤 방향을 덜 세게 보정한다(측면 부위가 대각선으로 튀지 않게). */
private const val WIDTH_TO_LENGTH = CAR_WIDTH_M / CAR_LENGTH_M

private fun sceneCamera(zone: CarZone): Point {
    val dir = rotate(outwardNormal(zone), if (zone.center.x < CAR_CENTER.x) 30.0 else -30.0)
    return offset(edgePoint(zone.center, dir), dir, SCENE_M)
}

/** [from]에서 [dir] 방향으로 나아가 차체 바깥 경계에 닿는 점. 촬영 거리는 이 점에서 잰다. */
private fun edgePoint(from: Point, dir: Point): Point {
    val tx = when {
        dir.x > 0f -> (CAR_WIDTH_M - from.x) / dir.x
        dir.x < 0f -> -from.x / dir.x
        else -> Float.POSITIVE_INFINITY
    }
    val ty = when {
        dir.y > 0f -> (CAR_LENGTH_M - from.y) / dir.y
        dir.y < 0f -> -from.y / dir.y
        else -> Float.POSITIVE_INFINITY
    }
    return offset(from, dir, minOf(tx, ty))
}

private fun unit(x: Float, y: Float): Point {
    val len = hypot(x, y).takeIf { it > 0f } ?: 1f
    return Point(x / len, y / len)
}

private fun rotate(v: Point, degrees: Double): Point {
    val r = degrees * PI / 180
    return Point((v.x * cos(r) - v.y * sin(r)).toFloat(), (v.x * sin(r) + v.y * cos(r)).toFloat())
}

private fun offset(p: Point, dir: Point, meters: Float) = Point(p.x + dir.x * meters, p.y + dir.y * meters)

private fun facingTo(from: Point, to: Point): Int {
    val deg = Math.toDegrees(atan2((to.x - from.x).toDouble(), (from.y - to.y).toDouble()))
    return ((deg + 360) % 360).roundToInt()
}

private fun Float.cm() = (this * 100).roundToInt().toString()

/** "앞 범퍼, 운전석 앞문". 시계 방향 순. 선택이 없으면 null. C7·C8 표시용. */
fun damagedZoneNames(zones: Set<CarZone>): String? =
    zones.takeIf { it.isNotEmpty() }?.sortedBy { clockwiseFromFront(it.center) }?.joinToString(", ") { it.label }

/** "앞 범퍼, 운전석 앞문 파손." 선택이 없으면 null. C7 음성용. */
fun damageSummary(zones: Set<CarZone>): String? = damagedZoneNames(zones)?.let { "$it 파손." }
