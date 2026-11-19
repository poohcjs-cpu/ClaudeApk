package io.github.poohcjs.fitkey.ui

import io.github.poohcjs.fitkey.engine.Hangul
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyKind
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.TouchModel
import kotlin.math.sqrt

/**
 * 터치 보정: 정해진 문장을 쳐 보게 하고, 눌러야 했던 글쇠와 실제로 누른 자리를 비교해 손 습관을 배운다.
 * 모든 자음·모음이 들어간 문장을 쓴다.
 */
class Calibration(private val touch: TouchModel, private val layout: KeyboardLayout) {

    data class Sentence(val text: String, val lang: Lang)
    /** 눌러야 할 글쇠 하나 (' ' = 스페이스). charIndex: 문장의 몇 번째 글자에 속하는지 */
    data class Target(val key: Char, val charIndex: Int)
    data class Sample(val key: Key, val ux: Float, val uy: Float)

    val sentences = listOf(
        Sentence("다람쥐 헌 쳇바퀴에 타고파", Lang.KO),
        Sentence("키스의 고유조건은 입술끼리 만나야 하고 특별한 기술은 필요치 않다", Lang.KO),
        Sentence("the quick brown fox jumps over the lazy dog", Lang.EN),
    )

    var sentence = 0
        private set
    var pos = 0
        private set
    val samples = ArrayList<Sample>()
    var misses = 0
        private set
    var wrongTaps = 0
        private set
    val done: Boolean get() = sentence >= sentences.size

    val current: Sentence? get() = sentences.getOrNull(sentence)
    val targets: List<Target> get() = current?.let { targetsOf(it) }.orEmpty()
    val next: Target? get() = targets.getOrNull(pos)
    /** 다 친 글자 수 (문장에서) */
    val typedChars: Int get() = targets.getOrNull(pos)?.charIndex ?: (current?.text?.length ?: 0)
    val totalKeys: Int get() = sentences.sumOf { targetsOf(it).size }
    val doneKeys: Int get() = sentences.take(sentence).sumOf { targetsOf(it).size } + pos

    fun targetsOf(s: Sentence): List<Target> {
        val out = ArrayList<Target>()
        s.text.forEachIndexed { i, ch ->
            when {
                ch == ' ' -> out += Target(' ', i)
                s.lang == Lang.KO -> Hangul.toKeys(ch.toString())?.forEach { out += Target(it, i) }
                else -> out += Target(ch.lowercaseChar(), i)
            }
        }
        return out
    }

    /**
     * 글쇠를 눌렀다. 눌러야 할 글쇠 근처(글쇠 1.2칸 안)면 받아들이고 배운다.
     * 엉뚱한 곳(실수로 다른 줄을 누름 등)은 배우지 않고, 다시 누르게 한다.
     * 돌려주는 값: 받아들였는지
     */
    fun tap(key: Key, ux: Float, uy: Float): Boolean {
        val t = next ?: return false
        if (t.key == ' ') {
            if (key.kind != KeyKind.SPACE) { wrongTaps++; return false }
            advance()
            return true
        }
        val want = layout.keyOf(t.key) ?: return false
        val dx = ux - want.cx
        val dy = (uy - want.cy) * touch.aspect
        if (sqrt(dx * dx + dy * dy) > 1.2f) { wrongTaps++; return false }
        val visual = layout.keyAt(ux, uy)
        if (visual?.letter != want.letter) misses++
        samples += Sample(want, ux, uy)
        touch.learn(want, ux, uy, 3f)
        advance()
        return true
    }

    private fun advance() {
        pos++
        if (pos >= targets.size) { sentence++; pos = 0 }
    }

    /** 배운 습관으로 같은 터치들을 다시 판정했을 때 빗나가는 수 */
    fun missesAfter(): Int = samples.count { s -> touch.hit(layout, s.ux, s.uy)?.letter != s.key.letter }
}
