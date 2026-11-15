package com.whitemason.okaynow.app.domain.report

import kotlinx.coroutines.flow.Flow

/** C8에서 전화할 보험사(사고접수·긴급출동 번호). C0 설정에서 목록으로 고르거나 직접 입력한다. */
data class Insurer(val name: String, val phone: String) {
    /** 다이얼에 넘길 숫자만. */
    val dialNumber: String get() = phone.filter { it.isDigit() || it == '+' }
}

/** 등록한 보험사 저장소(구현: DataStore). */
interface InsurerRepository {
    val insurer: Flow<Insurer?>
    suspend fun save(insurer: Insurer?)
}

/** 접수 요약 문장을 만든다(구현: Gleo LLM ON_DEVICE). 실패·시간 초과면 null. */
fun interface SummaryWriter {
    suspend fun summarize(prompt: String): String?
}

/** 원자료 + 요약을 보관한다(구현: 앱 내부 파일). 차 밖으로 보내지 않는다. */
fun interface ReportArchive {
    suspend fun save(report: AccidentReport, summary: ReportSummary)
    /** 보관 중인 사고 기록 수. */
    suspend fun count(): Int = 0
    /** 보관한 사고 기록을 모두 지운다(C0 개인정보 안내). */
    suspend fun clear() {}
}
