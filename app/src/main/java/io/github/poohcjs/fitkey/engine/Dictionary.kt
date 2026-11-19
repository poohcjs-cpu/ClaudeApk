package io.github.poohcjs.fitkey.engine

import java.io.BufferedReader
import kotlin.math.ln

/**
 * 낱말 사전. 낱말을 '글쇠 문자열'로 정렬해 둔다 (한글 '안녕' = "dkssud", 영어는 소문자 그대로).
 * 그래서 한글·영어 모두 "이 글쇠들로 시작하는 낱말이 있나?"를 같은 방법(이진 탐색)으로 빠르게 찾는다.
 *
 * 출처: FrequencyWords (OpenSubtitles 2018, Hermit Dave) — CC BY-SA 4.0
 */
class Dictionary(val lang: Lang, private val keys: Array<String>, private val counts: IntArray, private val display: Map<String, String> = emptyMap()) {
    val size: Int get() = keys.size
    private val total: Double = counts.sumOf { it.toLong() }.toDouble().coerceAtLeast(1.0)

    /** 첫 위치: key 이상인 첫 낱말 */
    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun count(key: String): Int {
        val i = lowerBound(key)
        return if (i < keys.size && keys[i] == key) counts[i] else 0
    }

    fun hasPrefix(prefix: String): Boolean {
        val i = lowerBound(prefix)
        return i < keys.size && keys[i].startsWith(prefix)
    }

    /** 낱말이 나올 가능성 (로그). 사전에 없으면 null */
    fun logProb(key: String): Float? = count(key).takeIf { it > 0 }?.let { ln(it / total).toFloat() }

    /** 가장 드문 낱말보다 조금 더 드문 값: 사전에 없는 낱말의 가능성 */
    val unknownLogProb: Float = ln(1.0 / total).toFloat() - 2f

    /** 이 글쇠들로 시작하는 낱말 중 많이 쓰는 것 */
    fun completions(prefix: String, max: Int = 3, scanLimit: Int = 4000): List<Pair<String, Int>> {
        if (prefix.isEmpty()) return emptyList()
        val top = ArrayList<Pair<String, Int>>()
        var i = lowerBound(prefix)
        var scanned = 0
        while (i < keys.size && scanned < scanLimit && keys[i].startsWith(prefix)) {
            top += keys[i] to counts[i]
            if (top.size > max * 4) { top.sortByDescending { it.second }; while (top.size > max) top.removeAt(top.lastIndex) }
            i++; scanned++
        }
        return top.sortedByDescending { it.second }.take(max)
    }

    /** 글쇠 문자열 → 화면에 쓸 낱말 */
    fun wordOf(key: String): String = when (lang) {
        Lang.KO -> Hangul.composeKeys(key)
        Lang.EN -> display[key] ?: CONTRACTIONS[key] ?: key
    }

    companion object {
        /** 영어 사전에서 따옴표가 빠진 줄임말을 되살린다 */
        val CONTRACTIONS = mapOf(
            "dont" to "don't", "im" to "I'm", "cant" to "can't", "wont" to "won't", "ive" to "I've", "youre" to "you're",
            "thats" to "that's", "didnt" to "didn't", "doesnt" to "doesn't", "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't",
            "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "theyre" to "they're", "whats" to "what's",
            "theres" to "there's", "youll" to "you'll", "youve" to "you've", "weve" to "we've", "theyve" to "they've", "hes" to "he's",
            "shes" to "she's", "itll" to "it'll", "id" to "I'd", "ill" to "I'll", "i" to "I", "lets" to "let's", "havent" to "haven't",
            "hasnt" to "hasn't", "werent" to "weren't", "hadnt" to "hadn't", "whos" to "who's", "heres" to "here's",
        )

        /** "글쇠\t횟수[\t보여 줄 낱말]" 줄들 (글쇠 순으로 정렬돼 있어야 함) */
        fun read(lang: Lang, reader: BufferedReader): Dictionary {
            val keys = ArrayList<String>(170_000)
            val counts = ArrayList<Int>(170_000)
            val display = HashMap<String, String>()
            reader.forEachLine { line ->
                val t1 = line.indexOf('\t')
                if (t1 <= 0) return@forEachLine
                val t2 = line.indexOf('\t', t1 + 1)
                val key = line.substring(0, t1)
                val n = (if (t2 < 0) line.substring(t1 + 1) else line.substring(t1 + 1, t2)).toIntOrNull() ?: return@forEachLine
                keys += key
                counts += n
                if (t2 > 0) display[key] = line.substring(t2 + 1)
            }
            return Dictionary(lang, keys.toTypedArray(), counts.toIntArray(), display)
        }

        /** 시험용: 낱말 목록으로 바로 만들기 */
        fun of(lang: Lang, words: Map<String, Int>): Dictionary {
            val pairs = words.mapNotNull { (w, n) ->
                val k = if (lang == Lang.KO) Hangul.toKeys(w) else w.lowercase().replace("'", "")
                k?.let { it to n }
            }.sortedBy { it.first }
            return Dictionary(lang, pairs.map { it.first }.toTypedArray(), pairs.map { it.second }.toIntArray())
        }
    }
}

/**
 * 내가 쓰는 낱말 (이 기기에만). 사전에 없는 말(이름·신조어)을 고치지 않게 하고, 자주 쓰는 말을 먼저 추천한다.
 * 비밀번호 칸에서는 쓰지 않는다.
 */
class UserWords {
    private val counts = HashMap<String, Int>()   // 글쇠 문자열 → 횟수 (언어 구분은 글쇠 앞에 'k:' / 'e:')
    var version = 0
        private set

    fun add(lang: Lang, key: String, n: Int = 1) {
        if (key.length < 2 || key.length > 30) return
        val k = tag(lang, key)
        counts[k] = (counts[k] ?: 0) + n
        if (counts.size > MAX) {
            counts.entries.sortedBy { it.value }.take(counts.size - MAX).forEach { counts.remove(it.key) }
        }
        version++
    }

    fun count(lang: Lang, key: String): Int = counts[tag(lang, key)] ?: 0

    /** 두 번 이상 쓴 말은 '아는 말'로 본다 */
    fun known(lang: Lang, key: String): Boolean = count(lang, key) >= 2

    fun completions(lang: Lang, prefix: String, max: Int = 3): List<Pair<String, Int>> {
        val p = tag(lang, prefix)
        return counts.entries.filter { it.key.startsWith(p) && it.value >= 2 }.sortedByDescending { it.value }.take(max)
            .map { it.key.substring(2) to it.value }
    }

    fun hasPrefix(lang: Lang, prefix: String): Boolean {
        val p = tag(lang, prefix)
        return counts.keys.any { it.startsWith(p) && (counts[it] ?: 0) >= 2 }
    }

    val size: Int get() = counts.size

    fun clear() { counts.clear(); version++ }

    fun serialize(): String = counts.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun load(text: String) {
        counts.clear()
        text.lines().forEach { l ->
            val t = l.indexOf('\t')
            if (t > 2) l.substring(t + 1).toIntOrNull()?.let { counts[l.substring(0, t)] = it }
        }
        version++
    }

    private fun tag(lang: Lang, key: String) = (if (lang == Lang.KO) "k:" else "e:") + key

    companion object { const val MAX = 5000 }
}
