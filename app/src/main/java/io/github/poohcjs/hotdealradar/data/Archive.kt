package io.github.poohcjs.hotdealradar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/*
 * 커뮤니티 핫딜 가격 기록 (수집기가 data 브랜치에 달별로 쌓는 data/archive/YYYY-MM.json).
 * 검색한 상품이 뽐뿌·루리웹·클리앙·다모앙 핫딜로 얼마에 올라왔었는지 찾아,
 * 지금 가격이 싼 편인지('지금 사도 될까?') 알려 준다. 네이버 스마트스토어·공식몰 특가처럼
 * 가격비교 사이트에 없는 가격도 커뮤니티 글로는 남는다.
 */

@Serializable
data class ArchiveFile(val version: Int = 0, val month: String = "", val updatedAt: String? = null, val items: List<ArchiveItem> = emptyList())

@Serializable
data class ArchiveItem(
    val k: String = "",
    /** 올라온 시각 'YYYY-MM-DDTHH:MM' (한국 시간) */
    val t: String = "",
    val s: String = "",
    val n: String = "",
    val m: String = "",
    val p: Long = 0,
    val u: String = "",
    val f: Long? = null,
    val sh: String? = null,
    val v: Int? = null,
    val c: Int? = null,
    val e: Int? = null,
    val cat: String? = null,
    val shop: String? = null,
) {
    val total: Long get() = p + (f ?: 0)
    val ended: Boolean get() = e == 1
    val sourceName: String get() = SOURCE_NAMES[s] ?: s
    val date: String get() = t.take(10).replace('-', '.')

    companion object {
        val SOURCE_NAMES = mapOf(
            "ppomppu" to "뽐뿌", "ppomppu_overseas" to "해외뽐뿌", "ruliweb" to "루리웹", "clien" to "클리앙", "damoang" to "다모앙",
            "quasarzone" to "퀘이사존", "arcalive" to "아카라이브", "coolenjoy" to "쿨엔조이", "fmkorea" to "에펨코리아",
        )
    }
}

/** 검색어와 같은 상품의 지난 딜 + 단가(가능하면) */
data class PastDeal(val item: ArchiveItem, val unit: UnitPrice?)

data class PriceHistory(
    val deals: List<PastDeal>,
    /** 기록이 시작된 달 (기록이 짧으면 화면에 알려 준다) */
    val since: String?,
    val months: Int,
)

/** '지금 사도 될까?' 판단 */
data class BuyVerdict(val good: Boolean, val title: String, val detail: String)

object ArchiveSearch {
    /** 검색어 낱말이 모두 들어 있는 글 (띄어쓰기는 무시) */
    fun match(items: List<ArchiveItem>, query: String): List<ArchiveItem> {
        val words = QueryCleaner.clean(query).lowercase().split(' ').map { it.trim() }.filter { it.length >= 2 || it.any { c -> c.isDigit() } }
        if (words.isEmpty()) return emptyList()
        return items.filter { it.p > 0 }.filter { it2 ->
            val hay = (it2.n + " " + it2.m).replace(" ", "").lowercase()
            words.all { w -> w in hay || splitMatch(w, hay) }
        }
    }

    /** '건봉국밥'이 '건봉 돼지국밥'에도 맞도록: 붙여 쓴 말을 두 토막(각 2글자 이상)으로 나눠 둘 다 들어 있으면 같은 상품으로 */
    private fun splitMatch(word: String, hay: String): Boolean {
        if (word.length < 4 || word.any { it.isDigit() }) return false
        return (2..word.length - 2).any { i -> word.substring(0, i) in hay && word.substring(i) in hay }
    }

    fun history(items: List<ArchiveItem>, query: String, since: String?, months: Int): PriceHistory {
        val found = match(items, query)
            .distinctBy { it.k }
            .map { PastDeal(it, UnitPrice.estimate(it.n, it.total)) }
        // 단위가 같은 것끼리는 단가로, 아니면 배송비 포함 가격으로
        val common = found.mapNotNull { it.unit?.per }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val sorted = found.sortedWith(compareBy<PastDeal> { if (common != null && it.unit?.per == common) 0 else 1 }
            .thenBy { if (common != null && it.unit?.per == common) it.unit!!.value else it.item.total })
        return PriceHistory(sorted, since, months)
    }

    /** '건봉국밥 6팩'처럼 용량 없는 지난 딜은 지금 검색 결과의 같은 상품 용량으로 단가를 짐작해 다시 줄 세운다 */
    fun withPeers(h: PriceHistory, peers: List<LiveProduct>): PriceHistory {
        if (h.deals.none { it.unit == null } || peers.isEmpty()) return h
        val pseudo = h.deals.mapIndexed { i, d ->
            LiveProduct("past:$i", d.item.n, "", d.item.total, null, d.unit, emptyList(), false, false, i)
        }
        val inferred = inferUnits(peers.filter { !it.pcode.startsWith("past:") } + pseudo).filter { it.pcode.startsWith("past:") }
        val deals = h.deals.mapIndexed { i, d -> if (d.unit != null) d else d.copy(unit = inferred.getOrNull(i)?.unit) }
        val common = deals.mapNotNull { it.unit?.per }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return h.copy(deals = deals.sortedWith(compareBy<PastDeal> { if (common != null && it.unit?.per == common) 0 else 1 }
            .thenBy { if (common != null && it.unit?.per == common) it.unit!!.value else it.item.total }))
    }

