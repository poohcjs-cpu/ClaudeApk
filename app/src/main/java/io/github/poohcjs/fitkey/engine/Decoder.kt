package io.github.poohcjs.fitkey.engine

/** 글쇠 한 번 누름: 화면에서 고른 글쇠, 실제로 누른 자리, 낸 글자 */
data class Stroke(
    val key: Key,
    /** 사전 글쇠 문자 (한글: Shift 쌍자음은 대문자, 영어: 소문자) */
    val keyChar: Char,
    /** 화면에 나간 글자 (한글은 자모, 영어는 대소문자 그대로) */
    val output: Char,
    val ux: Float,
    val uy: Float,
    val shifted: Boolean,
)

data class Candidate(val key: String, val word: String, val score: Float, val edits: Int, val inDict: Boolean)

/** 고칠 말 하나와 그 근거 */
data class Correction(val from: String, val to: String, val toKey: String, val strokes: List<Stroke>)

/**
 * 오타 추측: 누른 자리들과 사전을 함께 보고, 가장 그럴듯한 낱말을 찾는다.
 *
 *  점수 = Σ(그 자리를 눌렀을 때 그 글쇠였을 가능성) + (그 낱말을 쓸 가능성)
 *
 * 예: '안녕하세ㅛ'의 마지막 터치가 ㅛ와 ㅗ 사이였다면, 'ㅗ'로 바꾼 '안녕하세요'가 훨씬 그럴듯하다.
 * 글자를 바꾸는 오타(옆 글쇠 누름)와 Shift 실수(ㄱ↔ㄲ)를 고친다.
 */
class Decoder(private val touch: TouchModel) {

    private data class Beam(val prefix: String, val spatial: Float, val edits: Int)

    /** 누른 자리마다 그럴듯한 글쇠를 골라, 사전 낱말의 앞부분이 되는 글쇠 문자열들 (그럴듯한 순) */
    private fun beams(
        strokes: List<Stroke>, lang: Lang, dict: Dictionary?, user: UserWords?, layout: KeyboardLayout, beamWidth: Int,
    ): List<Beam> {
        if (strokes.isEmpty()) return emptyList()
        val maxEdits = if (strokes.size <= 4) 1 else 2
        var beams = listOf(Beam("", 0f, 0))
        for (s in strokes) {
            val cands = touch.candidates(layout, s.ux, s.uy, max = 4).toMutableList()
            if (cands.none { it.first.letter == s.key.letter }) cands += s.key to touch.logLikelihood(s.key, s.ux, s.uy)
            val best = cands.maxOf { it.second }
            val next = ArrayList<Beam>()
            for (b in beams) for ((k, ll) in cands) {
                for ((ch, pen) in charsFor(k, s, lang)) {
                    val edit = if (ch == s.keyChar) 0 else 1
                    if (b.edits + edit > maxEdits) continue
                    val p = b.prefix + ch
                    if (!(dict?.hasPrefix(p) ?: false) && !(user?.hasPrefix(lang, p) ?: false)) continue
                    next += Beam(p, b.spatial + (ll - best) + pen, b.edits + edit)
                }
            }
            if (next.isEmpty()) return emptyList()
            beams = next.sortedByDescending { it.spatial }.take(beamWidth)
        }
        return beams
    }

    /** 친 글쇠 수와 같은 길이의 사전 낱말 후보 (옆 글쇠·Shift 실수를 고친 것) */
    fun candidates(
        strokes: List<Stroke>, lang: Lang, dict: Dictionary?, user: UserWords?, layout: KeyboardLayout, beamWidth: Int = 48,
    ): List<Candidate> {
        return beams(strokes, lang, dict, user, layout, beamWidth).mapNotNull { b ->
            val dictLp = dict?.logProb(b.prefix)
            val userN = user?.count(lang, b.prefix) ?: 0
            val lp = when {
                dictLp != null && userN > 0 -> dictLp + 1f
                dictLp != null -> dictLp
                userN >= 2 -> (dict?.unknownLogProb ?: -20f) + 6f + minOf(userN, 10)
                else -> return@mapNotNull null
            }
            Candidate(b.prefix, dict?.wordOf(b.prefix) ?: b.prefix, b.spatial + lp, b.edits, dictLp != null)
        }.sortedByDescending { it.score }
    }

    /**
     * 치는 중인 낱말이 이어질 말: 앞부분을 고친 것까지 함께 본다.
     * 예: '감사합니다'를 치다 ㅎ 대신 ㅗ를 눌러 '감사ㅘㅂ니'가 되어도, 낱말을 다 치기 전에 '감사합니다'를 보여 준다.
     * 다 친 낱말(같은 길이)은 [candidates]가 맡는다.
     */
    fun completions(
        strokes: List<Stroke>, lang: Lang, dict: Dictionary?, user: UserWords?, layout: KeyboardLayout, max: Int = 3,
    ): List<Candidate> {
        if (dict == null || strokes.size < 2) return emptyList()
        val out = HashMap<String, Candidate>()
        fun put(c: Candidate) { if ((out[c.key]?.score ?: Float.NEGATIVE_INFINITY) < c.score) out[c.key] = c }
        for (b in beams(strokes, lang, dict, user, layout, 48).take(10)) {
            // 두 글쇠만 친 낱말은 친 그대로 이어지는 말만 (고친 앞부분은 너무 넓다)
            if (b.edits > 0 && strokes.size < 3) continue
            for ((k, n) in dict.completions(b.prefix, 3, scanLimit = 1500)) {
                if (k.length == b.prefix.length || n <= 0) continue
                val lp = dict.logProb(k) ?: continue
                put(Candidate(k, dict.wordOf(k), b.spatial + lp, b.edits, true))
            }
            if (user != null) for ((k, n) in user.completions(lang, b.prefix, 2)) {
                if (k.length == b.prefix.length) continue
                val lp = dict.logProb(k)?.let { it + 1f } ?: (dict.unknownLogProb + 6f + minOf(n, 10))
                put(Candidate(k, dict.wordOf(k), b.spatial + lp, b.edits, dict.count(k) > 0))
            }
        }
        return out.values.sortedByDescending { it.score }.take(max)
    }

