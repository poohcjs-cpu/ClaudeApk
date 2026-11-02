package io.github.poohcjs.hotdealradar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/*
 * 실시간 최저가 검색.
 * 사용자가 검색할 때만 다나와(여러 쇼핑몰 가격을 모아 둔 가격비교 사이트)에 직접 물어본다.
 *   검색:  https://search.danawa.com/?query=…        (robots.txt 허용)
 *   상품:  https://prod.danawa.com/info/?pcode=…    (robots.txt 허용, /info/ajax/ 등 막힌 경로는 쓰지 않음)
 * 같은 검색은 10분 동안 다시 묻지 않고, 같은 사이트에는 요청 사이를 띄운다. 막히면 우회하지 않고 알려 준다.
 */

data class LiveOption(
    val pcode: String,
    val label: String,
    val price: Long,
    val malls: Int?,
    val unit: UnitPrice?,
)

/** '98원/10m' → 98원 per '10m' */
data class UnitPrice(val text: String, val value: Long, val per: String) {
    companion object {
        private val RE = Regex("""([\d,]+)\s*원\s*/\s*(.+)""")
        fun parse(text: String?): UnitPrice? {
            val t = text?.trim().orEmpty()
            val m = RE.matchEntire(t) ?: return null
            val v = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
            return UnitPrice(t, v, m.groupValues[2].trim())
        }
    }
}

data class LiveProduct(
    val pcode: String,
    val name: String,
    val image: String,
    val price: Long,
    val malls: Int?,
    val unit: UnitPrice?,
    val options: List<LiveOption>,
    val used: Boolean,
    val overseas: Boolean,
    val rank: Int,
) {
    /** 단가가 가장 낮은 옵션 (단가 정보가 없으면 null) */
    val bestUnitOption: LiveOption? get() = options.filter { it.unit != null }.minByOrNull { it.unit!!.value }
}

data class LiveOffer(val mall: String, val price: Long, val shipping: String, val fee: Long?, val link: String)

data class LivePage(
    val pcode: String,
    val name: String,
    val image: String,
    val lowest: Long?,
    val offerCount: Int?,
    val offers: List<LiveOffer>,
)

class LiveBlocked(message: String) : IOException(message)

enum class LiveSort(val label: String) { UNIT("단가 낮은 순"), PRICE("가격 낮은 순"), RANK("다나와 추천순") }

/** 단가순: 가장 흔한 단위(예: 10m)끼리 단가로 줄 세우고, 단위가 다르거나 없는 것은 그 뒤에 가격순 */
fun sortLive(list: List<LiveProduct>, sort: LiveSort): List<LiveProduct> = when (sort) {
    LiveSort.PRICE -> list.sortedBy { it.price }
    LiveSort.RANK -> list.sortedBy { it.rank }
    LiveSort.UNIT -> {
        val unitOf = { p: LiveProduct -> (p.bestUnitOption?.unit ?: p.unit) }
        val common = list.mapNotNull { unitOf(it)?.per }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        list.sortedWith(
            compareBy<LiveProduct> { if (common != null && unitOf(it)?.per == common) 0 else 1 }
                .thenBy { if (common != null && unitOf(it)?.per == common) unitOf(it)!!.value else it.price },
        )
    }
}

/** 단위 정보가 절반 이상 있으면 단가순이 기본 */
fun defaultLiveSort(list: List<LiveProduct>): LiveSort =
    if (list.isNotEmpty() && list.count { it.unit != null || it.bestUnitOption != null } * 2 >= list.size) LiveSort.UNIT else LiveSort.PRICE

object DanawaParser {
    private const val PUSH = "self.__next_f.push([1,\""
    private val TAG = Regex("<[^>]+>")
    private val USED = listOf("중고", "리퍼", "전시", "반품", "B급")
    private val OVERSEAS = listOf("해외구매", "해외직구", "병행수입", "직구")

    private fun plain(s: String?): String =
        org.jsoup.parser.Parser.unescapeEntities(TAG.replace(s.orEmpty(), ""), false).replace(Regex("\\s+"), " ").trim()

