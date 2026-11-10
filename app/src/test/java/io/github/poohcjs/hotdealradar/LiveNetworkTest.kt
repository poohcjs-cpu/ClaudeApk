package io.github.poohcjs.hotdealradar

import io.github.poohcjs.hotdealradar.data.LiveSearchClient
import io.github.poohcjs.hotdealradar.data.MallDirectory
import io.github.poohcjs.hotdealradar.data.QueryCleaner
import io.github.poohcjs.hotdealradar.data.mergeSources
import io.github.poohcjs.hotdealradar.data.shownCost
import io.github.poohcjs.hotdealradar.data.shownUnit
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.sortLive
import io.github.poohcjs.hotdealradar.data.pickOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 다나와에 물어보는 확인 (-Plive 를 줄 때만 실행) */
class LiveNetworkTest {
    @Test fun searchAndOpenCheapestToiletPaper() = runBlocking {
        val c = LiveSearchClient()
        val list = c.search("화장지").filter { !it.used }
        println("검색 결과 ${list.size}개")
        assertTrue(list.size >= 10)
        val top = sortLive(list, LiveSort.UNIT).take(3)
        top.forEach { p -> val o = pickOption(p, LiveSort.UNIT); println("  ${p.name} ${o?.label} ${o?.price}원 ${o?.unit?.text} 쇼핑몰 ${o?.malls}곳") }
        val pc = pickOption(top.first(), LiveSort.UNIT)?.pcode ?: top.first().pcode
        val page = c.product(pc)
        println("상품 ${page.name}: 판매처 ${page.offers.size}곳, 최저 ${page.offers.firstOrNull()}")
        assertTrue(page.offers.isNotEmpty())
    }

    @Test fun babyToothpaste() = runBlocking {
        val c = LiveSearchClient()
        for (q in listOf("아기 치약", "아기치약", "유아 치약")) {
            val list = c.search(q)
            println("[$q] 결과 ${list.size}개 (중고 제외 ${list.count { !it.used }}개)")
            list.take(5).forEach { println("   ${it.name} | ${it.price}원 | ${it.unit?.text} | 옵션 ${it.options.size}") }
            assertTrue(list.isNotEmpty())
        }
    }

    @Test fun drNoahToothpaste() = runBlocking {
        val list = LiveSearchClient().search("닥터노아 치약")
        println("[닥터노아 치약] 결과 ${list.size}개 (쇼핑몰 개별 상품 ${list.count { it.isListing }}개)")
        sortLive(list, LiveSort.UNIT).take(5).forEach { println("   ${it.mall ?: "가격비교"} | ${it.name.take(40)} | ${it.price}원 | ${it.unit?.text}") }
        assertTrue(list.isNotEmpty())
    }

    @Test fun morePagesAndCardPrices() = runBlocking {
        val c = LiveSearchClient()
        val p1 = c.search("아기 치약", 1)
        val p2 = c.search("아기 치약", 2)
        val p3 = c.search("아기 치약", 3)
        val all = (p1 + p2 + p3).map { it.pcode }.toSet()
        println("[아기 치약 1~3쪽] ${p1.size} + ${p2.size} + ${p3.size} = 서로 다른 상품 ${all.size}개")
        assertTrue(all.size > p1.size)
        val page = c.product("18297722")
        println("[990 PRO 카드 할인가] ${page.cardOffers.map { "${it.mall} ${it.price}원 (${it.condition})" }}")
    }

    /** 사용자가 겪은 경우: '건봉국밥'을 찾으면 다나와·에누리보다 싼 공식몰 4+1팩이 1등이어야 한다 */
    @Test fun gunbongOfficialMallWins() = runBlocking {
        val c = LiveSearchClient()
        val q = QueryCleaner.clean("건봉국밥 최저가")
        val dn = runCatching { c.search(q) }.getOrDefault(emptyList())
        val en = c.enuriSearch(q)
        val mall = MallDirectory.match(q, MallDirectory.SEED).single().first
        val ml = c.mallSearch(mall, q)
        println("[$q] 다나와 ${dn.size} · 에누리 ${en.size} · 공식몰 ${ml.size}")
        ml.forEach { println("   공식몰 ${it.name} ${it.price}원 배송비 ${it.shippingFee}") }
        assertTrue(en.isNotEmpty() && ml.isNotEmpty())
        val sorted = sortLive(mergeSources(dn, en, ml), LiveSort.UNIT, withShipping = true)
        sorted.take(6).forEach { println("   ${it.source.label} | ${it.mall ?: "가격비교"} | ${it.name.take(36)} | ${shownCost(it, LiveSort.UNIT, true)}원 | ${shownUnit(it, LiveSort.UNIT, true)?.text} ${it.unitNote ?: ""}") }
        assertTrue(sorted.first().source == io.github.poohcjs.hotdealradar.data.LiveSource.MALL)
    }

    @Test fun enuriCatalogAndLink() = runBlocking {
        val c = LiveSearchClient()
        val list = c.enuriSearch("화장지")
        println("[에누리 화장지] ${list.size}개, 가격비교 ${list.count { !it.isListing }}개")
        assertTrue(list.size >= 20)
        val model = list.first { !it.isListing }
        val page = c.product(model.pcode)
        println("  ${page.name}: 판매처 ${page.offers.size}곳 ${page.offers.take(3)}")
        assertTrue(page.offers.isNotEmpty())
        val info = c.readLink("https://gunbong1987.com/product/50%EB%A7%8C%ED%8C%A9-%ED%8C%90%EB%A7%A4%EB%8F%8C%ED%8C%8C-38%EB%85%84-%EC%A0%84%ED%86%B5-%EC%88%9C%EC%B2%9C%EA%B1%B4%EB%B4%89%EA%B5%AD%EB%B0%A5-41%ED%8C%A9/20/category/26/display/1/")
        println("  링크: ${info.site} | ${info.name} | ${info.price}원 + ${info.fee} = ${info.total} | ${info.platform}")
        assertTrue(info.price != null && info.platform == "cafe24")
    }
}
