package io.github.poohcjs.fitkey

import io.github.poohcjs.fitkey.engine.Dictionary
import io.github.poohcjs.fitkey.engine.FieldInfo
import io.github.poohcjs.fitkey.engine.InputEngine
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.TouchModel
import io.github.poohcjs.fitkey.engine.UserBigrams
import io.github.poohcjs.fitkey.engine.UserWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 다음 낱말 예측: 내가 이어 쓰는 말 순서를 이 폰 안에서 배운다 */
class PredictionTest {
    private val ko = Dictionary.of(Lang.KO, mapOf("안녕하세요" to 5000, "감사합니다" to 3000, "사과" to 800, "가방" to 700))
    private val en = Dictionary.of(Lang.EN, mapOf("the" to 100000, "hello" to 5000, "world" to 3000))
    private val layout = KeyboardLayout.letters(false)

    private fun engine(b: UserBigrams = UserBigrams(), field: FieldInfo = FieldInfo()): Pair<InputEngine, EngineTest.FakeSink> {
        val sink = EngineTest.FakeSink()
        val e = InputEngine(sink, TouchModel(), UserWords(), { if (it == Lang.KO) ko else en }, clock = { 1000L }, bigrams = b)
        e.layout = layout
        e.startInput(field)
        return e to sink
    }

    private fun InputEngine.type(keys: String) {
        for (c in keys) {
            if (c == ' ') { onSpace(); continue }
            if (c.isUpperCase()) onShift()
            val k = layout.keyOf(c)!!
            onLetter(k, k.cx, k.cy)
        }
    }

    private fun InputEngine.words() = suggestions.items.map { it.word }

    @Test fun predictsNextWordAfterTwoUses() {
        val b = UserBigrams()
        val (e, s) = engine(b)
        e.type("dhsmf skfTl "); e.onEnter()          // 오늘 날씨
        e.type("dhsmf ")
        assertTrue("한 번 쓴 것은 아직 띄우지 않는다", e.suggestions.items.isEmpty())
        e.type("skfTl "); e.onEnter()                // 두 번째
        assertEquals(2, b.count(Lang.KO, "dhsmf", "skfTl"))
        e.type("dhsmf ")
        assertTrue(e.suggestions.predicted)
        assertEquals(listOf("날씨"), e.words())
        assertEquals(null, e.suggestions.autoIndex)   // 스페이스를 눌러도 저절로 들어가지 않는다
        e.onSuggestion(0)
        assertTrue(s.text.endsWith("오늘 날씨 "))
        assertEquals(3, b.count(Lang.KO, "dhsmf", "skfTl"))
    }

    @Test fun mostFrequentInTheMiddle() {
        val b = UserBigrams()
        repeat(5) { b.add(Lang.KO, "dhsmf", "skfTl") }   // 오늘 → 날씨 5번
        repeat(3) { b.add(Lang.KO, "dhsmf", "wjsur") }   // 오늘 → 저녁 3번
        repeat(2) { b.add(Lang.KO, "dhsmf", "anj") }     // 오늘 → 뭐 2번
        val (e, _) = engine(b)
        e.type("dhsmf ")
        assertEquals(listOf("저녁", "날씨", "뭐"), e.words())
    }

    @Test fun completesWithWordIUsuallyWriteNext() {
        val b = UserBigrams()
        b.add(Lang.KO, "dhsmf", "skfTl")                 // 한 번만 썼어도, 치기 시작한 글쇠와 맞으면 추천
        val (e, _) = engine(b)
        e.type("dhsmf sk")                               // 오늘 나
        assertTrue(e.words().toString(), "날씨" in e.words())
    }

    @Test fun newSentenceHasNoContext() {
        val b = UserBigrams()
        repeat(3) { b.add(Lang.KO, "dhsmf", "skfTl") }
        val (e, _) = engine(b)
        e.type("dhsmf"); e.onText("."); e.onSpace()      // '오늘. '
        assertTrue(e.suggestions.items.isEmpty())
        e.type("dhsmf "); assertTrue(e.suggestions.predicted)
        e.onBackspace()                                  // 띄어쓰기를 지우면 예측을 거둔다
        assertTrue(e.suggestions.items.isEmpty())
    }

    @Test fun englishAndCursorMoves() {
        val b = UserBigrams()
        val (e, _) = engine(b)
        e.onLang()
        repeat(2) { e.type("hello world "); e.onEnter() }
        e.type("hello ")
        assertEquals(listOf("world"), e.words())
        // 커서를 옮겨도 커서 앞 낱말로 다시 맞춘다
        e.onSelectionChanged(false)
        assertEquals(listOf("world"), e.words())
        e.onSelectionChanged(true)
        assertTrue(e.suggestions.items.isEmpty())
    }

    @Test fun noLearningInSecretOrIncognitoFields() {
        for (f in listOf(FieldInfo(secret = true), FieldInfo(incognito = true))) {
            val b = UserBigrams()
            val (e, _) = engine(b, f)
            repeat(3) { e.type("dhsmf skfTl "); e.onEnter() }
            assertEquals(0, b.size)
            repeat(3) { b.add(Lang.KO, "dhsmf", "skfTl") }
            e.type("dhsmf ")
            assertTrue("$f: 배운 것을 보여 주지 않는다", e.suggestions.items.isEmpty())
        }
    }

    @Test fun storesLimitsAndClears() {
        val b = UserBigrams()
        repeat(20) { i -> repeat(if (i == 0) 5 else 1) { b.add(Lang.KO, "dhsmf", "a$i") } }
        assertEquals(UserBigrams.PER_WORD, b.size)            // 한 낱말 뒤는 12개까지
        assertEquals(5, b.count(Lang.KO, "dhsmf", "a0"))      // 많이 쓴 것은 남는다
        b.add(Lang.EN, "see", "you", 2)
        val copy = UserBigrams().apply { load(b.serialize()) }
        assertEquals(b.size, copy.size)
        assertEquals(2, copy.count(Lang.EN, "see", "you"))
        assertEquals(0, copy.count(Lang.KO, "see", "you"))   // 언어마다 따로
        copy.clear()
        assertEquals(0, copy.size)
        assertTrue(copy.next(Lang.EN, "see", min = 1).isEmpty())
    }

    @Test fun prunesWhenTooMany() {
        val b = UserBigrams()
        for (i in 0 until UserBigrams.MAX + 10) b.add(Lang.EN, "w$i", "x")
        b.add(Lang.EN, "keep", "me", 9)
        assertTrue(b.size <= UserBigrams.MAX)
        assertEquals(9, b.count(Lang.EN, "keep", "me"))
    }
}
