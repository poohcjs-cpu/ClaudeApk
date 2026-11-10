package io.github.poohcjs.hotdealradar

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Repository
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.parseTime
import io.github.poohcjs.hotdealradar.ui.screens.CommunityScreen
import io.github.poohcjs.hotdealradar.ui.screens.DealsScreen
import io.github.poohcjs.hotdealradar.ui.screens.DetailScreen
import io.github.poohcjs.hotdealradar.ui.screens.SavedScreen
import io.github.poohcjs.hotdealradar.ui.screens.SettingsScreen
import io.github.poohcjs.hotdealradar.ui.screens.SearchScreen
import io.github.poohcjs.hotdealradar.ui.screens.LiveProductScreen
import io.github.poohcjs.hotdealradar.ui.LiveSearchState
import io.github.poohcjs.hotdealradar.ui.LivePageState
import io.github.poohcjs.hotdealradar.data.DanawaParser
import io.github.poohcjs.hotdealradar.ui.theme.HD
import io.github.poohcjs.hotdealradar.ui.theme.HotdealTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 화면 모습을 PNG로 남긴다 (기기 없이 디자인 확인용).
 *   ./gradlew testDebugUnitTest -Pscreenshots --tests 'io.github.poohcjs.hotdealradar.ScreenshotTest'
 * 결과: app/build/screenshots 폴더의 PNG 파일
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val index: Index by lazy {
        Index(Repository.parse(javaClass.classLoader!!.getResource("deals_sample.json")!!.readText()))
    }
    private val s = Settings(schema = 2)
    private val cutoff: Long get() = (parseTime(index.file.generatedAt) ?: 0L) - 3 * 3_600_000L
    private val favs: Map<String, Fav> by lazy {
        val d = index.deals.filter { it.pcode != null }
        mapOf(
            d[0].pcode!! to Fav(savedPrice = (d[0].price ?: 0) + 20000, savedAt = 2, target = (d[0].price ?: 0) + 1000, name = d[0].name),
            d[3].pcode!! to Fav(savedPrice = d[3].price, savedAt = 1, target = ((d[3].price ?: 0) * 0.8).toLong(), name = d[3].name),
        )
    }

    private fun shot(name: String, dark: Boolean = false, withBar: Tab? = null, content: @Composable () -> Unit) {
        rule.setContent {
            HotdealTheme(dark = dark) {
                Column(Modifier.fillMaxSize().background(HD.p.bg)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    if (withBar != null) BottomBar(withBar, 1) {}
                }
            }
        }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val none = PaddingValues()

    @Test fun deals() = shot("1_deals", withBar = Tab.DEALS) {
        DealsScreen(index, s, favs, cutoff, false, none, {}, {})
    }

    @Test @Config(qualifiers = "w400dp-h2400dp-xxhdpi")
    fun dealsFull() = shot("1_deals_full", withBar = Tab.DEALS) {
        DealsScreen(index, s, favs, cutoff, false, none, {}, {})
    }

    @Test fun dealsDark() = shot("1_deals_dark", dark = true, withBar = Tab.DEALS) {
        DealsScreen(index, s, favs, cutoff, false, none, {}, {})
    }

    @Test @Config(qualifiers = "w400dp-h2200dp-xxhdpi")
    fun detailCommunity() = shot("2_detail_community") {
        val x = index.deals.first { it.kind == ItemKind.COMMUNITY }
        DetailScreen(x, index, s, favs, none, {}, {}, { _, _ -> }, {})
    }

    @Test @Config(qualifiers = "w400dp-h2200dp-xxhdpi")
    fun detailMarket() = shot("2_detail_market") {
        val x = index.deals.first { it.kind == ItemKind.MARKET && it.pcode in favs }
        DetailScreen(x, index, s, favs, none, {}, {}, { _, _ -> }, {})
    }

    @Test fun community() = shot("3_community", withBar = Tab.COMMUNITY) {
        CommunityScreen(index, s, none, {}, {})
    }

    @Test fun saved() = shot("4_saved", withBar = Tab.SAVED) {
        SavedScreen(index, s, favs, none, {}, {})
    }

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test @Config(qualifiers = "w400dp-h1900dp-xxhdpi")
    fun searchLive() = shot("6_search_live", withBar = Tab.SEARCH) {
        val list = DanawaParser.parseSearch(res("danawa_search_toilet.html"))
        SearchScreen(
            index, s, favs, listOf("에어팟"), {}, LiveSearchState("화장지", false, list, null, System.currentTimeMillis()), {}, {},
            none, {}, {},
        )
    }

    @Test @Config(qualifiers = "w400dp-h1500dp-xxhdpi")
    fun searchListings() = shot("6_search_listings", withBar = Tab.SEARCH) {
        val list = DanawaParser.parseSearch(res("danawa_search_listings.html"))
        SearchScreen(
            index, s, favs, emptyList(), {}, LiveSearchState("닥터노아 치약", false, list, null, System.currentTimeMillis()), {}, {},
            none, {}, {},
        )
    }

    @Test @Config(qualifiers = "w400dp-h1700dp-xxhdpi")
    fun searchBaby() = shot("6_search_baby", withBar = Tab.SEARCH) {
        val list = DanawaParser.parseSearch(res("danawa_search_baby.html"))
        SearchScreen(
            index, s, favs, emptyList(), {}, LiveSearchState("아기 치약", false, list, null, System.currentTimeMillis(), loadingMore = true), {}, {},
            none, {}, {},
        )
    }

    /** 건봉국밥: 다나와·에누리·공식몰을 합쳐 공식몰이 1등, 커뮤니티 기록으로 '지금 사도 될까?' */
    private fun gunbongResults(extra: List<io.github.poohcjs.hotdealradar.data.LiveProduct> = emptyList()): List<io.github.poohcjs.hotdealradar.data.LiveProduct> {
        val en = io.github.poohcjs.hotdealradar.data.EnuriParser.parseSearch(res("enuri_search.html")).filter { "건봉" in it.name }
        val rule = io.github.poohcjs.hotdealradar.data.ShipRule(3500, 50000)
        val ml = io.github.poohcjs.hotdealradar.data.Cafe24Parser.parseSearch(res("cafe24_search.html"), io.github.poohcjs.hotdealradar.data.MallDirectory.SEED.first())
            .map { it.copy(shippingFee = rule.feeFor(it.price), total = it.price + rule.feeFor(it.price)) }
        val dn = listOf(
            io.github.poohcjs.hotdealradar.data.LiveProduct("EE1", "1987 건봉 돼지국밥 1인분 950g", "", 17960, null,
                io.github.poohcjs.hotdealradar.data.UnitPrice.estimate("950g", 17960), emptyList(), false, false, 0, mall = "11번가", link = "https://x", shippingFee = 3000, total = 20960),
        )
        return io.github.poohcjs.hotdealradar.data.mergeSources(dn, en, ml, extra)
    }

    private val gunbongHistory = io.github.poohcjs.hotdealradar.data.ArchiveSearch.history(
        listOf(
            io.github.poohcjs.hotdealradar.data.ArchiveItem("ppomppu:1", "2026-11-03T10:00", "ppomppu", "순천 건봉국밥 950g 6팩 무료배송", "건봉국밥", 54000, "u1", 0),
            io.github.poohcjs.hotdealradar.data.ArchiveItem("ruliweb:2", "2026-11-07T21:00", "ruliweb", "건봉 돼지국밥 950g 3팩", "G마켓", 36900, "u2", 0),
        ), "건봉국밥", "2026-11", 6,
    )

    private val gunbongSites = mapOf(
        io.github.poohcjs.hotdealradar.data.LiveSource.DANAWA to io.github.poohcjs.hotdealradar.ui.SiteState(false, 34),
        io.github.poohcjs.hotdealradar.data.LiveSource.ENURI to io.github.poohcjs.hotdealradar.ui.SiteState(false, 33),
        io.github.poohcjs.hotdealradar.data.LiveSource.MALL to io.github.poohcjs.hotdealradar.ui.SiteState(false, 2),
    )

    @Test @Config(qualifiers = "w400dp-h2300dp-xxhdpi")
    fun searchGunbong() = shot("6_search_gunbong", withBar = Tab.SEARCH) {
        SearchScreen(
            index, s, favs, emptyList(), {},
            LiveSearchState("건봉국밥", false, gunbongResults(), null, System.currentTimeMillis(), asked = "건봉국밥 최저가",
                sites = gunbongSites, malls = listOf("건봉국밥 공식몰"), history = gunbongHistory),
            {}, {}, none, {}, {},
        )
    }

    @Test @Config(qualifiers = "w400dp-h1600dp-xxhdpi")
    fun searchSharedLink() = shot("6_search_link", withBar = Tab.SEARCH) {
        val url = "https://gunbong1987.com/product/x/20/category/26/display/1/"
        val info = io.github.poohcjs.hotdealradar.data.LinkReader.parse(res("cafe24_product.html"), url)
        val mine = io.github.poohcjs.hotdealradar.data.LiveProduct("link:1", info.name, info.image, info.price!!, null, null, emptyList(), false, false, -1,
            mall = info.site + " (공유한 링크)", link = url, shippingFee = info.fee, total = info.total, source = io.github.poohcjs.hotdealradar.data.LiveSource.MALL)
        val results = gunbongResults(listOf(mine)).filter { it.source != io.github.poohcjs.hotdealradar.data.LiveSource.MALL || it.pcode == "link:1" }
        SearchScreen(
            index, s, favs, emptyList(), {},
            LiveSearchState("순천건봉국밥", false, results, null, System.currentTimeMillis(), sites = gunbongSites, history = gunbongHistory),
            {}, {}, none, {}, {},
            link = io.github.poohcjs.hotdealradar.ui.LinkState(url, false, info, null, false),
        )
    }

    @Test @Config(qualifiers = "w400dp-h2400dp-xxhdpi")
    fun dealsKeywords() = shot("1_deals_keywords", withBar = Tab.DEALS) {
        DealsScreen(index, s.copy(keywords = listOf("버즈", "두유")), favs, cutoff, false, none, {}, {})
    }

    @Test fun searchEmpty() = shot("6_search_empty", withBar = Tab.SEARCH) {
        SearchScreen(index, s, favs, listOf("에어팟", "생수 2L"), {}, LiveSearchState(), {}, {}, none, {}, {})
    }

    @Test @Config(qualifiers = "w400dp-h2000dp-xxhdpi")
    fun liveProduct() = shot("7_live_product") {
        val list = DanawaParser.parseSearch(res("danawa_search_toilet.html"))
        val page = DanawaParser.parseProduct(res("danawa_product.html"), "18297722")
        LiveProductScreen("16816127", list.first { it.pcode == "16816127" }, LivePageState(false, page, null), index, favs, none,
            { _, _ -> }, {}, {}, { _, _, _ -> }, {})
    }

    @Test @Config(qualifiers = "w400dp-h2600dp-xxhdpi")
    fun settings() = shot("5_settings", withBar = Tab.SETTINGS) {
        SettingsScreen(index, s, none, {}, {})
    }
}
