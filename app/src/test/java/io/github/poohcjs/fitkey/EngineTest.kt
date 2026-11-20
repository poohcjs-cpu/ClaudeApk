package io.github.poohcjs.fitkey

import io.github.poohcjs.fitkey.engine.Dictionary
import io.github.poohcjs.fitkey.engine.FieldInfo
import io.github.poohcjs.fitkey.engine.InputEngine
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.TextSink
import io.github.poohcjs.fitkey.engine.TouchModel
import io.github.poohcjs.fitkey.engine.UserWords
import io.github.poohcjs.fitkey.ui.Calibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 입력 두뇌: 조합·지우기·자동 고치기·되돌리기·배우기 */
class EngineTest {
    /** 입력칸 흉내: 확정된 글 + 조합 중인 글 */
    class FakeSink : TextSink {
        val done = StringBuilder()
        var comp = ""
        val text get() = done.toString() + comp
        override fun setComposing(text: String) { comp = text }
        override fun commit(text: String) { comp = ""; done.append(text) }
        override fun finishComposing() { done.append(comp); comp = "" }
        override fun deleteBefore(n: Int) { done.setLength(maxOf(0, done.length - n)) }
        override fun sendBackspace() { if (done.isNotEmpty()) done.setLength(done.length - 1) }
        override fun textBefore(n: Int): String = text.takeLast(n)
        override fun enter() { done.append("\n") }
    }

    private val ko = Dictionary.of(Lang.KO, mapOf("안녕하세요" to 5000, "감사합니다" to 3000, "사과" to 800, "가방" to 700, "가요" to 600, "있어" to 900))
    private val en = Dictionary.of(Lang.EN, mapOf("the" to 100000, "they" to 20000, "hello" to 5000, "world" to 3000))
    private val layout = KeyboardLayout.letters(false)

    private fun engine(touch: TouchModel = TouchModel(), user: UserWords = UserWords(), sink: FakeSink = FakeSink()): Pair<InputEngine, FakeSink> {
        val e = InputEngine(sink, touch, user, { if (it == Lang.KO) ko else en }, clock = { 1000L })
        e.layout = layout
        e.startInput(FieldInfo())
        return e to sink
    }

    /** 글쇠 가운데를 누르며 친다. 대문자는 Shift 다음 그 글쇠. 공백은 스페이스 */
    private fun InputEngine.type(keys: String, offsets: Map<Int, Pair<Float, Float>> = emptyMap()) {
        keys.forEachIndexed { i, c ->
            if (c == ' ') { onSpace(); return@forEachIndexed }
            if (c.isUpperCase()) onShift()
            val k = layout.keyOf(c)!!
            val (dx, dy) = offsets[i] ?: (0f to 0f)
            val hit = touch.hit(layout, k.cx + dx, k.cy + dy)!!
            onLetter(hit, k.cx + dx, k.cy + dy)
        }
    }

    @Test fun typesHangulAndSpaces() {
        val (e, s) = engine()
        e.type("dkssudgktpdy ")
        assertEquals("안녕하세요 ", s.text)
        e.type("dlTdj")
        assertEquals("안녕하세요 있어", s.text)
    }

    @Test fun backspaceRemovesOneJamo() {
        val (e, s) = engine()
        e.type("ekfr")
        assertEquals("닭", s.text)
        e.onBackspace(); assertEquals("달", s.text)
        e.onBackspace(); assertEquals("다", s.text)
        e.onBackspace(); assertEquals("ㄷ", s.text)
        e.onBackspace(); assertEquals("", s.text)
    }

    @Test fun autoCorrectsSlipToNeighbourKeyAndUndoes() {
        val (e, s) = engine()
        // '안녕하세요'의 ㅛ를 치려다 바로 아래 ㅗ의 위쪽 가장자리를 눌렀다
        e.type("dkssudgktpdh", mapOf(11 to (-0.2f to -0.42f)))
        assertEquals("안녕하세오", s.text)
        // 추천 막대 가운데(스페이스로 들어갈 말)는 고친 말
        assertEquals("안녕하세요", e.suggestions.items[e.suggestions.autoIndex!!].word)
        e.onSpace()
        assertEquals("안녕하세요 ", s.text)
        // 바로 지우기 → 원래 친 말로 되돌리고, 다음부터 이 말은 고치지 않는다
        e.onBackspace()
        assertEquals("안녕하세오", s.text)
        assertTrue(e.userWords.known(Lang.KO, "dkssudgktpdh"))
    }

    @Test fun doesNotTouchValidWordsOrWordPlusParticle() {
        val (e, s) = engine()
        e.type("tkrhkfmf ")       // 사과를: 사전의 '사과' + 조사 '를'
        assertEquals("사과를 ", s.text)
        e.type("rkqkd ")          // 가방
        assertEquals("사과를 가방 ", s.text)
    }

    @Test fun englishCorrectionKeepsCapital() {
        val (e, s) = engine()
        e.onLang()
        e.type("thw ")            // w는 e 바로 옆
        assertEquals("the ", s.text)
        e.type("Hrllo ")          // r은 e 바로 옆, 첫 글자 대문자 유지
        assertEquals("the Hello ", s.text)
    }

    @Test fun learnsFromBackspaceAndRetype() {
        val touch = TouchModel()
        val (e, _) = engine(touch)
        val t = layout.keyOf('t')!!
        val r = layout.keyOf('r')!!
        repeat(15) {
            // ㅅ(t)을 노렸지만 ㄱ(r) 글쇠의 오른쪽 끝을 눌렀다 → 지우고 t를 다시 눌렀다 → 띄어쓰기
            e.onLetter(r, r.cx + 0.45f, r.cy)
            e.onBackspace()
            e.onLetter(t, t.cx, t.cy)
            e.onSpace()
        }
        val (ox, _) = touch.offset('t')
        assertTrue("t의 치우침이 왼쪽으로 배워져야 함: $ox", ox < -0.15f)
        // 이제 같은 자리(r 오른쪽 끝)는 t로 알아듣는다
        assertEquals('t', touch.hit(layout, r.cx + 0.45f, r.cy)!!.letter)
        // r 글쇠 가운데는 여전히 r
        assertEquals('r', touch.hit(layout, r.cx, r.cy)!!.letter)
    }

