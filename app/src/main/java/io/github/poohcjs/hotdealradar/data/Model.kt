package io.github.poohcjs.hotdealradar.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * hotdeal-radar 수집기가 만드는 docs/data/deals.json (version 2) 형식.
 * 수집기 쪽에 칸이 늘어나도 앱이 깨지지 않도록 모든 칸에 기본값을 두고, 모르는 칸은 무시한다.
 */

@Serializable
data class DealsFile(
    val version: Int = 0,
    val generatedAt: String? = null,
    val lastRun: String? = null,
    val demo: Boolean = false,
    val params: Params = Params(),
    val categories: List<Category> = emptyList(),
    val sources: List<SourceStatus> = emptyList(),
    val stats: Stats = Stats(),
    val deals: List<Deal> = emptyList(),
    val posts: List<Post> = emptyList(),
    val products: Map<String, Product> = emptyMap(),
)

@Serializable
data class Params(
    val minMalls: Int = 3,
    val suspiciousRate: Double = 60.0,
    val keepHours: Int = 72,
    val recheckHours: Double = 8.0,
    val minRate: Double = 5.0,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String = "🛒",
    val hue: Int = 220,
    val keywords: List<String> = emptyList(),
)

@Serializable
data class SourceStatus(
    val id: String,
    val name: String = "",
    val board: String = "",
    val home: String = "",
    val kind: String = "",
    val enabled: Boolean = true,
    val status: String = "waiting",
    val message: String = "",
    val lastOk: String? = null,
    val count: Int? = null,
    val retryAfter: String? = null,
)

@Serializable
data class Stats(
    val posts: Int = 0,
    val verified: Int = 0,
    val products: Int = 0,
    val deals: Int = 0,
    val hot: Int = 0,
    val requests: Int? = null,
)

@Serializable
data class Mention(
    val id: String = "",
    val source: String = "",
    val url: String = "",
    val votes: Int? = null,
    val comments: Int? = null,
    val postedAt: String? = null,
    val ended: Boolean = false,
)

@Serializable
data class Deal(
    val id: String,
    val kind: String = "community", // community | market(가격비교 발굴)
    val title: String = "",
    val name: String = "",
    val danawaName: String = "",
    val mall: String = "",
    val price: Long = 0,
    val shipping: String = "",
    val url: String = "",
    val source: String = "",
    val cat: String = "etc",
    val postedAt: String? = null,
    val detectedAt: String? = null,
    val pcode: String = "",
    val rate: Double? = null,
    val reference: String? = null, // average | lowest
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
    val listed: Boolean = false,
    val votes: Int? = null,
    val views: Int? = null,
    val comments: Int? = null,
    val image: String = "",
    val mentions: List<Mention> = emptyList(),
)

@Serializable
data class Verify(
    val status: String = "pending", // ok | nomatch | skipped | pending | error | noprice
    val reason: String? = null,
    val code: String? = null,
    val rate: Double? = null,
    val reference: String? = null,
    val referencePrice: Long? = null,
    val average: Long? = null,
    val lowest: Long? = null,
    val lowestMall: String? = null,
    val malls: Int? = null,
    val pcode: String? = null,
    val danawaName: String? = null,
    val suspicious: Boolean = false,
    val basis: String? = null,
    val unitAdjusted: Boolean = false,
    val dealTotal: Long? = null,
    val checkedAt: String? = null,
    val matchScore: Double? = null,
)

@Serializable
data class Post(
    val id: String,
    val source: String = "",
    val url: String = "",
    val title: String = "",
    val name: String = "",
    val mall: String = "",
    val price: Long? = null,
    val currency: String? = null,
    val shipping: String = "",
    val postedAt: String? = null,
    val firstSeen: String? = null,
    val cat: String = "etc",
    val board: String = "",
    val votes: Int? = null,
    val views: Int? = null,
    val comments: Int? = null,
    val ended: Boolean = false,
    val flags: List<String> = emptyList(),
    val priceFrom: String? = null,
    val verify: Verify = Verify(),
)

@Serializable
data class Offer(
    val mall: String = "",
    val price: Long = 0,
    val shipping: String = "",
    val fee: Long? = null,
)

@Serializable
data class Product(
    val pcode: String,
    val name: String = "",
    val cat: String = "etc",
    val image: String = "",
    val lowest: Long? = null,
    val average: Long? = null,
    val othersAverage: Long? = null,
    val malls: Int? = null,
    val watch: Boolean = false,
    val offerCount: Int? = null,
    val rate: Double? = null,
    val bestMall: String? = null,
    val checkedAt: String? = null,
    val url: String = "",
    val keyword: String = "",
    val posts: List<String> = emptyList(),
    val offers: List<Offer> = emptyList(),
    // [날짜, 최저가, 평균가] — 평균가는 비어 있을 수 있다("")
    val history: List<JsonArray> = emptyList(),
) {
    fun historyPoints(): List<HistoryPoint> = history.mapNotNull { row ->
        val date = (row.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
        val lowest = (row.getOrNull(1) as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
        val avg = (row.getOrNull(2) as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
        HistoryPoint(date, lowest, avg)
    }
}

data class HistoryPoint(val date: String, val lowest: Long?, val average: Long?)
