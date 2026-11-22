package io.github.poohcjs.fitkey.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.poohcjs.fitkey.engine.FieldInfo
import io.github.poohcjs.fitkey.engine.EngineSettings
import io.github.poohcjs.fitkey.engine.InputEngine
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyKind
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.Mode
import io.github.poohcjs.fitkey.engine.TextSink

/** 안드로이드 키보드 서비스: 화면(KeyboardView)과 두뇌(InputEngine)를 입력칸에 이어 준다 */
class FitKeyService : InputMethodService(), KeyboardView.Listener {

    private lateinit var engine: InputEngine
    private var view: KeyboardView? = null
    private var settings = KeySettings()
    private var letters = KeyboardLayout.letters(true)
    private var numpad = KeyboardLayout.numpad(5)
    private var editor: EditorInfo? = null

    private val sink = object : TextSink {
        override fun setComposing(text: String) { currentInputConnection?.setComposingText(text, 1) }
        override fun commit(text: String) { currentInputConnection?.commitText(text, 1) }
        override fun finishComposing() { currentInputConnection?.finishComposingText() }
        override fun deleteBefore(n: Int) { currentInputConnection?.deleteSurroundingText(n, 0) }
        override fun sendBackspace() { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        override fun textBefore(n: Int): String? = currentInputConnection?.getTextBeforeCursor(n, 0)?.toString()
        override fun enter() {
            val info = editor
            val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
            val noAction = info == null || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
                action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
            if (noAction) sendKeyChar('\n') else currentInputConnection?.performEditorAction(action)
        }
    }

    private val settingsListener: () -> Unit = { applySettings() }
    private val otpListener: () -> Unit = { view?.post { refreshTopBar() } }
    private var clipboard: android.content.ClipboardManager? = null
    private val clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener { refreshTopBar() }
    /** 지금 칸이 비밀번호 칸인지 (클립보드 내용을 가린다) */
    private var secretField = false
    /** 위 줄에서 붙여 넣은 클립의 복사 시각 (같은 것은 다시 띄우지 않는다) */
    private var pastedClipAt = 0L

    override fun onCreate() {
        super.onCreate()
        FitKeyStore.init(this)
        engine = InputEngine(sink, FitKeyStore.touch, FitKeyStore.userWords, { FitKeyStore.dict(it) }, bigrams = FitKeyStore.bigrams)
        engine.onSuggestions = { bar -> view?.suggestions = bar }
        engine.onStateChanged = { syncView() }
        FitKeyStore.addListener(settingsListener)
        OtpStore.addListener(otpListener)
        clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        runCatching { clipboard?.addPrimaryClipChangedListener(clipListener) }
        applySettings()
    }

    override fun onDestroy() {
        FitKeyStore.removeListener(settingsListener)
        OtpStore.removeListener(otpListener)
        runCatching { clipboard?.removePrimaryClipChangedListener(clipListener) }
        FitKeyStore.save()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        v.toolbar = true
        v.hitTest = { layout, x, y -> if (layout === letters) FitKeyStore.touch.hit(layout, x, y) else layout.keyAt(x, y) }
        v.onAspect = { FitKeyStore.touch.aspect = it }
        // 화면 아래 내비게이션 막대에 가리지 않게
        ViewCompat.setOnApplyWindowInsetsListener(v) { _, insets ->
            v.bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insets
        }
        view = v
        applySettings()
        syncView()
        return v
    }

    private fun applySettings() {
        settings = FitKeyStore.settings(this)
        if (::engine.isInitialized) engine.settings = EngineSettings(settings.autoCorrect, settings.learn, settings.suggestions, settings.doubleSpacePeriod)
        // 숫자 줄·한/A 글쇠 설정이 바뀔 때만 새로 만든다 (글쇠 판정은 같은 배열 객체인지로 구분한다)
        val wantRows = if (settings.numberRow) 5 else 4
        val hasNumpadKey = letters.keys.any { it.kind == KeyKind.NUMPAD }
        if (letters.rows != wantRows || hasNumpadKey != settings.spaceSwipeLang) {
            letters = KeyboardLayout.letters(settings.numberRow, numpadKey = settings.spaceSwipeLang)
        }
        if (numpad.rows != wantRows) numpad = KeyboardLayout.numpad(wantRows)
        if (::engine.isInitialized) engine.layout = letters
        view?.let { applyLook(it, settings) }
        syncView()
    }

    /** 가로 화면에서도 입력칸 전체를 덮는 모드로 바꾸지 않는다 (앱 화면이 그대로 보이게) */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editor = info
        applySettings()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val flags = info.inputType and InputType.TYPE_MASK_FLAGS
        val secret = (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val addressLike = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
        )
        val noSuggest = (flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        val field = FieldInfo(
            secret = secret,
            autoCorrect = !addressLike && !noSuggest && !numeric,
            autoCap = (flags and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0,
            numeric = numeric,
            // 앱이 '배우지 말라'고 한 칸 (브라우저 시크릿 모드 등)
            incognito = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0,
        )
        // 비밀번호·주소 칸은 영어로 시작, 나머지는 마지막에 쓰던 언어
        val startLang = if (secret || addressLike) Lang.EN else FitKeyStore.lastLang(this)
        engine.startInput(field, keepLang = false, startLang = startLang)
        view?.enterLabel = enterLabel(info)
        secretField = secret
        // 비밀번호 칸에서도 클립보드(비밀번호 관리 앱에서 복사한 것)는 붙여 넣을 수 있게 위 줄을 보여 주되, 내용은 가린다
        view?.stripHint = ""
        refreshTopBar()
        syncView()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        view?.let { v ->
            if (v.adjusting) { v.adjusting = false; onAdjust(v.heightScale, v.widthScale, v.align, v.bottomGap, true) }
        }
        engine.resetWord(finish = true)
        FitKeyStore.saveLang(this, engine.lang)
        FitKeyStore.save()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        if (::engine.isInitialized) engine.resetWord(finish = false)
        super.onFinishInput()
    }

    /** 사용자가 커서를 옮기면(글 가운데를 누르는 등) 조합을 끝낸다 */
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (engine.currentText.isNotEmpty() && (newSelStart != newSelEnd || candidatesEnd < 0 || newSelEnd != candidatesEnd)) {
            engine.resetWord(finish = true)
        } else if (engine.currentText.isEmpty()) {
            // 낱말을 치는 중이 아니면 커서 앞 낱말로 '다음에 올 말'을 다시 맞춘다
            engine.onSelectionChanged(newSelStart != newSelEnd)
        }
    }

    private fun syncView() {
        val v = view ?: return
        if (!::engine.isInitialized) return
        v.lang = engine.lang
        v.shift = engine.shift
        v.mode = engine.mode
        val want = when (engine.mode) {
            Mode.LETTERS -> letters
            Mode.SYMBOLS -> SYM1
            Mode.NUMPAD -> numpad
            Mode.SYMBOLS2 -> SYM2
        }
        if (v.layout !== want) v.layout = want
        v.suggestions = engine.suggestions
    }

    private fun enterLabel(info: EditorInfo): String {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "검색"
            EditorInfo.IME_ACTION_SEND -> "보내기"
            EditorInfo.IME_ACTION_GO -> "이동"
            EditorInfo.IME_ACTION_DONE -> "완료"
            EditorInfo.IME_ACTION_NEXT -> "다음"
            else -> "↵"
        }
    }

