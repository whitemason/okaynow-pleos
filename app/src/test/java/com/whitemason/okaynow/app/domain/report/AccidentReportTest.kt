package com.whitemason.okaynow.app.domain.report

import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.location.LocationFix
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.Base64
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccidentReportTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun report(
        location: LocationFix? = LocationFix(37.5665, 126.978, 0, address = "서울 중구 세종대로 110"),
        injured: Boolean? = false,
        issues: List<String> = emptyList(),
        zones: List<CarZone> = listOf(CarZone.entries.first()),
    ) = AccidentReport(
        occurredAtMillis = 1_793_859_000_000L, // 2026-11-05 15:10 KST
        zone = seoul,
        location = location,
        injured = injured,
        emergencyGuided = injured == true,
        secureSceneDone = true,
        vehicleIssues = issues,
        vehicleChecked = listOf("도어", "타이어"),
        damageZones = zones,
        insurer = Insurer("테스트 보험", "1588-0000"),
    )

    @Test
    fun timeAndLocationText() {
        assertEquals("11월 5일 오후 3시 10분", report().occurredAtText)
        assertEquals("서울 중구 세종대로 110", report().locationText)
        assertEquals("북위 37.56650도, 동경 126.97800도", report(location = LocationFix(37.5665, 126.978, 0)).locationText)
    }

    @Test
    fun template_onlyUsesKnownFacts() {
        val text = report(location = null, zones = emptyList()).templateSummary()
        assertEquals("11월 5일 오후 3시 10분 사고가 났습니다. 다친 사람 없음. 차량 점검 결과는 이상 없음 입니다.", text)
    }

    @Test
    fun template_includesLocationDamageAndIssues() {
        val text = report(injured = true, issues = listOf("타이어: 공기압 낮음 · 주행 금지")).templateSummary()
        assertTrue(text.contains("서울 중구 세종대로 110 에서"))
        assertTrue(text.contains("다친 사람 있음, 119 안내를 받았습니다."))
        assertTrue(text.contains("파손 부위는 ${CarZone.entries.first().label} 입니다."))
        assertTrue(text.contains("타이어: 공기압 낮음 · 주행 금지"))
    }

    @Test
    fun json_hasRawFields_andEscapes() {
        val json = report(location = LocationFix(1.0, 2.0, 0, address = "따옴표\"주소\n")).toJson()
        assertTrue(json.startsWith("{\"version\":1,"))
        assertTrue(json.contains("\"occurredAt\":\"2026-11-05T15:10:00+09:00\""))
        assertTrue(json.contains("\"address\":\"따옴표\\\"주소\\n\""))
        assertTrue(json.contains("\"injury\":\"no\""))
        assertTrue(json.contains("\"insurer\":{\"name\":\"테스트 보험\",\"phone\":\"1588-0000\"}"))
        assertTrue(json.contains("\"damageZones\":[{\"id\":\"${CarZone.entries.first().id}\""))
        assertFalse(json.contains("\n"))
    }

    @Test
    fun noResponseInjury_isRecordedAsNoResponse() {
        assertTrue(report(injured = null).toJson().contains("\"injury\":\"no_response\""))
        assertEquals("응답 없음", report(injured = null).injuryText)
    }

    @Test
    fun qrFragment_roundTrips_andFitsInQr() {
        val summary = ReportSummary("가".repeat(200), ReportSummary.Source.GLEO_LLM)
        val r = report(issues = listOf("타이어: 공기압 낮음 · 주행 금지", "도어: 열림"), zones = CarZone.entries.take(4))
        val fragment = reportFragment(r, summary)

        val inflater = Inflater(true).apply { setInput(Base64.getUrlDecoder().decode(fragment)) }
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        while (!inflater.finished()) out.write(buf, 0, inflater.inflate(buf))
        assertEquals(archiveJson(r, summary), out.toString(Charsets.UTF_8))

        // QR 버전 40·오류정정 L의 바이트 용량(2953)보다 넉넉히 작아야 차 화면에서 읽힌다.
        val url = reportUrl("https://example.com/web/", r, summary)
        assertTrue("url length ${url.length}", url.length < 1_800)
    }

    @Test
    fun dialNumber_keepsDigitsOnly() {
        assertEquals("15880000", Insurer("a", "1588-0000").dialNumber)
    }
}
