package io.github.poohcjs.hotdealradar.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

/*
 * 에누리 가격비교 (다나와 다음으로 큰 가격비교 사이트).
 * 다나와에 없는 판매처(네이버 스마트스토어 판매자 일부, 롯데ON·G마켓 개별 상품 등)가 함께 나와 빈틈을 메운다.
 *   검색:  https://price.enuri.com/search?keyword=…   (robots.txt: /api 아래만 막고 나머지 허용)
 *   상품:  https://price.enuri.com/catalog/…         (같음)
 * 두 화면 모두 처음 받은 HTML(Next.js)에 상품 데이터가 실려 있어, 막힌 /api 를 부르지 않는다.
 */
object EnuriParser {
    const val CATALOG = "en:"     // 에누리 가격비교 묶음 (여러 쇼핑몰)
    const val LISTING = "enp:"    // 에누리에만 있는 쇼핑몰 개별 상품
    private const val BASE = "https://price.enuri.com"

    fun isEnuri(pcode: String) = pcode.startsWith(CATALOG) || pcode.startsWith(LISTING)
    fun catalogUrl(pcode: String) = "$BASE/catalog/${pcode.removePrefix(CATALOG)}"

    private fun payload(html: String): String = buildString {
        for (raw in DanawaParser.pushedStrings(html)) append(DanawaParser.unescapeJsonString(raw))
    }

    /** "key": 뒤에 오는 { … } 또는 [ … ] 하나를 JSON으로 */
    internal fun valueAfter(payload: String, key: String): kotlinx.serialization.json.JsonElement? {
        val at = payload.indexOf("\"$key\":")
        if (at < 0) return null
        val start = at + key.length + 3
        if (start >= payload.length) return null
        val end = blockEnd(payload, start)
        if (end <= start) return null
        return runCatching { JSON.parseToJsonElement(payload.substring(start, end)) }.getOrNull()
    }

    /** 문자열 안 괄호는 건너뛰며 { … } / [ … ] 의 끝 */
    private fun blockEnd(s: String, start: Int): Int {
        val open = s[start]
        if (open != '{' && open != '[') return -1
        var depth = 0
        var inStr = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') i++ else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '{', '[' -> depth++
                '}', ']' -> { depth--; if (depth == 0) return i + 1 }
            }
            i++
        }
        return -1
    }

    /** '10m당 87원' → 87원/10m (다나와와 같은 모양으로) */
    internal fun unitOf(text: String?): UnitPrice? {
        val m = Regex("""(.+?)당\s*([\d,]+)\s*원""").find(text?.trim().orEmpty()) ?: return null
        val v = m.groupValues[2].replace(",", "").toLongOrNull() ?: return null
        val per = m.groupValues[1].trim()
        return UnitPrice("${v}원/$per", v, per)
    }

