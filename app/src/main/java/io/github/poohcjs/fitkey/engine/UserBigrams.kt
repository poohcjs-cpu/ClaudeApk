package io.github.poohcjs.fitkey.engine

/**
 * 내가 이어 쓰는 낱말 순서: '앞 낱말 → 다음 낱말'을 몇 번 썼는지.
 * 이 휴대폰 안에서만 배우고 저장한다 (앱에 인터넷 권한이 없다).
 * 낱말은 글쇠 문자열로 적는다 (한국어는 두벌식 글쇠, 영어는 소문자).
 */
class UserBigrams {
    private val map = HashMap<String, HashMap<String, Int>>()   // 'k:앞 낱말' → (다음 낱말 → 횟수)
    private var pairs = 0
    var version = 0
        private set

    fun add(lang: Lang, prev: String, next: String, n: Int = 1) {
        if (prev.isEmpty() || next.isEmpty() || prev.length > 30 || next.length > 30) return
        val m = map.getOrPut(tag(lang, prev)) { HashMap() }
        if (next !in m) pairs++
        m[next] = (m[next] ?: 0) + n
        // 한 낱말 뒤에 오는 말은 PER_WORD개까지만 (드물게 쓴 것부터 잊는다)
        if (m.size > PER_WORD) {
            m.entries.filter { it.key != next }.minByOrNull { it.value }?.let { m.remove(it.key); pairs-- }
        }
        if (pairs > MAX) prune()
        version++
    }

    fun count(lang: Lang, prev: String, next: String): Int = map[tag(lang, prev)]?.get(next) ?: 0

    /**
     * 이 낱말 다음에 자주 쓴 말 (많이 쓴 순).
     * prefix: 다음 낱말을 치기 시작했으면 그 글쇠로 시작하는 것만. min: 적어도 이만큼 써야 보여 준다.
     */
    fun next(lang: Lang, prev: String, prefix: String = "", max: Int = 3, min: Int = 2): List<Pair<String, Int>> {
        val m = map[tag(lang, prev)] ?: return emptyList()
        return m.entries.filter { it.value >= min && it.key.startsWith(prefix) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(max).map { it.key to it.value }
    }

    /** 기억하는 낱말 쌍의 수 */
    val size: Int get() = pairs

    fun clear() { map.clear(); pairs = 0; version++ }

    /** 너무 많아지면 한 번만 쓴 것부터 지워 90%로 줄인다 */
    private fun prune() {
        val all = map.entries.flatMap { (p, m) -> m.entries.map { Triple(p, it.key, it.value) } }.sortedBy { it.third }
        all.take(pairs - MAX * 9 / 10).forEach { (p, n, _) ->
            map[p]?.let { m -> m.remove(n); if (m.isEmpty()) map.remove(p) }
        }
        pairs = map.values.sumOf { it.size }
    }

    fun serialize(): String = buildString {
        for ((p, m) in map) for ((n, c) in m) append(p).append('\t').append(n).append('\t').append(c).append('\n')
    }

    fun load(text: String) {
        map.clear()
        text.lineSequence().forEach { l ->
            val parts = l.split('\t')
            if (parts.size == 3 && parts[0].length > 2 && parts[1].isNotEmpty()) {
                parts[2].toIntOrNull()?.let { map.getOrPut(parts[0]) { HashMap() }[parts[1]] = it }
            }
        }
        pairs = map.values.sumOf { it.size }
        version++
    }

    private fun tag(lang: Lang, key: String) = (if (lang == Lang.KO) "k:" else "e:") + key

    companion object {
        const val MAX = 20000
        const val PER_WORD = 12
    }
}
