package io.github.poohcjs.fitkey

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.Mode
import io.github.poohcjs.fitkey.engine.ShiftState
import io.github.poohcjs.fitkey.engine.Suggestion
import io.github.poohcjs.fitkey.engine.SuggestionBar
import io.github.poohcjs.fitkey.ime.FitKeyStore
import io.github.poohcjs.fitkey.ime.KeyboardView
import io.github.poohcjs.fitkey.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 화면 그림 (-Pscreenshots 로만 실행) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun save(name: String, v: View, width: Int = 1200) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun keyboard(block: KeyboardView.() -> Unit = {}) = KeyboardView(ctx).apply { block() }

    @Test fun koreanKeyboard() = save("1_ko", keyboard {
        suggestions = SuggestionBar(listOf(Suggestion("안녕하세오", null, true), Suggestion("안녕하세요", null, false), Suggestion("안녕하세요오", null, false)), 1)
    })

    @Test fun koreanShift() = save("2_ko_shift", keyboard { shift = ShiftState.ONCE })

    @Test fun english() = save("3_en", keyboard { lang = Lang.EN; shift = ShiftState.ONCE; enterLabel = "보내기" })

    @Test fun symbols() = save("4_symbols", keyboard { layout = KeyboardLayout.symbols(1); mode = Mode.SYMBOLS })

    @Test @Config(qualifiers = "w400dp-h880dp-night-xxhdpi")
    fun dark() = save("5_dark", keyboard { suggestions = SuggestionBar(listOf(Suggestion("the", null, false), Suggestion("they", null, false), Suggestion("then", null, false)), 0); lang = Lang.EN })

    @Test fun calibrationResult() {
        FitKeyStore.init(ctx)
        val t = FitKeyStore.touch
        val l = KeyboardLayout.letters(true)
        // 오른쪽 아래를 누르는 습관을 배운 모습
        for (k in l.letters) repeat(10) { t.learn(k, k.cx + 0.25f + (k.cx - 5f) * 0.03f, k.cy + 0.18f) }
        save("6_offsets", keyboard {
            layout = l; offsets = t.offsets(); stripHint = "보정 결과"
            missRates = l.letters.associate { it.letter!! to when (it.letter) { 'p', 'b' -> 0.33f; 'z', 'o', 'l' -> 0.15f; else -> 0.02f } }
        })
        t.clear()
    }

    @Test fun oneHanded() = save("8_one_hand", keyboard {
        widthScale = 0.8f; align = 1f; bottomGap = 24; toolbar = true
        suggestions = SuggestionBar(listOf(Suggestion("저녁", "wjsur", false), Suggestion("날씨", "skfTl", false), Suggestion("뭐", "anj", false)), null, predicted = true)
    })

    @Test fun calibrationScreen() = activityShot("9_calibrate", "calibrate")

    @Test @Config(qualifiers = "w400dp-h880dp-night-xxhdpi")
    fun calibrationDark() = activityShot("9_calibrate_dark", "calibrate")

    @Test fun toolbarAndSwipeLabel() = save("11_toolbar", keyboard { toolbar = true; clip = "부산 해운대구 우동 1234-5 해운대아파트 101동" })

    @Test @Config(qualifiers = "w400dp-h880dp-night-xxhdpi")
    fun otpBar() = save("14_otp", keyboard { toolbar = true; clip = "메모"; otp = "482915"; layout = KeyboardLayout.numpad(5); mode = Mode.NUMPAD })

    @Test fun numpad() = save("13_numpad", keyboard { layout = KeyboardLayout.numpad(5); mode = Mode.NUMPAD; enterLabel = "완료" })

    @Test @Config(qualifiers = "w400dp-h880dp-night-xxhdpi")
    fun adjustModeDark() = save("12_adjust_dark", keyboard { adjusting = true; widthScale = 0.85f; align = 0.4f })

    @Test fun sizeScreen() = activityShot("10_size", "size")

    private fun activityShot(name: String, screen: String) {
        val intent = android.content.Intent(ctx, MainActivity::class.java).putExtra("screen", screen)
        val a = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1200, 2640)
        shadowOf(Looper.getMainLooper()).idle()
        val bmp = Bitmap.createBitmap(1200, 2640, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1200, 2640)
        val bmp = Bitmap.createBitmap(1200, 2640, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "7_home.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
