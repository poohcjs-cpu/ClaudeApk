package io.github.poohcjs.fitkey.engine

/*
 * 자판 배열과 글쇠 위치.
 * 위치는 '글쇠 단위'로 적는다: 가로 1 = 글쇠 하나 너비(한 줄 10칸), 세로 1 = 줄 높이 하나.
 * 터치 습관(어느 쪽으로 얼마나 빗나가는지)도 이 단위로 배운다. 그래서 키보드 높이를 바꿔도 배운 것이 그대로 맞는다.
 */

enum class KeyKind { LETTER, DIGIT, SYMBOL, SHIFT, BACKSPACE, SPACE, ENTER, LANG, MODE, COMMA, PERIOD }

enum class Lang { KO, EN }

enum class Mode { LETTERS, SYMBOLS, SYMBOLS2 }

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
) {
    val cx: Float get() = x + w / 2
    val cy: Float get() = row + 0.5f
    fun contains(ux: Float, uy: Float) = ux >= x && ux < x + w && uy >= row && uy < row + 1
}

class KeyboardLayout(val keys: List<Key>, val rows: Int) {
    val letters: List<Key> = keys.filter { it.kind == KeyKind.LETTER }
    private val byLetter = letters.associateBy { it.letter!! }
    fun keyOf(letter: Char): Key? = byLetter[letter.lowercaseChar()]

    /** 단순히 그 자리에 있는 글쇠 (가장자리 바깥은 가장 가까운 글쇠) */
    fun keyAt(ux: Float, uy: Float): Key? =
        keys.firstOrNull { it.contains(ux, uy) } ?: keys.minByOrNull { k ->
            val dx = maxOf(k.x - ux, 0f, ux - (k.x + k.w))
            val dy = maxOf(k.row - uy, 0f, uy - (k.row + 1))
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

        fun letters(numberRow: Boolean): KeyboardLayout {
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
            bottomRow(keys, r)
            return KeyboardLayout(keys, r + 1)
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
            bottomRow(keys, 4)
            return KeyboardLayout(keys, 5)
        }

        private fun bottomRow(keys: MutableList<Key>, r: Int) {
            keys += Key("mode", KeyKind.MODE, r, 0f, 1.5f)
            keys += Key("comma", KeyKind.COMMA, r, 1.5f, 1f, text = ",", longPress = "!")
            keys += Key("lang", KeyKind.LANG, r, 2.5f, 1f)
            keys += Key("space", KeyKind.SPACE, r, 3.5f, 4f)
            keys += Key("period", KeyKind.PERIOD, r, 7.5f, 1f, text = ".", longPress = "?")
            keys += Key("enter", KeyKind.ENTER, r, 8.5f, 1.5f)
        }
    }
}
