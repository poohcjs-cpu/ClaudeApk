package io.github.poohcjs.fitkey

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyKind
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
}
