package io.github.poohcjs.hotdealradar.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/*
 * 브랜드 공식몰·자사몰 직접 검색.
 * 공식몰 특가(예: 건봉국밥 공식몰 4+1팩)는 다나와·에누리에 안 올라오는 경우가 많다.
 * 앱이 아는 공식몰(기본 목록 + 수집기가 커뮤니티 딜 링크로 알아낸 곳 + 사용자가 공유로 알려 준 곳) 가운데
 * 검색어와 관련 있는 곳만 골라, 그 쇼핑몰의 검색 화면을 직접 읽는다. 쇼핑몰마다 robots.txt를 먼저 확인한다.
 * 지금은 국내 자사몰에서 가장 흔한 카페24 쇼핑몰을 읽는다.
 */

@Serializable
data class Mall(
    val host: String,
    val name: String = "",
    val platform: String = "cafe24",
    val keywords: List<String> = emptyList(),
    /** 사용자가 직접 알려 준 쇼핑몰 */
    val user: Boolean = false,
) {
    val label: String get() = name.ifBlank { host }
}

/** 수집기가 만드는 data/malls.json */
@Serializable
data class MallsFile(val version: Int = 0, val updatedAt: String? = null, val malls: Map<String, MallEntry> = emptyMap())

@Serializable
data class MallEntry(val name: String = "", val platform: String = "", val keywords: List<String> = emptyList(), val count: Int = 0)

object MallDirectory {
    /** 처음부터 아는 공식몰 (카페24, 검색 화면 robots 허용 확인) */
    val SEED = listOf(
        Mall("gunbong1987.com", "건봉국밥 공식몰", "cafe24", listOf("건봉", "건봉국밥", "순천건봉국밥", "돼지국밥", "순대국밥", "국밥")),
    )

    fun fromFile(f: MallsFile): List<Mall> = f.malls.mapNotNull { (host, e) ->
        if (e.platform != "cafe24") null else Mall(host, e.name, e.platform, e.keywords)
    }

    /** 같은 주소는 사용자 > 기본 > 수집기 순으로 하나만 */
    fun merge(user: List<Mall>, learned: List<Mall>): List<Mall> {
        val out = LinkedHashMap<String, Mall>()
        for (m in user + SEED + learned) out.putIfAbsent(m.host, m)
        return out.values.toList()
    }

    /**
     * 검색어와 관련 있는 쇼핑몰. 쇼핑몰 이름이나 그 쇼핑몰 딜에 나온 낱말이 검색어에 들어 있으면 점수를 준다.
     * 많아야 [max]곳만 (쇼핑몰마다 요청이 1~3번 늘어서).
     */
    fun match(query: String, malls: List<Mall>, max: Int = 3): List<Pair<Mall, String>> {
        val q = query.replace(" ", "").lowercase()
        if (q.length < 2) return emptyList()
        val qWords = productWords(query).map { it.lowercase() }
        return malls.mapNotNull { m ->
            var score = 0
            var hit: String? = null
            val name = m.name.replace(" ", "").lowercase().removeSuffix("공식몰").removeSuffix("몰")
            if (name.length >= 2 && (name in q || qWords.any { it.length >= 2 && it in name })) { score += 10; hit = qWords.firstOrNull { it in name } ?: name }
            for (k in m.keywords) {
                val kw = k.replace(" ", "").lowercase()
                if (kw.length < 2 || kw in GENERIC_WORDS) continue
                if (kw in q) { score += kw.length; if (hit == null || kw.length > hit.length) hit = kw }
            }
            if (m.user) score += 2
            if (score < 2) null else Triple(m, hit ?: query, score)
        }.sortedByDescending { it.third }.take(max).map { it.first to it.second }
    }

    private val GENERIC_WORDS = setOf("무료배송", "특가", "할인", "세트", "공식몰", "쿠폰", "증정", "선물", "추천", "정품", "국내산")
}

