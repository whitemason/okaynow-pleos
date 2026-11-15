package com.whitemason.okaynow.app.domain.common

/** 신호 수신 시각과 같은 단조 시계(밀리초). Android에서는 elapsedRealtime. */
fun interface Clock {
    fun nowMillis(): Long
}

/** 흐름 기록(상태 전이·부수 효과). Android에서는 Logcat. */
fun interface FlowLogger {
    fun log(message: String)
}
