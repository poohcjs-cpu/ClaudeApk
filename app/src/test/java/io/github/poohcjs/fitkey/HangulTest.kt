package io.github.poohcjs.fitkey

import io.github.poohcjs.fitkey.engine.Hangul
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 두벌식 조합 규칙 */
class HangulTest {
    private fun k(keys: String) = Hangul.composeKeys(keys)

    @Test fun basicSyllables() {
        assertEquals("안녕하세요", k("dkssudgktpdy"))
        assertEquals("가", k("rk"))
        assertEquals("ㄱ", k("r"))
        assertEquals("ㅏ", k("k"))
        assertEquals("ㄱㄴ", k("rs"))
    }

    @Test fun doubleFinalsAndSplitting() {
        assertEquals("닭", k("ekfr"))
        assertEquals("달기", k("ekfrl"))       // 겹받침의 뒤 자음이 다음 글자로
        assertEquals("앉아", k("dkswdk"))
        assertEquals("앉", k("dksw"))
        assertEquals("가가", k("rkrk"))        // 받침이 다음 글자 첫소리로
        assertEquals("없어", k("djqtdj"))
        assertEquals("값", k("rkqt"))
    }

    @Test fun doubleVowels() {
        assertEquals("과", k("rhk"))
        assertEquals("왜", k("dho"))
        assertEquals("의", k("dml"))
        assertEquals("뭐", k("anj"))
        assertEquals("ㅘ", k("hk"))           // 자음 없는 겹모음
    }

    @Test fun shiftedConsonants() {
        assertEquals("까", k("Rk"))
        assertEquals("있어", k("dlTdj"))
        assertEquals("떡", k("Ejr"))
        assertEquals("빠", k("Qk"))
        // ㄸ·ㅃ·ㅉ은 받침이 될 수 없다
        assertEquals("가ㄸ", k("rkE"))
        assertEquals("가따", k("rkEk"))
        assertEquals("얘", k("dO"))
    }

    @Test fun roundTrip() {
        for (w in listOf("안녕하세요", "닭갈비", "앉아서", "읽었어요", "괜찮아", "뷁", "ㅋㅋㅋ", "ㅠㅠ", "됐어", "쇘다")) {
            val keys = Hangul.toKeys(w)!!
            assertEquals(w, Hangul.composeKeys(keys))
        }
        assertNull(Hangul.toKeys("abc"))
        assertEquals("ekfr", Hangul.toKeys("닭"))
    }

    @Test fun brokenSyllables() {
        assertTrue(Hangul.hasBrokenSyllable("안녕하세ㅛ"))
        assertFalse(Hangul.hasBrokenSyllable("ㅋㅋㅋ"))
        assertFalse(Hangul.hasBrokenSyllable("안녕"))
    }
}