    // ── KeyboardView.Listener ─────────────────────────────
    override fun onKey(key: Key, ux: Float, uy: Float) {
        when (key.kind) {
            KeyKind.LETTER -> engine.onLetter(key, ux, uy)
            KeyKind.DIGIT, KeyKind.SYMBOL, KeyKind.COMMA, KeyKind.PERIOD -> engine.onText(key.text ?: return)
            KeyKind.SPACE -> engine.onSpace()
            KeyKind.ENTER -> engine.onEnter()
            KeyKind.SHIFT -> engine.onShift()
            KeyKind.LANG -> { engine.onLang(); FitKeyStore.saveLang(this, engine.lang) }
            KeyKind.NUMPAD -> engine.onNumpad()
            KeyKind.MODE -> engine.onMode()
            KeyKind.BACKSPACE -> Unit
        }
    }

    override fun onLongPress(key: Key, text: String) = engine.onText(text)

    override fun onBackspace() = engine.onBackspace()

    override fun onSuggestion(index: Int) = engine.onSuggestion(index)

    override fun onCursorMove(steps: Int) {
        engine.resetWord(finish = true)
        val ic = currentInputConnection ?: return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    /**
     * 방금 복사·잘라낸 글: (붙여 넣을 글, 비밀 글인지, 복사한 때).
     * 복사한 지 1분 안에만 보여 주고, 위 줄에서 한 번 붙여 넣으면 더는 보여 주지 않는다.
     */
    private fun clipText(): Triple<String, Boolean, Long>? = runCatching {
        val cm = clipboard ?: return null
        val desc = cm.primaryClipDescription ?: return null
        val age = System.currentTimeMillis() - desc.timestamp
        if (age > CLIP_SHOW_MS || age < -5_000L || desc.timestamp == pastedClipAt) return null
        if (!desc.hasMimeType("text/*")) return null
        val clip = cm.primaryClip ?: return null
        val t = clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (t.isEmpty()) return null
        val sensitive = android.os.Build.VERSION.SDK_INT >= 33 && desc.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE) == true
        Triple(t, sensitive, desc.timestamp)
    }.getOrNull()

