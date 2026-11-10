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
    /** 배송비 포함 최저가 (다나와가 따로 알려 줄 때) */
    val total: Long? = null,
) {
    fun cost(withShipping: Boolean) = if (withShipping) total ?: price else price
    fun unitFor(withShipping: Boolean) = unit?.scaled(price, cost(withShipping))
}

/** '98원/10m' → 98원 per '10m'. estimated: 다나와가 준 값이 아니라 상품 이름의 용량·개수로 계산한 값 */
data class UnitPrice(val text: String, val value: Long, val per: String, val estimated: Boolean = false) {
    companion object {
        private val RE = Regex("""([\d,]+)\s*원\s*/\s*(.+)""")
        fun parse(text: String?): UnitPrice? {
            val t = text?.trim().orEmpty()
            val m = RE.matchEntire(t) ?: return null
            val v = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
            return UnitPrice(t, v, m.groupValues[2].trim())
        }

        private val SIZE = Regex("""(\d+(?:\.\d+)?)\s*(kg|KG|Kg|g|G|ml|mL|ML|L|l|리터)(?![A-Za-z가-힣])""")
        // '6개월'은 개수가 아니다
        private val COUNT = Regex("""(\d+)\s*(개입|개(?!월)|입|팩|EA|ea|병|통|박스|세트|캔|봉|포)""")
        // '4+1팩', '1+1' → 5개, 2개
        private val PLUS = Regex("""(\d{1,2})\s*\+\s*(\d{1,2})(?!\s*(?:g|G|ml|mL|kg|L|l|mm|cm|%|\d))""")
        // '500ml x 24', '1kg×2'
        private val TIMES = Regex("""[xX×*]\s*(\d{1,3})(?!\s*(?:g|G|ml|mL|kg|L|l|mm|cm|m(?![a-z])|\d|개|입|팩|EA|ea|병|통|박스|세트|캔|봉|포))""")

        private data class Size(val amount: Double, val liquid: Boolean, val range: IntRange)

        private fun sizes(name: String): List<Size> = SIZE.findAll(name).mapNotNull { m ->
            var v = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val u = m.groupValues[2].lowercase()
            if (u == "kg" || u == "l" || u == "리터") v *= 1000
            if (v <= 0) null else Size(v, u == "ml" || u == "l" || u == "리터", m.range)
        }.toList()

        /** 이름에 나온 개수: 'N+M' 은 더하고, 용량 뒤의 'N개'·'xN' 은 곱한다. from: 이 위치 뒤의 개수만 */
        internal fun pieces(name: String, from: Int = -1): Int? {
            val plus = PLUS.findAll(name).firstOrNull { (it.groupValues[1].toIntOrNull() ?: 0) in 1..50 && (it.groupValues[2].toIntOrNull() ?: 0) in 1..50 }
            val plusCount = plus?.let { it.groupValues[1].toInt() + it.groupValues[2].toInt() }
            val counts = (COUNT.findAll(name, from + 1) + TIMES.findAll(name, from + 1))
                .filter { m -> plus == null || m.range.first > plus.range.last || m.range.last < plus.range.first }
                .mapNotNull { it.groupValues[1].toIntOrNull()?.takeIf { n -> n in 1..500 } }.toList()
            if (plusCount == null && counts.isEmpty()) return null
            return (counts.fold(1) { a, b -> a * b } * (plusCount ?: 1)).coerceIn(1, 1000)
        }

        /** 이름의 '60g 3개' → 가격을 10g당(액체는 100ml당)으로. 다나와 단가와 같은 단위를 쓴다.
         *  용량이 여럿이면('건더기250g+육수350g … 950g') 가장 큰 값을 한 개의 용량으로 본다. */
        fun estimate(name: String, price: Long): UnitPrice? {
            val all = sizes(name)
            val size = all.maxByOrNull { it.amount } ?: return null
            val count = pieces(name, size.range.last) ?: 1
            return fromAmount(size.amount * count, size.liquid, price)
        }

        internal fun fromAmount(amount: Double, liquid: Boolean, price: Long, estimated: Boolean = true): UnitPrice? {
            if (amount <= 0 || price <= 0) return null
            val per = if (liquid) "100ml" else "10g"
            val base = if (liquid) 100.0 else 10.0
            val v = Math.round(price / (amount / base))
            if (v <= 0) return null
            return UnitPrice("${v}원/$per", v, per, estimated = estimated)
        }

        /** 용량이 한 번만, 개수는 그 뒤에만 나오는 깔끔한 이름이면 '한 개의 용량' (같은 상품끼리 비교할 때 기준) */
        internal fun cleanPieceSize(name: String): Pair<Double, Boolean>? {
            val all = sizes(name)
            if (all.size != 1) return null
            val s = all[0]
            if (PLUS.containsMatchIn(name)) return null
            // '2팩 3세트 2850g'처럼 개수가 용량 앞에 오면 용량이 전체인지 한 개인지 알 수 없어 뺀다
            val before = COUNT.findAll(name).any { it.range.last < s.range.first }
            if (before) return null
            // '950g 3개'의 950g이 한 개의 용량
            return s.amount to s.liquid
        }

        /** 용량 없이 개수만 있는 이름 ('순천건봉국밥 4+1팩') */
        internal fun countOnly(name: String): Int? = if (sizes(name).isEmpty()) pieces(name) else null
    }

