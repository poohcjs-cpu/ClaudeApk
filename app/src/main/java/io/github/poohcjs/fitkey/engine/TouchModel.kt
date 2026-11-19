package io.github.poohcjs.fitkey.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 내 손가락 습관을 배우는 글쇠 모델.
 *
 * 글쇠마다 '실제로 누른 자리 - 글쇠 가운데'의 평균(어느 쪽으로 치우치는지)과 퍼짐(얼마나 흩어지는지)을 배운다.
 * 예: ㅔ(p)를 누를 때 늘 오른쪽 아래를 누른다면, ㅔ 글쇠의 '진짜 가운데'를 그만큼 옮겨 생각한다.
 * 터치가 어느 글쇠인지 정할 때와, 오타를 고칠 때(이 터치가 사실은 옆 글쇠였을 가능성) 모두 이 모델을 쓴다.
 *
 * 위치 단위는 글쇠 단위(가로 = 글쇠 너비, 세로 = 줄 높이). 키보드 크기를 바꿔도 배운 것이 맞는다.
 */
class TouchModel {
    class Stat(var n: Float = 0f, var mx: Float = 0f, var my: Float = 0f, var vx: Float = PRIOR_VAR, var vy: Float = PRIOR_VAR)

    /**
     * 줄 높이 ÷ 글쇠 너비 (화면에서 글쇠가 세로로 얼마나 긴지). 세로 1칸은 가로 1칸보다 실제로 길어서,
     * 거리(가능성)를 잴 때 세로를 이만큼 늘려 본다. 키보드 화면이 알려 준다.
     */
    var aspect: Float = 1.3f
        set(v) { field = v.coerceIn(0.8f, 2.2f) }
    private val priorVarY: Float get() = PRIOR_VAR / (aspect * aspect)

    private val stats = HashMap<Char, Stat>()
    /** 모든 글쇠에 공통인 치우침 (손가락이 전체적으로 아래를 누르는 습관 등). 글쇠별 자료가 적을 때 쓴다 */
    private val global = Stat()

    /** 글쇠별 치우침: 자료가 적으면 공통 치우침 쪽으로 당긴다 */
    fun offset(letter: Char): Pair<Float, Float> {
        val s = stats[letter.lowercaseChar()]
        val gx = global.mx * shrink(global.n, 10f)
        val gy = global.my * shrink(global.n, 10f)
        if (s == null || s.n <= 0f) return gx.clampOff() to gy.clampOff()
        val w = shrink(s.n, 6f)
        return (s.mx * w + gx * (1 - w)).clampOff() to (s.my * w + gy * (1 - w)).clampOff()
    }

    /** 글쇠별 퍼짐 (가로는 글쇠 너비 단위, 세로는 줄 높이 단위) */
    fun spread(letter: Char): Pair<Float, Float> {
        val s = stats[letter.lowercaseChar()]
        val w = if (s == null) 0f else shrink(s.n, 12f)
        val vx = (s?.vx ?: PRIOR_VAR) * w + PRIOR_VAR * (1 - w)
        val vy = (s?.vy ?: priorVarY) * w + priorVarY * (1 - w)
        return sqrt(vx).coerceIn(0.28f, 0.7f) to sqrt(vy).coerceIn(0.28f / aspect, 0.7f / aspect)
    }

    /** 이 자리를 눌렀을 때 그 글쇠였을 가능성 (로그) */
    fun logLikelihood(key: Key, ux: Float, uy: Float): Float {
        val l = key.letter ?: return -50f
        val (ox, oy) = offset(l)
        val (sx, sy) = spread(l)
        val dx = (ux - key.cx - ox) / sx
        val dy = (uy - key.cy - oy) / sy
        return -0.5f * (dx * dx + dy * dy) - ln(sx * sy)
    }

    /**
     * 누른 자리가 어느 글자 글쇠인지. 기능 글쇠(Shift·지우기·스페이스 등) 자리면 그 글쇠 그대로.
     * 글자 글쇠끼리는 배운 습관으로 고른다 (단, 화면의 글쇠에서 너무 멀리 가지는 않게 이웃 글쇠까지만).
     */
    fun hit(layout: KeyboardLayout, ux: Float, uy: Float): Key? {
        val plain = layout.keyAt(ux, uy) ?: return null
        if (plain.kind != KeyKind.LETTER) return plain
        val near = layout.letters.filter { k -> kotlin.math.abs(k.cx - ux) < 1.3f && kotlin.math.abs(k.cy - uy) < 1.1f }
        // 화면에서 누른 글쇠에 덤을 준다: 배운 습관이 꽤 뚜렷할 때만 옆 글쇠로 바꾼다
        return near.maxByOrNull { logLikelihood(it, ux, uy) + if (it === plain) VISUAL_BONUS else 0f } ?: plain
    }