    /** 검색 결과 화면(Next.js)에 함께 실려 오는 상품 데이터 */
    fun parseSearch(html: String): List<LiveProduct> {
        if (!html.contains("__next_f")) return emptyList()
        val payload = buildString {
            for (raw in pushedStrings(html)) {
                runCatching { append(JSON.decodeFromString<String>("\"" + raw + "\"")) }
            }
        }
        val out = mutableListOf<LiveProduct>()
        val seen = HashSet<String>()
        var from = 0
        while (true) {
            val start = payload.indexOf("{\"prodType\":", from)
            if (start < 0) break
            val end = objectEnd(payload, start)
            from = if (end > start) end else start + 1
            if (end <= start) continue
            val obj = runCatching { JSON.parseToJsonElement(payload.substring(start, end)).jsonObject }.getOrNull() ?: continue
            val pcode = obj.str("productSeq") ?: continue
            val price = obj.long("minPrice") ?: 0
            if (pcode in seen || price <= 0 || !pcode.all { it.isDigit() }) continue
            if (obj["isAdultProduct"]?.let { (it as? JsonPrimitive)?.contentOrNull } == "true") continue
            seen += pcode
            val name = plain(obj.str("productName"))
            val bundle = plain(obj.str("bundleName"))
            val unit = UnitPrice.parse(obj.str("unitPrice"))
            val options = mutableListOf<LiveOption>()
            if (bundle.isNotEmpty()) options += LiveOption(pcode, bundle, price, obj.long("shopCount")?.toInt(), unit)
            (obj["bundleList"] as? JsonArray)?.forEach { b ->
                val bo = b as? JsonObject ?: return@forEach
                val bp = bo.str("productSeq") ?: return@forEach
                val bprice = bo.long("minPrice") ?: return@forEach
                if (bp != pcode && bprice > 0 && options.none { it.pcode == bp }) {
                    options += LiveOption(bp, plain(bo.str("bundleName")), bprice, bo.long("shopCount")?.toInt(), UnitPrice.parse(bo.str("unitPrice")))
                }
            }
            out += LiveProduct(
                pcode = pcode, name = name, image = obj.str("imageUrl")?.substringBefore('?').orEmpty(),
                price = price, malls = obj.long("shopCount")?.toInt(), unit = unit,
                options = options.sortedBy { it.price },
                used = USED.any { it in name }, overseas = OVERSEAS.any { it in name }, rank = out.size,
            )
        }
        return out
    }

    /** self.__next_f.push([1,"…"]) 의 따옴표 안 글자들 (정규식은 아주 긴 문자열에서 스택이 넘쳐 직접 읽는다) */
    internal fun pushedStrings(html: String): List<String> {
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = html.indexOf(PUSH, from)
            if (start < 0) break
            var i = start + PUSH.length
            val begin = i
            while (i < html.length && html[i] != '"') i += if (html[i] == '\\') 2 else 1
            if (i >= html.length) break
            out += html.substring(begin, i)
            from = i + 1
        }
        return out
    }

    /** 문자열 안의 괄호는 건너뛰며 { … } 의 끝 위치를 찾는다 */
    internal fun objectEnd(s: String, start: Int): Int {
        var depth = 0
        var inStr = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') i++ else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i + 1 }
            }
            i++
        }
        return -1
    }

    /** 상품 페이지: 검색엔진용 구조화 데이터 + 처음부터 실려 오는 판매처 목록 (카드 조건가는 뺀다) */
    fun parseProduct(html: String, pcode: String): LivePage {
        val doc = Jsoup.parse(html, "https://prod.danawa.com/")
        var name = ""
        var image = ""
        var lowest: Long? = null
        var offerCount: Int? = null
        for (s in doc.select("script[type=application/ld+json]")) {
            val el = runCatching { JSON.parseToJsonElement(s.data()) }.getOrNull() ?: continue
            val items = when (el) {
                is JsonArray -> el.toList()
                is JsonObject -> (el["@graph"] as? JsonArray)?.toList() ?: listOf(el)
                else -> emptyList()
            }
            val prod = items.mapNotNull { it as? JsonObject }.firstOrNull { (it["@type"] as? JsonPrimitive)?.contentOrNull == "Product" } ?: continue
            name = plain(prod.str("name"))
            image = when (val img = prod["image"]) {
                is JsonArray -> (img.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
                is JsonPrimitive -> img.contentOrNull.orEmpty()
                else -> ""
            }
            (prod["offers"] as? JsonObject)?.let { o ->
                lowest = o.long("lowPrice")
                offerCount = o.long("offerCount")?.toInt()
            }
            break
        }
        if (name.isEmpty()) name = doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" : 다나와")?.trim().orEmpty()
        if (image.isEmpty()) image = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        if (image.startsWith("//")) image = "https:$image"

        val best = LinkedHashMap<String, LiveOffer>()
        for (row in doc.select("li.list-item")) {
            if (row.selectFirst(".box__logo, [class*=logo]") == null) continue
            if (row.parents().any { it.hasClass("list__membership") }) continue // '○○카드 결제 시' 가격
            val mall = mallOf(row)
            if (mall.isBlank()) continue
            val price = priceOf(row) ?: continue
            val (ship, fee) = shippingOf(row)
            val link = row.selectFirst("a.link__full-cover, a[href*=bridge]")?.absUrl("href").orEmpty()
            val cur = best[mall]
            if (cur == null || price < cur.price) best[mall] = LiveOffer(mall, price, ship, fee, link)
        }
        val offers = best.values.sortedBy { it.price }
        return LivePage(pcode, name, image, offers.firstOrNull()?.price?.let { p -> lowest?.let { minOf(it, p) } ?: p } ?: lowest, offerCount, offers)
    }

    private fun mallOf(row: Element): String {
        row.selectFirst(".box__logo img[alt], [class*=logo] img[alt]")?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() }?.let { return normalizeMall(it) }
        return normalizeMall(row.selectFirst(".box__logo, [class*=mall-name], [class*=mall_name]")?.text()?.trim().orEmpty())
    }

    private fun priceOf(row: Element): Long? {
        row.selectFirst(".box__price .sell-price .text__num, .sell-price .text__num")?.text()?.digits()?.takeIf { it >= 100 }?.let { return it }
        val m = Regex("""(\d{1,3}(?:,\d{3})+|\d{3,})\s*원""").find(row.text()) ?: return null
        return m.groupValues[1].digits()
    }

    private fun shippingOf(row: Element): Pair<String, Long?> {
        val box = row.selectFirst(".box__delivery") ?: return "" to null
        val own = box.ownText().trim().ifEmpty { box.text().trim() }
        return when {
            own.contains("무료") -> "무료배송" to 0L
            else -> Regex("""([\d,]+)\s*원""").find(own)?.groupValues?.get(1)?.digits()?.let { "배송비 ${won(it)}원" to it } ?: ("" to null)
        }
    }

    private val MALL_ALIAS = mapOf("SSG.COM" to "SSG닷컴", "신세계몰" to "SSG닷컴", "지마켓" to "G마켓", "롯데온" to "롯데ON")
    private fun normalizeMall(s: String) = MALL_ALIAS[s] ?: s

    private fun String.digits(): Long? = filter { it.isDigit() }.toLongOrNull()
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.filter { c -> c.isDigit() }?.toLongOrNull() }
}

