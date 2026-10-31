package io.github.poohcjs.hotdealradar.data

import java.time.Duration
import java.time.OffsetDateTime
import java.util.Locale

/*
 * 화면에 보여 줄 '항목'과 판정 규칙. 웹앱(docs/index.html)의 규칙을 그대로 옮겼다.
 *   핫딜      = 할인율(쇼핑몰 평균가 대비) ≥ 내 기준
 *   가격 확인 필요 = 수집기가 suspicious로 표시 (평균보다 60% 넘게 쌈 → 옵션가·리퍼·오류일 때가 많음)
 */

enum class ItemKind { COMMUNITY, MARKET, PRODUCT, POST }

data class Item(
    val key: String,
    val kind: ItemKind,
    val name: String,
    val title: String,
    val danawaName: String = "",
    val mall: String = "",
    val price: Long? = null,
    val shipping: String = "",
    val url: String = "",
    val source: String = "",
    val cat: String = "etc",
    val postedAt: Long? = null,
    val detectedAt: Long? = null,
    val pcode: String? = null,
    val rate: Double? = null,
    val reference: String? = null,
    val referencePrice: Long? = null,
    val dealTotal: Long? = null,
    val average: Long? = null,
    val lowest: Long? = null,
    val lowestMall: String? = null,
    val malls: Int? = null,
    val basis: String? = null,
    val secondPrice: Long? = null,
    val unitAdjusted: Boolean = false,
    val suspicious: Boolean = false,
    val ended: Boolean = false,
    val votes: Int? = null,
    val comments: Int? = null,
    val views: Int? = null,
    val image: String = "",
    val mentions: List<Mention> = emptyList(),
    /** 다음으로 싼 곳(커뮤니티 딜: 다른 쇼핑몰 최저가, 발굴: 두 번째로 싼 곳)보다 몇 % 싼지. 모르면 null */
    val vsBest: Double? = null,
    val verifyStatus: String? = null,
    val verifyReason: String? = null,
    val priceFromBody: Boolean = false,
) {
    /** 반응 점수: 추천 ×3 + 댓글 + 올라온 게시판 수 ×5 */
    val buzz: Int get() = (votes ?: 0) * 3 + (comments ?: 0) + mentions.size * 5

    /** 기준가보다 얼마나 싼지(원). 음수면 비쌈 */
    val save: Long?
        get() = referencePrice?.let { ref -> ref - (dealTotal ?: price ?: return null) }
}

private fun cheaperBy(price: Long?, other: Long?): Double? =
    if (price == null || other == null || other <= 0) null else (other - price) * 100.0 / other

fun parseTime(iso: String?): Long? =
    iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

fun won(v: Long?): String = if (v == null) "-" else String.format(Locale.KOREA, "%,d", v)

fun pct(v: Double?): String = when {
    v == null -> "-"
    v >= 10 || v <= -10 -> String.format(Locale.KOREA, "%.0f", v)
    else -> String.format(Locale.KOREA, "%.1f", v).removeSuffix(".0")
}

fun ago(ms: Long?, now: Long = System.currentTimeMillis()): String {
    if (ms == null) return ""
    val d = Duration.ofMillis((now - ms).coerceAtLeast(0))
    return when {
        d.toMinutes() < 1 -> "방금"
        d.toMinutes() < 60 -> "${d.toMinutes()}분 전"
        d.toHours() < 24 -> "${d.toHours()}시간 전"
        else -> "${d.toDays()}일 전"
    }
}

/** deals.json 하나를 화면에서 쓰기 좋게 정리한 색인 */
class Index(val file: DealsFile) {
    val categories: List<Category> = file.categories.ifEmpty { listOf(Category("etc", "기타")) }
    private val catIds = categories.map { it.id }.toSet()
    private fun catOf(id: String?) = if (id != null && id in catIds) id else "etc"
    val sourceNames: Map<String, String> = file.sources.associate { s ->
        s.id to (if (s.id.endsWith("overseas")) "${s.name}(해외)" else s.name)
    }

    fun srcName(id: String): String = sourceNames[id] ?: id
    fun catName(id: String): String = categories.firstOrNull { it.id == id }?.let { "${it.icon} ${it.name}" } ?: "🛒 기타"

    val products: Map<String, Product> = file.products

