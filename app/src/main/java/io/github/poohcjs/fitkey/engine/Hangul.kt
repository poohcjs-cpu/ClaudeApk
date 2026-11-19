package io.github.poohcjs.fitkey.engine

/**
 * 두벌식 한글 조합.
 *
 * 낱말 하나를 이루는 자모를 순서대로 모아 두고, 글자를 처음부터 다시 조합한다.
 * 그래서 지우기(백스페이스)는 마지막 자모 하나만 빼고 다시 조합하면 된다 (예: '닭' → '달' → '다' → 'ㄷ').
 * 자판의 글쇠(쿼티 글자)와 자모는 1:1이라, 사전도 글쇠 문자열로 다룬다 ('닭' = "ekfr").
 */
object Hangul {
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private val JONG = listOf(
        null, 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ',
        'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    /** 겹받침: (앞, 뒤) → 겹받침 */
    private val DOUBLE_JONG = mapOf(
        ('ㄱ' to 'ㅅ') to 'ㄳ', ('ㄴ' to 'ㅈ') to 'ㄵ', ('ㄴ' to 'ㅎ') to 'ㄶ', ('ㄹ' to 'ㄱ') to 'ㄺ', ('ㄹ' to 'ㅁ') to 'ㄻ',
        ('ㄹ' to 'ㅂ') to 'ㄼ', ('ㄹ' to 'ㅅ') to 'ㄽ', ('ㄹ' to 'ㅌ') to 'ㄾ', ('ㄹ' to 'ㅍ') to 'ㄿ', ('ㄹ' to 'ㅎ') to 'ㅀ',
        ('ㅂ' to 'ㅅ') to 'ㅄ',
    )
    private val SPLIT_JONG = DOUBLE_JONG.entries.associate { (k, v) -> v to k }

    /** 겹모음: (앞, 뒤) → 겹모음 */
    private val DOUBLE_JUNG = mapOf(
        ('ㅗ' to 'ㅏ') to 'ㅘ', ('ㅗ' to 'ㅐ') to 'ㅙ', ('ㅗ' to 'ㅣ') to 'ㅚ', ('ㅜ' to 'ㅓ') to 'ㅝ', ('ㅜ' to 'ㅔ') to 'ㅞ',
        ('ㅜ' to 'ㅣ') to 'ㅟ', ('ㅡ' to 'ㅣ') to 'ㅢ',
    )

    /** 두벌식 자판: 쿼티 글쇠 → 자모 (대문자 = Shift: 쌍자음·ㅒㅖ) */
    val KEY_TO_JAMO: Map<Char, Char> = mapOf(
        'q' to 'ㅂ', 'Q' to 'ㅃ', 'w' to 'ㅈ', 'W' to 'ㅉ', 'e' to 'ㄷ', 'E' to 'ㄸ', 'r' to 'ㄱ', 'R' to 'ㄲ', 't' to 'ㅅ', 'T' to 'ㅆ',
        'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'O' to 'ㅒ', 'p' to 'ㅔ', 'P' to 'ㅖ',
        'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ', 'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
        'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ', 'b' to 'ㅠ', 'n' to 'ㅜ', 'm' to 'ㅡ',
    )
    val JAMO_TO_KEY: Map<Char, Char> = KEY_TO_JAMO.entries.associate { (k, v) -> v to k }

    /** Shift로 다른 자모가 나오는 글쇠 */
    val SHIFTABLE = setOf('q', 'w', 'e', 'r', 't', 'o', 'p')

    fun isVowel(c: Char) = c in 'ㅏ'..'ㅣ'
    fun isConsonant(c: Char) = c in 'ㄱ'..'ㅎ'
    fun isJamo(c: Char) = c in 'ㄱ'..'ㅣ'
    fun isSyllable(c: Char) = c in '가'..'힣'

    /** 글쇠 하나 → 자모 (Shift가 의미 없는 글쇠는 소문자와 같다) */
    fun jamoOf(key: Char): Char? = KEY_TO_JAMO[key] ?: KEY_TO_JAMO[key.lowercaseChar()]

    /** 글쇠 문자열("ekfr") → 글자("닭") */
    fun composeKeys(keys: CharSequence): String = compose(keys.mapNotNull { jamoOf(it) })

    /** 자모 목록 → 글자 */
    fun compose(jamos: List<Char>): String {
        val out = StringBuilder()
        var cho: Char? = null
        var jung: Char? = null
        var jong: Char? = null

        fun flush() {
            when {
                cho != null && jung != null -> out.append(syllable(cho!!, jung!!, jong))
                cho != null -> out.append(cho)
                jung != null -> out.append(jung)
            }
            cho = null; jung = null; jong = null
        }

        for (j in jamos) {
            if (isConsonant(j)) {
                when {
                    jung == null && cho == null -> cho = j
                    jung == null -> { flush(); cho = j }               // 자음 다음 자음: 앞 자음은 홀로
                    cho == null -> { flush(); cho = j }                // 홀로 쓴 모음 다음 자음
                    jong == null -> if (canBeJong(j)) jong = j else { flush(); cho = j }
                    else -> {
                        val d = DOUBLE_JONG[jong!! to j]
                        if (d != null) jong = d else { flush(); cho = j }
                    }
                }
            } else if (isVowel(j)) {
                when {
                    jung == null -> jung = j                           // 첫 모음 (자음 뒤 또는 홀로)
                    jong == null -> {
                        val d = DOUBLE_JUNG[jung!! to j]
                        if (d != null) jung = d else { flush(); jung = j }
                    }
                    else -> {
                        // 받침이 다음 글자의 첫소리로 넘어간다: '닭'+ㅣ → '달기', '각'+ㅏ → '가가'
                        val split = SPLIT_JONG[jong!!]
                        val next: Char
                        if (split != null) { jong = split.first; next = split.second } else { next = jong!!; jong = null }
                        flush()
                        cho = next; jung = j
                    }
                }
            } else {
                flush()
                out.append(j)
            }
        }
        flush()
        return out.toString()
    }

    private fun canBeJong(c: Char) = c in JONG

    fun syllable(cho: Char, jung: Char, jong: Char?): Char {
        val ci = CHO.indexOf(cho)
        val ji = JUNG.indexOf(jung)
        val ki = JONG.indexOf(jong).coerceAtLeast(0)
        if (ci < 0 || ji < 0) return cho
        return ('가'.code + (ci * 21 + ji) * 28 + ki).toChar()
    }

    private val JUNG_KEYS = listOf("k", "o", "i", "O", "j", "p", "u", "P", "h", "hk", "ho", "hl", "y", "n", "nj", "np", "nl", "b", "m", "ml", "l")
    private val JONG_KEYS = listOf("", "r", "R", "rt", "s", "sw", "sg", "e", "f", "fr", "fa", "fq", "ft", "fx", "fv", "fg", "a", "q", "qt", "t", "T", "d", "w", "c", "z", "x", "v", "g")
    private val COMPAT_KEYS = mapOf(
        'ㄳ' to "rt", 'ㄵ' to "sw", 'ㄶ' to "sg", 'ㄺ' to "fr", 'ㄻ' to "fa", 'ㄼ' to "fq", 'ㄽ' to "ft", 'ㄾ' to "fx", 'ㄿ' to "fv", 'ㅀ' to "fg",
        'ㅄ' to "qt", 'ㅘ' to "hk", 'ㅙ' to "ho", 'ㅚ' to "hl", 'ㅝ' to "nj", 'ㅞ' to "np", 'ㅟ' to "nl", 'ㅢ' to "ml",
    )

    /** 글자 → 글쇠 문자열 ("닭" → "ekfr"). 한글이 아닌 글자가 있으면 null */
    fun toKeys(text: String): String? {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                isSyllable(ch) -> {
                    val i = ch.code - '가'.code
                    sb.append(JAMO_TO_KEY[CHO[i / 588]]).append(JUNG_KEYS[(i % 588) / 28]).append(JONG_KEYS[i % 28])
                }
                ch in JAMO_TO_KEY -> sb.append(JAMO_TO_KEY[ch])
                ch in COMPAT_KEYS -> sb.append(COMPAT_KEYS[ch])
                else -> return null
            }
        }
        return sb.toString()
    }

    /** 자모가 홀로 남은 글자가 있는지 ('안녕하세ㅛ'): 오타일 가능성이 크다. 'ㅋㅋ', 'ㅠㅠ'처럼 자모만으로 된 말은 제외 */
    fun hasBrokenSyllable(text: String): Boolean =
        text.any { isSyllable(it) } && text.any { isJamo(it) }
}
