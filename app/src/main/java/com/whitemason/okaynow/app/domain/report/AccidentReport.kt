package com.whitemason.okaynow.app.domain.report

import com.whitemason.okaynow.app.domain.damage.CarZone
import com.whitemason.okaynow.app.domain.location.LocationFix
import com.whitemason.okaynow.app.domain.location.coordinates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 보험사 접수용 원자료. C8에서 만들고, 요약 문장([ReportSummary])과 함께 JSON으로 보관한다([toJson]).
 * C9 체크리스트와 폰 QR도 이 값을 쓴다. 사실만 담는다(추정·판단 없음).
 */
data class AccidentReport(
    /** 사고 처리를 시작한 시각(벽시계, epoch ms). */
    val occurredAtMillis: Long,
    val zone: ZoneId = ZoneId.systemDefault(),
    val location: LocationFix?,
    /** C2 답: true 다친 사람 있음, false 없음, null 응답 없음. */
    val injured: Boolean?,
    /** C3 119 안내를 거쳤는지. */
    val emergencyGuided: Boolean,
    /** C4 안전 확보를 "완료"로 마쳤는지. */
    val secureSceneDone: Boolean,
    /** C5 이상 항목("타이어: 공기압 낮음 · 주행 금지"). */
    val vehicleIssues: List<String>,
    /** C5에서 확인할 수 있었던 항목 이름. 비었으면 차량 신호를 확인하지 못했다. */
    val vehicleChecked: List<String>,
    val damageZones: List<CarZone>,
    val insurer: Insurer?,
    /** C8에서 보험사 다이얼 화면을 열었는지(발신 여부는 알 수 없다). */
    val insurerDialed: Boolean = false,
) {
    val occurredAtText: String
        get() = Instant.ofEpochMilli(occurredAtMillis).atZone(zone).format(DateTimeFormatter.ofPattern("M월 d일 a h시 mm분", Locale.KOREA))

    val locationText: String?
        get() = location?.let { it.address?.takeIf(String::isNotBlank)?.trim() ?: it.coordinates() }

    val damageText: String?
        get() = damageZones.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label }

    val vehicleText: String
        get() = when {
            vehicleChecked.isEmpty() -> "확인 못 함"
            vehicleIssues.isEmpty() -> "이상 없음"
            else -> vehicleIssues.joinToString(", ")
        }

    val injuryText: String
        get() = when (injured) {
            true -> "다친 사람 있음"
            false -> "다친 사람 없음"
            null -> "응답 없음"
        }
}

/** 접수 요약 문장과 만든 방법. */
data class ReportSummary(val text: String, val source: Source) {
    enum class Source(val id: String) { GLEO_LLM("gleo-llm"), TEMPLATE("template") }
}

/** LLM이 없거나 실패했을 때 쓰는 문장. 값이 없는 항목은 빼고, 지어내지 않는다. */
fun AccidentReport.templateSummary(): String = buildList {
    add(listOfNotNull(occurredAtText, locationText?.let { "$it 에서" }).joinToString(" ") + " 사고가 났습니다.")
    add("$injuryText${if (emergencyGuided) ", 119 안내를 받았습니다." else "."}")
    damageText?.let { add("파손 부위는 $it 입니다.") }
    add("차량 점검 결과는 $vehicleText 입니다.")
}.joinToString(" ")

/** Gleo LLM에 보낼 요청. 원자료를 그대로 주고 없는 내용을 만들지 않게 한다. */
fun AccidentReport.llmPrompt(): String = """
    |아래는 자동차 사고 정보(JSON)입니다. 운전자가 보험사 상담원에게 전화로 그대로 읽어 줄 수 있게
    |한국어 존댓말 2~3문장으로 요약해 주세요. JSON에 없는 내용은 절대 만들지 말고, 시각·주소·좌표·부위 이름은 그대로 쓰세요.
    |요약 문장만 답하세요.
    |
    |${toJson()}
""".trimMargin()

/** 원자료 JSON(보관·QR 공용). 키는 영어, 값은 화면에 보이는 한국어 그대로. */
fun AccidentReport.toJson(): String = Json.obj(
    "version" to 1,
    "occurredAt" to Instant.ofEpochMilli(occurredAtMillis).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")),
    "occurredAtText" to occurredAtText,
    "location" to location?.let {
        Json.obj("latitude" to it.latitude, "longitude" to it.longitude, "address" to it.address, "text" to locationText)
    },
    "injury" to when (injured) { true -> "yes"; false -> "no"; null -> "no_response" },
    "emergencyGuided" to emergencyGuided,
    "secureSceneDone" to secureSceneDone,
    "vehicle" to Json.obj("checked" to vehicleChecked, "issues" to vehicleIssues),
    "damageZones" to damageZones.map { Json.obj("id" to it.id, "label" to it.label) },
    "insurer" to insurer?.let { Json.obj("name" to it.name, "phone" to it.phone) },
    "insurerDialed" to insurerDialed,
).json

/** 보관 파일·QR에 담는 전체: 원자료 + 요약. */
fun archiveJson(report: AccidentReport, summary: ReportSummary): String = Json.obj(
    "raw" to Json.Raw(report.toJson()),
    "summary" to Json.obj("text" to summary.text, "source" to summary.source.id),
).json
