package com.whitemason.okaynow.app.data.report

import android.content.Context
import android.util.Log
import com.whitemason.okaynow.app.domain.report.AccidentReport
import com.whitemason.okaynow.app.domain.report.ReportArchive
import com.whitemason.okaynow.app.domain.report.ReportSummary
import com.whitemason.okaynow.app.domain.report.archiveJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 원자료 + 요약을 앱 내부 저장소 files/reports/<사고 시각>.json에 보관한다(외부로 보내지 않는다).
 * 같은 사고는 같은 파일을 덮어쓴다(C8에서 만들고 C9에서 보험사 연락 여부를 더해 다시 저장).
 */
@Singleton
class FileReportArchive @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReportArchive {
    private val dir get() = File(context.filesDir, "reports")

    override suspend fun save(report: AccidentReport, summary: ReportSummary): Unit = withContext(Dispatchers.IO) {
        val file = File(dir.apply { mkdirs() }, "${report.occurredAtMillis}.json")
        file.writeText(archiveJson(report, summary))
        Log.i("ReportArchive", "saved ${file.name} (${summary.source.id})")
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) { files().size }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        val deleted = files().count { it.delete() }
        Log.i("ReportArchive", "cleared $deleted")
    }

    private fun files(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty()
}