/** robots.txt (우리 이름 'HotdealRadar' 그룹, 없으면 '*' 그룹). 가장 길게 맞는 규칙이 이기고, 같으면 허용 */
class Robots(text: String?) {
    private data class Rule(val allow: Boolean, val pattern: String)
    private val rules: List<Rule>

    init {
        val groups = mutableListOf<Pair<MutableList<String>, MutableList<Rule>>>()
        var agents = mutableListOf<String>()
        var current: MutableList<Rule>? = null
        for (raw in text.orEmpty().lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val key = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            when (key) {
                "user-agent" -> {
                    if (current != null) { agents = mutableListOf(); current = null }
                    agents += value.lowercase()
                }
                "allow", "disallow" -> {
                    if (current == null) { current = mutableListOf(); groups += agents to current }
                    if (value.isNotEmpty()) current += Rule(key == "allow", value)
                }
            }
        }
        rules = (groups.firstOrNull { (a, _) -> a.any { "hotdealradar" in it } } ?: groups.firstOrNull { (a, _) -> "*" in a })?.second ?: emptyList()
    }

    fun allowed(pathAndQuery: String): Boolean {
        val best = rules.filter { matches(it.pattern, pathAndQuery) }
            .maxWithOrNull(compareBy<Rule> { it.pattern.length }.thenBy { if (it.allow) 1 else 0 }) ?: return true
        return best.allow
    }

    private fun matches(pattern: String, path: String): Boolean {
        val anchored = pattern.endsWith("$")
        val body = pattern.removeSuffix("$")
        val re = buildString {
            append('^')
            for (c in body) if (c == '*') append(".*") else append(Regex.escape(c.toString()))
            if (anchored) append('$')
        }
        return Regex(re).containsMatchIn(path)
    }
}

/** 카페24 쇼핑몰 화면 읽기 */
object Cafe24Parser {
    private val PRICE = Regex("""(\d{1,3}(?:,\d{3})+|\d{3,9})\s*원""")
    private val NOT_PRICE = listOf("적립", "포인트", "소비자", "정가", "리뷰", "쿠폰", "배송")

    fun searchUrl(host: String, keyword: String) = "https://$host/product/search.html?keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8")

    fun parseSearch(html: String, mall: Mall): List<LiveProduct> {
        val doc = Jsoup.parse(html, "https://${mall.host}/")
        val out = mutableListOf<LiveProduct>()
        for (li in doc.select("li[id^=anchorBoxId_]")) {
            val no = li.id().removePrefix("anchorBoxId_")
            if (li.select("img[alt*=품절], .soldout, .icon_soldout").any { it.parents().none { p -> p.hasClass("displaynone") } }) continue
            val a = li.selectFirst(".name a, .description a[href*=/product/]") ?: li.selectFirst("a[href*=/product/]") ?: continue
            val name = a.text().replace(Regex("""^\s*상품명\s*:?\s*"""), "").trim()
            if (name.isEmpty()) continue
            val link = a.absUrl("href").ifBlank { continue }
            val img = li.selectFirst(".thumbnail img, .prdImg img, img")?.let { it.absUrl("src").ifBlank { it.attr("src") } }.orEmpty()
                .let { if (it.startsWith("//")) "https:$it" else it }
            val price = priceOf(li) ?: continue
            out += LiveProduct(
                pcode = "mall:${mall.host}:$no", name = name, image = img, price = price, malls = null,
                unit = UnitPrice.estimate(name, price), options = emptyList(), used = false, overseas = false, rank = out.size,
                mall = mall.label, link = link, source = LiveSource.MALL,
            )
        }
        return out
    }

    /** 상품 칸의 '판매가'·'할인판매가' 중 가장 싼 값 (적립금·소비자가는 뺀다) */
    internal fun priceOf(li: org.jsoup.nodes.Element): Long? {
        val labeled = li.select(".spec li, ul.xans-product-listitem li, .description li").mapNotNull { row ->
            val label = row.selectFirst("strong, .title")?.text().orEmpty()
            if (NOT_PRICE.any { it in label }) return@mapNotNull null
            if (label.isNotBlank() && listOf("판매가", "할인", "가격", "혜택", "price", "Price").none { it in label }) return@mapNotNull null
            PRICE.find(row.text().removePrefix(label))?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        }.filter { it >= 100 }
        if (labeled.isNotEmpty()) return labeled.min()
        val text = li.text()
        return PRICE.findAll(text).filter { m ->
            val before = text.substring(maxOf(0, m.range.first - 12), m.range.first)
            NOT_PRICE.none { it in before }
        }.mapNotNull { it.groupValues[1].replace(",", "").toLongOrNull() }.filter { it >= 100 }.minOrNull()
    }
}

