package com.whitemason.okaynow.app.data.location

import com.whitemason.okaynow.app.domain.location.LocationFix
import com.whitemason.okaynow.app.domain.location.LocationSource

/** 시뮬레이션 모드용 고정 위치(서울시청 앞, 예시 값). */
class FakeLocationSource(private val nowMillis: () -> Long) : LocationSource {
    override suspend fun current() = LocationFix(37.56650, 126.97800, nowMillis(), address = "서울특별시 중구 세종대로 110")
}
