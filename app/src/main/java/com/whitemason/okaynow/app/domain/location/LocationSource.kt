package com.whitemason.okaynow.app.domain.location

import java.util.Locale
import kotlin.math.abs

/** 현재 위치. C3 낭독·C8 접수 요약용. [address]는 내비가 알려 준 주소(없으면 null). */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val address: String? = null,
)

interface LocationSource {
    /** 위치를 얻지 못하면 null. */
    suspend fun current(): LocationFix?
}

/** 구조대에 불러 줄 문장. 주소가 있으면 주소를, 없으면 좌표를 읽는다. */
fun LocationFix.spoken(): String =
    address?.takeIf { it.isNotBlank() }?.let { "현재 위치는 ${it.trim()}입니다." }
        ?: "현재 위치는 ${coordinates()}입니다."

/** "북위 37.56650도, 동경 126.97800도" */
fun LocationFix.coordinates(): String = String.format(
    Locale.KOREA, "%s %.5f도, %s %.5f도",
    if (latitude >= 0) "북위" else "남위", abs(latitude),
    if (longitude >= 0) "동경" else "서경", abs(longitude),
)