/** 상품 페이지에서 배송비 규칙: '배송비 3,500원 (50,000원 이상 구매 시 무료)' */
data class ShipRule(val fee: Long, val freeOver: Long? = null) {
    fun feeFor(price: Long): Long = if (freeOver != null && price >= freeOver) 0 else fee
    val text: String get() = when {
        fee == 0L -> "무료배송"
        freeOver != null -> "배송비 ${won(fee)}원 (${won(freeOver)}원 이상 무료)"
        else -> "배송비 ${won(fee)}원"
    }

    companion object {
        private val RULE = Regex("""배송비\s*[:：]?\s*(무료|[\d,]+\s*원)(?:\s*\(\s*([\d,]+)\s*원\s*이상[^)]{0,20}무료)?""")
        fun parse(text: String): ShipRule? {
            val m = RULE.find(text) ?: return null
            val fee = if (m.groupValues[1].startsWith("무료")) 0L else m.groupValues[1].filter { it.isDigit() }.toLongOrNull() ?: return null
            val over = m.groupValues[2].filter { it.isDigit() }.toLongOrNull()
            return ShipRule(fee, over)
        }
    }
}

/** 공유받은 상품 링크를 읽은 결과 */
data class LinkInfo(
    val url: String,
    val host: String,
    val site: String,
    val name: String,
    val price: Long?,
    val ship: ShipRule?,
    val image: String,
    val platform: String?,
) {
    val fee: Long? get() = price?.let { ship?.feeFor(it) }
    val total: Long? get() = price?.let { it + (fee ?: 0) }
}

object LinkReader {
    private val URL = Regex("""https?://[^\s<>"']+""")

    fun findUrl(text: String?): String? = text?.let { URL.find(it)?.value?.trimEnd('.', ',', ')', ']') }

    fun platformOf(url: String): String? {
        val path = runCatching { URI(url) }.getOrNull()?.let { (it.rawPath ?: "") + (it.rawQuery?.let { q -> "?$q" } ?: "") } ?: return null
        return when {
            Regex("""/product/detail\.html|/product/[^/?#]+/\d+(?:/|$)|/surl/[OP]/\d+""").containsMatchIn(path) -> "cafe24"
            "/goods/goods_view.php" in path -> "godomall"
            "/shop/shopdetail.html" in path -> "makeshop"
            else -> null
        }
    }

    fun hostOf(url: String): String = (runCatching { URI(url).host }.getOrNull() ?: "").lowercase().removePrefix("www.").let {
        if (it.startsWith("m.") && it.count { c -> c == '.' } >= 2) it.removePrefix("m.") else it
    }

