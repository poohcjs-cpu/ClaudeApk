package io.github.poohcjs.fitkey

import io.github.poohcjs.fitkey.engine.Decoder
import io.github.poohcjs.fitkey.engine.Dictionary
import io.github.poohcjs.fitkey.engine.Hangul
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.Stroke
import io.github.poohcjs.fitkey.engine.TouchModel
import io.github.poohcjs.fitkey.engine.UserWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random
import java.util.zip.GZIPInputStream

/** 앱에 들어가는 실제 사전으로: 흔한 오타는 고치고, 맞는 말은 건드리지 않는지 */
class RealDictionaryTest {
    companion object {
        private fun load(lang: Lang, name: String): Dictionary {
            val f = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name")).first { it.exists() }
            return GZIPInputStream(f.inputStream()).bufferedReader().use { Dictionary.read(lang, it) }
        }
        val ko by lazy { load(Lang.KO, "ko.tsv.gz") }
        val en by lazy { load(Lang.EN, "en.tsv.gz") }
    }

    private val layout = KeyboardLayout.letters(false)
    private val touch = TouchModel()
    private val decoder = Decoder(touch)

    /** 글쇠 문자열을 주어진 자리(가운데 + 치우침)로 눌렀을 때의 터치들 */
    private fun strokes(keys: String, lang: Lang, at: (Int, Char) -> Pair<Float, Float> = { _, _ -> 0f to 0f }): List<Stroke> =
        keys.mapIndexed { i, c ->
            val k = layout.keyOf(c)!!
            val (dx, dy) = at(i, c)
            // 흉내에서는 글자 글쇠 밖(Shift·지우기 줄)으로 나간 터치는 노린 글쇠로 둔다
            val hit = touch.hit(layout, k.cx + dx, k.cy + dy)?.takeIf { it.letter != null } ?: k
            val l = hit.letter!!
            val kc = if (lang == Lang.KO && c.isUpperCase() && l in Hangul.SHIFTABLE) l.uppercaseChar() else l
            Stroke(hit, kc, if (lang == Lang.KO) Hangul.jamoOf(kc)!! else kc, k.cx + dx, k.cy + dy, c.isUpperCase())
        }

    private fun typed(s: List<Stroke>, lang: Lang) = if (lang == Lang.KO) Hangul.compose(s.map { it.output }) else s.map { it.output }.joinToString("")

    private fun fix(text: String, lang: Lang, slips: Map<Int, Pair<Float, Float>>): String {
        val keys = if (lang == Lang.KO) Hangul.toKeys(text)!! else text
        val s = strokes(keys, lang) { i, _ -> slips[i] ?: (0f to 0f) }
        val t = typed(s, lang)
        return decoder.autoCorrect(s, t, lang, if (lang == Lang.KO) ko else en, UserWords(), layout)?.to ?: t
    }

    @Test fun loadsAndLooksUp() {
        val t0 = System.nanoTime()
        assertTrue(ko.size > 150_000 && en.size > 40_000)
        println("사전 읽기 ${(System.nanoTime() - t0) / 1_000_000}ms · 한국어 ${ko.size} · 영어 ${en.size}")
        assertTrue(ko.count(Hangul.toKeys("안녕하세요")!!) > 1000)
        assertEquals("don't", en.wordOf("dont"))
    }

    @Test fun fixesCommonSlips() {
        // ㅛ 대신 아래 ㅗ 위쪽 끝을 누름
        assertEquals("안녕하세요", fix("안녕하세요", Lang.KO, mapOf(11 to (-0.3f to 0.55f))))
        // ㅏ(k) 대신 오른쪽 ㅣ(l)를 누름: '감사합니다'의 마지막 ㅏ
        assertEquals("감사합니다", fix("감사합니다", Lang.KO, mapOf(11 to (0.6f to 0f))))
        // 영어: the → thw (e 대신 왼쪽 w)
        assertEquals("the", fix("the", Lang.EN, mapOf(2 to (-0.6f to 0f))))
        assertEquals("people", fix("people", Lang.EN, mapOf(1 to (0.6f to 0f))))
    }

    @Test fun leavesCorrectWordsAlone() {
        val words = listOf("오늘", "점심", "먹었어", "뭐해", "ㅋㅋㅋ", "그래서", "근데", "진짜", "맛있게", "회의", "내일", "사랑해", "괜찮아", "학교에서", "엄마한테",
            "카톡", "주말에", "고마워요")
        for (w in words) {
            val keys = Hangul.toKeys(w)!!
            val s = strokes(keys, Lang.KO)
            assertNull("‘$w’는 고치면 안 됨", decoder.autoCorrect(s, typed(s, Lang.KO), Lang.KO, ko, UserWords(), layout))
        }
    }

