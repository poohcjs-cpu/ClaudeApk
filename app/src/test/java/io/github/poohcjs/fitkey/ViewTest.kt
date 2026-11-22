package io.github.poohcjs.fitkey

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyKind
import io.github.poohcjs.fitkey.engine.Suggestion
import io.github.poohcjs.fitkey.engine.SuggestionBar
import io.github.poohcjs.fitkey.ime.KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** 키보드 화면의 손가락 동작: 스페이스바 밀기, 길게 눌러 커서, 손잡이 끌기 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h880dp-xxhdpi")
class ViewTest {
    private val events = mutableListOf<String>()
    private val view = KeyboardView(ApplicationProvider.getApplicationContext()).apply {
        vibrate = false
        listener = object : KeyboardView.Listener {
            override fun onKey(key: Key, ux: Float, uy: Float) { events += "key:${key.id}" }
            override fun onSpaceSwipe(dir: Int) { events += "swipe:$dir" }
            override fun onCursorMove(steps: Int) { events += "cursor:$steps" }
            override fun onAdjust(height: Float, width: Float, align: Float, gap: Int, done: Boolean) { events += "adjust:$done" }
            override fun onToolbar(action: String) { events += "bar:$action" }
            override fun onSuggestion(index: Int) { events += "sug:$index" }
        }
        measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }
    private val keyW = (1200 - 2 * 3 * 3f) / 10f
    private val dp = 3f
    private val strip = 44 * dp
    private val rowH = 50 * dp

    private fun ev(action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private val space: Key get() = view.layout.keys.first { it.kind == KeyKind.SPACE }
    private fun spaceX() = 9f + space.cx * keyW
    private fun spaceY() = strip + space.cy * rowH

    @Test fun tapOnSpaceTypesSpace() {
        ev(MotionEvent.ACTION_DOWN, spaceX(), spaceY())
        ev(MotionEvent.ACTION_UP, spaceX(), spaceY())
        assertEquals(listOf("key:space"), events)
    }

    @Test fun swipeOnSpaceSwitchesLanguageOnce() {
        ev(MotionEvent.ACTION_DOWN, spaceX(), spaceY())
        ev(MotionEvent.ACTION_MOVE, spaceX() + keyW * 0.5f, spaceY())
        ev(MotionEvent.ACTION_MOVE, spaceX() + keyW * 1.2f, spaceY())
        ev(MotionEvent.ACTION_MOVE, spaceX() + keyW * 2.5f, spaceY())
        ev(MotionEvent.ACTION_UP, spaceX() + keyW * 2.5f, spaceY())
        assertEquals(listOf("swipe:1"), events)   // 한 번만 바뀌고, 스페이스는 입력되지 않는다
    }

    @Test fun longPressThenDragMovesCursor() {
        ev(MotionEvent.ACTION_DOWN, spaceX(), spaceY())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        ev(MotionEvent.ACTION_MOVE, spaceX() - keyW * 1.0f, spaceY())
        ev(MotionEvent.ACTION_UP, spaceX() - keyW * 1.0f, spaceY())
        assertTrue(events.isNotEmpty() && events.all { it == "cursor:-1" })
    }

    @Test fun draggingTopHandleMakesKeyboardTaller() {
        view.adjusting = true
        view.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val before = view.heightScale
        ev(MotionEvent.ACTION_DOWN, 600f, strip)          // 위 손잡이 (가운데 위)
        ev(MotionEvent.ACTION_MOVE, 600f, strip - 100f)   // 위로 끌기
        ev(MotionEvent.ACTION_UP, 600f, strip - 100f)
        assertTrue("${view.heightScale} > $before", view.heightScale > before + 0.1f)
        // 오른쪽 손잡이를 왼쪽으로 끌면 너비가 줄고 왼쪽에 붙는다
        val midY = strip + view.layout.rows * rowH * view.heightScale / 2
        ev(MotionEvent.ACTION_DOWN, 1200f - 9f - 13 * dp, midY)
        ev(MotionEvent.ACTION_MOVE, 1200f - 9f - 13 * dp - 250f, midY)
        ev(MotionEvent.ACTION_UP, 1200f - 9f - 13 * dp - 250f, midY)
        assertTrue(view.widthScale < 0.85f)
        assertTrue(view.align < -0.9f)
        assertTrue(events.contains("adjust:false"))
    }

    @Test fun topBarShowsClipboardAndSettings() {
        view.toolbar = true
        view.clip = "부산 해운대구 우동 123"
        view.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1200, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)))
        // 설정은 왼쪽 끝, 클립보드·인증번호는 오른쪽
        ev(MotionEvent.ACTION_DOWN, 1100f, strip / 2); ev(MotionEvent.ACTION_UP, 1100f, strip / 2)
        ev(MotionEvent.ACTION_DOWN, 50f, strip / 2); ev(MotionEvent.ACTION_UP, 50f, strip / 2)
        view.otp = "482915"
        view.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1200, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)))
        ev(MotionEvent.ACTION_DOWN, 1100f, strip / 2); ev(MotionEvent.ACTION_UP, 1100f, strip / 2)
        assertEquals(listOf("bar:clip", "bar:settings", "bar:otp"), events)
    }

    private fun drawNow() = view.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1200, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)))
    private fun tap(x: Float, y: Float) { ev(MotionEvent.ACTION_DOWN, x, y); ev(MotionEvent.ACTION_UP, x, y) }

    /** 한 손 모드에서 빈 쪽을 눌러도 키보드가 옮겨지지 않고, 가장 가까운 끝 글쇠가 쳐진다 */
    @Test fun oneHandEmptySideTypesNearestKey() {
        view.widthScale = 0.8f
        view.align = 1f
        drawNow()
        val q = view.layout.keyOf('q')!!
        val l = view.layout.keyOf('l')!!
        tap(60f, strip + q.cy * rowH)
        // 오른쪽으로 모으면 오른쪽 끝은 그대로 l 자리
        tap(1188f, strip + l.cy * rowH)
        assertEquals(listOf("key:${q.id}", "key:${l.id}"), events)
    }

    /** 띄어 쓴 뒤 예측은 인증번호·방금 복사한 글에 자리를 내준다 (치는 중 추천은 그대로) */
    @Test fun predictionsGiveWayToOtpAndClip() {
        view.toolbar = true
        view.suggestions = SuggestionBar(listOf(Suggestion("날씨", "skfTl", false)), null, predicted = true)
        drawNow(); tap(100f, strip / 2)                   // 예측을 누름
        view.clip = "부산 해운대구"
        drawNow(); tap(1100f, strip / 2)                  // 방금 복사한 글은 예측보다 앞
        view.clip = null; view.otp = "482915"
        drawNow(); tap(1100f, strip / 2)                  // 인증번호도 예측보다 앞
        view.suggestions = SuggestionBar(listOf(Suggestion("날", "skf", true)), 0)
        drawNow(); tap(100f, strip / 2)                   // 낱말을 치는 중이면 추천이 앞
        assertEquals(listOf("sug:0", "bar:clip", "bar:otp", "sug:0"), events)
    }
}
