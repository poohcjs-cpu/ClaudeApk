package io.github.poohcjs.hotdealradar

import io.github.poohcjs.hotdealradar.data.ArchiveItem
import io.github.poohcjs.hotdealradar.data.ArchiveSearch
import io.github.poohcjs.hotdealradar.data.Cafe24Parser
import io.github.poohcjs.hotdealradar.data.EnuriParser
import io.github.poohcjs.hotdealradar.data.LinkReader
import io.github.poohcjs.hotdealradar.data.LiveProduct
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.LiveSource
import io.github.poohcjs.hotdealradar.data.MallDirectory
import io.github.poohcjs.hotdealradar.data.QueryCleaner
import io.github.poohcjs.hotdealradar.data.Robots
import io.github.poohcjs.hotdealradar.data.ShipRule
import io.github.poohcjs.hotdealradar.data.UnitPrice
import io.github.poohcjs.hotdealradar.data.inferUnits
import io.github.poohcjs.hotdealradar.data.linkQuery
import io.github.poohcjs.hotdealradar.data.mergeSources
import io.github.poohcjs.hotdealradar.data.shownUnit
import io.github.poohcjs.hotdealradar.data.sortLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 다나와 밖까지 찾기: 에누리·공식몰(카페24)·공유 링크·커뮤니티 기록 (2026-11 실제 화면을 줄인 파일) */
class MultiSourceTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private fun listing(name: String, price: Long, mall: String, fee: Long? = 0, source: LiveSource = LiveSource.DANAWA) = LiveProduct(
        "x:" + name.hashCode() + price, name, "", price, null, UnitPrice.estimate(name, price), emptyList(), false, false, 0,
        mall = mall, link = "https://example.com", shippingFee = fee, total = fee?.let { price + it }, source = source,
    )

    // ── 단가 계산 ──
    @Test fun unitCountsPlusPacksAndTimes() {
        assertEquals(5, UnitPrice.countOnly("(50만팩 판매돌파!) 38년 전통 순천건봉국밥 ★4+1팩★"))
        assertEquals(2, UnitPrice.countOnly("닭가슴살 1+1"))
        // 'x24'는 개수, 'X 5개'는 한 번만
        assertEquals(UnitPrice.fromAmount(12000.0, true, 12000), UnitPrice.estimate("생수 500ml x 24", 12000))
        assertEquals(UnitPrice.fromAmount(375.0, false, 5000), UnitPrice.estimate("치약 75g 1개입 X 5개", 5000))
        // 용량이 여럿이면 가장 큰 값(전체 용량)
        assertEquals(UnitPrice.fromAmount(950.0, false, 15670), UnitPrice.estimate("건더기250g 1팩+육수350g 2팩 1세트 950g", 15670))
        assertNull(UnitPrice.estimate("순천건봉국밥 4+1팩", 45000))
    }

    @Test fun officialMallPackGetsUnitFromSameProductElsewhere() {
        val list = listOf(
            listing("38년 건봉 1987 돼지국밥 2인분 같은 1인분 950g 3개", 43350, "11번가"),
            listing("38년 건봉 1987 돼지국밥 2인분 같은 1인분 950g 2개", 32760, "11번가"),
            listing("1987 건봉 돼지국밥 1인분 950g", 17960, "11번가", 3000),
            listing("(50만팩 판매돌파!) 38년 전통 순천건봉국밥 4+1팩", 45000, "건봉국밥 공식몰", 3500, LiveSource.MALL),
        )
        val out = inferUnits(list)
        val official = out.last()
        assertNotNull(official.unit)
        assertEquals("10g", official.unit!!.per)
        assertTrue(official.unitNote!!.contains("950g × 5개"))
        // 배송비 포함 단가로도 공식몰이 가장 싸다
        val best = sortLive(out, LiveSort.UNIT, withShipping = true).first()
        assertEquals("건봉국밥 공식몰", best.mall)
        assertEquals(102L, shownUnit(best, LiveSort.UNIT, true)!!.value) // 48,500원 / 4,750g
    }

    @Test fun noInferenceWhenSizesDisagree() {
        val list = listOf(
            listing("건봉 국밥 950g", 15000, "A"),
            listing("건봉 국밥 600g", 12000, "B"),
            listing("건봉국밥 4+1팩", 45000, "C"),
        )
        assertNull(inferUnits(list).last().unit)
        // 이름이 잘린 한두 개('…건더기250g')는 다수(950g)를 따른다
        val mostly = listOf(
            listing("건봉 돼지국밥 950g 2개", 30000, "A"), listing("건봉 순대국밥 950g", 15000, "B"),
            listing("건봉 국밥 950g 3개", 43000, "C"), listing("건봉 돼지국밥 건더기250g", 15000, "D"),
            listing("건봉국밥 4+1팩", 45000, "E"),
        )
        assertEquals(95L, inferUnits(mostly).last().unit!!.value)
    }

    // ── 에누리 ──
    @Test fun parsesEnuriSearch() {
        val list = EnuriParser.parseSearch(res("enuri_search.html"))
        assertEquals(4, list.size)
        val g = list.first()
        assertEquals("enp:29348241916", g.pcode)
        assertEquals("G마켓", g.mall)
        assertEquals(15670L, g.price)
        assertEquals(0L, g.shippingFee)
        assertEquals(LiveSource.ENURI, g.source)
        assertTrue(list.any { it.mall == "billy (스마트스토어)" && it.shippingFee == 3490L })
        val model = list.single { !it.isListing }
        assertEquals("en:19044911", model.pcode)
        assertEquals(658, model.malls)
        assertTrue(model.options.size >= 3)
        assertEquals(UnitPrice("87원/10m", 87, "10m"), model.options.first { it.label == "1팩" }.unit)
    }

    @Test fun parsesEnuriCatalogOffersWithSmartstoreSellers() {
        val page = EnuriParser.parseCatalog(res("enuri_catalog.html"), "en:19044911")
        assertTrue(page.name.startsWith("코디 순수 3겹 데코"))
        assertEquals(26470L, page.lowest)
        assertEquals(5, page.offers.size)
        val smart = page.offers.first { it.mall.startsWith("휴블리스마켓") }
        assertEquals("휴블리스마켓 (스마트스토어)", smart.mall)
        assertEquals(5000L, smart.fee)
        assertEquals(31480L, smart.total)
        assertTrue(page.offers.first().link.contains("pl_no="))
    }

    @Test fun mergeDropsEnuriDuplicatesOfDanawa() {
        val dn = listOf(listing("코디 순수 3겹 데코 30m (30롤)", 26470, "x").copy(pcode = "1", mall = null))
        val en = listOf(
            listing("코디 순수 3겹 데코 30m (30롤)", 26470, "x", source = LiveSource.ENURI).copy(pcode = "en:1", mall = null),
            listing("코디 화장지 다른 상품", 9000, "G마켓", source = LiveSource.ENURI).copy(pcode = "enp:2"),
        )
        val merged = mergeSources(dn, en, emptyList())
        assertEquals(listOf("1", "enp:2"), merged.map { it.pcode })
    }

    // ── 다나와 새 상품 화면 (2026-11-10부터) ──
    @Test fun parsesNewDanawaProductPage() {
        val page = io.github.poohcjs.hotdealradar.data.DanawaParser.parseProduct(res("danawa_product_next.html"), "3630770")
        assertEquals("모나리자 점보롤 2겹 300m (16롤)", page.name)
        assertEquals(listOf("11번가", "롯데ON", "옥션", "현대Hmall"), page.offers.map { it.mall }) // 쇼핑몰마다 가장 싼 것
        assertEquals(16860L, page.offers.first().price)
        assertEquals(19870L, page.offers[1].total)
        assertTrue(page.offers.first().link.contains("loadingBridge"))
        assertEquals(15900L, page.cardOffers.single().price)
    }

    // ── 공식몰 (카페24) ──
    @Test fun parsesCafe24SearchAndShipping() {
        val mall = MallDirectory.SEED.first()
        val list = Cafe24Parser.parseSearch(res("cafe24_search.html"), mall)
        assertEquals(2, list.size)
        val pack = list.first()
        assertTrue(pack.name.contains("순천건봉국밥"))
        assertFalse(pack.name.startsWith("상품명"))
        assertEquals(45000L, pack.price) // 판매가 55,000원이 아니라 할인가
        assertTrue(pack.link.startsWith("https://gunbong1987.com/product/"))
        assertEquals(LiveSource.MALL, pack.source)
        val rule = ShipRule.parse(org.jsoup.Jsoup.parse(res("cafe24_product.html")).text())!!
        assertEquals(ShipRule(3500, 50000), rule)
        assertEquals(3500L, rule.feeFor(45000))
        assertEquals(0L, rule.feeFor(54000))
    }

    @Test fun matchesMallsByBrandWords() {
        assertEquals("gunbong1987.com", MallDirectory.match("건봉국밥", MallDirectory.SEED).single().first.host)
        assertEquals("gunbong1987.com", MallDirectory.match("돼지국밥 밀키트", MallDirectory.SEED).single().first.host)
        assertTrue(MallDirectory.match("화장지", MallDirectory.SEED).isEmpty())
    }

    @Test fun robotsRules() {
        val cafe24 = Robots("User-agent: *\nDisallow: /admin\nDisallow: /member/\nDisallow: /*board*page=\n\nUser-agent: bingbot\nDisallow: /")
        assertTrue(cafe24.allowed("/product/search.html?keyword=x"))
        assertFalse(cafe24.allowed("/member/login.html"))
        assertFalse(cafe24.allowed("/board/free/list.html?board_no=1&page=2"))
        val clien = Robots("User-agent: *\nAllow:/service/board/\nDisallow: /*?*")
        assertTrue(clien.allowed("/service/board/jirum/123"))
        // 가장 길게 맞는 규칙이 이긴다 (RFC 9309): '/service/board/' 허용이 '/*?*' 금지보다 길다
        assertTrue(clien.allowed("/service/board/jirum?po=2"))
        assertFalse(clien.allowed("/service/search?q=x"))
        assertFalse(Robots("User-agent: *\nDisallow: /").allowed("/x"))
        assertTrue(Robots(null).allowed("/x"))
        assertFalse(Robots("User-agent: HotdealRadar\nDisallow: /p\n\nUser-agent: *\nAllow: /").allowed("/p/1"))
    }

    // ── 공유 링크 ──
    @Test fun readsSharedCafe24Link() {
        val url = "https://gunbong1987.com/product/50만팩-판매돌파-38년-전통-순천건봉국밥-41팩/20/category/26/display/1/"
        val info = LinkReader.parse(res("cafe24_product.html"), url)
        assertEquals("gunbong1987.com", info.host)
        assertEquals("건봉국밥", info.site)
        assertEquals("(50만팩 판매돌파!) 38년 전통 순천건봉국밥 4+1팩", info.name)
        assertEquals(45000L, info.price)
        assertEquals(48500L, info.total)
        assertEquals("cafe24", info.platform)
        assertEquals("전통 순천건봉국밥", linkQuery(info.name))
        assertEquals(url, LinkReader.findUrl("건봉국밥 보세요 $url"))
    }

    @Test fun cleansChattyQueries() {
        assertEquals("화장지", QueryCleaner.clean("가장 싼 화장지"))
        assertEquals("닥터노아 치약", QueryCleaner.clean("닥터노아 치약 최저가"))
        assertEquals("생수 2L", QueryCleaner.clean("생수 2L 어디서 싸게 사?"))
        assertEquals("최저가", QueryCleaner.clean("최저가"))
    }

    // ── 커뮤니티 핫딜 기록 ──
    @Test fun pastDealsAndVerdict() {
        val items = listOf(
            ArchiveItem("ppomppu:1", "2026-09-12T10:00", "ppomppu", "순천 건봉국밥 950g 6팩", "건봉국밥", 54000, "u1", 0),
            ArchiveItem("clien:2", "2026-10-02T10:00", "clien", "건봉국밥 950g 3팩", "11번가", 36000, "u2", 0),
            ArchiveItem("ruliweb:3", "2026-10-03T10:00", "ruliweb", "다른 국밥 950g", "G마켓", 9000, "u3", 0),
            ArchiveItem("damoang:4", "2026-10-04T10:00", "damoang", "건봉 돼지국밥 950g 4팩", "G마켓", 50000, "u4", 0),
        )
        val h = ArchiveSearch.history(items, "건봉국밥", "2026-09", 6)
        // '건봉 돼지국밥'도 '건봉국밥'으로 찾는다 (붙여 쓴 말 나누기)
        assertEquals(listOf("ppomppu:1", "clien:2", "damoang:4"), h.deals.map { it.item.k })
        // 지금 단가가 지난 핫딜(54,000/5,700g ≈ 95원/10g)보다 60% 비싸면 기다리라고
        val v = ArchiveSearch.verdict(h, UnitPrice("152원/10g", 152, "10g", true), 43350)!!
        assertFalse(v.good)
        assertTrue(v.title.contains("비싸요"))
        val ok = ArchiveSearch.verdict(h, UnitPrice("96원/10g", 96, "10g", true), 45000)!!
        assertTrue(ok.good)
        // 단위가 다르면 판단하지 않는다
        assertNull(ArchiveSearch.verdict(h, UnitPrice("87원/10m", 87, "10m"), 7790))
    }
}
