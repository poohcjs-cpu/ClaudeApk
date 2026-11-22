package io.github.poohcjs.fitkey.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 보정 결과로 키보드 크기·위치를 맞춘다.
 *
 * 1) 크기: 손가락 터치의 흩어짐(σ)은 글쇠를 키워도 실제 거리(mm)는 그대로라, 글쇠 단위로는 줄어든다.
 *    치우침은 보정으로 메우므로, 흩어짐 때문에 글쇠 밖으로 나가는 비율(빗나감)을 정규분포로 계산해
 *    빗나감이 목표(약 3%) 아래가 되는 가장 작은 크기를 고른다. 이미 충분히 정확하면 조금 줄여 화면을 넓힌다.
 * 2) 좌우 위치: 한쪽 끝 글쇠만 유독 자주 빗나가면 (엄지가 잘 닿지 않는 쪽) 키보드를 반대쪽으로 모은다.
 * 3) 위아래 위치: 아래 줄을 늘 위쪽으로 누르면 키보드를 조금 올린다 (화면 아래 끝이 불편한 경우).
 */
object SizeAdvisor {
    /** 글쇠 하나의 보정 자료: 위치(글쇠 단위), 잰 횟수, 빗나간 횟수, 배운 치우침 */
    data class KeyStat(val letter: Char, val cx: Float, val row: Int, val n: Int, val misses: Int, val ox: Float, val oy: Float)

    data class Look(val height: Float, val width: Float, val align: Float, val bottomGap: Int)

    data class Advice(
        val from: Look,
        val to: Look,
        /** 왜 바꾸는지 (사람이 읽을 문장) */
        val reasons: List<String>,
        /** 흩어짐으로 계산한 빗나감 비율: 지금 크기 → 추천 크기 */
        val missNow: Float,
        val missAfter: Float,
    ) {
        val changed: Boolean get() = from != to
    }

    const val TARGET = 0.03f
    const val MAX_HEIGHT = 1.4f
    const val MIN_HEIGHT = 0.9f

    /** 한 방향으로 글쇠(반폭 0.5) 밖으로 나갈 확률 */
    fun outside(sigma: Float): Float = (2 * (1 - phi(0.5 / sigma))).toFloat()

    /** 두 방향을 함께 본 빗나감 비율 */
    fun missRate(sx: Float, sy: Float): Float = 1 - (1 - outside(sx)) * (1 - outside(sy))

