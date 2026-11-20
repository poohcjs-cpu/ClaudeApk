package io.github.poohcjs.fitkey.ui

import io.github.poohcjs.fitkey.engine.Hangul
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyKind
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.TouchModel
import kotlin.math.sqrt

/**
 * 터치 보정: 정해진 문장을 평소처럼 쳐 보게 한다 (틀리면 지우고 고쳐도 된다).
 * 문장을 다 치면, 친 글쇠들을 문장의 글쇠들과 맞춰 보고(편집 거리 정렬) '노린 글쇠 ↔ 실제로 누른 자리'를 배운다.
 * 지우고 다시 친 경우, 지워진 터치도 그 자리에서 노린 글쇠의 자료로 쓴다 (실수한 터치가 가장 귀한 자료라서).
 */
class Calibration(private val touch: TouchModel, private val layout: KeyboardLayout) {

    data class Sentence(val text: String, val lang: Lang)
    /** 친 것 하나: 화면에서 누른 글쇠(글자 또는 스페이스)와 누른 자리 */
    data class Tap(val key: Key, val ux: Float, val uy: Float, val shifted: Boolean) {
        val isSpace get() = key.kind == KeyKind.SPACE
    }
    data class Sample(val key: Key, val ux: Float, val uy: Float)

    val sentences = listOf(
        Sentence("다람쥐 헌 쳇바퀴에 타고파", Lang.KO),
        Sentence("키스의 고유조건은 입술끼리 만나야 하고 특별한 기술은 필요치 않다", Lang.KO),
        Sentence("오늘 저녁에 뭐 먹을지 같이 정해요", Lang.KO),
        Sentence("the quick brown fox jumps over the lazy dog", Lang.EN),
    )

    var sentence = 0
        private set
    val typed = ArrayList<Tap>()
    /** 지운 터치: 그 자리(몇 번째)에서 다시 치기 전에 쳤던 것 */
    private val attempts = HashMap<Int, MutableList<Tap>>()
    var shift = false
        private set
    val samples = ArrayList<Sample>()
    var misses = 0
        private set
    val done: Boolean get() = sentence >= sentences.size
    val current: Sentence? get() = sentences.getOrNull(sentence)

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
        sentence++
        typed.clear(); attempts.clear(); shift = false
    }

    /** 이 문장은 배우지 않고 넘어가기 */
    fun skip() {
        sentence++
        typed.clear(); attempts.clear(); shift = false
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
                if (t.key.letter != want.letter) misses++
            }
        }
    }

    /** 배운 습관으로 같은 터치들을 다시 판정했을 때 빗나가는 수 */
    fun missesAfter(): Int = samples.count { s -> touch.hit(layout, s.ux, s.uy)?.letter != s.key.letter }
}
