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