    /**
     * 지금 찾은 가장 좋은 값(단가 또는 가격)과 지난 핫딜 최저를 비교한다.
     * 단위가 같을 때만 단가로 비교하고, 단위를 모르면 판단하지 않는다(엉뚱한 비교를 막으려고).
     */
    fun verdict(history: PriceHistory, bestNowUnit: UnitPrice?, bestNowCost: Long?): BuyVerdict? {
        val past = history.deals.firstOrNull() ?: return null
        val pu = past.unit
        val (now, then, per) = when {
            bestNowUnit != null && pu != null && pu.per == bestNowUnit.per -> Triple(bestNowUnit.value, pu.value, pu.per)
            else -> return null
        }
        val when_ = "${past.item.date} ${past.item.sourceName}"
        val diff = (now - then) * 100.0 / then
        return when {
            diff <= 3 -> BuyVerdict(
                true, "지금 가격이 지난 핫딜가 수준이에요",
                "지난 핫딜 최저 ${won(then)}원/$per ($when_) · 지금 ${won(now)}원/$per" + (bestNowCost?.let { "" } ?: ""),
            )
            diff <= 15 -> BuyVerdict(
                true, "괜찮은 가격이에요 (지난 핫딜보다 ${diff.toInt()}% 비쌈)",
                "지난 핫딜 최저 ${won(then)}원/$per ($when_). 급하면 지금 사도 손해가 크지 않아요",
            )
            else -> BuyVerdict(
                false, "지난 핫딜보다 ${diff.toInt()}% 비싸요 — 급하지 않으면 기다려 보세요",
                "지난 핫딜 최저 ${won(then)}원/$per ($when_) · 지금 ${won(now)}원/$per. 관심 키워드로 등록하면 핫딜이 올라올 때 알려 드려요",
            )
        }
    }
}

/** 달별 기록 파일 받기. 지난달 이전 파일은 하루, 이번 달은 30분 동안 다시 받지 않는다 */
class ArchiveClient(context: Context, private val baseUrl: () -> String) {
    private val dir = File(context.filesDir, "archive").apply { mkdirs() }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
    private val memory = HashMap<String, Pair<Long, List<ArchiveItem>>>()

    suspend fun load(months: Int = 6): Pair<List<ArchiveItem>, String?> = coroutineScope {
        val now = YearMonth.now(ZoneId.of("Asia/Seoul"))
        val list = (0 until months).map { now.minusMonths(it.toLong()).toString() }
        val parts = list.map { m -> async(Dispatchers.IO) { m to month(m, m == now.toString()) } }.awaitAll()
        val have = parts.filter { it.second.isNotEmpty() }
        have.flatMap { it.second } to have.minByOrNull { it.first }?.first
    }

    private fun month(m: String, current: Boolean): List<ArchiveItem> {
        val maxAge = if (current) 30 * 60_000L else 24 * 3_600_000L
        memory[m]?.takeIf { System.currentTimeMillis() - it.first < maxAge }?.let { return it.second }
        val file = File(dir, "$m.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < maxAge
        val text = if (fresh) file.readText() else runCatching { download(m)?.also { t -> file.writeText(t) } }.getOrNull()
            ?: file.takeIf { it.exists() }?.readText()
        val items = text?.let { runCatching { JSON.decodeFromString(ArchiveFile.serializer(), it).items }.getOrNull() }.orEmpty()
        memory[m] = System.currentTimeMillis() to items
        return items
    }

    private var mallsCache: Pair<Long, List<Mall>>? = null

    /** 수집기가 커뮤니티 딜 링크로 알아낸 공식몰 주소록 (6시간마다 다시 받기) */
    suspend fun malls(): List<Mall> = withContext(Dispatchers.IO) {
        mallsCache?.takeIf { System.currentTimeMillis() - it.first < 6 * 3_600_000L }?.let { return@withContext it.second }
        val file = File(dir, "malls.json")
        val text = runCatching { fetch("${baseUrl()}/data/malls.json")?.also { file.writeText(it) } }.getOrNull()
            ?: file.takeIf { it.exists() }?.readText()
        val list = text?.let { runCatching { MallDirectory.fromFile(JSON.decodeFromString(MallsFile.serializer(), it)) }.getOrNull() }.orEmpty()
        mallsCache = System.currentTimeMillis() to list
        list
    }

    private fun download(m: String): String? = fetch("${baseUrl()}/data/archive/$m.json")

    private fun fetch(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "HotdealRadar-Android").build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return ""
            if (!resp.isSuccessful) return null
            return resp.body.string()
        }
    }
}
