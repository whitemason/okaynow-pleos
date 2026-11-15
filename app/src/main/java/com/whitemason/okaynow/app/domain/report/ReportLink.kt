package com.whitemason.okaynow.app.domain.report

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * C9 QR에 담을 처리 기록. 원자료 + 요약 JSON을 raw deflate로 줄이고 base64url로 바꿔
 * `report.html#<값>`의 # 뒤에 넣는다. # 뒤는 서버로 전송되지 않아 기록이 폰 밖으로 나가지 않는다.
 * 폰 쪽은 report.html(whitemason/okaynow-pages)이 DecompressionStream("deflate-raw")로 푼다.
 */
fun reportFragment(report: AccidentReport, summary: ReportSummary): String {
    val bytes = archiveJson(report, summary).toByteArray(Charsets.UTF_8)
    val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    val out = ByteArrayOutputStream()
    try {
        deflater.setInput(bytes)
        deflater.finish()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    } finally {
        deflater.end()
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
}

/** [baseUrl]은 폰 페이지(whitemason/okaynow-pages) 주소(끝에 /). */
fun reportUrl(baseUrl: String, report: AccidentReport, summary: ReportSummary): String =
    baseUrl + "report.html#" + reportFragment(report, summary)
