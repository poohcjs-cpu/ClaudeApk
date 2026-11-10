package io.github.poohcjs.hotdealradar

import io.github.poohcjs.hotdealradar.data.DanawaParser
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.Repository
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.currentBest
import io.github.poohcjs.hotdealradar.data.matchesKeyword
import io.github.poohcjs.hotdealradar.data.popularPosts
import io.github.poohcjs.hotdealradar.data.shownCost
import io.github.poohcjs.hotdealradar.data.shownUnit
import io.github.poohcjs.hotdealradar.data.sortLive
import io.github.poohcjs.hotdealradar.data.targetReached
import io.github.poohcjs.hotdealradar.ui.screens.dominantCategory
import io.github.poohcjs.hotdealradar.ui.screens.queryFromShared
import io.github.poohcjs.hotdealradar.work.DealCheckWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11 보완: 배송비 포함 비교, 분류, 카드 할인가, 키워드, 인기 딜, 발굴 이상값, 찜 가격 추적, 공유 검색 */
class FeatureTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val baby by lazy { DanawaParser.parseSearch(res("danawa_search_baby.html")) }
    private val index by lazy { Index(Repository.parse(res("deals_sample.json"))) }

    @Test fun shippingChangesTheRealPrice() {
        val pororo = baby.first { it.pcode == "31137734" } // 뽀로로 치약 1,540원, 배송비 포함 4,540원
        assertEquals(1540L, shownCost(pororo, LiveSort.PRICE, false))
        assertEquals(4540L, shownCost(pororo, LiveSort.PRICE, true))
        assertEquals(171L, shownUnit(pororo, LiveSort.UNIT, false)!!.value)
        assertEquals(504L, shownUnit(pororo, LiveSort.UNIT, true)!!.value) // 배송비를 넣으면 10g당 504원
        // 단가순: 배송비를 빼면 뽀로로가 앞쪽이지만, 넣으면 뒤로 밀린다
        val noShip = sortLive(baby, LiveSort.UNIT, false).indexOfFirst { it.pcode == "31137734" }
        val ship = sortLive(baby, LiveSort.UNIT, true).indexOfFirst { it.pcode == "31137734" }
        assertTrue("$noShip -> $ship", ship > noShip)
    }

    @Test fun categoryFilterDropsAccessoriesAndToys() {
        assertEquals("치약", dominantCategory(baby))
        val toothpaste = baby.filter { it.category == "치약" }
        assertFalse(toothpaste.any { "짜개" in it.name || "양치놀이" in it.name })
    }

    @Test fun cardOnlyPricesKeptSeparately() {
        val page = DanawaParser.parseProduct(res("danawa_product.html"), "18297722")
        val card = page.cardOffers.first { it.mall == "이마트몰" }
        assertEquals(757293L, card.price)
        assertTrue(card.condition, "카드" in card.condition)
        assertTrue(page.offers.none { it.price == 757293L })
    }

    @Test fun keywordMatchingIgnoresSpacesAndCase() {
        assertEquals("닥터노아", matchesKeyword("[G마켓] 닥터 노아 불소치약 60g 3개", listOf("닥터노아")))
        assertEquals("airpods", matchesKeyword("Apple AirPods Pro 2", listOf("airpods")))
        assertEquals(null, matchesKeyword("뽀로로 치약", listOf("닥터노아", "a")))
    }

    @Test fun popularPostsAreUnverifiedButLiked() {
        val now = (index.posts.mapNotNull { it.postedAt }.maxOrNull() ?: 0L) + 60_000
        val pop = popularPosts(index, Settings(), now = now, limit = 50)
        pop.forEach {
            assertEquals(ItemKind.POST, it.kind)
            assertEquals(null, it.rate)
            assertTrue((it.votes ?: 0) >= 5 || (it.comments ?: 0) >= 15)
        }
    }

    @Test fun lonelyLowMarketPriceIsFlagged() {
        val odd = index.deals.filter { it.outlier }
        odd.forEach { assertTrue(it.kind == ItemKind.MARKET && (it.vsBest ?: 0.0) >= 25.0) }
        // 확인 필요 딜로는 알림을 보내지 않는다
        val picked = DealCheckWorker.pickNewDeals(index, Settings(notifyThreshold = 5, notifyInterestsOnly = false), setOf("x"))
        assertTrue(picked.none { it.outlier })
    }

    @Test fun favPriceCheckedByAppCountsForTarget() {
        val fav = Fav(name = "닥터노아", target = 20000).withPrice(19500, "11번가", at = 1)
        val favs = mapOf("99999999" to fav)
        assertEquals(19500L, currentBest("99999999", fav, index)!!.price)
        assertTrue(targetReached("99999999", favs, index))
        assertEquals(19500L, fav.withPrice(21000, "G마켓").lowestSeen)
        assertNotNull(currentBest("99999999", fav, null))
    }

    @Test fun sharedTextBecomesQuery() {
        assertEquals("닥터노아 불소 치약 60g 3개", queryFromShared("[닥터노아] 불소 치약 60g 3개 https://link.coupang.com/a/xyz"))
        assertEquals("에어팟 프로 2", queryFromShared("에어팟 프로 2\nhttps://smartstore.naver.com/x/products/1"))
    }
}
