package io.github.poohcjs.fitkey.ui

import io.github.poohcjs.fitkey.engine.Hangul
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyKind
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.TouchModel
import kotlin.math.sqrt

/**
 * 터치 보정: 문장을 평소처럼 쳐 보게 한다 (틀리면 지우고 고쳐도 된다).
 * 문장을 다 치면, 친 글쇠들을 문장의 글쇠들과 맞춰 보고(편집 거리 정렬) '노린 글쇠 ↔ 실제로 누른 자리'를 배운다.
 * 지우고 다시 친 경우, 지워진 터치도 그 자리에서 노린 글쇠의 자료로 쓴다 (실수한 터치가 가장 귀한 자료라서).
 *
 * 문장 고르기: 처음 3문장(모든 자모가 들어간 문장)으로 기본 습관을 잰 뒤에는, 자료가 부족하거나 자주 빗나가는 글쇠가
 * 많이 들어간 문장을 골라 낸다. 모든 글쇠를 충분히 재면(또는 최대 문장 수가 되면) 끝난다. 중간에 그만둬도 된다.
 */
class Calibration(private val touch: TouchModel, private val layout: KeyboardLayout) {

    data class Sentence(val text: String, val lang: Lang)
    /** 친 것 하나: 화면에서 누른 글쇠(글자 또는 스페이스)와 누른 자리 */
    data class Tap(val key: Key, val ux: Float, val uy: Float, val shifted: Boolean) {
        val isSpace get() = key.kind == KeyKind.SPACE
    }
    data class Sample(val key: Key, val ux: Float, val uy: Float)
    /** 글쇠 하나의 결과: 잰 횟수, 화면 글쇠를 벗어난 횟수, 배운 치우침 */
    data class KeyReport(val letter: Char, val n: Int, val misses: Int, val offset: Pair<Float, Float>) {
        val missRate: Float get() = if (n == 0) 0f else misses.toFloat() / n
    }

    /** 처음에 꼭 치는 문장 (모든 자모가 들어 있음) */
    private val fixed = listOf(
        Sentence("다람쥐 헌 쳇바퀴에 타고파", Lang.KO),
        Sentence("키스의 고유조건은 입술끼리 만나야 하고 특별한 기술은 필요치 않다", Lang.KO),
        Sentence("오늘 저녁에 뭐 먹을지 같이 정해요", Lang.KO),
    )

    /** 그다음에 골라 낼 문장 (일상 대화체, 모든 글쇠가 고르게 나오도록) */
    private val pool = listOf(
        "내일 아침 일찍 회의가 있어서 먼저 들어갈게요", "택배가 도착하면 문 앞에 놓아 주세요", "주말에 날씨가 좋으면 공원에 산책하러 가자",
        "커피 한 잔 마시면서 잠깐 쉬었다 할까", "휴대폰 배터리가 얼마 안 남았어요", "퇴근하고 마트에서 우유랑 계란 좀 사 올게",
        "아이가 열이 나서 병원에 다녀왔어요", "지난번에 보내 준 사진 정말 예쁘더라", "치킨 시킬까 피자 시킬까 고민 중이야",
        "다음 주 금요일 저녁 일곱 시에 만나요", "비가 많이 오니까 우산 꼭 챙겨", "택시 타고 가면 십오 분쯤 걸릴 거예요",
        "얘기 들었어 걔네 다음 달에 결혼한대", "냉장고에 있는 김치찌개 데워 먹어", "교통카드 충전하는 걸 깜빡했네",
        "쌍둥이 동생이 뛰어와서 깜짝 놀랐어", "빨리 짜장면이랑 탕수육 먹고 싶다", "예약 확인 부탁드립니다",
        "투표 결과는 내일 오후에 발표합니다", "휴가 계획 세웠어 제주도 같이 갈래", "프린터 토너가 다 떨어졌어요",
        "티켓 예매 성공했다 ㅋㅋ", "코트 주머니에 차 키가 있을 거야", "퀴즈 정답은 칠판 오른쪽에 적어 뒀어",
        "팥빙수 먹으러 카페 갈 사람", "축구 경기 보면서 치킨 시켜 먹자", "엄마 생신 선물로 꽃다발 어때",
        "캠핑장 예약은 토요일부터 일요일까지야", "편의점에서 콜라랑 과자 좀 사다 줘", "유튜브에 귀여운 고양이 영상이 올라왔어",
        "야식으로 족발이랑 보쌈 시켜 먹을래", "뉴스에서 내일 휴일이라고 하던데 맞아", "약국에서 감기약 사 가지고 갈게",
    ).map { Sentence(it, Lang.KO) } + listOf(
        "please call me back when you get this message", "pack my box with five dozen liquor jugs",
        "see you tomorrow at the coffee shop", "the quick brown fox jumps over the lazy dog",
    ).map { Sentence(it, Lang.EN) }