    val deals: List<Item> = file.deals
        .filter { it.price > 0 && it.rate != null && (it.referencePrice ?: 0) > 0 }
        .map { d ->
            Item(
                key = "deal:${d.id}",
                kind = if (d.kind == "market") ItemKind.MARKET else ItemKind.COMMUNITY,
                name = d.name.ifBlank { d.danawaName },
                title = d.title, danawaName = d.danawaName, mall = d.mall, price = d.price, shipping = d.shipping,
                url = d.url, source = d.source, cat = catOf(d.cat),
                postedAt = parseTime(d.postedAt), detectedAt = parseTime(d.detectedAt) ?: parseTime(d.postedAt),
                pcode = d.pcode.ifBlank { null }, rate = d.rate, reference = d.reference, referencePrice = d.referencePrice,
                dealTotal = d.dealTotal, average = d.average, lowest = d.lowest, lowestMall = d.lowestMall, malls = d.malls,
                basis = d.basis, secondPrice = d.secondPrice, unitAdjusted = d.unitAdjusted, suspicious = d.suspicious,
                ended = d.ended, votes = d.votes, comments = d.comments, views = d.views,
                image = d.image.ifBlank { products[d.pcode]?.image.orEmpty() }, mentions = d.mentions,
                vsBest = if (d.kind == "market") cheaperBy(d.price, d.secondPrice) else cheaperBy(d.dealTotal ?: d.price, d.lowest),
            )
        }

    val dealsByPcode: Map<String, List<Item>> = deals.filter { it.pcode != null }.groupBy { it.pcode!! }
    private val dealByMention: Map<String, Item> = deals.flatMap { d -> d.mentions.map { it.id to d } }.toMap()

    val posts: List<Item> = file.posts.map { p ->
        val v = p.verify
        val ok = v.status == "ok"
        Item(
            key = "post:${p.id}", kind = ItemKind.POST, name = p.name.ifBlank { p.title }, title = p.title,
            danawaName = v.danawaName.orEmpty(), mall = p.mall, price = p.price, shipping = p.shipping, url = p.url,
            source = p.source, cat = catOf(p.cat), postedAt = parseTime(p.postedAt), detectedAt = parseTime(p.firstSeen),
            pcode = v.pcode, rate = if (ok) v.rate else null, reference = v.reference, referencePrice = if (ok) v.referencePrice else null,
            dealTotal = v.dealTotal, average = v.average, lowest = v.lowest, lowestMall = v.lowestMall, malls = v.malls,
            basis = v.basis, unitAdjusted = v.unitAdjusted, suspicious = v.suspicious, ended = p.ended,
            votes = p.votes, comments = p.comments, views = p.views,
            image = v.pcode?.let { products[it]?.image }.orEmpty(),
            mentions = listOf(Mention(p.id, p.source, p.url, p.votes, p.comments, p.postedAt, p.ended)),
            verifyStatus = v.status, verifyReason = v.reason, priceFromBody = p.priceFrom == "body",
            vsBest = if (ok) cheaperBy(v.dealTotal ?: p.price, v.lowest) else null,
        )
    }

    /** 커뮤니티 글을 눌렀을 때: 묶인 딜이 있으면 딜, 없으면 글 자체 */
    fun itemForPost(post: Item): Item = dealByMention[post.key.removePrefix("post:")] ?: post

    fun productItem(p: Product): Item = Item(
        key = "prod:${p.pcode}", kind = ItemKind.PRODUCT, name = p.name, title = p.name, danawaName = p.name,
        mall = p.bestMall.orEmpty(), price = p.lowest, url = p.url, source = "danawa", cat = catOf(p.cat),
        postedAt = parseTime(p.checkedAt), pcode = p.pcode, rate = p.rate,
        reference = if (p.othersAverage != null) "average" else null, referencePrice = p.othersAverage,
        dealTotal = p.lowest, average = p.average, lowest = p.lowest, lowestMall = p.bestMall, malls = p.malls,
        image = p.image,
        secondPrice = p.offers.map { it.price }.sorted().getOrNull(1),
        vsBest = cheaperBy(p.lowest, p.offers.map { it.price }.sorted().getOrNull(1)),
    )

    fun find(key: String): Item? = when {
        key.startsWith("deal:") -> deals.firstOrNull { it.key == key }
        key.startsWith("post:") -> posts.firstOrNull { it.key == key }
        key.startsWith("prod:") -> products[key.removePrefix("prod:")]?.let { productItem(it) }
        else -> null
    }

