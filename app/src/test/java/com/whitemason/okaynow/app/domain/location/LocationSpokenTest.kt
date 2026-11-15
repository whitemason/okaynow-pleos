package com.whitemason.okaynow.app.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSpokenTest {

    @Test
    fun northEast() {
        assertEquals("현재 위치는 북위 37.56650도, 동경 126.97800도입니다.", LocationFix(37.5665, 126.978, 0).spoken())
    }

    @Test
    fun negativeCoordinates_useSouthAndWest() {
        assertEquals("현재 위치는 북위 37.42200도, 서경 122.08400도입니다.", LocationFix(37.422, -122.084, 0).spoken())
        assertEquals("현재 위치는 남위 33.86880도, 동경 151.20930도입니다.", LocationFix(-33.8688, 151.2093, 0).spoken())
    }

    @Test
    fun address_isReadInsteadOfCoordinates() {
        val fix = LocationFix(37.5665, 126.978, 0, address = " 서울특별시 중구 세종대로 110 ")
        assertEquals("현재 위치는 서울특별시 중구 세종대로 110입니다.", fix.spoken())
        assertEquals("북위 37.56650도, 동경 126.97800도", fix.coordinates())
    }

    @Test
    fun blankAddress_fallsBackToCoordinates() {
        assertEquals("현재 위치는 북위 1.00000도, 동경 2.00000도입니다.", LocationFix(1.0, 2.0, 0, address = " ").spoken())
    }
}
