package io.github.poohcjs.hotdealradar

import io.github.poohcjs.hotdealradar.data.LiveSearchClient
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.sortLive
import io.github.poohcjs.hotdealradar.ui.screens.pickOption
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
}