/** 다나와에 직접 묻는 부분. 화면에서 검색·상품 열기를 할 때만 부른다 */
class LiveSearchClient(private val client: OkHttpClient = defaultClient()) {
    private data class Cached<T>(val at: Long, val value: T)
    private val searches = HashMap<String, Cached<List<LiveProduct>>>()
    private val pages = HashMap<String, Cached<LivePage>>()
    private val gate = Mutex()
    private val lastAt = HashMap<String, Long>()

    suspend fun search(query: String): List<LiveProduct> {
        val q = query.trim()
        searches[q]?.takeIf { fresh(it.at) }?.let { return it.value }
        val html = get("https://search.danawa.com/?query=" + URLEncoder.encode(q, "UTF-8"))
        val list = DanawaParser.parseSearch(html)
        searches[q] = Cached(System.currentTimeMillis(), list)
        return list
    }

    suspend fun product(pcode: String): LivePage {
        pages[pcode]?.takeIf { fresh(it.at) }?.let { return it.value }
        val page = DanawaParser.parseProduct(get("https://prod.danawa.com/info/?pcode=$pcode"), pcode)
        pages[pcode] = Cached(System.currentTimeMillis(), page)
        return page
    }

    private fun fresh(at: Long) = System.currentTimeMillis() - at < CACHE_MS

    private suspend fun get(url: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        gate.withLock {
            val wait = (lastAt[host] ?: 0L) + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastAt[host] = System.currentTimeMillis()
        }
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (resp.code in listOf(403, 429, 430, 451) || CHALLENGE.any { it in body.take(6000) }) {
                    throw LiveBlocked("다나와가 지금은 자동 요청을 받지 않아요")
                }
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                body
            }
        }
    }

    companion object {
        // 수집기와 같은, 이름을 숨기지 않는 User-Agent (휴대폰 표시를 넣으면 다나와가 모바일 화면으로 보내 형식이 달라진다)
        const val UA = "Mozilla/5.0 (compatible; HotdealRadar/2.0; personal price checker)"
        const val CACHE_MS = 10 * 60 * 1000L
        const val MIN_GAP_MS = 2_000L
        private val CHALLENGE = listOf("cf-chl", "challenge-platform", "<title>Just a moment...</title>", "captcha-delivery")
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
}