    /** '무료배송' → 0, '배송비 3,000원' → 3000, 모르면 null */
    internal fun feeOf(text: String?): Long? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        if ("무료" in t) return 0
        return Regex("""([\d,]+)\s*원""").find(t)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
    }

    fun parseSearch(html: String): List<LiveProduct> {
        if (!html.contains("__next_f")) return emptyList()
        val list = valueAfter(payload(html), "initProductList") as? JsonObject ?: return emptyList()
        val items = list["items"] as? JsonArray ?: return emptyList()
        val out = mutableListOf<LiveProduct>()
        for (el in items) {
            val o = el as? JsonObject ?: continue
            if (o.bool("isSoldOut") == true || o.bool("isAd") == true || o.bool("isRental") == true) continue
            val name = o.str("name")?.trim().orEmpty()
            val info = o["priceInfo"] as? JsonObject
            val price = info?.long("price") ?: continue
            if (name.isEmpty() || price <= 0) continue
            val thumb = o["thumbnail"] as? JsonObject
            val image = (thumb?.str("imageUrl") ?: thumb?.str("fallbackImageUrl")).orEmpty()
            val category = ((o["category"] as? JsonObject)?.str("categoryName")).orEmpty()
            when (o.str("type")) {
                "model" -> {
                    val id = o.str("productId")?.removePrefix("G:") ?: (o["logIds"] as? JsonObject)?.str("modelNo") ?: continue
                    val options = (o["purchaseOptions"] as? JsonArray).orEmpty().mapNotNull { p ->
                        val po = p as? JsonObject ?: return@mapNotNull null
                        val pid = po.str("modelId") ?: return@mapNotNull null
                        val pp = po.long("price")?.takeIf { it > 0 } ?: return@mapNotNull null
                        LiveOption(CATALOG + pid, po.str("optionName").orEmpty(), pp, po.long("mallCount")?.toInt(), unitOf(po.str("unitPriceText")))
                    }
                    val own = options.firstOrNull { it.pcode == CATALOG + id }
                    out += LiveProduct(
                        pcode = CATALOG + id, name = name, image = image, price = price, malls = o.long("mallCount")?.toInt(),
                        unit = own?.unit ?: UnitPrice.estimate("$name ${o.str("condiName").orEmpty()}", price),
                        options = options.sortedBy { it.price }, used = false, overseas = false, rank = out.size,
                        category = category, source = LiveSource.ENURI,
                    )
                }
                else -> {
                    val pl = (o["logIds"] as? JsonObject)?.str("plNo") ?: o.str("productId")?.removePrefix("P:") ?: continue
                    val link = o.str("link")?.takeIf { it.startsWith("http") } ?: continue
                    val delivery = o.str("deliveryInfo")
                    val fee = feeOf(delivery)
                    val smart = info.str("mallType")?.contains("naver") == true
                    val mall = info.str("mallName").orEmpty().ifBlank { "쇼핑몰" }
                    out += LiveProduct(
                        pcode = LISTING + pl, name = name, image = image, price = price, malls = null,
                        unit = UnitPrice.estimate(name, price), options = emptyList(),
                        used = USED.any { it in name }, overseas = OVERSEAS.any { it in name }, rank = out.size,
                        mall = normalizeMallName(mall) + if (smart) " (스마트스토어)" else "", link = link,
                        shippingFee = fee, total = fee?.let { price + it }, category = category, source = LiveSource.ENURI,
                    )
                }
            }
        }
        return out
    }

    /** 가격비교 묶음 화면: 구조화 데이터(이름·사진·최저가) + 처음부터 실려 오는 판매처 목록(가격 낮은 순 20곳 안팎) */
    fun parseCatalog(html: String, pcode: String): LivePage {
        val id = pcode.removePrefix(CATALOG)
        val doc = Jsoup.parse(html, BASE)
        var name = ""
        var image = ""
        var lowest: Long? = null
        var count: Int? = null
        for (s in doc.select("script[type=application/ld+json]")) {
            val o = runCatching { JSON.parseToJsonElement(s.data()).jsonObject }.getOrNull() ?: continue
            if (o.str("@type") != "Product") continue
            name = o.str("name").orEmpty()
            image = when (val img = o["image"]) {
                is JsonArray -> (img.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
                is JsonPrimitive -> img.contentOrNull.orEmpty()
                else -> ""
            }
            (o["offers"] as? JsonObject)?.let { lowest = it.long("lowPrice"); count = it.long("offerCount")?.toInt() }
            break
        }
        if (name.isEmpty()) name = doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val offers = LinkedHashMap<String, LiveOffer>()
        if (html.contains("__next_f")) {
            val arr = valueAfter(payload(html), "priceList") as? JsonArray
            arr?.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                if (o.bool("isAd") == true || o.bool("isOverseas") == true) return@forEach
                val price = o.long("price")?.takeIf { it > 0 } ?: return@forEach
                val shop = o.str("shopName")?.trim().orEmpty().ifBlank { return@forEach }
                val mall = normalizeMallName(shop) + if (o.str("shopType") == "4") " (스마트스토어)" else ""
                val fee = o.long("deliveryPrice") ?: feeOf(o.str("deliveryText"))
                val ship = when {
                    fee == 0L -> "무료배송"
                    fee != null -> "배송비 ${won(fee)}원"
                    else -> o.str("deliveryText").orEmpty()
                }
                val pl = o.str("plno").orEmpty()
                val link = "https://www.enuri.com/move/Redirect.jsp?cmd=move_link&vcode=${o.str("shopCode").orEmpty()}&modelno=$id&pl_no=$pl&from=detail"
                val cur = offers[mall]
                if (cur == null || price < cur.price) offers[mall] = LiveOffer(mall, price, ship, fee, link)
            }
        }
        val sorted = offers.values.sortedBy { it.price }
        return LivePage(pcode, name, image, sorted.firstOrNull()?.price?.let { p -> lowest?.let { minOf(it, p) } ?: p } ?: lowest, count, sorted)
    }

    private val USED = listOf("중고", "리퍼", "전시", "반품", "B급")
    private val OVERSEAS = listOf("해외구매", "해외직구", "병행수입", "직구")
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.filter { c -> c.isDigit() }?.toLongOrNull() }
    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}

private val MALL_ALIAS = mapOf("SSG.COM" to "SSG닷컴", "신세계몰" to "SSG닷컴", "지마켓" to "G마켓", "롯데온" to "롯데ON", "GS SHOP" to "GS샵")
fun normalizeMallName(s: String) = MALL_ALIAS[s.trim()] ?: s.trim()
