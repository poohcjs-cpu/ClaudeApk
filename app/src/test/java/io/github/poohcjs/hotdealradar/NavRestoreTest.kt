package io.github.poohcjs.hotdealradar

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.JSON
import io.github.poohcjs.hotdealradar.data.Repository
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.isHot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 보던 화면 유지: 딜 상세를 보다가 다른 앱(공유)에 다녀오거나 앱이 정리돼도 그 화면으로 돌아온다 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi", application = NavRestoreTest.TestApp::class)
class NavRestoreTest {
    /** 시험 환경 준비는 앱이 설정을 읽기 전에(앱 시작 때) 해야 한다 */
    class TestApp : HotdealApp() {
        override fun onCreate() {
            // 시험 환경에는 WorkManager가 자동으로 켜지지 않아 시험용으로 먼저 켠다
            androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(this)
            File(filesDir, "deals.json").writeText(sample()) // 저장해 둔 데이터로 바로 뜨게
            // 새로 받기는 일부러 실패시켜(접속 불가 주소) 시험 중 데이터가 바뀌지 않게 한다
            getSharedPreferences("hotdeal", Context.MODE_PRIVATE).edit()
                .putString("settings", JSON.encodeToString(Settings.serializer(), SETTINGS))
                .remove("nav").remove("navAt") // 앞 시험에서 저장한 '보던 화면'을 지우고 시작
                .commit()
            super.onCreate()
        }
    }

    companion object {
        /** 시험 화면 높이: 880dp × 3 (xxhdpi) */
        const val SCREEN_H = 2640f
        val SETTINGS = Settings(schema = 2, dataUrl = "http://127.0.0.1:9/none.json", notify = false, notifyTarget = false)
        fun sample(): String = NavRestoreTest::class.java.classLoader!!.getResource("deals_sample.json")!!.readText()
    }

    @get:Rule val rule = createEmptyComposeRule()

    private val title: String by lazy {
        Index(Repository.parse(sample())).deals
            .filter { isHot(it, SETTINGS) && !it.suspicious && !it.outlier && !it.ended }.maxBy { it.rate ?: 0.0 }.name
    }

    private fun waitFor(text: String) = runCatching {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }.getOrElse {
        println("기다린 글자: $text")
        val roots = rule.onAllNodes(androidx.compose.ui.test.isRoot())
        println("화면 수: ${roots.fetchSemanticsNodes().size}")
        roots.fetchSemanticsNodes().forEachIndexed { i, _ ->
            println("[$i] " + roots[i].printToString(maxDepth = 5).lines().filter { "Text =" in it }.take(8).joinToString(" / "))
        }
        throw it
    }

    private fun openDetail() {
        waitFor(title)
        rule.onAllNodesWithText(title)[0].performClick()
        waitFor("어떻게 비교했나요")
    }

    /**
     * 한 시험 안에서 차례로 확인한다 (Robolectric에서 화면을 재생성한 뒤에는 그 화면이 다음 시험까지 남아
     * 시험끼리 섞이므로, 재생성은 맨 마지막에).
     * 1) 앱을 완전히 닫았다가 30분 안에 다시 열면 보던 딜 상세로 돌아온다
     * 2) 다른 앱(공유)에 다녀오는 사이 화면이 정리됐다가 다시 만들어져도 그대로다
     */
    @Test fun detailSurvivesRestartAndRecreation() {
        val first = ActivityScenario.launch(MainActivity::class.java)
        // 0) 목록을 내려 둔 채 딜을 열었다 돌아와도 내려 둔 자리 그대로
        waitFor("오늘의 핫딜")
        rule.onNode(mainList()).performScrollToIndex(10)
        rule.waitForIdle()
        rule.onAllNodesWithText("오늘의 핫딜").assertAll(isNotDisplayedMatcher())
        rule.onAllNodes(hasClickAction() and hasText("원", substring = true))
            .filter(isDisplayedMatcher())[0].performClick()
        waitFor("어떻게 비교했나요")
        rule.onNodeWithContentDescription("뒤로").performClick()
        waitFor("커뮤니티") // 아래 탭 막대가 다시 보이면 목록으로 돌아온 것
        rule.onAllNodesWithText("오늘의 핫딜").assertAll(isNotDisplayedMatcher()) // 맨 위로 돌아가지 않았다
        rule.onNode(mainList()).performScrollToIndex(0)
        rule.waitForIdle()
        openDetail()
        first.close()
        val again = ActivityScenario.launch(MainActivity::class.java)
        waitFor("어떻게 비교했나요")
        again.recreate()
        waitFor("어떻게 비교했나요")
        again.close()
    }

    /** 화면에 실제로 보이는지 (목록 밖으로 밀려나면 false) */
    private fun isDisplayedMatcher() = SemanticsMatcher("화면에 보임") { node ->
        val b = node.boundsInRoot
        b.width > 0 && b.height > 0 && b.bottom > 0 && b.top < SCREEN_H
    }

    private fun isNotDisplayedMatcher() = SemanticsMatcher("화면에 안 보임") { node ->
        val b = node.boundsInRoot
        !(b.width > 0 && b.height > 0 && b.bottom > 0 && b.top < SCREEN_H)
    }

    /** 세로 메인 목록 (가로로 넘기는 TOP 5·칩 줄과 구분) */
    private fun mainList() = hasScrollToIndexAction() and SemanticsMatcher("세로 목록") { node ->
        val key = androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo
        val info = if (node.config.contains(key)) node.config[key] else null
        info != null && info.columnCount == 1 && info.rowCount > 1
    }
}
