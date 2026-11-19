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

    override fun onCreate() {
        super.onCreate()
        FitKeyStore.init(this)
        engine = InputEngine(sink, FitKeyStore.touch, FitKeyStore.userWords, { FitKeyStore.dict(it) })
        engine.onSuggestions = { bar -> view?.suggestions = bar }
        engine.onStateChanged = { syncView() }
        FitKeyStore.addListener(settingsListener)
        applySettings()
    }

    override fun onDestroy() {
        FitKeyStore.removeListener(settingsListener)
        FitKeyStore.save()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
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
        if (::engine.isInitialized) engine.settings = EngineSettings(settings.autoCorrect, settings.learn, settings.suggestions)
        // 숫자 줄 설정이 바뀔 때만 새로 만든다 (글쇠 판정은 같은 배열 객체인지로 구분한다)
        val wantRows = if (settings.numberRow) 5 else 4
        if (letters.rows != wantRows) letters = KeyboardLayout.letters(settings.numberRow)
        if (::engine.isInitialized) engine.layout = letters
        view?.let {
            it.heightScale = settings.height
            it.vibrate = settings.vibrate
            it.showStrip = true
        }
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
        )
        // 비밀번호·주소 칸은 영어로 시작, 나머지는 마지막에 쓰던 언어
        val startLang = if (secret || addressLike) Lang.EN else FitKeyStore.lastLang(this)
        engine.startInput(field, keepLang = false, startLang = startLang)
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            engine.settings = engine.settings.copy(learn = false)
        }
        view?.enterLabel = enterLabel(info)
        view?.stripHint = if (secret) "🔒 비밀번호 칸: 입력을 배우거나 기억하지 않아요" else ""
        syncView()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
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

    override fun onLangLongPress() {
        // 다른 키보드(예: 삼성 키보드)로 바로 바꿀 수 있게
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    companion object {
        private val SYM1 = KeyboardLayout.symbols(1)
        private val SYM2 = KeyboardLayout.symbols(2)
    }
}
