package com.whitemason.okaynow.app.domain.report

/**
 * 의존성 없는 작은 JSON 작성기(순수 Kotlin, 단위 테스트에서도 동작). 문자열·숫자·불리언·null·목록·[obj]·[Raw]만 다룬다.
 */
object Json {
    /** 이미 JSON인 문자열을 그대로 넣을 때. */
    class Raw(val json: String)

    /** 객체. 다른 [obj] 안에 넣을 수 있게 [Raw]로 돌려준다. */
    fun obj(vararg entries: Pair<String, Any?>): Raw =
        Raw(entries.joinToString(",", "{", "}") { (k, v) -> "${string(k)}:${value(v)}" })

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Raw -> v.json
        is String -> string(v)
        is Boolean, is Int, is Long -> v.toString()
        is Double -> if (v.isFinite()) v.toString() else "null"
        is Float -> value(v.toDouble())
        is Iterable<*> -> v.joinToString(",", "[", "]") { value(it) }
        else -> string(v.toString())
    }

    private fun string(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
}