    /** 마친(또는 건너뛴) 문장 수 */
    var sentence = 0
        private set
    var current: Sentence? = fixed.first()
        private set
    private val used = HashSet<Sentence>()
    val typed = ArrayList<Tap>()
    /** 지운 터치: 그 자리(몇 번째)에서 다시 치기 전에 쳤던 것 */
    private val attempts = HashMap<Int, MutableList<Tap>>()
    var shift = false
        private set
    val samples = ArrayList<Sample>()
    var misses = 0
        private set
    /** 글쇠별 [잰 횟수, 빗나간 횟수] */
    private val perKey = HashMap<Char, IntArray>()
    val done: Boolean get() = current == null

    /** 충분히 잰 글쇠 수 (26개 중) */
    val covered: Int get() = layout.letters.count { (perKey[it.letter]?.get(0) ?: 0) >= MIN_SAMPLES }
    val totalLetters: Int get() = layout.letters.size

    private fun isProblem(c: Char): Boolean {
        val k = perKey[c] ?: return false
        return k[0] >= 3 && k[1].toFloat() / k[0] >= 0.2f
    }

    /** 이 글쇠에 앞으로 더 필요한 자료 수 (자주 빗나가는 글쇠는 더 많이) */
    private fun need(c: Char): Float {
        val n = perKey[c]?.get(0) ?: 0
        return if (isProblem(c)) (PROBLEM_SAMPLES - n).coerceAtLeast(0) * 1.5f else (MIN_SAMPLES - n).coerceAtLeast(0).toFloat()
    }

    /** 다음 문장 고르기: 필요한 글쇠를 많이 채워 주는 문장 (너무 긴 문장은 조금 덜) */
    private fun pickNext(): Sentence? {
        if (sentence < fixed.size) return fixed[sentence]
        val enoughAll = layout.letters.all { k -> need(k.letter!!) <= 0f }
        if (enoughAll || sentence >= MAX_SENTENCES) return null
        return pool.filter { it !in used }.maxByOrNull { s ->
            val counts = targetKeys(s).filter { it != ' ' }.lowercase().groupingBy { it }.eachCount()
            counts.entries.sumOf { (c, n) -> minOf(n.toFloat(), need(c)).toDouble() * (if (isProblem(c)) 1.5 else 1.0) } / (1 + targetKeys(s).length / 60.0)
        }
    }

    /** 크기·위치 추천에 쓰는 글쇠별 자료 */
    fun keyStats(): List<io.github.poohcjs.fitkey.engine.SizeAdvisor.KeyStat> = layout.letters.map { k ->
        val c = k.letter!!
        val v = perKey[c] ?: intArrayOf(0, 0)
        val (ox, oy) = touch.offset(c)
        io.github.poohcjs.fitkey.engine.SizeAdvisor.KeyStat(c, k.cx, k.row, v[0], v[1], ox, oy)
    }

    /** 글자 줄 번호 (위·가운데·아래) */
    val letterRows: List<Int> get() = layout.letters.map { it.row }.distinct().sorted()

    /** 글쇠별 결과 (보정이 많이 필요한 순) */
    fun report(): List<KeyReport> = layout.letters.map { k ->
        val c = k.letter!!
        val v = perKey[c] ?: intArrayOf(0, 0)
        KeyReport(c, v[0], v[1], touch.offset(c))
    }.sortedWith(compareByDescending<KeyReport> { it.missRate }.thenByDescending { kotlin.math.hypot(it.offset.first, it.offset.second) })

    /** 문장의 글쇠들 (' ' = 스페이스, 쌍자음은 대문자) */
    fun targetKeys(s: Sentence): String = buildString {
        for (ch in s.text) when {
            ch == ' ' -> append(' ')
            s.lang == Lang.KO -> Hangul.toKeys(ch.toString())?.let { append(it) }
            else -> append(ch.lowercaseChar())
        }
    }

    /** 지금까지 친 글쇠 문자열 (' ' = 스페이스) */
    private fun typedKeys(): String = buildString {
        for (t in typed) append(if (t.isSpace) ' ' else keyChar(t))
    }

    private fun keyChar(t: Tap): Char {
        val l = t.key.letter ?: return '?'
        return if (current?.lang == Lang.KO && t.shifted && l in Hangul.SHIFTABLE) l.uppercaseChar() else l
    }

    /** 화면에 보여 줄 친 글 */
    fun typedText(): String {
        val s = current ?: return ""
        return typedKeys().split(' ').joinToString(" ") { w -> if (s.lang == Lang.KO) Hangul.composeKeys(w) else w }
    }

    /** 지금까지 친 것이 문장 앞부분과 같으면 다음에 칠 글쇠 (화면에 테두리로 보여 준다) */
    fun nextKey(): Char? {
        val s = current ?: return null
        val t = targetKeys(s)
        val k = typedKeys()
        return if (t.startsWith(k) && k.length < t.length) t[k.length] else null
    }

