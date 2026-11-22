package io.github.poohcjs.fitkey

import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Otp
import io.github.poohcjs.fitkey.engine.SizeAdvisor
import io.github.poohcjs.fitkey.engine.TouchModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 크기·위치 자동 맞춤, 크기 바꿀 때 환산, 인증번호 찾기 */
class AdvisorAndOtpTest {
    private val layout = KeyboardLayout.letters(true)
    private val rows = layout.letters.map { it.row }.distinct().sorted()
    private val base = SizeAdvisor.Look(1f, 1f, 0f, 0)

    private fun stats(miss: (Char) -> Int = { 0 }, oy: (Char) -> Float = { 0f }) =
        layout.letters.map { k -> SizeAdvisor.KeyStat(k.letter!!, k.cx, k.row, 8, miss(k.letter!!), 0f, oy(k.letter!!)) }

    @Test fun scatteredTouchesGetTallerKeyboard() {
        // 위아래로 많이 흩어짐 (줄 높이의 0.3) → 높이를 키워 빗나감을 줄인다
        val a = SizeAdvisor.advise(base, 0.2f, 0.3f, stats(), rows)
        assertTrue(a.changed)
        assertTrue("${a.to.height}", a.to.height > 1.1f)
        assertTrue(a.missAfter < a.missNow)
        assertTrue(a.missAfter <= SizeAdvisor.TARGET + 0.005f || a.to.height >= SizeAdvisor.MAX_HEIGHT)
    }

    @Test fun preciseTouchesKeepOrShrink() {
        assertFalse(SizeAdvisor.advise(base, 0.15f, 0.12f, stats(), rows).changed)
        // 이미 크게 쓰는데 아주 정확하면 조금 줄여 화면을 넓힌다 (0.9 아래로는 안 줄임)
        val a = SizeAdvisor.advise(base.copy(height = 1.3f), 0.15f, 0.1f, stats(), rows)
        assertTrue(a.to.height < 1.3f && a.to.height >= SizeAdvisor.MIN_HEIGHT)
    }

    @Test fun weakLeftEdgeOnlySuggestsOneHandMode() {
        val left = setOf('q', 'w', 'e', 'a', 's', 'z', 'x')
        val a = SizeAdvisor.advise(base, 0.18f, 0.15f, stats(miss = { if (it in left) 3 else 0 }), rows)
        // 한 손 모드는 저절로 켜지 않는다: 위치·너비는 그대로, 권하는 말만
        assertEquals(base.align, a.to.align)
        assertEquals(base.width, a.to.width)
        assertTrue(a.reasons.any { "한 손 모드" in it })
    }

    @Test fun bottomRowPressedHighRaisesKeyboard() {
        val bottom = setOf('z', 'x', 'c', 'v', 'b', 'n', 'm')
        val a = SizeAdvisor.advise(base, 0.18f, 0.15f, stats(miss = { if (it in bottom) 2 else 0 }, oy = { if (it in bottom) -0.2f else 0f }), rows)
        assertEquals(16, a.to.bottomGap)
    }

    @Test fun rescaleKeepsPhysicalDistance() {
        val t = TouchModel()
        val k = layout.keyOf('k')!!
        repeat(30) { t.learn(k, k.cx + 0.2f, k.cy + 0.3f) }
        val (ox, oy) = t.offset('k')
        // 높이를 1.0 → 1.5로: 세로 치우침은 글쇠 단위로 2/3이 된다, 너비는 그대로
        t.rescale(1f, 1f / 1.5f)
        val (nx, ny) = t.offset('k')
        assertEquals(ox, nx, 1e-4f)
        assertEquals(oy / 1.5f, ny, 1e-3f)
    }

    @Test fun findsVerificationCodes() {
        assertEquals("123456", Otp.extract("[Web발신] [네이버] 인증번호 [123456]를 입력해주세요."))
        assertEquals("482915", Otp.extract("[카카오] 인증번호 482915 를 입력하세요. 타인 노출 금지"))
        assertEquals("7391", Otp.extract("Your verification code is 7391"))
        assertEquals("482913", Otp.extract("G-482913 is your Google verification code."))
        assertEquals("3829", Otp.extract("본인확인 인증번호는 (3829)입니다. 유효시간 3분"))
        assertEquals("30012345", Otp.extract("[신한카드] 승인번호 30012345 결제금액 15,000원"))
        assertEquals("654321", Otp.extract("ＯＴＰ ６５４３２１"))
    }

    @Test fun ignoresNonCodes() {
        assertNull(Otp.extract("[쿠팡] 주문하신 상품이 2026년 11월 22일 도착합니다"))
        assertNull(Otp.extract("엄마: 오늘 저녁 7시에 1234번 버스 타고 갈게"))
        assertNull(Otp.extract("인증이 완료되었습니다. 결제금액 25000원"))
        assertNull(Otp.extract("인증 문의는 010-1234-5678 로 연락 주세요"))
    }
}