    /** 글쇠 하나가 낼 수 있는 사전 글쇠 문자 (+ 덧붙는 감점) */
    private fun charsFor(k: Key, s: Stroke, lang: Lang): List<Pair<Char, Float>> {
        val l = k.letter ?: return emptyList()
        if (lang == Lang.EN) return listOf(l to 0f)
        if (l !in Hangul.SHIFTABLE) return listOf(l to 0f)
        val up = l.uppercaseChar()
        // Shift를 눌렀는지 안 눌렀는지도 틀릴 수 있다 (ㄱ↔ㄲ): 조금 감점
        return if (s.shifted) listOf(up to 0f, l to -2.5f) else listOf(l to 0f, up to -3f)
    }

    /** 그대로 친 낱말의 점수 (비교 기준) */
    fun typedScore(strokes: List<Stroke>, lang: Lang, dict: Dictionary?, user: UserWords?, layout: KeyboardLayout): Float {
        val key = keysOf(strokes)
        var spatial = 0f
        for (s in strokes) {
            val cands = touch.candidates(layout, s.ux, s.uy, max = 4)
            val best = (cands.map { it.second } + touch.logLikelihood(s.key, s.ux, s.uy)).max()
            spatial += touch.logLikelihood(s.key, s.ux, s.uy) - best
        }
        val lp = dict?.logProb(key) ?: if ((user?.count(lang, key) ?: 0) >= 2) -12f else (dict?.unknownLogProb ?: -20f)
        return spatial + lp
    }

    /**
     * 스페이스를 눌렀을 때 자동으로 고칠지. 확실할 때만 고친다:
     *  - 친 그대로가 사전·내 낱말에 있으면 고치지 않는다 (한국어는 '사전 낱말 + 조사'도 맞는 말로 본다)
     *  - 고친 말이 친 그대로보다 훨씬(약 50배 이상) 그럴듯해야 한다
     */
    fun autoCorrect(
        strokes: List<Stroke>, typed: String, lang: Lang, dict: Dictionary?, user: UserWords?, layout: KeyboardLayout,
    ): Correction? {
        if (dict == null || strokes.size < 2) return null
        val key = keysOf(strokes)
        if (isValid(typed, key, lang, dict, user)) return null
        val best = candidates(strokes, lang, dict, user, layout).firstOrNull { it.key != key } ?: return null
        val margin = best.score - typedScore(strokes, lang, dict, user, layout)
        val broken = lang == Lang.KO && Hangul.hasBrokenSyllable(typed)
        if (margin < (if (broken) 2.5f else 4f)) return null
        return Correction(typed, matchCase(best.word, typed, lang), best.key, strokes)
    }

    fun isValid(typed: String, key: String, lang: Lang, dict: Dictionary, user: UserWords?): Boolean {
        if (dict.count(key) > 0 || (user?.known(lang, key) == true)) return true
        if (lang == Lang.KO) {
            if (typed.all { Hangul.isJamo(it) }) return true             // ㅋㅋ, ㅠㅠ, ㅇㅇ
            for (p in PARTICLES) {
                if (typed.length > p.length && typed.endsWith(p)) {
                    val stem = Hangul.toKeys(typed.dropLast(p.length)) ?: continue
                    if (dict.count(stem) > 0 || user?.known(lang, stem) == true) return true
                }
            }
        }
        return false
    }

    companion object {
        fun keysOf(strokes: List<Stroke>): String = buildString { strokes.forEach { append(it.keyChar) } }

        /** 친 낱말의 대소문자 모양을 따른다 (Hello → Hello, HELLO → HELLO) */
        fun matchCase(word: String, typed: String, lang: Lang): String {
            if (lang != Lang.EN || typed.isEmpty()) return word
            return when {
                typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
                typed[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
                else -> word
            }
        }

        /** 사전 낱말 뒤에 붙어도 맞는 말로 보는 조사·어미 (긴 것부터) */
        val PARTICLES = listOf(
            "에서부터", "에서는", "에서도", "으로는", "으로도", "에게는", "한테는", "까지는", "부터는", "이라도", "이라고", "이라는", "이에요",
            "입니다", "이었어", "였어요", "이랑은", "에서", "에게", "한테", "으로", "까지", "부터", "처럼", "보다", "마다", "조차", "밖에", "이나",
            "이랑", "라고", "라는", "예요", "이야", "이다", "이지", "인데", "에는", "에도", "와는", "과는", "로는", "로도", "들이", "들은", "들을",
            "들도", "들의", "들", "은", "는", "이", "가", "을", "를", "의", "에", "로", "와", "과", "도", "만", "요", "께", "랑", "나", "야",
        )
    }
}
