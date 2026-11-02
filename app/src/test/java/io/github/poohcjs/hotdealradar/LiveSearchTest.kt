package io.github.poohcjs.hotdealradar

import io.github.poohcjs.hotdealradar.data.DanawaParser
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.UnitPrice
import io.github.poohcjs.hotdealradar.data.defaultLiveSort
import io.github.poohcjs.hotdealradar.data.sortLive
import io.github.poohcjs.hotdealradar.ui.screens.pickOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실시간 검색: 다나와 실제 화면(2026-11, '화장지' 검색과 990 PRO 상품 페이지)을 줄인 파일로 확인 */
class LiveSearchTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val results by lazy { DanawaParser.parseSearch(res("danawa_search_toilet.html")) }

    @Test fun parsesSearchProductsWithUnitPricesAndOptions() {
        assertEquals(9, results.size) // 가격 없는(판매 중지) 상품은 뺀다
        val first = results.first()
        assertEquals("1128841", first.pcode)
        assertEquals("깨끗한나라 순수 프리미엄 30m (30롤)", first.name) // <b> 강조 태그 제거
        assertEquals(UnitPrice("125원/10m", 125, "10m"), first.unit)
        assertTrue(first.options.size >= 3)
        assertTrue(results.single { it.pcode == "88888888" }.used)
    }

    @Test fun cheapestByUnitPriceComesFirst() {
        val clean = results.filter { !it.used }
        assertEquals(LiveSort.UNIT, defaultLiveSort(clean))
        val sorted = sortLive(clean, LiveSort.UNIT)
        val units = sorted.map { (pickOption(it, LiveSort.UNIT)?.unit ?: it.unit)!!.value }
        assertEquals(units.sorted(), units)
        assertEquals(units.min(), units.first())
        val byPrice = sortLive(clean, LiveSort.PRICE).map { it.price }
        assertEquals(byPrice.sorted(), byPrice)
    }

    @Test fun parsesUnitPriceText() {
        assertEquals(UnitPrice("1,481원/10m", 1481, "10m"), UnitPrice.parse("1,481원/10m"))
        assertEquals(null, UnitPrice.parse(""))
        assertEquals(null, UnitPrice.parse("가격비교"))
    }

    @Test fun productPageOffersSkipCardOnlyPrices() {
        val page = DanawaParser.parseProduct(res("danawa_product.html"), "18297722")
        assertTrue(page.offers.size >= 5)
        val emart = page.offers.first { it.mall == "이마트몰" }
        assertEquals(807293L, emart.price) // '현대카드 결제 시' 757,293원은 판매가가 아님
        assertEquals(2900L, emart.fee)
        assertEquals(page.offers.map { it.price }.sorted(), page.offers.map { it.price })
        assertFalse(page.offers.any { it.mall.isBlank() })
    }

    @Test fun objectEndSkipsBracesInStrings() {
        val s = """xx{"a":"}{","b":{"c":1}}yy"""
        assertEquals(s.length - 2, DanawaParser.objectEnd(s, 2))
    }

    @Test fun unescapesJsonStrings() {
        assertEquals("a\"b\\c/d\n<b>한</b>", DanawaParser.unescapeJsonString("a\\\"b\\\\c\\/d\\n\\u003cb\\u003e\\ud55c\\u003c/b\\u003e"))
        assertEquals("그대로", DanawaParser.unescapeJsonString("그대로"))
    }
}