    /** 위 줄 다시 그리기: 인증번호가 있으면 인증번호, 없으면 방금 복사한 글 */
    private val topBarTimer = Runnable { refreshTopBar() }

    private fun refreshTopBar() {
        val v = view ?: return
        v.removeCallbacks(topBarTimer)
        val now = System.currentTimeMillis()
        val o = OtpStore.fresh()
        v.otp = o?.code
        val clip = clipText()
        v.clip = clip?.let { (t, sensitive) -> if (sensitive || secretField) "●●●●●● 붙여넣기" else t.take(60) }
        // 인증번호는 5분, 복사한 글은 1분이 지나면 저절로 사라지게
        val until = listOfNotNull(o?.let { it.at + OtpStore.VALID_MS }, clip?.let { it.third + CLIP_SHOW_MS }).minOrNull()
        if (until != null) v.postDelayed(topBarTimer, (until - now).coerceAtLeast(500))
    }

    /** 스페이스바를 옆으로 밀면 한/영 바꾸기 */
    override fun onSpaceSwipe(dir: Int) {
        engine.onLang()
        FitKeyStore.saveLang(this, engine.lang)
    }

    /** 추천 막대 자리의 도구 단추 */
    override fun onToolbar(action: String) {
        when (action) {
            "otp" -> OtpStore.fresh()?.let { o -> engine.onText(o.code); OtpStore.consume() }
            "clip" -> clipText()?.let { (t, _, at) -> engine.onText(t); pastedClipAt = at; refreshTopBar() }
            "settings" -> startActivity(
                android.content.Intent(this, io.github.poohcjs.fitkey.ui.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 손잡이로 크기·위치를 바꿈: '확인'을 누르면 저장 */
    override fun onAdjust(height: Float, width: Float, align: Float, gap: Int, done: Boolean) {
        if (done) FitKeyStore.saveSettings(this, FitKeyStore.settings(this).copy(height = height, width = width, align = align, bottomGap = gap))
    }

    override fun onLangLongPress() {
        // 다른 키보드(예: 삼성 키보드)로 바로 바꿀 수 있게
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    companion object {
        /** 설정의 크기·위치를 키보드 화면에 (설정 앱의 미리보기도 같은 함수를 쓴다) */
        fun applyLook(v: KeyboardView, s: KeySettings) {
            if (v.adjusting) return   // 손잡이로 조절하는 동안에는 화면 값이 앞선다
            v.heightScale = s.height
            v.widthScale = s.width
            v.align = s.align
            v.bottomGap = s.bottomGap
            v.vibrate = s.vibrate
            v.vibeLevel = s.vibeLevel
            v.sound = s.sound
            v.theme = s.theme
            v.preview = s.preview
            v.hints = s.hints
            v.longPressMs = s.longPressMs
            v.spaceSwipeLang = s.spaceSwipeLang
            v.showStrip = true
        }

        /** 복사한 글을 위 줄에 보여 주는 시간 */
        private const val CLIP_SHOW_MS = 60_000L
        private val SYM1 = KeyboardLayout.symbols(1)
        private val SYM2 = KeyboardLayout.symbols(2)
    }
}