    fun parse(html: String, url: String): LinkInfo {
        val doc = Jsoup.parse(html, url)
        val host = hostOf(url)
        val site = meta(doc, "og:site_name")?.trim().orEmpty().ifBlank { host }
        var name = ""
        var price: Long? = null
        var image = meta(doc, "og:image").orEmpty()
        for (s in doc.select("script[type=application/ld+json]")) {
            val el = runCatching { JSON.parseToJsonElement(s.data()) }.getOrNull() ?: continue
            val items = when (el) {
                is JsonArray -> el.toList()
                is JsonObject -> (el["@graph"] as? JsonArray)?.toList() ?: listOf(el)
                else -> emptyList()
            }
            val prod = items.mapNotNull { it as? JsonObject }.firstOrNull { (it["@type"] as? JsonPrimitive)?.contentOrNull == "Product" } ?: continue
            name = (prod["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val offers = prod["offers"]
            val o = (offers as? JsonObject) ?: (offers as? JsonArray)?.firstOrNull() as? JsonObject
            price = o?.let { num(it, "price") ?: num(it, "lowPrice") }
            if (image.isEmpty()) image = when (val img = prod["image"]) {
                is JsonArray -> (img.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
                is JsonPrimitive -> img.contentOrNull.orEmpty()
                else -> ""
            }
            break
        }
        val metaPrice = listOf("product:sale_price:amount", "product:price:amount", "og:price:amount")
            .firstNotNullOfOrNull { meta(doc, it)?.filter { c -> c.isDigit() || c == '.' }?.toDoubleOrNull()?.toLong()?.takeIf { p -> p > 0 } }
        if (metaPrice != null) price = price?.let { minOf(it, metaPrice) } ?: metaPrice
        if (name.isBlank()) name = meta(doc, "og:title") ?: doc.title()
        name = cleanTitle(name, site)
        if (image.startsWith("//")) image = "https:$image"
        return LinkInfo(url, host, site, name, price, ShipRule.parse(doc.text()), image, platformOf(url))
    }

    /** 'XX - 쇼핑몰이름', '[공식몰] XX' 같은 꾸밈을 뺀 상품 이름 */
    fun cleanTitle(title: String, site: String = ""): String {
        var t = title.trim()
        if (site.isNotBlank()) for (sep in listOf(" - ", " | ", " : ", " – ")) if (t.endsWith(sep + site)) t = t.removeSuffix(sep + site)
        t = t.replace(Regex("""[★☆♥❤️✨🔥]+"""), " ")
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    private fun meta(doc: Document, prop: String): String? =
        doc.selectFirst("meta[property=$prop], meta[name=$prop], meta[itemprop=$prop]")?.attr("content")?.takeIf { it.isNotBlank() }

    private fun num(o: JsonObject, k: String): Long? =
        (o[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }?.takeIf { it > 0 }
}

/** 링크 상품 이름 → 검색어: 괄호 속 광고 문구와 숫자 낱말을 빼고 상품을 가리키는 낱말 3개까지 */
fun linkQuery(name: String): String {
    val plain = name.replace(Regex("""[(\[【<][^)\]】>]*[)\]】>]"""), " ")
    val words = productWords(plain).take(3)
    return words.joinToString(" ").ifBlank { QueryCleaner.clean(plain).split(' ').take(4).joinToString(" ") }
}

/** 검색어 다듬기: '가장 싼 화장지 추천' → '화장지' (사람 말투로 써도 상품 이름만 검색) */
object QueryCleaner {
    private val FILLER = listOf(
        "가장 싼", "제일 싼", "가장 저렴한", "제일 저렴한", "최저가", "최저", "싼곳", "싼 곳", "싸게", "저렴하게", "저렴한", "싼",
        "추천", "어디서", "사는곳", "사는 곳", "사려면", "살까", "사요", "사", "구매", "구입", "가격", "얼마", "비교", "찾기", "찾아줘", "알려줘", "할인", "특가", "핫딜",
    )

    fun clean(query: String): String {
        var q = " " + query.trim().replace(Regex("""[?!~.]+$"""), "") + " "
        for (f in FILLER.sortedByDescending { it.length }) q = q.replace(" $f ", " ").replace(Regex("""\s$f(?=\s)"""), " ")
        val out = q.replace(Regex("""\s+"""), " ").trim()
        return out.ifBlank { query.trim() }
    }
}