    /**
     * 손가락이 흔들리는 사람 흉내: 터치마다 글쇠 가운데에서 무작위로 빗나간다.
     * 화면 글쇠 그대로일 때와 자동 고치기를 거친 뒤 맞게 나온 낱말 비율을 비교한다.
     */
    @Test fun noisyTypingAccuracy() {
        for ((lang, dict, sample) in listOf(
            Triple(Lang.KO, ko, listOf("안녕하세요", "감사합니다", "괜찮아요", "그러니까", "어디야", "알겠어", "지금", "사람들이", "생각해", "모르겠어", "좋아요", "보고싶어",
                "고마워", "미안해", "언제", "어떻게", "정말요", "왜요", "그래요", "있잖아", "회사", "친구", "오늘은", "저녁", "먹을래")),
            Triple(Lang.EN, en, listOf("hello", "thanks", "people", "because", "something", "really", "think", "would", "about", "there", "where",
                "please", "tomorrow", "morning", "together", "nothing", "should", "little", "before", "always")),
        )) {
            val rnd = Random(7)
            var raw = 0
            var fixed = 0
            var broken = 0
            var n = 0
            repeat(40) {
                for (w in sample) {
                    val keys = if (lang == Lang.KO) Hangul.toKeys(w)!! else w
                    val s = strokes(keys, lang) { _, _ -> (rnd.nextGaussian() * 0.33).toFloat() to (rnd.nextGaussian() * 0.26).toFloat() }
                    val t = typed(s, lang)
                    val out = decoder.autoCorrect(s, t, lang, dict, UserWords(), layout)?.to ?: t
                    n++
                    if (t == w) raw++
                    if (out == w) fixed++
                    if (t == w && out != w) broken++
                }
            }
            println("[$lang] 낱말 ${n}개: 화면 그대로 ${raw * 100 / n}% 맞음 → 자동 고침 후 ${fixed * 100 / n}% (맞던 걸 망친 것 $broken)")
            assertTrue(fixed > raw)
            assertTrue(broken * 50 < n)   // 맞게 친 말을 망치는 일은 2% 미만
        }
    }

    /** 낱말을 치는 동안 추천 막대: 이어질 말(자동 완성)과 고칠 말 */
    @Test fun suggestionsWhileTyping() {
        class Sink : io.github.poohcjs.fitkey.engine.TextSink {
            val sb = StringBuilder(); var comp = ""
            override fun setComposing(text: String) { comp = text }
            override fun commit(text: String) { comp = ""; sb.append(text) }
            override fun finishComposing() { sb.append(comp); comp = "" }
            override fun deleteBefore(n: Int) { sb.setLength(maxOf(0, sb.length - n)) }
            override fun sendBackspace() {}
            override fun textBefore(n: Int) = (sb.toString() + comp).takeLast(n)
            override fun enter() {}
        }
        val e = io.github.poohcjs.fitkey.engine.InputEngine(Sink(), touch, UserWords(), { if (it == Lang.KO) ko else en })
        e.layout = layout
        e.startInput(io.github.poohcjs.fitkey.engine.FieldInfo())
        for (c in "dkssudgk") { val k = layout.keyOf(c)!!; e.onLetter(k, k.cx, k.cy) }   // '안녕하'
        val words = e.suggestions.items.map { it.word }
        println("‘안녕하’ 추천: $words")
        assertTrue(words.any { it.startsWith("안녕하") && it.length > 3 })
        e.onSpace()
        println("띄어 쓴 뒤 추천: ${e.suggestions.items.map { it.word }}")
    }

    /** 치다가 옆 글쇠를 눌러도, 낱말을 다 치기 전에 노린 말을 가운데에 보여 주고, 누르면 그 말로 바뀐다 */
    @Test fun showsIntendedWordWhileTyping() {
        val sink = EngineTest.FakeSink()
        val e = io.github.poohcjs.fitkey.engine.InputEngine(sink, touch, UserWords(), { if (it == Lang.KO) ko else en })
        e.layout = layout
        e.startInput(io.github.poohcjs.fitkey.engine.FieldInfo())
        val keys = "rkatkgkqsl"                          // '감사합니' (감사합니다를 치는 중)
        keys.forEachIndexed { i, c ->
            val k = layout.keyOf(c)!!
            val hit = if (i == 5) layout.keyOf('h')!! else k  // ㅎ 대신 아래 ㅗ를 누름
            val ux = k.cx + (hit.cx - k.cx) * 0.7f
            val uy = k.cy + (hit.cy - k.cy) * 0.7f
            e.onLetter(touch.hit(layout, ux, uy)!!, ux, uy)
        }
        val bar = e.suggestions
        println("‘${e.currentText}’ 추천: ${bar.items.map { it.word }} (가운데 ${bar.likelyIndex})")
        assertEquals("감사ㅘㅂ니", e.currentText)
        assertNull(bar.autoIndex)                        // 스페이스로는 바꾸지 않는다 (다 친 게 아니니까)
        val i = bar.likelyIndex!!
        assertEquals("감사합니다", bar.items[i].word)
        e.onSuggestion(i)
        assertEquals("감사합니다 ", sink.text)
    }
}