    val onTrack: Boolean get() = current?.let { targetKeys(it).startsWith(typedKeys()) } ?: true
    val complete: Boolean get() = current?.let { typedKeys() == targetKeys(it) } ?: false
    /** 다음 문장으로 넘어가도 될 만큼 쳤는지 (오타가 남아 있어도 됨) */
    val enough: Boolean get() = current?.let { typed.size >= targetKeys(it).length * 0.6f } ?: false

    /** 글쇠를 눌렀다. 문장을 정확히 다 치면 저절로 다음 문장으로 넘어간다 (true를 돌려준다) */
    fun press(key: Key, ux: Float, uy: Float): Boolean {
        if (done) return false
        when (key.kind) {
            KeyKind.LETTER -> { typed += Tap(key, ux, uy, shift); shift = false }
            KeyKind.SPACE -> typed += Tap(key, ux, uy, false)
            KeyKind.SHIFT -> shift = !shift
            KeyKind.BACKSPACE -> if (typed.isNotEmpty()) {
                val gone = typed.removeAt(typed.lastIndex)
                if (!gone.isSpace) attempts.getOrPut(typed.size) { mutableListOf() } += gone
            }
            else -> Unit
        }
        if (complete) { next(); return true }
        return false
    }

    fun backspace() {
        if (typed.isNotEmpty()) {
            val gone = typed.removeAt(typed.lastIndex)
            if (!gone.isSpace) attempts.getOrPut(typed.size) { mutableListOf() } += gone
        }
    }

    /** 이 문장을 마치고 배운 뒤 다음 문장으로 */
    fun next() {
        val s = current ?: return
        learnFrom(targetKeys(s))
        advance(s)
    }

    /** 이 문장은 배우지 않고 넘어가기 */
    fun skip() {
        advance(current ?: return)
    }

    /** 여기까지로 끝내기 (지금 치던 문장은 친 만큼 배운다) */
    fun finish() {
        val s = current ?: return
        if (typed.size >= 4) learnFrom(targetKeys(s))
        current = null
        typed.clear(); attempts.clear(); shift = false
    }

    private fun advance(s: Sentence) {
        used += s
        sentence++
        typed.clear(); attempts.clear(); shift = false
        current = pickNext()
    }

    private fun dist(t: Tap, target: Key): Float {
        val dx = t.ux - target.cx
        val dy = (t.uy - target.cy) * touch.aspect
        return sqrt(dx * dx + dy * dy)
    }

    /** 친 글쇠들과 문장 글쇠들을 맞춰 보고 (편집 거리), 짝이 된 터치를 그 글쇠로 배운다 */
    private fun learnFrom(target: String) {
        val u = typed
        val n = u.size
        val m = target.length
        fun sub(i: Int, j: Int): Float {
            val t = u[i]
            val c = target[j]
            if (c == ' ' || t.isSpace) return if (c == ' ' && t.isSpace) 0f else 3f
            val want = layout.keyOf(c) ?: return 3f
            return when {
                t.key.letter == want.letter -> 0f
                dist(t, want) <= 1.3f -> 1f
                else -> 2.5f
            }
        }
        val d = Array(n + 1) { FloatArray(m + 1) }
        for (i in 0..n) d[i][0] = i.toFloat()
        for (j in 0..m) d[0][j] = j.toFloat()
        for (i in 1..n) for (j in 1..m) {
            d[i][j] = minOf(d[i - 1][j - 1] + sub(i - 1, j - 1), d[i - 1][j] + 1f, d[i][j - 1] + 1f)
        }
        // 거꾸로 따라가며 짝을 찾는다
        var i = n
        var j = m
        val pairs = ArrayList<Pair<Int, Int>>()
        while (i > 0 && j > 0) {
            when {
                d[i][j] == d[i - 1][j - 1] + sub(i - 1, j - 1) -> { pairs += (i - 1) to (j - 1); i--; j-- }
                d[i][j] == d[i - 1][j] + 1f -> i--
                else -> j--
            }
        }
        for ((ti, tj) in pairs) {
            val c = target[tj]
            if (c == ' ') continue
            val want = layout.keyOf(c) ?: continue
            val list = listOf(u[ti]) + attempts[ti].orEmpty()
            for (t in list) {
                if (t.isSpace || dist(t, want) > 1.3f) continue
                touch.learn(want, t.ux, t.uy, 3f)
                samples += Sample(want, t.ux, t.uy)
                val stat = perKey.getOrPut(want.letter!!) { IntArray(2) }
                stat[0]++
                if (t.key.letter != want.letter) { misses++; stat[1]++ }
            }
        }
    }

    /** 배운 습관으로 같은 터치들을 다시 판정했을 때 빗나가는 수 */
    fun missesAfter(): Int = samples.count { s -> touch.hit(layout, s.ux, s.uy)?.letter != s.key.letter }

    companion object {
        /** 글쇠마다 이만큼은 재고 싶다 */
        const val MIN_SAMPLES = 6
        /** 자주 빗나가는 글쇠는 이만큼 */
        const val PROBLEM_SAMPLES = 12
        /** 너무 길어지지 않게 (처음 3문장 포함) */
        const val MAX_SENTENCES = 14
    }
}
