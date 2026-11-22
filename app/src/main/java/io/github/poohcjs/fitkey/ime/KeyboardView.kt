package io.github.poohcjs.fitkey.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import io.github.poohcjs.fitkey.engine.Hangul
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyKind
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.Mode
import io.github.poohcjs.fitkey.engine.ShiftState
import io.github.poohcjs.fitkey.engine.SuggestionBar
import kotlin.math.abs

/**
 * 키보드 화면 (직접 그리는 View: 가볍고 빠르다).
 * 누른 자리를 '글쇠 단위' 위치로 바꿔 넘긴다. 어느 글쇠인지는 hitTest(배운 손 습관)가 정한다.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        /** 글쇠를 눌렀다 뗐다 (ux, uy: 누른 자리, 글쇠 단위) */
        fun onKey(key: Key, ux: Float, uy: Float)
        fun onLongPress(key: Key, text: String) {}
        fun onBackspace() {}
        fun onSuggestion(index: Int) {}
        /** 스페이스를 누른 채 옆으로 밀기: 커서 옮기기 */
        fun onCursorMove(steps: Int) {}
        /** 한/영 글쇠 길게: 다른 키보드로 바꾸기 */
        fun onLangLongPress() {}
        /** 한 손 모드에서 빈 쪽을 누름: 키보드를 그쪽으로 옮기기 (-1 왼쪽, 1 오른쪽) */
        fun onSideTap(side: Int) {}
        /** 스페이스바를 옆으로 밀기: 한/영 바꾸기 */
        fun onSpaceSwipe(dir: Int) {}
        /** 크기·위치 조절 중 (done: '확인'을 눌러 끝냄) */
        fun onAdjust(height: Float, width: Float, align: Float, gap: Int, done: Boolean) {}
        /** 추천 막대가 비었을 때의 단추 ("otp" 인증번호 넣기, "clip" 클립보드 붙여넣기, "settings" 설정) */
        fun onToolbar(action: String) {}
    }

    var listener: Listener? = null
    var layout: KeyboardLayout = KeyboardLayout.letters(true)
        set(v) { field = v; requestLayout(); invalidate() }
    /** 누른 자리 → 글쇠 (기본: 화면 그대로) */
    var hitTest: (KeyboardLayout, Float, Float) -> Key? = { l, x, y -> l.keyAt(x, y) }
    /** 화면 크기가 정해지면 '줄 높이 ÷ 글쇠 너비'를 알려 준다 (터치 모델이 거리를 잴 때 쓴다) */
    var onAspect: (Float) -> Unit = {}

    var lang = Lang.KO; set(v) { field = v; invalidate() }
    var shift = ShiftState.OFF; set(v) { field = v; invalidate() }
    var mode = Mode.LETTERS; set(v) { field = v; invalidate() }
    var enterLabel = "↵"; set(v) { field = v; invalidate() }
    var suggestions = SuggestionBar(); set(v) { field = v; invalidate() }
    var showStrip = true; set(v) { field = v; requestLayout(); invalidate() }
    var stripHint = ""; set(v) { field = v; invalidate() }
    var heightScale = 1f; set(v) { field = v.coerceIn(0.75f, 1.5f); requestLayout(); invalidate(); notifyAspect() }
    /** 글쇠 부분 너비 (1 = 화면 너비 전체, 0.7 = 한 손 모드 정도) */
    var widthScale = 1f; set(v) { field = v.coerceIn(0.6f, 1f); invalidate(); notifyAspect() }
    /** 글쇠 부분 좌우 위치 (-1 왼쪽 끝 ~ 0 가운데 ~ 1 오른쪽 끝). 너비가 1이면 의미 없음 */
    var align = 0f; set(v) { field = v.coerceIn(-1f, 1f); invalidate() }
    /** 키보드 아래 여백 (dp): 키보드를 위로 올린다 */
    var bottomGap = 0; set(v) { field = v.coerceIn(0, 120); requestLayout() }
    var vibrate = true
    var bottomInset = 0; set(v) { field = v; requestLayout() }
    /** 스페이스바를 옆으로 밀면 한/영 바꾸기 (끄면 밀어서 커서 옮기기) */
    var spaceSwipeLang = true; set(v) { field = v; invalidate() }
    /** 추천이 없을 때 위 줄에 클립보드·인증번호·설정 단추를 보여 줄지 */
    var toolbar = false; set(v) { field = v; invalidate() }
    /** 클립보드 최근 기록 (보여 줄 글, 비밀 글이면 가린 글) */
    var clip: String? = null; set(v) { field = v; invalidate() }
    /** 알림으로 온 인증번호 (보여 줄 때만) */
    var otp: String? = null; set(v) { field = v; invalidate() }
    private val chipRect = RectF()
    private val gearRect = RectF()
    /** 크기·위치 조절 모드: 파란 테두리와 손잡이를 끌어서 맞춘다 */
    var adjusting = false; set(v) { if (field != v) { field = v; requestLayout() }; adjustTarget = null; invalidate() }
    /** 보정 화면: 다음에 눌러야 할 글쇠 */
    var target: Char? = null; set(v) { field = v; invalidate() }
    /** 보정 화면: 다음에 Shift를 눌러야 함 (쌍자음·ㅒ·ㅖ) */
    var targetShift = false; set(v) { field = v; invalidate() }
    /** 배운 치우침 보기 (글쇠 단위) */
    var offsets: Map<Char, Pair<Float, Float>>? = null; set(v) { field = v; invalidate() }
    /** 보정 결과: 글쇠별 빗나간 비율 (0~1) → 초록·노랑·빨강으로 칠한다 */
    var missRates: Map<Char, Float>? = null; set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity.coerceAtMost(dp * 1.2f)
    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val stripH get() = if (showStrip) 44 * dp else 0f
    private val rowH get() = 50 * dp * heightScale
    private val padH get() = 3 * dp
    private val areaW get() = (width - 2 * padH) * widthScale
    /** 글쇠 부분의 왼쪽 끝 */
    private val left get() = padH + ((width - 2 * padH) - areaW) * (align + 1f) / 2f
    private val keyW get() = areaW / 10f

    private fun notifyAspect() { if (width > 0) onAspect(rowH / keyW) }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }.getOrNull()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // 조절 중에는 아래 손잡이가 잘리지 않게 조금 더 둔다
        val h = (stripH + layout.rows * rowH + 6 * dp + bottomGap * dp + (if (adjusting) 14 * dp else 0f)).toInt() + bottomInset
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = notifyAspect()

    // ── 그리기 ────────────────────────────────────────────
    private val bg get() = if (dark) 0xFF1B1C20.toInt() else 0xFFE6E8EC.toInt()
    private val keyBg get() = if (dark) 0xFF3B3D44.toInt() else Color.WHITE
    private val fnBg get() = if (dark) 0xFF2A2C32.toInt() else 0xFFCDD2DA.toInt()
    private val ink get() = if (dark) 0xFFF2F3F5.toInt() else 0xFF15171C.toInt()
    private val sub get() = if (dark) 0xFF9AA0AA.toInt() else 0xFF6B7280.toInt()
    private val brand = 0xFF2F6BFF.toInt()
    private val pressed = HashSet<Key>()

    override fun onDraw(canvas: Canvas) {
        // 내 영역 안에만 그린다 (drawColor는 화면 전체를 칠할 수 있어, 설정 앱 안에 넣었을 때 위쪽 글이 가려졌다)
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = bg
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (showStrip) drawStrip(canvas)
        for (k in layout.keys) drawKey(canvas, k)
        drawSideHandle(canvas)
        offsets?.let { drawOffsets(canvas, it) }
        if (adjusting) { drawAdjust(canvas); return }
        pressed.firstOrNull { it.kind == KeyKind.LETTER || it.kind == KeyKind.DIGIT || it.kind == KeyKind.SYMBOL }?.let { drawPreview(canvas, it) }
    }

    private fun keyRect(k: Key, out: RectF) {
        val gapX = 2.5f * dp
        val gapY = 4f * dp
        out.set(
            left + k.x * keyW + gapX, stripH + k.y * rowH + gapY,
            left + (k.x + k.w) * keyW - gapX, stripH + (k.y + k.h) * rowH - gapY + 1,
        )
    }

    private fun isFn(k: Key) = k.kind !in setOf(KeyKind.LETTER, KeyKind.DIGIT, KeyKind.SYMBOL, KeyKind.SPACE, KeyKind.COMMA, KeyKind.PERIOD)

    private fun drawKey(c: Canvas, k: Key) {
        keyRect(k, rect)
        val isPressed = k in pressed
        paint.color = when {
            k.kind == KeyKind.ENTER -> brand
            k.kind == KeyKind.SHIFT && shift != ShiftState.OFF && mode == Mode.LETTERS -> if (dark) 0xFF5A6E9C.toInt() else 0xFFB9CBFF.toInt()
            isFn(k) -> fnBg
            else -> keyBg
        }
        missRates?.get(k.letter)?.let { r ->
            val tint = when {
                r >= 0.25f -> 0xFFFF6B3D.toInt()
                r >= 0.1f -> 0xFFFFC233.toInt()
                else -> 0xFF12A150.toInt()
            }
            paint.color = blend(paint.color, tint, if (dark) 0.45f else 0.32f)
        }
        if (isPressed) paint.color = blend(paint.color, if (dark) Color.WHITE else Color.BLACK, 0.12f)
        c.drawRoundRect(rect, 7 * dp, 7 * dp, paint)
        if ((target != null && k.letter == target) || (targetShift && k.kind == KeyKind.SHIFT)) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * dp; paint.color = 0xFFFF6B3D.toInt()
            c.drawRoundRect(rect, 7 * dp, 7 * dp, paint)
            paint.style = Paint.Style.FILL
        }
        val label = labelOf(k)
        text.color = if (k.kind == KeyKind.ENTER) Color.WHITE else ink
        text.typeface = if (k.kind == KeyKind.LETTER) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        text.textSize = when {
            mode == Mode.NUMPAD && k.kind in setOf(KeyKind.DIGIT, KeyKind.SYMBOL, KeyKind.PERIOD) -> 27 * sp
            mode == Mode.NUMPAD && k.kind == KeyKind.SPACE -> 24 * sp
            else -> null
        } ?: when (k.kind) {
            KeyKind.LETTER -> if (lang == Lang.KO) 21 * sp else 22 * sp
            KeyKind.DIGIT, KeyKind.SYMBOL, KeyKind.COMMA, KeyKind.PERIOD -> 19 * sp
            KeyKind.SPACE -> 13 * sp
            else -> if (label.length > 2) 13.5f * sp else 17 * sp
        } * heightScale.coerceIn(0.9f, 1.15f)
        if (k.kind == KeyKind.SPACE && mode != Mode.NUMPAD) text.color = sub
        // 좁은 글쇠(한 손 모드 등)에서는 글자가 넘치지 않게 줄인다
        val maxW = rect.width() * 0.86f
        val w = text.measureText(label)
        if (w > maxW) text.textSize *= maxW / w
        val cy = rect.centerY() - (text.descent() + text.ascent()) / 2
        c.drawText(label, rect.centerX(), cy, text)
        // 길게 누르면 나오는 글자: 오른쪽 위에 작게
        val hint = k.longPress
        if (hint != null && mode == Mode.LETTERS && k.kind == KeyKind.LETTER) {
            text.textSize = 10 * sp; text.color = sub; text.typeface = Typeface.DEFAULT
            c.drawText(hint, rect.right - 7 * dp, rect.top + 12 * dp, text)
        }
    }

    fun labelOf(k: Key): String = when (k.kind) {
        KeyKind.LETTER -> {
            val l = k.letter!!
            when (lang) {
                Lang.KO -> (Hangul.jamoOf(if (shift != ShiftState.OFF && l in Hangul.SHIFTABLE) l.uppercaseChar() else l) ?: l).toString()
                Lang.EN -> (if (shift != ShiftState.OFF) l.uppercaseChar() else l).toString()
            }
        }
        KeyKind.SHIFT -> if (mode == Mode.LETTERS) (if (shift == ShiftState.LOCK) "⇪" else "⇧") else k.text ?: "1/2"
        KeyKind.BACKSPACE -> "⌫"
        KeyKind.SPACE -> if (mode == Mode.NUMPAD) "␣" else (if (lang == Lang.KO) "한국어" else "English").let { if (spaceSwipeLang) "◂ $it ▸" else it }
        KeyKind.ENTER -> enterLabel
        KeyKind.LANG -> if (lang == Lang.KO) "한/A" else "A/한"
        KeyKind.NUMPAD -> "123"
        KeyKind.MODE -> if (mode == Mode.LETTERS) "!#1" else if (lang == Lang.KO) "가나다" else "ABC"
        else -> k.text ?: ""
    }

    private fun drawStrip(c: Canvas) {
        val items = suggestions.items
        text.typeface = Typeface.DEFAULT
        if (adjusting) {
            text.textSize = 13 * sp; text.color = sub
            c.drawText("손잡이를 끌어서 크기와 위치를 맞추세요", width / 2f, stripH / 2 - (text.descent() + text.ascent()) / 2, text)
            return
        }
        if (items.isEmpty()) {
            if (stripHint.isNotEmpty()) {
                text.textSize = 13 * sp; text.color = sub
                c.drawText(stripHint, width / 2f, stripH / 2 - (text.descent() + text.ascent()) / 2, text)
            } else if (toolbar) {
                drawTopBar(c)
            }
            return
        }
        val cellW = width / 3f
        items.forEachIndexed { i, s ->
            val auto = i == suggestions.autoIndex
            text.textSize = 16 * sp
            text.typeface = if (auto) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            text.color = if (auto) brand else ink
            val label = if (s.isTyped && !auto) "“${s.word}”" else s.word
            c.drawText(ellipsize(label, cellW - 12 * dp), cellW * i + cellW / 2, stripH / 2 - (text.descent() + text.ascent()) / 2, text)
            if (i > 0) {
                paint.color = if (dark) 0x33FFFFFF else 0x22000000
                c.drawRect(cellW * i - 0.5f * dp, stripH * 0.28f, cellW * i + 0.5f * dp, stripH * 0.72f, paint)
            }
        }
    }

    /** 위 줄: 왼쪽 끝에 설정(덜 씀), 오른쪽에 인증번호(있으면) 또는 클립보드 최근 기록(자주 씀, 엄지가 닿기 쉬운 쪽) */
    private fun drawTopBar(c: Canvas) {
        val gearW = 40 * dp
        gearRect.set(6 * dp, stripH * 0.14f, 6 * dp + gearW, stripH * 0.86f)
        text.textSize = 18 * sp; text.color = sub; text.typeface = Typeface.DEFAULT
        c.drawText("⚙", gearRect.centerX(), gearRect.centerY() - (text.descent() + text.ascent()) / 2, text)
        chipRect.setEmpty()
        val label = when {
            otp != null -> "🔑 인증번호 $otp  넣기"
            clip != null -> "📋 ${clip!!.replace('\n', ' ')}"
            else -> return
        }
        val isOtp = otp != null
        text.textSize = 14.5f * sp
        text.typeface = if (isOtp) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val maxW = width - gearRect.right - 18 * dp - 24 * dp
        val shown = ellipsize(label, maxW)
        val w = text.measureText(shown) + 24 * dp
        chipRect.set(width - 10 * dp - w, stripH * 0.16f, width - 10 * dp, stripH * 0.84f)
        paint.color = if (isOtp) brand else if (dark) 0xFF2A2C32.toInt() else 0xFFD9DDE3.toInt()
        c.drawRoundRect(chipRect, chipRect.height() / 2, chipRect.height() / 2, paint)
        text.color = if (isOtp) Color.WHITE else ink
        text.textAlign = Paint.Align.LEFT
        c.drawText(shown, chipRect.left + 12 * dp, chipRect.centerY() - (text.descent() + text.ascent()) / 2, text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun ellipsize(s: String, max: Float): String {
        if (text.measureText(s) <= max) return s
        var t = s
        while (t.length > 1 && text.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun drawPreview(c: Canvas, k: Key) {
        keyRect(k, rect)
        val w = rect.width() * 1.25f
        val h = rowH * 1.05f
        val left = (rect.centerX() - w / 2).coerceIn(0f, width - w)
        val top = (rect.top - h - 2 * dp).coerceAtLeast(0f)
        val r = RectF(left, top, left + w, top + h)
        paint.color = if (dark) 0xFF4A4D55.toInt() else Color.WHITE
        paint.setShadowLayer(6 * dp, 0f, 2 * dp, 0x40000000)
        c.drawRoundRect(r, 9 * dp, 9 * dp, paint)
        paint.clearShadowLayer()
        text.textSize = 30 * sp; text.color = ink; text.typeface = Typeface.DEFAULT
        c.drawText(labelOf(k), r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawOffsets(c: Canvas, map: Map<Char, Pair<Float, Float>>) {
        paint.strokeWidth = 2.5f * dp
        for (k in layout.letters) {
            val (ox, oy) = map[k.letter] ?: continue
            val cx = left + k.cx * keyW
            val cy = stripH + k.cy * rowH
            val tx = cx + ox * keyW
            val ty = cy + oy * rowH
            paint.color = 0xFFFF6B3D.toInt()
            c.drawLine(cx, cy, tx, ty, paint)
            c.drawCircle(tx, ty, 4 * dp, paint)
        }
    }

    /** 한 손 모드: 빈 쪽에 '이쪽으로 옮기기' 화살표 */
    private fun sideSpace(): Pair<Float, Float> = left to (width - (left + areaW))

    private fun drawSideHandle(c: Canvas) {
        if (widthScale > 0.92f) return
        val (l, r) = sideSpace()
        val top = stripH
        val bottom = stripH + layout.rows * rowH
        text.textSize = 26 * sp; text.color = sub; text.typeface = Typeface.DEFAULT
        val cy = (top + bottom) / 2 - (text.descent() + text.ascent()) / 2
        if (l > 24 * dp) c.drawText("‹", l / 2, cy, text)
        if (r > 24 * dp) c.drawText("›", width - r / 2, cy, text)
    }

    // ── 크기·위치 조절 (삼성 키보드처럼 손잡이를 끌어서) ──────────
    private val frameT get() = stripH
    private val frameB get() = stripH + layout.rows * rowH
    private val frameL get() = left
    private val frameR get() = left + areaW
    private val frameCx get() = (frameL + frameR) / 2
    private val frameCy get() = (frameT + frameB) / 2
    private val btnY get() = frameB - rowH * 0.55f
    private val btnReset get() = RectF(frameCx - 118 * dp, btnY - 21 * dp, frameCx - 8 * dp, btnY + 21 * dp)
    private val btnOk get() = RectF(frameCx + 8 * dp, btnY - 21 * dp, frameCx + 118 * dp, btnY + 21 * dp)

    private fun drawAdjust(c: Canvas) {
        // 글쇠를 흐리게 덮고 파란 테두리
        paint.color = if (dark) 0xB31B1C20.toInt() else 0xB3E6E8EC.toInt()
        c.drawRect(0f, frameT, width.toFloat(), frameB, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * dp; paint.color = brand
        c.drawRect(frameL + 1.5f * dp, frameT + 1.5f * dp, frameR - 1.5f * dp, frameB - 1.5f * dp, paint)
        paint.style = Paint.Style.FILL
        // 손잡이: 위·아래(높이), 왼쪽·오른쪽(너비), 가운데(옮기기)
        handle(c, frameCx, frameT, 54 * dp, 26 * dp, "≡")
        handle(c, frameCx, frameB, 54 * dp, 26 * dp, "≡")
        handle(c, frameL + 13 * dp, frameCy, 26 * dp, 54 * dp, "‖")
        handle(c, frameR - 13 * dp, frameCy, 26 * dp, 54 * dp, "‖")
        paint.color = brand
        c.drawCircle(frameCx, frameCy, 28 * dp, paint)
        text.color = Color.WHITE; text.textSize = 22 * sp; text.typeface = Typeface.DEFAULT_BOLD
        c.drawText("✥", frameCx, frameCy - (text.descent() + text.ascent()) / 2, text)
        // 단추
        for ((r, label) in listOf(btnReset to "다시 설정", btnOk to "확인")) {
            paint.color = Color.WHITE
            paint.setShadowLayer(4 * dp, 0f, 1 * dp, 0x40000000)
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint)
            paint.clearShadowLayer()
            text.color = 0xFF15171C.toInt(); text.textSize = 16 * sp
            c.drawText(label, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
    }

    private fun handle(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, glyph: String) {
        paint.color = brand
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        c.drawRoundRect(r, 8 * dp, 8 * dp, paint)
        text.color = Color.WHITE; text.textSize = 16 * sp; text.typeface = Typeface.DEFAULT_BOLD
        c.drawText(glyph, cx, cy - (text.descent() + text.ascent()) / 2, text)
    }

    private class Adjust(val what: String, val rx: Float, val ry: Float, val h: Float, val w: Float, val align: Float, val gap: Int, val left: Float, val areaW: Float)
    private var adjustTarget: Adjust? = null

    private fun adjustTouch(e: MotionEvent) {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fun near(hx: Float, hy: Float, r: Float = 34 * dp) = abs(x - hx) < r && abs(y - hy) < r
                val what = when {
                    btnReset.contains(x, y) -> "reset"
                    btnOk.contains(x, y) -> "ok"
                    near(frameCx, frameT) -> "top"
                    near(frameCx, frameB) -> "bottom"
                    near(frameL + 13 * dp, frameCy, 40 * dp) -> "left"
                    near(frameR - 13 * dp, frameCy, 40 * dp) -> "right"
                    near(frameCx, frameCy, 44 * dp) -> "move"
                    else -> null
                } ?: return
                adjustTarget = Adjust(what, e.rawX, e.rawY, heightScale, widthScale, align, bottomGap, left, areaW)
                buzz()
            }
            MotionEvent.ACTION_MOVE -> {
                val a = adjustTarget ?: return
                val dx = e.rawX - a.rx
                val dy = e.rawY - a.ry
                val baseRows = layout.rows * 50 * dp
                val full = width - 2 * padH
                fun place(newLeft: Float, newW: Float) {
                    widthScale = newW / full
                    val w = areaW
                    val free = full - w
                    align = if (free < 1f) 0f else (((newLeft - padH) / free) * 2f - 1f)
                }
                when (a.what) {
                    "top" -> heightScale = (a.h * baseRows - dy) / baseRows
                    "bottom" -> {
                        // 아래 손잡이: 위쪽은 그대로 두고 아래 끝을 올리고 내린다
                        val newGap = (a.gap - dy / dp).toInt().coerceIn(0, 120)
                        val moved = (newGap - a.gap) * dp
                        heightScale = (a.h * baseRows - moved) / baseRows
                        bottomGap = newGap
                    }
                    "left" -> { val nw = (a.areaW - dx).coerceIn(full * 0.6f, full); place(a.left + a.areaW - nw, nw) }
                    "right" -> { val nw = (a.areaW + dx).coerceIn(full * 0.6f, full); place(a.left, nw) }
                    "move" -> {
                        place((a.left + dx).coerceIn(padH, padH + full - a.areaW), a.areaW)
                        bottomGap = (a.gap - dy / dp).toInt().coerceIn(0, 120)
                    }
                    else -> return
                }
                listener?.onAdjust(heightScale, widthScale, align, bottomGap, false)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val a = adjustTarget ?: return
                adjustTarget = null
                when (a.what) {
                    "reset" -> if (btnReset.contains(x, y)) {
                        heightScale = 1f; widthScale = 1f; align = 0f; bottomGap = 0
                        listener?.onAdjust(1f, 1f, 0f, 0, false)
                    }
                    "ok" -> if (btnOk.contains(x, y)) {
                        adjusting = false
                        listener?.onAdjust(heightScale, widthScale, align, bottomGap, true)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> adjustTarget = null
        }
    }

    // ── 터치 ──────────────────────────────────────────────
    private class Ptr(val key: Key, val ux: Float, val uy: Float, val downX: Float) {
        var longFired = false
        var cursorMode = false
        var swiped = false
        var lastX = downX
    }

    private val ptrs = HashMap<Int, Ptr>()
    /** 길게 누름·지우기 반복 타이머 (화면에 붙어 있는지와 상관없이 같은 방식으로 돈다) */
    private val timers = android.os.Handler(android.os.Looper.getMainLooper())
    private var stripDown = -1
    private val longPress = Runnable { onLongPressTimeout() }
    private val repeatDelete = object : Runnable {
        override fun run() {
            if (ptrs.values.none { it.key.kind == KeyKind.BACKSPACE }) return
            listener?.onBackspace()
            timers.postDelayed(this, 55)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (adjusting) { adjustTouch(e); return true }
        val i = e.actionIndex
        val id = e.getPointerId(i)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(id, e.getX(i), e.getY(i))
            MotionEvent.ACTION_MOVE -> for (p in 0 until e.pointerCount) move(e.getPointerId(p), e.getX(p))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(id, e.getX(i), e.getY(i))
            MotionEvent.ACTION_CANCEL -> { ptrs.clear(); pressed.clear(); timers.removeCallbacks(longPress); timers.removeCallbacks(repeatDelete); invalidate() }
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (y < stripH) { stripDown = id; return }
        // 한 손 모드의 빈 쪽을 누르면 키보드를 그쪽으로
        if (widthScale <= 0.92f && y < stripH + layout.rows * rowH) {
            val (l, r) = sideSpace()
            if (x < left && l > 24 * dp) { listener?.onSideTap(-1); return }
            if (x > left + areaW && r > 24 * dp) { listener?.onSideTap(1); return }
        }
        // 빠르게 겹쳐 치기: 앞 손가락을 떼기 전에 다음 글쇠를 누르면 앞 글쇠를 먼저 낸다
        for ((pid, p) in ptrs.entries.toList()) {
            if (p.key.kind != KeyKind.BACKSPACE && p.key.kind != KeyKind.SPACE && !p.longFired) {
                ptrs.remove(pid); pressed.remove(p.key)
                listener?.onKey(p.key, p.ux, p.uy)
            }
        }
        val ux = (x - left) / keyW
        val uy = (y - stripH) / rowH
        val key = hitTest(layout, ux, uy) ?: return
        ptrs[id] = Ptr(key, ux, uy, x)
        pressed += key
        buzz()
        timers.removeCallbacks(longPress)
        if (key.kind == KeyKind.BACKSPACE) {
            listener?.onBackspace()
            timers.removeCallbacks(repeatDelete)
            timers.postDelayed(repeatDelete, 420)
        } else if (key.longPress != null || key.kind == KeyKind.LANG || key.kind == KeyKind.NUMPAD || key.kind == KeyKind.SPACE) {
            timers.postDelayed(longPress, if (key.kind == KeyKind.SPACE) 350L else 380L)
        }
        invalidate()
    }

    private fun move(id: Int, x: Float) {
        val p = ptrs[id] ?: return
        if (p.key.kind != KeyKind.SPACE || p.swiped) return
        if (spaceSwipeLang && !p.cursorMode) {
            // 스페이스바를 옆으로 밀면 한/영 바꾸기 (길게 누른 뒤 밀면 커서 이동)
            if (abs(x - p.downX) > keyW * 0.9f) {
                p.swiped = true
                timers.removeCallbacks(longPress)
                buzz()
                listener?.onSpaceSwipe(if (x > p.downX) 1 else -1)
            }
            return
        }
        if (!p.cursorMode && abs(x - p.downX) > keyW * 0.6f) p.cursorMode = true
        if (!p.cursorMode) return
        val step = keyW * 0.45f
        while (x - p.lastX >= step) { listener?.onCursorMove(1); p.lastX += step }
        while (p.lastX - x >= step) { listener?.onCursorMove(-1); p.lastX -= step }
    }

    private fun up(id: Int, x: Float, y: Float) {
        if (id == stripDown) {
            stripDown = -1
            if (y < stripH && suggestions.items.isNotEmpty()) listener?.onSuggestion((x / (width / 3f)).toInt().coerceIn(0, suggestions.items.lastIndex))
            else if (y < stripH && toolbar && stripHint.isEmpty()) {
                when {
                    x <= gearRect.right + 8 * dp -> listener?.onToolbar("settings")
                    !chipRect.isEmpty && x >= chipRect.left - 8 * dp -> listener?.onToolbar(if (otp != null) "otp" else "clip")
                }
            }
            return
        }
        val p = ptrs.remove(id) ?: return
        pressed.remove(p.key)
        if (ptrs.isEmpty()) { timers.removeCallbacks(longPress); timers.removeCallbacks(repeatDelete) }
        if (!p.longFired && !p.cursorMode && !p.swiped && p.key.kind != KeyKind.BACKSPACE) listener?.onKey(p.key, p.ux, p.uy)
        invalidate()
    }

    private fun onLongPressTimeout() {
        val p = ptrs.values.lastOrNull() ?: return
        if (p.longFired) return
        when {
            p.key.kind == KeyKind.SPACE -> {
                // 길게 누름: 이제 밀면 커서가 움직인다
                if (!p.swiped) { p.cursorMode = true; buzz() }
                return
            }
            p.key.kind == KeyKind.LANG || p.key.kind == KeyKind.NUMPAD -> { p.longFired = true; listener?.onLangLongPress() }
            p.key.longPress != null && mode == Mode.LETTERS || p.key.kind == KeyKind.COMMA || p.key.kind == KeyKind.PERIOD -> {
                p.longFired = true
                buzz()
                listener?.onLongPress(p.key, p.key.longPress ?: return)
            }
        }
        pressed.remove(p.key)
        invalidate()
    }

    private fun buzz() {
        if (!vibrate) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            else vibrator?.vibrate(VibrationEffect.createOneShot(12, 60))
        }
    }

    companion object {
        fun blend(a: Int, b: Int, t: Float): Int {
            fun ch(s: Int) = ((((a shr s) and 0xFF) * (1 - t)) + (((b shr s) and 0xFF) * t)).toInt() and 0xFF
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
