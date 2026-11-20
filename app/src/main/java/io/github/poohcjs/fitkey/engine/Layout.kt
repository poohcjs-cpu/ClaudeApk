package io.github.poohcjs.fitkey.engine

/*
 * 자판 배열과 글쇠 위치.
 * 위치는 '글쇠 단위'로 적는다: 가로 1 = 글쇠 하나 너비(한 줄 10칸), 세로 1 = 줄 높이 하나.
 * 터치 습관(어느 쪽으로 얼마나 빗나가는지)도 이 단위로 배운다. 그래서 키보드 높이를 바꿔도 배운 것이 그대로 맞는다.
 */

enum class KeyKind { LETTER, DIGIT, SYMBOL, SHIFT, BACKSPACE, SPACE, ENTER, LANG, MODE, COMMA, PERIOD, NUMPAD }

enum class Lang { KO, EN }

enum class Mode { LETTERS, SYMBOLS, SYMBOLS2, NUMPAD }

data class Key(
    val id: String,
    val kind: KeyKind,
    /** 줄 번호 (0부터, 숫자 줄이 있으면 숫자 줄이 0) */
    val row: Int,
    /** 왼쪽 위치와 너비 (글쇠 단위) */
    val x: Float,
    val w: Float,
    /** 글자 글쇠: 쿼티 글자 (한글 자판에서도 이 글자로 자모를 찾는다) */
    val letter: Char? = null,
    /** 숫자·기호 글쇠가 내는 글자 */
    val text: String? = null,
    /** 길게 누르면 나오는 글자 */
    val longPress: String? = null,
    /** 위쪽 위치와 높이 (줄 단위). 보통은 줄 번호 그대로, 숫자 패드처럼 줄 높이가 다른 배열만 따로 준다 */
    val y: Float = row.toFloat(),
    val h: Float = 1f,
) {
    val cx: Float get() = x + w / 2
    val cy: Float get() = y + h / 2
    fun contains(ux: Float, uy: Float) = ux >= x && ux < x + w && uy >= y && uy < y + h
}

class KeyboardLayout(val keys: List<Key>, val rows: Int) {
    val letters: List<Key> = keys.filter { it.kind == KeyKind.LETTER }
    private val byLetter = letters.associateBy { it.letter!! }
    fun keyOf(letter: Char): Key? = byLetter[letter.lowercaseChar()]

    /** 단순히 그 자리에 있는 글쇠 (가장자리 바깥은 가장 가까운 글쇠) */
    fun keyAt(ux: Float, uy: Float): Key? =
        keys.firstOrNull { it.contains(ux, uy) } ?: keys.minByOrNull { k ->
            val dx = maxOf(k.x - ux, 0f, ux - (k.x + k.w))
            val dy = maxOf(k.y - uy, 0f, uy - (k.y + k.h))
            dx * dx + dy * dy
        }