    /** 지금 살 수 있는 가장 싼 값: 진행 중인 딜(가격 확인 필요 제외)과 다나와 최저가 중 */
    fun bestNow(pcode: String): BestPrice? {
        val opts = mutableListOf<BestPrice>()
        dealsByPcode[pcode].orEmpty().filter { !it.ended && !it.suspicious && it.price != null }.minByOrNull { it.price!! }?.let {
            val via = if (it.kind == ItemKind.COMMUNITY) "${srcName(it.mentions.firstOrNull()?.source ?: it.source)} 딜" else "다나와"
            opts += BestPrice(it.price!!, it.mall, via, it)
        }
        products[pcode]?.let { p -> p.lowest?.let { opts += BestPrice(it, p.bestMall.orEmpty(), "다나와 최저가", null) } }
        return opts.minByOrNull { it.price }
    }
}

data class BestPrice(val price: Long, val mall: String, val via: String, val deal: Item?)

// ── 판정 ─────────────────────────────────────────────────────

/** 다른 쇼핑몰이 같은 값이거나 더 싸면(1% 미만 차이) 핫딜로 치지 않는다. 비교할 값이 없으면 통과 */
fun beatsOthers(x: Item) = x.vsBest == null || x.vsBest >= 1.0

fun isHot(x: Item, s: Settings) = x.rate != null && x.rate >= s.threshold && (!s.onlyCheapest || beatsOthers(x))

enum class Tier(val label: String) { LEGEND("역대급"), HOT("핫딜"), GOOD("알뜰") }

/** 할인율 단계: 30% 이상 역대급, 15% 이상 핫딜, 그 아래는 알뜰 */
fun tier(rate: Double?): Tier? = when {
    rate == null -> null
    rate >= 30 -> Tier.LEGEND
    rate >= 15 -> Tier.HOT
    rate > 0 -> Tier.GOOD
    else -> null
}

fun interestActive(s: Settings) = s.interestOnly && s.interests.isNotEmpty()

fun inHomeScope(x: Item, s: Settings) = !interestActive(s) || x.cat in s.interests

fun srcOn(id: String, s: Settings) = id !in s.hiddenSources

fun dealVisible(x: Item, s: Settings): Boolean {
    if (x.mall in s.excludedMalls) return false
    if (s.hideSuspicious && x.suspicious) return false
    if (s.hideEnded && x.ended) return false
    if (x.kind == ItemKind.MARKET) return srcOn("danawa", s)
    return if (x.mentions.isNotEmpty()) x.mentions.any { srcOn(it.source, s) } else srcOn(x.source, s)
}

enum class DealSort(val label: String) { DISCOUNT("할인율"), NEW("새로 뜬 순"), HOT("반응순"), PRICE("가격") }

fun sortDeals(list: List<Item>, sort: DealSort, newCutoff: Long): List<Item> = when (sort) {
    DealSort.DISCOUNT -> list.sortedByDescending { it.rate ?: -999.0 }
    DealSort.NEW -> list.sortedWith(compareByDescending<Item> { isNew(it, newCutoff) }.thenByDescending { it.detectedAt ?: 0 })
    DealSort.HOT -> list.sortedWith(compareByDescending<Item> { it.buzz }.thenByDescending { it.rate ?: 0.0 })
    DealSort.PRICE -> list.sortedBy { it.price ?: Long.MAX_VALUE }
}

fun isNew(x: Item, cutoff: Long) = (x.detectedAt ?: 0) > cutoff

fun refLabel(x: Item) = if (x.reference == "lowest") "다나와 최저가" else "쇼핑몰 평균"

