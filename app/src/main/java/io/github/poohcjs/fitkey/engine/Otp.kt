package io.github.poohcjs.fitkey.engine

/**
 * 알림 글에서 인증번호 찾기 ("[네이버] 인증번호 [123456]를 입력해 주세요" → 123456).
 * 인증·코드 같은 말이 있는 글에서만 찾고, 금액·날짜·시간처럼 보이는 숫자는 뺀다.
 */
object Otp {
    private val KEYWORDS = listOf(
        "인증", "확인번호", "승인번호", "보안코드", "보안 코드", "코드", "일회용", "본인확인", "otp", "code", "verification", "verify", "pin", "passcode",
    )
    private val NUMBER = Regex("""(?<![0-9])([0-9]{4,8})(?![0-9])""")
    private val NEAR = Regex("""(인증\s*번호|확인\s*번호|승인\s*번호|코드|code|otp|pin)""", RegexOption.IGNORE_CASE)

    fun extract(raw: String?): String? {
        val text = raw?.let { toAsciiDigits(it) } ?: return null
        val low = text.lowercase()
        if (KEYWORDS.none { it in low }) return null
        val cands = NUMBER.findAll(text).filter { m -> plausible(text, m.range) }.toList()
        if (cands.isEmpty()) return null
        // 괄호 안 숫자 ([123456], (1234)) → '인증번호' 바로 뒤 숫자 → 첫 숫자
        cands.firstOrNull { m ->
            val before = text.getOrNull(m.range.first - 1)
            val after = text.getOrNull(m.range.last + 1)
            before in listOf('[', '(', '【', '<') && after in listOf(']', ')', '】', '>')
        }?.let { return it.value }
        val near = NEAR.find(text)
        if (near != null) cands.firstOrNull { it.range.first > near.range.last && it.range.first - near.range.last < 25 }?.let { return it.value }
        return cands.first().value
    }

    /** 금액(원·₩·,), 날짜·시간(년·월·일·시·분·초), 전화번호 일부처럼 보이면 아님 */
    private fun plausible(t: String, r: IntRange): Boolean {
        val after = t.substring(r.last + 1).trimStart().take(2)
        val before = t.substring(0, r.first).trimEnd().takeLast(2)
        if (listOf("원", "년", "월", "일", "시", "분", "초", "개", "건", "명", "%").any { after.startsWith(it) }) return false
        if (before.endsWith("₩") || before.endsWith("$")) return false
        if (t.getOrNull(r.last + 1) == ',' || t.getOrNull(r.first - 1) == ',') return false
        if (t.getOrNull(r.first - 1) == '-' && t.getOrNull(r.first - 2)?.isDigit() == true) return false   // 010-1234-5678
        if (t.getOrNull(r.last + 1) == '-' && t.getOrNull(r.last + 2)?.isDigit() == true) return false
        if (t.getOrNull(r.first - 1) == '.' || t.getOrNull(r.last + 1) == '.' && t.getOrNull(r.last + 2)?.isDigit() == true) return false
        return true
    }

    private fun toAsciiDigits(s: String): String = buildString(s.length) {
        // 전각 문자(０-９, Ａ-Ｚ 등)를 보통 글자로
        for (c in s) append(if (c in '\uFF01'..'\uFF5E') (c.code - 0xFEE0).toChar() else c)
    }
}