    /**
     * sx, sy: 평균 흩어짐 (가로 = 글쇠 너비 단위, 세로 = 줄 높이 단위), 지금 크기에서 잰 값
     * letterRows: 글자 줄 번호 (위·가운데·아래 줄), keys: 글쇠별 자료
     */
    fun advise(now: Look, sx: Float, sy: Float, keys: List<KeyStat>, letterRows: List<Int>): Advice {
        val reasons = ArrayList<String>()
        var h = now.height
        var w = now.width
        var align = now.align
        var gap = now.bottomGap

        // ── 1. 크기 ──
        fun missAt(height: Float, width: Float) = missRate(sx * now.width / width, sy * now.height / height)
        val missNow = missAt(now.height, now.width)
        if (missNow > TARGET) {
            // 세로부터 키운다 (가로는 화면 너비가 한계): 높이를 5%씩 올려 목표에 닿는 가장 작은 값
            var bestH = now.height
            var bestW = now.width
            for (ws in steps(now.width, 1.0f)) for (hs in steps(now.height, MAX_HEIGHT)) {
                if (missAt(hs, ws) <= TARGET) { bestH = hs; bestW = ws; break }
                bestH = hs; bestW = ws
            }
            // 목표에 못 닿으면 가장 큰 값
            if (missAt(bestH, bestW) > TARGET) { bestH = maxOf(now.height, MAX_HEIGHT); bestW = maxOf(now.width, 1f) }
            if (bestH > now.height + 0.01f) {
                h = bestH
                reasons += "손가락 터치가 위아래로 흩어지는 편이라 키보드 높이를 ${pct(now.height)}% → ${pct(h)}%로 키워요"
            }
            if (bestW > now.width + 0.01f) {
                w = bestW
                reasons += "좌우로도 흩어져서 너비를 ${pct(now.width)}% → ${pct(w)}%로 넓혀요"
            } else if (now.width >= 0.99f && outside(sx) > TARGET / 2) {
                reasons += "좌우 흩어짐은 화면 너비가 한계라, 배운 습관으로 메워요"
            }
        } else if (now.height > 1.05f) {
            // 충분히 정확하면 조금 줄여 화면을 넓게 (목표의 절반 여유를 두고)
            var hs = now.height
            while (hs - 0.05f >= MIN_HEIGHT && missAt(hs - 0.05f, now.width) <= TARGET * 0.6f) hs -= 0.05f
            if (hs < now.height - 0.01f) {
                h = hs
                reasons += "터치가 정확해서 키보드를 ${pct(now.height)}% → ${pct(h)}%로 줄여도 돼요 (화면이 넓어져요)"
            }
        }

        // ── 2. 좌우: 한쪽 끝만 유독 빗나가면 반대쪽으로 모으기 ──
        fun rate(list: List<KeyStat>): Float? {
            val n = list.sumOf { it.n }
            return if (n < 10) null else list.sumOf { it.misses }.toFloat() / n
        }
        val left = rate(keys.filter { it.cx < 3.4f })
        val right = rate(keys.filter { it.cx > 6.6f })
        if (left != null && right != null) {
            if (left >= 0.2f && left >= right * 2.5f) {
                w = minOf(w, 0.85f); align = 1f
                reasons += "왼쪽 끝 글쇠를 자주 놓쳐요 (${pct(left)}%) → 키보드를 오른쪽으로 모아 엄지가 닿기 쉽게"
            } else if (right >= 0.2f && right >= left * 2.5f) {
                w = minOf(w, 0.85f); align = -1f
                reasons += "오른쪽 끝 글쇠를 자주 놓쳐요 (${pct(right)}%) → 키보드를 왼쪽으로 모아 엄지가 닿기 쉽게"
            }
        }

        // ── 3. 위아래: 아래 줄을 늘 위로 누르면 키보드 올리기 ──
        if (letterRows.size == 3) {
            val bottom = keys.filter { it.row == letterRows[2] && it.n >= 2 }
            val top = keys.filter { it.row == letterRows[0] && it.n >= 2 }
            val bottomRate = rate(bottom)
            val bottomUp = if (bottom.isEmpty()) 0f else bottom.map { it.oy }.average().toFloat()
            val topRate = rate(top)
            val topDown = if (top.isEmpty()) 0f else top.map { it.oy }.average().toFloat()
            if (bottomRate != null && bottomRate >= 0.15f && bottomUp < -0.12f) {
                gap = (gap + 16).coerceAtMost(48)
                reasons += "아래 줄(ㅋ~ㅡ)을 늘 위쪽으로 눌러요 → 키보드를 ${gap - now.bottomGap}dp 올려요"
            } else if (topRate != null && topRate >= 0.15f && topDown > 0.12f && gap > 0) {
                gap = (gap - 16).coerceAtLeast(0)
                reasons += "맨 위 글자 줄을 늘 아래쪽으로 눌러요 → 키보드를 조금 내려요"
            }
        }

        val to = Look(round05(h), round05(w), align, gap)
        return Advice(now, to, reasons, missNow, missAt(to.height, to.width))
    }

    private fun steps(from: Float, to: Float): List<Float> {
        val out = ArrayList<Float>()
        var v = from
        while (v <= to + 1e-4f) { out += v; v += 0.05f }
        if (out.isEmpty()) out += from
        return out
    }

    private fun round05(v: Float) = Math.round(v * 20) / 20f
    private fun pct(v: Float) = Math.round(v * 100)

    /** 표준정규분포 누적확률 */
    private fun phi(z: Double): Double = 0.5 * (1 + erf(z / sqrt(2.0)))

    private fun erf(x: Double): Double {
        // Abramowitz–Stegun 7.1.26 (오차 1.5e-7)
        val t = 1 / (1 + 0.3275911 * abs(x))
        val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
        return if (x >= 0) y else -y
    }
}