    /** 배송비를 더한 값으로 단가를 다시 계산 (단가는 상품가 기준이라 같은 비율로 늘린다) */
    fun scaled(price: Long, cost: Long): UnitPrice {
        if (cost == price || price <= 0) return this
        val v = Math.round(value.toDouble() * cost / price)
        return copy(text = "${v}원/$per", value = v)
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
    /** 가격비교로 묶이지 않은 쇼핑몰 개별 상품('검색상품')이면 그 쇼핑몰 이름 */
    val mall: String? = null,
    /** 개별 상품의 쇼핑몰 바로가기 (다나와 연결 주소) */
    val link: String = "",
    val shippingFee: Long? = null,
    /** 배송비 포함 최저가 */
    val total: Long? = null,
    /** 다나와 분류의 마지막 단계 (예: '치약', '칫솔/치실') */
    val category: String = "",
    val source: LiveSource = LiveSource.DANAWA,
    /** 단가를 같은 상품의 다른 판매처 용량으로 짐작했을 때 그 근거 (예: '950g × 5팩') */
    val unitNote: String? = null,
) {
    val isListing: Boolean get() = mall != null
    fun cost(withShipping: Boolean) = if (withShipping) total ?: price else price
    fun unitFor(withShipping: Boolean) = unit?.scaled(price, cost(withShipping))

    /** 단가가 가장 낮은 옵션 (단가 정보가 없으면 null) */
    val bestUnitOption: LiveOption? get() = bestUnitOption(false)
    fun bestUnitOption(withShipping: Boolean): LiveOption? =
        options.filter { it.unit != null }.minByOrNull { it.unitFor(withShipping)!!.value }
}

data class LiveOffer(val mall: String, val price: Long, val shipping: String, val fee: Long?, val link: String) {
    /** 배송비 포함 금액 (배송비를 모르면 상품가) */
    val total: Long get() = price + (fee ?: 0)
}

/** '현대카드 결제 시' 처럼 특정 카드로 결제해야 나오는 값 (판매가와 따로 참고용으로) */
data class CardOffer(val mall: String, val price: Long, val condition: String, val link: String)

data class LivePage(
    val pcode: String,
    val name: String,
    val image: String,
    val lowest: Long?,
    val offerCount: Int?,
    val offers: List<LiveOffer>,
    val cardOffers: List<CardOffer> = emptyList(),
)

class LiveBlocked(message: String) : IOException(message)

enum class LiveSort(val label: String) { UNIT("단가 낮은 순"), PRICE("가격 낮은 순"), RANK("추천순") }

/** 결과를 어디서 찾았는지 */
enum class LiveSource(val label: String) { DANAWA("다나와"), ENURI("에누리"), MALL("공식몰") }

/** 정렬에 맞는 대표 옵션: 단가순이면 단가가 가장 낮은 묶음, 가격순이면 가장 싼 묶음 */
fun pickOption(p: LiveProduct, sort: LiveSort, withShipping: Boolean = false): LiveOption? = when (sort) {
    LiveSort.UNIT -> p.bestUnitOption(withShipping) ?: p.options.minByOrNull { it.cost(withShipping) }
    LiveSort.PRICE -> p.options.minByOrNull { it.cost(withShipping) }
    LiveSort.RANK -> p.options.firstOrNull { it.pcode == p.pcode } ?: p.options.minByOrNull { it.cost(withShipping) }
}

/** 화면에 보여 줄 값: 대표 옵션이 있으면 그 옵션, 없으면 상품 자체 */
fun shownCost(p: LiveProduct, sort: LiveSort, withShipping: Boolean): Long =
    pickOption(p, sort, withShipping)?.cost(withShipping) ?: p.cost(withShipping)

fun shownUnit(p: LiveProduct, sort: LiveSort, withShipping: Boolean): UnitPrice? =
    pickOption(p, sort, withShipping)?.unitFor(withShipping) ?: p.unitFor(withShipping)

/** 단가순: 가장 흔한 단위(예: 10m)끼리 단가로 줄 세우고, 단위가 다르거나 없는 것은 그 뒤에 가격순 */
fun sortLive(list: List<LiveProduct>, sort: LiveSort, withShipping: Boolean = false): List<LiveProduct> = when (sort) {
    LiveSort.PRICE -> list.sortedBy { shownCost(it, sort, withShipping) }
    LiveSort.RANK -> list.sortedBy { it.rank }
    LiveSort.UNIT -> {
        val unitOf = { p: LiveProduct -> shownUnit(p, sort, withShipping) }
        val common = list.mapNotNull { unitOf(it)?.per }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        list.sortedWith(
            compareBy<LiveProduct> { if (common != null && unitOf(it)?.per == common) 0 else 1 }
                .thenBy { if (common != null && unitOf(it)?.per == common) unitOf(it)!!.value else shownCost(it, sort, withShipping) },
        )
    }
}

private val GENERIC = setOf(
    "무료배송", "무배", "무료", "배송", "정품", "국내산", "대용량", "특가", "할인", "세트", "기획", "증정", "오늘출발", "당일발송", "같은",
    "인분", "1인분", "2인분", "식사", "간편", "한식", "혼밥", "점심", "외식", "선물", "추천", "인기", "공식", "공식몰", "본사", "판매",
)
private val WORD = Regex("""[가-힣A-Za-z][가-힣A-Za-z0-9]+""")

/** 이름에서 상품을 가리키는 낱말 (숫자·용량·흔한 광고 문구 빼고) */
internal fun productWords(name: String): Set<String> =
    WORD.findAll(name).map { it.value }.filter { w -> w.length >= 2 && w !in GENERIC && !w.any { it.isDigit() } }.toSet()

/**
 * 용량 없이 '4+1팩'처럼 개수만 적은 상품(주로 공식몰)은 단가를 낼 수 없다.
 * 같은 낱말을 쓰는 다른 판매처 상품 대부분이 같은 한 개 용량(예: 950g)이라고 하면, 그 용량으로 단가를 짐작해 함께 비교한다.
 * 다른 판매처끼리 한 개 용량이 제각각이면(70% 넘게 같지 않으면) 짐작하지 않는다.
 */
fun inferUnits(list: List<LiveProduct>): List<LiveProduct> {
    data class Peer(val words: Set<String>, val compact: String, val each: Double, val liquid: Boolean)
    val peers = list.mapNotNull { p ->
        val (each, liquid) = UnitPrice.cleanPieceSize(p.name) ?: return@mapNotNull null
        Peer(productWords(p.name), p.name.replace(" ", ""), each, liquid)
    }
    if (peers.size < 2) return list
    return list.map { p ->
        if (p.unit != null || p.options.any { it.unit != null }) return@map p
        val n = UnitPrice.countOnly(p.name) ?: return@map p
        val words = productWords(p.name)
        val compact = p.name.replace(" ", "")
        val same = peers.filter { peer -> words.any { it in peer.compact } || peer.words.any { it in compact } }
        if (same.size < 2 || same.map { it.liquid }.toSet().size != 1) return@map p
        // 대부분(70% 이상)이 같은 한 개 용량(±10%)이어야 한다. 이름이 잘린 몇 개('…건더기250g')는 무시
        val (each, agree) = same.map { c -> c.each to same.count { o -> o.each in c.each / 1.1..c.each * 1.1 } }.maxByOrNull { it.second } ?: return@map p
        if (agree < 2 || agree < same.size * 0.7) return@map p
        val unit = UnitPrice.fromAmount(each * n, same[0].liquid, p.price) ?: return@map p
        val sizeText = if (same[0].liquid) "${each.toLong()}ml" else if (each >= 1000 && each % 1000 == 0.0) "${(each / 1000).toLong()}kg" else "${each.toLong()}g"
        p.copy(unit = unit, unitNote = "다른 판매처의 같은 상품이 한 개 $sizeText → $sizeText × ${n}개로 계산")
    }
}

private fun nameKey(name: String) = name.lowercase().replace(Regex("[^0-9a-z가-힣]"), "")

/**
 * 다나와·에누리·공식몰 결과를 하나로. 에누리 결과 중 다나와와 같은 가격비교 묶음(이름이 같음)이나
 * 같은 쇼핑몰·같은 가격·같은 이름의 개별 상품은 뺀다. 추천순은 공식몰 → 다나와 → 에누리를 번갈아.
 * extra: 공유받은 링크 상품처럼 함께 비교할 것
 */
fun mergeSources(danawa: List<LiveProduct>, enuri: List<LiveProduct>, malls: List<LiveProduct>, extra: List<LiveProduct> = emptyList()): List<LiveProduct> {
    val catalogs = danawa.filter { !it.isListing }.map { nameKey(it.name) }.toHashSet()
    val listingKey = { p: LiveProduct -> "${p.mall?.substringBefore(" (")}|${p.price}|${nameKey(p.name).take(24)}" }
    val listings = danawa.filter { it.isListing }.map(listingKey).toHashSet()
    val en = enuri.filter { p -> if (p.isListing) listingKey(p) !in listings else nameKey(p.name) !in catalogs }
    val out = ArrayList<LiveProduct>(danawa.size + en.size + malls.size + extra.size)
    extra.forEachIndexed { i, p -> out += p.copy(rank = -extra.size + i) }
    malls.forEachIndexed { i, p -> out += p.copy(rank = i * 3) }
    danawa.forEachIndexed { i, p -> out += p.copy(rank = i * 3 + 1) }
    en.forEachIndexed { i, p -> out += p.copy(rank = i * 3 + 2) }
    return inferUnits(out.distinctBy { it.pcode })
}

/** 단위 정보가 절반 이상 있으면 단가순이 기본 */
fun defaultLiveSort(list: List<LiveProduct>): LiveSort =
    if (list.isNotEmpty() && list.count { it.unit != null || it.bestUnitOption != null } * 2 >= list.size) LiveSort.UNIT else LiveSort.PRICE

object DanawaParser {
    private const val PUSH = "self.__next_f.push([1,\""
    private val TAG = Regex("<[^>]+>")
    private val USED = listOf("중고", "리퍼", "전시", "반품", "B급")
    private val OVERSEAS = listOf("해외구매", "해외직구", "병행수입", "직구")

    private fun categoryOf(obj: JsonObject): String =
        (obj["categoryNameList"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }?.lastOrNull { it.isNotEmpty() }.orEmpty()

    private fun plain(s: String?): String =
        org.jsoup.parser.Parser.unescapeEntities(TAG.replace(s.orEmpty(), ""), false).replace(Regex("\\s+"), " ").trim()

    /** 검색 결과 화면(Next.js)에 함께 실려 오는 상품 데이터 */
    fun parseSearch(html: String): List<LiveProduct> {
        if (!html.contains("__next_f")) return emptyList()
        val payload = buildString {
            for (raw in pushedStrings(html)) {
                append(unescapeJsonString(raw))
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
            if (pcode in seen || price <= 0) continue
            if (obj["isAdultProduct"]?.let { (it as? JsonPrimitive)?.contentOrNull } == "true") continue
            seen += pcode
            val name = plain(obj.str("productName"))
            if (!pcode.all { it.isDigit() }) {
                // 쇼핑몰 개별 상품 (예: 'EE128_4846453693'): 가격비교 묶음이 없어 상품 페이지 대신 쇼핑몰로 바로 간다
                val link = obj.str("linkUrl")?.let { if (it.startsWith("//")) "https:$it" else it }.orEmpty()
                if (!link.startsWith("http")) continue
                val img = obj.str("imageUrl").orEmpty().let { if (it.startsWith("//")) "https:$it" else it }
                out += LiveProduct(
                    pcode = pcode, name = name, image = img, price = price, malls = null,
                    unit = UnitPrice.parse(obj.str("unitPrice")) ?: UnitPrice.estimate(name, price),
                    options = emptyList(), used = USED.any { it in name }, overseas = OVERSEAS.any { it in name }, rank = out.size,
                    mall = normalizeMall(obj.str("shopName").orEmpty().ifBlank { "쇼핑몰" }), link = link,
                    shippingFee = obj.long("postPrice"),
                    total = (obj.long("totalPrice")?.takeIf { it > price } ?: obj.long("postPrice")?.let { price + it }),
                    category = categoryOf(obj),
                )
                continue
            }
            val bundle = plain(obj.str("bundleName"))
            val unit = UnitPrice.parse(obj.str("unitPrice")) ?: UnitPrice.estimate("$name $bundle", price)
            val options = mutableListOf<LiveOption>()
            val total = obj.long("totalPrice")?.takeIf { it >= price }
            if (bundle.isNotEmpty()) options += LiveOption(pcode, bundle, price, obj.long("shopCount")?.toInt(), unit, total)
            (obj["bundleList"] as? JsonArray)?.forEach { b ->
                val bo = b as? JsonObject ?: return@forEach
                val bp = bo.str("productSeq") ?: return@forEach
                val bprice = bo.long("minPrice") ?: return@forEach
                if (bp != pcode && bprice > 0 && options.none { it.pcode == bp }) {
                    val bl = plain(bo.str("bundleName"))
                    options += LiveOption(
                        bp, bl, bprice, bo.long("shopCount")?.toInt(), UnitPrice.parse(bo.str("unitPrice")) ?: UnitPrice.estimate("$name $bl", bprice),
                        bo.long("totalPrice")?.takeIf { it >= bprice },
                    )
                }
            }
            out += LiveProduct(
                pcode = pcode, name = name, image = obj.str("imageUrl")?.substringBefore('?').orEmpty(),
                price = price, malls = obj.long("shopCount")?.toInt(), unit = unit,
                options = options.sortedBy { it.price },
                used = USED.any { it in name }, overseas = OVERSEAS.any { it in name }, rank = out.size,
                total = total, category = categoryOf(obj),
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

    /** JSON 문자열 안의 \" \\ \n \uXXXX 같은 표기를 글자로 되돌린다 (라이브러리 없이 직접) */
    internal fun unescapeJsonString(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { sb.append(c); i++; continue }
            when (val n = s[i + 1]) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'r' -> sb.append('\r')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'u' -> {
                    val hex = s.substring(i + 2, minOf(i + 6, s.length))
                    val code = hex.toIntOrNull(16)
                    if (hex.length == 4 && code != null) { sb.append(code.toChar()); i += 6; continue }
                    sb.append(n)
                }
                else -> sb.append(n) // \" \\ \/
            }
            i += 2
        }
        return sb.toString()
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
        val cards = LinkedHashMap<String, CardOffer>()
        for (row in doc.select("li.list-item")) {
            if (row.selectFirst(".box__logo, [class*=logo]") == null) continue
            if (row.parents().any { it.hasClass("list__membership") }) {
                // '○○카드 결제 시' 가격: 판매가 비교에서는 빼고 참고용으로 따로 모은다
                val mall = mallOf(row)
                val price = row.selectFirst(".text__discount-price")?.text()?.digits()
                    ?: Regex("""(\d{1,3}(?:,\d{3})+)\s*원""").findAll(row.text()).lastOrNull()?.groupValues?.get(1)?.digits()
                val cond = row.selectFirst(".box__desc-wrap")?.text()?.trim().orEmpty().ifBlank { "카드 결제 시" }
                if (mall.isNotBlank() && price != null && price >= 100) {
                    val link = row.selectFirst("a.link__full-cover, a[href*=bridge]")?.absUrl("href").orEmpty()
                    val key = "$mall|$cond"
                    if ((cards[key]?.price ?: Long.MAX_VALUE) > price) cards[key] = CardOffer(mall, price, cond, link)
                }
                continue
            }
            val mall = mallOf(row)
            if (mall.isBlank()) continue
            val price = priceOf(row) ?: continue
            val (ship, fee) = shippingOf(row)
            val link = row.selectFirst("a.link__full-cover, a[href*=bridge]")?.absUrl("href").orEmpty()
            val cur = best[mall]
            if (cur == null || price < cur.price) best[mall] = LiveOffer(mall, price, ship, fee, link)
        }
        val offers = best.values.sortedBy { it.price }
        return LivePage(
            pcode, name, image, offers.firstOrNull()?.price?.let { p -> lowest?.let { minOf(it, p) } ?: p } ?: lowest, offerCount, offers,
            cards.values.sortedBy { it.price },
        )
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

    private fun normalizeMall(s: String) = normalizeMallName(s)

    private fun String.digits(): Long? = filter { it.isDigit() }.toLongOrNull()
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.filter { c -> c.isDigit() }?.toLongOrNull() }
}

/** 다나와·에누리·공식몰에 직접 묻는 부분. 화면에서 검색·상품 열기를 할 때만 부른다 */
class LiveSearchClient(private val client: OkHttpClient = defaultClient()) {
    private data class Cached<T>(val at: Long, val value: T)
    private val searches = HashMap<String, Cached<List<LiveProduct>>>()
    private val pages = HashMap<String, Cached<LivePage>>()
    private val robots = HashMap<String, Cached<Robots?>>()
    private val ships = HashMap<String, Cached<ShipRule?>>()
    private val gate = Mutex()
    private val lastAt = HashMap<String, Long>()

    /** 다나와 검색. page: 1쪽부터, 한 쪽에 40개 안팎 */
    suspend fun search(query: String, page: Int = 1): List<LiveProduct> = cached("d:${query.trim()}#$page") {
        val url = "https://search.danawa.com/?query=" + URLEncoder.encode(query.trim(), "UTF-8") + if (page > 1) "&page=$page" else ""
        DanawaParser.parseSearch(get(url, "다나와"))
    }

    /** 에누리 검색. 한 쪽에 30~40개 */
    suspend fun enuriSearch(query: String, page: Int = 1): List<LiveProduct> = cached("e:${query.trim()}#$page") {
        val url = "https://price.enuri.com/search?keyword=" + URLEncoder.encode(query.trim(), "UTF-8") + if (page > 1) "&page=$page" else ""
        EnuriParser.parseSearch(get(url, "에누리"))
    }

    /**
     * 공식몰 검색 (카페24). robots.txt가 검색 화면을 허용할 때만 읽는다.
     * 첫 상품의 상품 페이지에서 배송비 규칙을 읽어 그 쇼핑몰 상품 모두에 적용한다 (쇼핑몰마다 규칙이 하나인 경우가 대부분).
     */
    suspend fun mallSearch(mall: Mall, keyword: String): List<LiveProduct> = cached("m:${mall.host}:${keyword.trim()}") {
        val url = Cafe24Parser.searchUrl(mall.host, keyword.trim())
        if (!robotsAllow(url)) return@cached emptyList()
        val list = Cafe24Parser.parseSearch(get(url, mall.label), mall).take(12)
        val first = list.firstOrNull() ?: return@cached list
        val rule = shipRule(mall.host, first.link)
        if (rule == null) list else list.map { it.copy(shippingFee = rule.feeFor(it.price), total = it.price + rule.feeFor(it.price)) }
    }

    private suspend fun shipRule(host: String, productUrl: String): ShipRule? {
        ships[host]?.takeIf { System.currentTimeMillis() - it.at < 6 * 3_600_000L }?.let { return it.value }
        val rule = if (robotsAllow(productUrl)) runCatching { ShipRule.parse(Jsoup.parse(get(productUrl, host)).text()) }.getOrNull() else null
        ships[host] = Cached(System.currentTimeMillis(), rule)
        return rule
    }

    /** 공유받은 상품 링크 읽기 (사용자가 보낸 한 페이지만, robots.txt가 막지 않을 때) */
    suspend fun readLink(url: String): LinkInfo {
        if (!robotsAllow(url)) throw LiveBlocked("이 쇼핑몰은 자동으로 읽는 것을 허용하지 않아요")
        return LinkReader.parse(get(url, LinkReader.hostOf(url)), url)
    }

    /** 다나와(숫자) 또는 에누리('en:…') 가격비교 상품 페이지 */
    suspend fun product(pcode: String): LivePage {
        pages[pcode]?.takeIf { fresh(it.at) }?.let { return it.value }
        val page = if (EnuriParser.isEnuri(pcode)) EnuriParser.parseCatalog(get(EnuriParser.catalogUrl(pcode), "에누리"), pcode)
        else DanawaParser.parseProduct(get("https://prod.danawa.com/info/?pcode=$pcode", "다나와"), pcode)
        pages[pcode] = Cached(System.currentTimeMillis(), page)
        return page
    }

    private suspend fun cached(key: String, load: suspend () -> List<LiveProduct>): List<LiveProduct> {
        searches[key]?.takeIf { fresh(it.at) }?.let { return it.value }
        val list = load()
        searches[key] = Cached(System.currentTimeMillis(), list)
        return list
    }

    private fun fresh(at: Long) = System.currentTimeMillis() - at < CACHE_MS

    /** robots.txt 확인 (사이트마다 하루 한 번). 없으면(404) 허용, 읽지 못하면 읽지 않는다 */
    private suspend fun robotsAllow(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val origin = "${uri.scheme}://${uri.host}"
        val r = robots[origin]?.takeIf { System.currentTimeMillis() - it.at < 24 * 3_600_000L }?.value ?: run {
            val text = runCatching { getRaw("$origin/robots.txt", allowMissing = true) }.getOrElse { return false }
            Robots(text).also { robots[origin] = Cached(System.currentTimeMillis(), it) }
        }
        return r.allowed((uri.rawPath ?: "/").ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: ""))
    }

    private suspend fun get(url: String, site: String): String =
        getRaw(url, site = site) ?: throw IOException("HTTP 404")

    /** allowMissing: 404면 null (robots.txt가 없는 사이트) */
    private suspend fun getRaw(url: String, allowMissing: Boolean = false, site: String = ""): String? {
        val host = url.substringAfter("://").substringBefore('/')
        // 같은 사이트에는 요청 사이를 띄운다 (다른 사이트끼리는 기다리지 않게 자리만 잡고 밖에서 기다림)
        val wait = gate.withLock {
            val now = System.currentTimeMillis()
            val next = maxOf(now, (lastAt[host] ?: 0L) + MIN_GAP_MS)
            lastAt[host] = next
            next - now
        }
        if (wait > 0) delay(wait)
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .build()
            client.newCall(req).execute().use { resp ->
                if (allowMissing && resp.code in 400..499 && resp.code !in listOf(403, 429)) return@use null
                val body = resp.body.string()
                if (resp.code in listOf(403, 429, 430, 451) || CHALLENGE.any { it in body.take(6000) }) {
                    throw LiveBlocked("${site.ifBlank { "사이트" }}가 지금은 자동 요청을 받지 않아요")
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
