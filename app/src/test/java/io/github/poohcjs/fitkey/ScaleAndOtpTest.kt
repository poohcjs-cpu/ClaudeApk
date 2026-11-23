package io.github.poohcjs.fitkey

import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Otp
import io.github.poohcjs.fitkey.engine.TouchModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 크기를 바꿀 때 배운 습관 환산, 인증번호 찾기 */
class ScaleAndOtpTest {
    private val layout = KeyboardLayout.letters(true)

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