    /** 이 터치에 가능성 높은 글자 글쇠들 (오타 고치기 후보), 가능성 순 */
    fun candidates(layout: KeyboardLayout, ux: Float, uy: Float, max: Int = 4, floor: Float = -5.5f): List<Pair<Key, Float>> {
        val all = layout.letters.map { it to logLikelihood(it, ux, uy) }.sortedByDescending { it.second }
        val best = all.firstOrNull()?.second ?: return emptyList()
        return all.filter { it.second >= floor && it.second >= best - 6f }.take(max)
    }

    /**
     * 배우기: 이 자리를 누른 것은 이 글쇠였다.
     * weight: 보정 화면·되돌려 고친 글자처럼 확실한 자료는 크게, 그냥 친 글자는 작게.
     * 너무 멀리 빗나간 것(글쇠 1.6칸 밖)은 실수로 보고 배우지 않는다.
     */
    fun learn(key: Key, ux: Float, uy: Float, weight: Float = 1f) {
        val l = key.letter ?: return
        val dx = ux - key.cx
        val dy = uy - key.cy
        if (dx * dx + dy * dy > 1.6f * 1.6f) return
        update(stats.getOrPut(l.lowercaseChar()) { Stat(vy = priorVarY) }, dx, dy, weight)
        update(global, dx, dy, weight * 0.5f)
        version++
    }

    private fun update(s: Stat, dx: Float, dy: Float, weight: Float) {
        // 오래된 자료는 조금씩 잊는다 (최근 습관을 따르게): 자료가 많아도 최대 MAX_N 개처럼 다룬다
        val n = (s.n + weight).coerceAtMost(MAX_N)
        val a = weight / n
        val ox = s.mx
        val oy = s.my
        s.mx += a * (dx - s.mx)
        s.my += a * (dy - s.my)
        s.vx = ((1 - a) * (s.vx + a * (dx - ox) * (dx - ox))).coerceIn(0.03f, 0.6f)
        s.vy = ((1 - a) * (s.vy + a * (dy - oy) * (dy - oy))).coerceIn(0.02f, 0.6f)
        s.n = n
    }

    /** 바뀔 때마다 늘어난다 (저장할 때가 됐는지 보려고) */
    var version = 0
        private set

    val samples: Int get() = stats.values.sumOf { it.n.toDouble() }.toInt()

    /** 화면에 보여 줄 글쇠별 치우침 */
    fun offsets(): Map<Char, Pair<Float, Float>> = stats.keys.associateWith { offset(it) }

    /** 손가락 흩어짐이 큰 편인지 (키보드를 키우면 좋은지 판단) */
    fun averageSpread(): Float {
        val s = stats.values.filter { it.n >= 3 }
        if (s.isEmpty()) return sqrt(PRIOR_VAR)
        return s.map { sqrt((it.vx + it.vy) / 2) }.average().toFloat()
    }

    fun clear() {
        stats.clear()
        global.n = 0f; global.mx = 0f; global.my = 0f; global.vx = PRIOR_VAR; global.vy = priorVarY
        version++
    }

    /** 저장: 한 줄에 글쇠 하나 ("q n mx my vx vy") */
    fun serialize(): String = buildString {
        append("* ${global.n} ${global.mx} ${global.my} ${global.vx} ${global.vy}\n")
        for ((k, s) in stats) append("$k ${s.n} ${s.mx} ${s.my} ${s.vx} ${s.vy}\n")
    }

    fun load(text: String) {
        clear()
        for (line in text.lines()) {
            val p = line.trim().split(' ')
            if (p.size != 6) continue
            val v = p.drop(1).map { it.toFloatOrNull() ?: return@map null }
            if (v.any { it == null }) continue
            val s = Stat(v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!)
            if (p[0] == "*") { global.n = s.n; global.mx = s.mx; global.my = s.my; global.vx = s.vx; global.vy = s.vy }
            else if (p[0].length == 1) stats[p[0][0]] = s
        }
        version++
    }

    private fun Float.clampOff() = coerceIn(-MAX_OFF, MAX_OFF)

    companion object {
        /** 처음 퍼짐: 글쇠 너비의 약 0.45 */
        const val PRIOR_VAR = 0.2f
        const val MAX_N = 150f
        /** 글쇠 가운데를 옮겨 생각하는 최대 거리 (글쇠 0.45칸): 너무 옮기면 화면과 달라 헷갈린다 */
        const val MAX_OFF = 0.45f
        /** 화면에서 실제로 누른 글쇠에 주는 덤 (가능성 약 2.7배) */
        const val VISUAL_BONUS = 1.0f
        private fun shrink(n: Float, k: Float) = n / (n + k)
        fun probability(logL: Float) = exp(logL.toDouble()).toFloat()
    }
}