/** 상세 화면의 판정 문장 */
fun verdictText(x: Item, s: Settings, srcName: (String) -> String = { it }): String {
    if (x.kind == ItemKind.POST && x.rate == null) {
        return x.verifyReason ?: when (x.verifyStatus) {
            "pending" -> "아직 다나와 가격과 비교하지 않았어요. 다음 수집 때 확인해요."
            else -> "다나와 가격과 비교하지 못했어요."
        }
    }
    val rate = x.rate ?: return "다나와 판매처가 적어 쇼핑몰 평균가를 낼 수 없어요. 쇼핑몰별 가격을 직접 비교해 보세요."
    val isComm = x.kind == ItemKind.COMMUNITY || x.kind == ItemKind.POST
    val refPhrase = if (x.reference == "lowest") {
        "다나와 최저가(${won(x.referencePrice)}원)"
    } else {
        val who = when (x.kind) {
            ItemKind.PRODUCT -> "나머지 "
            ItemKind.MARKET -> "다른 "
            else -> "이 쇼핑몰을 뺀 "
        }
        "${who}쇼핑몰 ${x.malls ?: ""}곳 평균(${won(x.referencePrice)}원)"
    }
    val save = x.save ?: 0
    val sb = StringBuilder()
    if (save >= 0) {
        sb.append("${refPhrase}보다 ${won(save)}원(${pct(rate)}%) 싸요. ")
        sb.append(if (rate >= s.threshold) "내 기준 ${s.threshold}%를 넘었어요." else "내 기준 ${s.threshold}%에는 못 미쳐요.")
    } else {
        sb.append("${refPhrase}보다 ${won(-save)}원 비싸요.")
    }
    val price = x.price
    if (isComm && x.reference == "average" && x.lowest != null && price != null && price < x.lowest) {
        sb.append(" 다나와 최저가(${x.lowestMall.orEmpty()} ${won(x.lowest)}원)보다도 ${won(x.lowest - price)}원 싸요.")
    }
    if (x.kind == ItemKind.MARKET && x.secondPrice != null && price != null && x.secondPrice > price) {
        sb.append(" 두 번째로 싼 곳보다 ${won(x.secondPrice - price)}원 싸요.")
    }
    if (!beatsOthers(x)) {
        sb.append(" 다만 다른 쇼핑몰에서도 거의 같은 값에 팔고 있어 특별히 싼 딜은 아니에요.")
    }
    return sb.toString()
}

/** 어떻게 비교했는지 */
fun howText(x: Item, params: Params): List<String> = buildList {
    if (x.kind == ItemKind.MARKET) {
        add("다나와 판매처 목록에서 가장 싼 쇼핑몰을 나머지 쇼핑몰들의 평균가와 비교했어요.")
    } else if (x.kind == ItemKind.COMMUNITY || (x.kind == ItemKind.POST && x.rate != null)) {
        add("커뮤니티 글 제목에서 쇼핑몰·가격을 읽고, 다나와에서 같은 제품을 찾아 그 쇼핑몰을 뺀 판매처들의 가격과 비교했어요.")
    }
    if (x.reference == "lowest") add("평균을 낼 판매처가 ${params.minMalls}곳보다 적어 다나와 최저가와 비교했어요(더 보수적인 값).")
    if (x.basis == "total") add("딜과 판매처의 배송비를 모두 알아서 배송비 포함 금액끼리 비교했어요.")
    else if (x.rate != null) add("배송비를 모두 알 수 없어 상품 가격끼리 비교했어요.")
    if (x.unitAdjusted) add("수량이 달라 개당 가격으로 맞춰 비교했어요.")
    if (x.priceFromBody) add("제목에 가격이 없어 글 본문에서 가격을 읽었어요.")
    if (x.suspicious) add("평균보다 ${pct(params.suspiciousRate)}% 넘게 싸요. 옵션가·리퍼·가격 오류인지 꼭 확인하세요.")
}

/** 목표가에 닿았는지 */
fun targetReached(pcode: String, favs: Map<String, Fav>, index: Index): Boolean {
    val f = favs[pcode] ?: return false
    val target = f.target ?: return false
    val best = index.bestNow(pcode) ?: return false
    return best.price <= target
}

/** 검색: 지켜보는 제품 + 커뮤니티 글에서 이름으로 찾기 */
fun normalizeQuery(s: String) = s.lowercase(Locale.KOREA).replace(Regex("[\\s·\\-_/()\\[\\]]+"), "")

fun search(index: Index, query: String): List<Item> {
    val q = normalizeQuery(query)
    if (q.length < 2) return emptyList()
    val prods = index.products.values.filter { normalizeQuery(it.name).contains(q) }.map { p ->
        index.dealsByPcode[p.pcode]?.maxByOrNull { it.rate ?: 0.0 } ?: index.productItem(p)
    }
    val seenPcodes = prods.mapNotNull { it.pcode }.toSet()
    val posts = index.posts.filter { (it.pcode == null || it.pcode !in seenPcodes) && normalizeQuery("${it.title} ${it.mall}").contains(q) }
    return (prods + posts).distinctBy { it.key }
}