    companion object {
        private const val ROW1 = "qwertyuiop"
        private const val ROW2 = "asdfghjkl"
        private const val ROW3 = "zxcvbnm"
        // 숫자 줄이 없을 때 첫 줄 글쇠를 길게 누르면 숫자
        private const val DIGITS = "1234567890"
        // 길게 누르면 나오는 기호 (삼성 키보드와 비슷하게)
        private val LONG2 = mapOf('a' to "@", 's' to "#", 'd' to "₩", 'f' to "%", 'g' to "&", 'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")")
        private val LONG3 = mapOf('z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";", 'n' to "!", 'm' to "?")

        /** numpadKey: 아래 줄 세 번째 글쇠를 '123'(숫자 패드)으로. false면 '한/A' (스페이스바 밀어서 한/영을 끈 경우) */
        fun letters(numberRow: Boolean, numpadKey: Boolean = true): KeyboardLayout {
            val keys = mutableListOf<Key>()
            var r = 0
            if (numberRow) {
                DIGITS.forEachIndexed { i, d -> keys += Key("d$d", KeyKind.DIGIT, r, i.toFloat(), 1f, text = d.toString()) }
                r++
            }
            ROW1.forEachIndexed { i, c ->
                keys += Key(c.toString(), KeyKind.LETTER, r, i.toFloat(), 1f, letter = c, longPress = if (numberRow) null else DIGITS[i].toString())
            }
            r++
            ROW2.forEachIndexed { i, c -> keys += Key(c.toString(), KeyKind.LETTER, r, 0.5f + i, 1f, letter = c, longPress = LONG2[c]) }
            r++
            keys += Key("shift", KeyKind.SHIFT, r, 0f, 1.5f)
            ROW3.forEachIndexed { i, c -> keys += Key(c.toString(), KeyKind.LETTER, r, 1.5f + i, 1f, letter = c, longPress = LONG3[c]) }
            keys += Key("back", KeyKind.BACKSPACE, r, 8.5f, 1.5f)
            r++
            bottomRow(keys, r, numpadKey)
            return KeyboardLayout(keys, r + 1)
        }

        /**
         * 숫자 패드 (전화 다이얼 모양). 글자 키보드와 높이가 같게, 전체 줄 수(rows)를 4줄로 나눈다.
         *  1 2 3 -
         *  4 5 6 ␣
         *  7 8 9 ⌫
         *  가나다 0 . ↵
         */
        fun numpad(rows: Int): KeyboardLayout {
            val h = rows / 4f
            val keys = mutableListOf<Key>()
            fun add(id: String, kind: KeyKind, r: Int, col: Int, text: String? = null, long: String? = null) {
                keys += Key(id, kind, r, col * 2.5f, 2.5f, text = text, longPress = long, y = r * h, h = h)
            }
            listOf("1", "2", "3").forEachIndexed { i, d -> add("n$d", KeyKind.DIGIT, 0, i, d) }
            add("n-", KeyKind.SYMBOL, 0, 3, "-", "+")
            listOf("4", "5", "6").forEachIndexed { i, d -> add("n$d", KeyKind.DIGIT, 1, i, d) }
            add("space", KeyKind.SPACE, 1, 3)
            listOf("7", "8", "9").forEachIndexed { i, d -> add("n$d", KeyKind.DIGIT, 2, i, d) }
            add("back", KeyKind.BACKSPACE, 2, 3)
            add("mode", KeyKind.MODE, 3, 0)
            add("n0", KeyKind.DIGIT, 3, 1, "0")
            add("period", KeyKind.PERIOD, 3, 2, ".", ",")
            add("enter", KeyKind.ENTER, 3, 3)
            return KeyboardLayout(keys, rows)
        }

        private val SYM1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        private val SYM2 = listOf("+", "×", "÷", "=", "/", "_", "<", ">", "[", "]")
        private val SYM3 = listOf("!", "@", "#", "₩", "%", "^", "&", "*", "(", ")")
        private val SYM4 = listOf("-", "'", "\"", ":", ";", ",", "?")
        private val SYM2_2 = listOf("`", "~", "\\", "|", "{", "}", "€", "£", "¥", "$")
        private val SYM3_2 = listOf("°", "•", "○", "●", "□", "■", "♤", "♡", "◇", "☆")
        private val SYM4_2 = listOf("※", "《", "》", "¤", "♧", "¡", "¿")

        fun symbols(page: Int): KeyboardLayout {
            val keys = mutableListOf<Key>()
            val rowsText = if (page == 1) listOf(SYM1, SYM2, SYM3) else listOf(SYM1, SYM2_2, SYM3_2)
            rowsText.forEachIndexed { r, row -> row.forEachIndexed { i, s -> keys += Key("s$r-$i", KeyKind.SYMBOL, r, i.toFloat(), 1f, text = s) } }
            keys += Key("page", KeyKind.SHIFT, 3, 0f, 1.5f, text = if (page == 1) "1/2" else "2/2")
            (if (page == 1) SYM4 else SYM4_2).forEachIndexed { i, s -> keys += Key("s3-$i", KeyKind.SYMBOL, 3, 1.5f + i, 1f, text = s) }
            keys += Key("back", KeyKind.BACKSPACE, 3, 8.5f, 1.5f)
            bottomRow(keys, 4, true)
            return KeyboardLayout(keys, 5)
        }

        private fun bottomRow(keys: MutableList<Key>, r: Int, numpadKey: Boolean) {
            keys += Key("mode", KeyKind.MODE, r, 0f, 1.5f)
            keys += Key("comma", KeyKind.COMMA, r, 1.5f, 1f, text = ",", longPress = "!")
            keys += if (numpadKey) Key("numpad", KeyKind.NUMPAD, r, 2.5f, 1f) else Key("lang", KeyKind.LANG, r, 2.5f, 1f)
            keys += Key("space", KeyKind.SPACE, r, 3.5f, 4f)
            keys += Key("period", KeyKind.PERIOD, r, 7.5f, 1f, text = ".", longPress = "?")
            keys += Key("enter", KeyKind.ENTER, r, 8.5f, 1.5f)
        }
    }
}
