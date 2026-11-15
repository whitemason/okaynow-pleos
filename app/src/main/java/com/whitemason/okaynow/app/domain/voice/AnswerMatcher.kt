package com.whitemason.okaynow.app.domain.voice

/**
 * 알아들은 문장을 [choices] 중 하나로 고른다. 공백·문장부호는 무시한다.
 * 여러 선택지의 표현이 함께 들어 있으면 가장 긴 표현이 맞은 쪽을 고른다
 * ("안 다쳤어"는 "다쳤어"보다 길어서 NOT_INJURED). 길이가 같으면 판단하지 않는다(null).
 * 한 글자 표현("응", "어", "네")은 그 글자만 말했을 때만 인정한다("틀어줘", "어디야"의 "어"는 대답이 아니다).
 */
fun matchAnswer(heard: String, choices: List<VoiceChoice>): VoiceChoice? {
    val text = heard.normalized()
    if (text.isEmpty()) return null
    val best = choices.map { choice ->
        choice to (choice.phrases.map { it.normalized() }.filter { it.isNotEmpty() && text.contains(it) }.maxOfOrNull { it.length } ?: 0)
    }.filter { it.second > 0 }.sortedByDescending { it.second }
    if (best.isEmpty()) return null
    if (best.size > 1 && best[0].second == best[1].second) return null
    return best[0].first
}

private fun String.normalized() = filter { it.isLetterOrDigit() }

/** 한 글자 표현은 그 글자만 말했을 때("네", "네네")만 맞는다. */
private fun String.contains(phrase: String) =
    if (phrase.length == 1) all { it == phrase[0] } else indexOf(phrase) >= 0
