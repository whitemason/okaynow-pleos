package com.whitemason.okaynow.app.domain.damage

/**
 * 탑뷰 12구역(PLAN.md 7절). 좌표는 차를 위에서 본 미터 단위로, 앞이 위(y=0), 운전석(왼쪽)이 x=0이다.
 * 차 크기는 중형 SUV 기준 [CAR_WIDTH_M] × [CAR_LENGTH_M]로 둔다(그림·촬영 거리 계산용 근사치).
 *
 * @property id 서버·웹과 주고받는 이름(PLAN.md의 구역 이름)
 * @property code 촬영 안내 페이지 주소에 넣는 짧은 이름
 */
enum class CarZone(val id: String, val code: String, val label: String, val rect: ZoneRect) {
    FRONT_BUMPER("front_bumper", "fb", "앞 범퍼", ZoneRect(SIDE, 0f, CAR_WIDTH_M - SIDE, BUMPER)),
    HOOD("hood", "hd", "보닛", ZoneRect(SIDE, BUMPER, CAR_WIDTH_M - SIDE, HOOD_END)),
    FRONT_LEFT_FENDER("front_left_fender", "flf", "운전석 쪽 앞 펜더", ZoneRect(0f, 0f, SIDE, HOOD_END)),
    FRONT_RIGHT_FENDER("front_right_fender", "frf", "조수석 쪽 앞 펜더", ZoneRect(CAR_WIDTH_M - SIDE, 0f, CAR_WIDTH_M, HOOD_END)),
    LEFT_FRONT_DOOR("left_front_door", "lfd", "운전석 앞문", ZoneRect(0f, HOOD_END, SIDE, DOOR_SPLIT)),
    LEFT_REAR_DOOR("left_rear_door", "lrd", "운전석 쪽 뒷문", ZoneRect(0f, DOOR_SPLIT, SIDE, REAR_START)),
    RIGHT_FRONT_DOOR("right_front_door", "rfd", "조수석 앞문", ZoneRect(CAR_WIDTH_M - SIDE, HOOD_END, CAR_WIDTH_M, DOOR_SPLIT)),
    RIGHT_REAR_DOOR("right_rear_door", "rrd", "조수석 쪽 뒷문", ZoneRect(CAR_WIDTH_M - SIDE, DOOR_SPLIT, CAR_WIDTH_M, REAR_START)),
    ROOF_GLASS("roof_glass", "rg", "지붕·유리", ZoneRect(SIDE, HOOD_END, CAR_WIDTH_M - SIDE, TRUNK_START)),
    REAR_LEFT_QUARTER("rear_left_quarter", "rlq", "운전석 쪽 뒤 펜더", ZoneRect(0f, REAR_START, SIDE, CAR_LENGTH_M)),
    REAR_RIGHT_QUARTER("rear_right_quarter", "rrq", "조수석 쪽 뒤 펜더", ZoneRect(CAR_WIDTH_M - SIDE, REAR_START, CAR_WIDTH_M, CAR_LENGTH_M)),
    TRUNK_REAR_BUMPER("trunk_rear_bumper", "trb", "트렁크·뒤 범퍼", ZoneRect(SIDE, TRUNK_START, CAR_WIDTH_M - SIDE, CAR_LENGTH_M)),
    ;

    val center: Point get() = Point((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)

    companion object {
        /** 탭한 위치(미터)의 구역. 차 밖이면 null. */
        fun at(x: Float, y: Float): CarZone? = entries.firstOrNull { it.rect.contains(x, y) }

        fun fromCode(code: String): CarZone? = entries.firstOrNull { it.code == code }
    }
}

data class Point(val x: Float, val y: Float)

data class ZoneRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

const val CAR_WIDTH_M = 1.9f
const val CAR_LENGTH_M = 4.6f

// 구역 경계(미터). 좌우 측면 폭, 앞에서부터의 거리.
private const val SIDE = 0.3f
private const val BUMPER = 0.35f
private const val HOOD_END = 1.4f
private const val DOOR_SPLIT = 2.4f
private const val REAR_START = 3.4f
private const val TRUNK_START = 3.75f