    /** 보정 화면에서 문장 하나를 친다: 노린 글쇠 가운데 + 치우침 자리를 누르고, 화면에서 그 자리의 글쇠가 눌린다 */
    private fun Calibration.typeSentence(bias: Pair<Float, Float>, skipIndex: Int = -1, extraAt: Int = -1) {
        val keys = targetKeys(current!!)
        keys.forEachIndexed { i, c ->
            if (i == skipIndex) return@forEachIndexed
            if (i == extraAt) { val z = layout.keyOf('z')!!; press(z, z.cx, z.cy) }
            if (c == ' ') { press(layout.keys.first { it.id == "space" }, 5f, 3.5f); return@forEachIndexed }
            val k = layout.keyOf(c)!!
            val x = k.cx + bias.first
            val y = k.cy + bias.second
            press(layout.keyAt(x, y)!!, x, y)
        }
        if (current != null && typed.isNotEmpty()) next()
    }

    @Test fun calibrationLearnsSystematicBias() {
        val touch = TouchModel()
        val cal = Calibration(touch, layout)
        // 늘 글쇠 가운데보다 오른쪽으로 0.55칸(옆 글쇠에 걸침), 아래로 0.2줄 누르는 사람. 한 글자 빼먹고, 엉뚱한 글쇠 하나 더 치기도 한다
        while (!cal.done) cal.typeSentence(0.55f to 0.2f, skipIndex = 3, extraAt = 6)
        assertTrue(cal.samples.size > 100)
        val before = cal.misses
        val after = cal.missesAfter()
        assertTrue("보정 전 $before → 후 $after", after < before / 3)
        val (ox, oy) = touch.offset('k')
        assertTrue("$ox $oy", ox > 0.3f && oy > 0.1f)
    }

    @Test fun calibrationShowsTypedTextAndUsesCorrectedTaps() {
        val touch = TouchModel()
        val cal = Calibration(touch, layout)
        val e = layout.keyOf('e')!!
        val w = layout.keyOf('w')!!
        // '다'의 ㄷ(e)을 치려다 ㅈ(w) 오른쪽 끝을 누름 → 지우고 ㄷ을 다시 침
        cal.press(w, w.cx + 0.45f, w.cy)
        cal.backspace()
        cal.press(e, e.cx, e.cy)
        cal.press(layout.keyOf('k')!!, layout.keyOf('k')!!.cx, layout.keyOf('k')!!.cy)
        assertEquals("다", cal.typedText())
        assertTrue(cal.onTrack)
        assertEquals('f', cal.nextKey())   // 다음: '람'의 ㄹ
        // 문장 끝까지 정확히 치면 저절로 다음 문장으로
        val rest = cal.targetKeys(cal.current!!).drop(2)
        rest.forEach { c ->
            if (c == ' ') cal.press(layout.keys.first { it.id == "space" }, 5f, 3.5f)
            else layout.keyOf(c)!!.let { cal.press(it, it.cx, it.cy) }
        }
        assertEquals(1, cal.sentence)
        // 지웠던 터치(w 오른쪽 끝)도 'ㄷ' 자료로 배웠다
        assertTrue(cal.samples.any { it.key.letter == 'e' && it.ux < e.cx - 0.4f })
        assertEquals(1, cal.misses)
    }

    @Test fun secretFieldsNeverLearnOrSuggest() {
        val user = UserWords()
        val (e, s) = engine(user = user)
        e.startInput(FieldInfo(secret = true))
        e.onLang()
        e.type("thw ")
        assertEquals("thw ", s.text)          // 비밀번호는 고치지 않는다
        assertTrue(e.suggestions.items.isEmpty())
        assertEquals(0, user.size)
    }

    @Test fun numpadKeyAndLayout() {
        // 아래 줄 세 번째 글쇠는 123(숫자 패드), 스페이스바 밀기를 끄면 한/A
        assertTrue(KeyboardLayout.letters(true).keys.any { it.id == "numpad" })
        assertTrue(KeyboardLayout.letters(true, numpadKey = false).keys.any { it.id == "lang" })
        val pad = KeyboardLayout.numpad(5)
        assertEquals(5, pad.rows)                                   // 글자 키보드와 같은 높이
        assertEquals("5", pad.keyAt(3.7f, 1.9f)!!.text)               // 가운데 줄 가운데 = 5
        assertEquals("0", pad.keyAt(3.7f, 4.4f)!!.text)
        val (e, s) = engine()
        e.type("rk")
        e.onNumpad()
        assertEquals(io.github.poohcjs.fitkey.engine.Mode.NUMPAD, e.mode)
        e.onText("1"); e.onText("2"); e.onSpace(); e.onText("3")
        assertEquals("가12 3", s.text)
        assertEquals(io.github.poohcjs.fitkey.engine.Mode.NUMPAD, e.mode) // 띄어 써도 숫자 패드 그대로
        e.onMode()
        assertEquals(io.github.poohcjs.fitkey.engine.Mode.LETTERS, e.mode)
        e.startInput(FieldInfo(numeric = true))
        assertEquals(io.github.poohcjs.fitkey.engine.Mode.NUMPAD, e.mode)   // 숫자 칸은 숫자 패드로 시작
    }
}
