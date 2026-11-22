package io.github.poohcjs.fitkey.engine

/** 글을 넣을 곳 (실제 앱에서는 InputConnection, 시험에서는 가짜) */
interface TextSink {
    /** 조합 중인 글(밑줄)을 이 글로 바꾼다 */
    fun setComposing(text: String)
    /** 조합 중인 글을 지우고 이 글을 확정해 넣는다 */
    fun commit(text: String)
    /** 조합 중인 글을 그대로 확정 */
    fun finishComposing()
    /** 커서 앞 글자 n개 지우기 (조합 중이 아닐 때) */
    fun deleteBefore(n: Int)
    /** 지우기 키 그대로 보내기 (선택한 글·이모지도 앱이 알아서 지운다) */
    fun sendBackspace()
    fun textBefore(n: Int): String?
    /** 엔터: 칸의 동작(검색·보내기 등)이 있으면 그 동작, 없으면 줄바꿈 */
    fun enter()
}

enum class ShiftState { OFF, ONCE, LOCK }

/** 이 입력칸에서 무엇을 해도 되는지 */
data class FieldInfo(
    /** 비밀번호 칸: 추천·고치기·배우기 모두 하지 않는다 */
    val secret: Boolean = false,
    /** 자동 고치기를 해도 되는 칸 (이메일·주소 칸은 아님) */
    val autoCorrect: Boolean = true,
    /** 문장 첫 글자를 대문자로 (영어) */
    val autoCap: Boolean = false,
    /** 숫자 칸 */
    val numeric: Boolean = false,
    /** 앱이 '배우지 말라'고 한 칸 (시크릿 모드 등): 배우지도, 배운 것을 보여 주지도 않는다 */
    val incognito: Boolean = false,
)

data class Suggestion(val word: String, val key: String?, val isTyped: Boolean)

data class SuggestionBar(
    val items: List<Suggestion> = emptyList(),
    /** 스페이스를 누르면 들어갈 항목 */
    val autoIndex: Int? = null,
    /** 띄어 쓴 뒤 '다음에 올 말' 예측 (내가 이어 쓰던 말에서 배운 것) */
    val predicted: Boolean = false,
)

/** 사용자 설정 중 입력에 영향을 주는 것 */
data class EngineSettings(val autoCorrect: Boolean = true, val learn: Boolean = true, val suggestions: Boolean = true)

/**
 * 키보드의 두뇌: 글쇠 누름을 받아 글을 만들고, 오타를 고치고, 손 습관을 배운다.
 * 화면·안드로이드와 떨어져 있어 단위 시험으로 확인할 수 있다.
 */
class InputEngine(
    private val sink: TextSink,
    val touch: TouchModel,
    val userWords: UserWords,
    private val dicts: (Lang) -> Dictionary?,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** 내가 이어 쓰는 낱말 순서 (다음 낱말 예측) */
    val bigrams: UserBigrams = UserBigrams(),
) {
    var lang = Lang.KO
        private set
    var mode = Mode.LETTERS
        private set
    var shift = ShiftState.OFF
        private set
    var settings = EngineSettings()
    var field = FieldInfo()
        private set
    var layout: KeyboardLayout = KeyboardLayout.letters(true)
    var onSuggestions: (SuggestionBar) -> Unit = {}
    var onStateChanged: () -> Unit = {}

    private val decoder = Decoder(touch)
    private val word = ArrayList<Stroke>()
    private var composing = ""
    private var bar = SuggestionBar()
    /** 방금 자동으로 고친 말 (바로 지우기를 누르면 되돌린다) */
    private var lastCorrection: Correction? = null
    private var lastCorrectionSuffix = ""
    /** 지우기로 지운 마지막 글쇠: 곧바로 다른 글쇠를 누르면 '사실은 그 글쇠였다'고 배운다 */
    private var deleted: Pair<Stroke, Long>? = null
    /** 낱말을 확정한 뒤 배울 것 (자동 고친 말은 되돌리지 않았을 때만 배운다) */
    private var pendingLearn: (() -> Unit)? = null
    private var lastShiftTap = 0L
    /** 지금 치는 낱말 바로 앞 낱말의 글쇠 (그 낱말 뒤에 한 칸 띄우고 치기 시작했을 때) */
    private var context: String? = null

    val currentText: String get() = composing
    val suggestions: SuggestionBar get() = bar
    private val canLearn get() = settings.learn && !field.secret && !field.incognito
    private val canCorrect get() = settings.autoCorrect && field.autoCorrect && !field.secret

    fun startInput(info: FieldInfo, keepLang: Boolean = true, startLang: Lang? = null) {
        resetWord()
        field = info
        if (!keepLang && startLang != null) lang = startLang
        mode = if (info.numeric) Mode.NUMPAD else Mode.LETTERS
        shift = ShiftState.OFF
        lastCorrection = null
        pendingLearn = null
        refreshAutoCap()
        updateSuggestions()
        onStateChanged()
    }

    /** 커서가 옮겨졌거나 칸이 바뀌어 조합을 끝내야 할 때 */
    fun resetWord(finish: Boolean = true) {
        if (finish && composing.isNotEmpty()) sink.finishComposing()
        word.clear()
        composing = ""
        deleted = null
        updateSuggestions()
    }

    // ── 글자 ──────────────────────────────────────────────
    fun onLetter(key: Key, ux: Float, uy: Float) {
        val l = key.letter ?: return
        lastCorrection = null
        runPendingLearn()
        // 방금 지운 글쇠 대신 옆 글쇠를 눌렀다 → 지운 터치는 사실 이 글쇠를 노린 것
        // (그 터치가 새 글쇠 바로 옆이었을 때만: 지우고 전혀 다른 말을 이어 치는 경우는 배우지 않는다)
        deleted?.let { (d, at) ->
            val dx = d.ux - key.cx
            val dy = (d.uy - key.cy) * touch.aspect
            if (canLearn && clock() - at < 2500 && d.key.letter != l && dx * dx + dy * dy < 0.85f * 0.85f) touch.learn(key, d.ux, d.uy, 2f)
        }
        deleted = null
        if (word.isEmpty()) context = contextKey()
        val shifted = shift != ShiftState.OFF
        val stroke = when (lang) {
            Lang.KO -> {
                val kc = if (shifted && l in Hangul.SHIFTABLE) l.uppercaseChar() else l
                Stroke(key, kc, Hangul.jamoOf(kc)!!, ux, uy, shifted)
            }
            Lang.EN -> Stroke(key, l, if (shifted) l.uppercaseChar() else l, ux, uy, shifted)
        }
        word += stroke
        if (shift == ShiftState.ONCE) shift = ShiftState.OFF
        render()
        onStateChanged()
    }

    private fun render() {
        composing = when (lang) {
            Lang.KO -> Hangul.compose(word.map { it.output })
            Lang.EN -> buildString { word.forEach { append(it.output) } }
        }
        sink.setComposing(composing)
        updateSuggestions()
    }

    // ── 지우기 ────────────────────────────────────────────
    fun onBackspace() {
        runPendingLearnIfNotUndo()
        val c = lastCorrection
        if (c != null && word.isEmpty()) {
            lastCorrection = null
            val committed = c.to + lastCorrectionSuffix
            if (sink.textBefore(committed.length) == committed) {
                // 자동으로 고친 말을 바로 지우면 원래 친 말로 되돌리고, 그 말은 다음부터 고치지 않는다
                sink.deleteBefore(committed.length)
                sink.commit(c.from)
                if (canLearn) Decoder.keysOf(c.strokes).let { userWords.add(lang, it, 2) }
                pendingLearn = null
                updateSuggestions()
                return
            }
        }
        lastCorrection = null
        if (word.isNotEmpty()) {
            deleted = word.removeAt(word.lastIndex) to clock()
            if (word.isEmpty()) {
                composing = ""
                sink.setComposing("")
                sink.finishComposing()
                updateSuggestions()
            } else render()
            return
        }
        deleted = null
        sink.sendBackspace()
        // 앞 글을 지우면 예측은 거둔다 (지운 뒤 커서가 자리 잡으면 onSelectionChanged가 다시 계산)
        if (bar.items.isNotEmpty()) { bar = SuggestionBar(); onSuggestions(bar) }
        refreshAutoCap()
        onStateChanged()
    }

    // ── 스페이스·문장부호·엔터 ─────────────────────────────
    fun onSpace() {
        // 기호 화면은 띄어 쓰면 글자로 돌아가고, 숫자 패드는 그대로 둔다
        if (mode == Mode.SYMBOLS || mode == Mode.SYMBOLS2) { mode = Mode.LETTERS; onStateChanged() }
        finishWord(" ", correct = true)
        refreshAutoCap()
        onStateChanged()
    }

    /** 숫자·기호·쉼표·마침표 */
    fun onText(text: String) {
        val sentenceEnd = text in listOf(".", ",", "?", "!")
        finishWord(text, correct = sentenceEnd)
        refreshAutoCap()
        onStateChanged()
    }

    fun onEnter() {
        finishWord("", correct = false)
        sink.enter()
        // 새 줄은 새 문장: 예측을 거둔다
        if (bar.items.isNotEmpty()) { bar = SuggestionBar(); onSuggestions(bar) }
        refreshAutoCap()
        onStateChanged()
    }

    /** 추천 낱말을 누름: 그 낱말로 바꾸고 띄어 쓴다 */
    fun onSuggestion(index: Int) {
        val s = bar.items.getOrNull(index) ?: return
        val strokes = word.toList()
        if (strokes.isEmpty()) {
            // 띄어 쓴 뒤 '다음에 올 말'을 누름
            if (!bar.predicted) return
            val ctx = contextKey()
            runPendingLearn()
            sink.commit(s.word + " ")
            if (canLearn) s.key?.let { k -> userWords.add(lang, k); ctx?.let { bigrams.add(lang, it, k) } }
            lastCorrection = null; deleted = null
            refreshAutoCap()
            updateSuggestions()
            onStateChanged()
            return
        }
        val ctx = context
        runPendingLearn()
        sink.commit(s.word + " ")
        if (canLearn) {
            val k = s.key ?: Decoder.keysOf(strokes)
            userWords.add(lang, k, if (s.isTyped) 2 else 1)
            learnWord(strokes, k, 1.2f)
            ctx?.let { bigrams.add(lang, it, k) }
        }
        word.clear(); composing = ""; lastCorrection = null; deleted = null
        refreshAutoCap()
        updateSuggestions()
        onStateChanged()
    }

    private fun finishWord(suffix: String, correct: Boolean) {
        val strokes = word.toList()
        val typed = composing
        val ctx = context
        lastCorrection = null
        runPendingLearn()
        if (strokes.isEmpty()) {
            if (suffix.isNotEmpty()) { sink.commit(suffix); updateSuggestions() }
            return
        }
        val fix = if (correct && canCorrect) decoder.autoCorrect(strokes, typed, lang, dicts(lang), userWords, layout) else null
        if (fix != null) {
            sink.commit(fix.to + suffix)
            lastCorrection = fix
            lastCorrectionSuffix = suffix
            // 고친 대로 두면(다음 글자를 치면) 그때 배운다
            pendingLearn = {
                if (canLearn) {
                    learnWord(strokes, fix.toKey, 1.5f)
                    userWords.add(lang, fix.toKey)
                    ctx?.let { bigrams.add(lang, it, fix.toKey) }
                }
            }
        } else {
            sink.commit(typed + suffix)
            if (canLearn) {
                val key = Decoder.keysOf(strokes)
                val dict = dicts(lang)
                val known = dict?.count(key)?.let { it > 0 } == true || userWords.count(lang, key) > 0
                userWords.add(lang, key)
                ctx?.let { bigrams.add(lang, it, key) }
                // 아는 말이면 각 터치가 그 글쇠였다고 조금 배운다
                if (known) learnWord(strokes, key, 0.3f)
            }
        }
        word.clear(); composing = ""; deleted = null
        updateSuggestions()
    }

    /** 낱말의 각 터치를 그 낱말의 글쇠로 배운다 (글자 수가 같을 때만) */
    private fun learnWord(strokes: List<Stroke>, key: String, weight: Float) {
        if (key.length != strokes.size) return
        strokes.forEachIndexed { i, s ->
            val k = layout.keyOf(key[i]) ?: return@forEachIndexed
            touch.learn(k, s.ux, s.uy, if (k.letter == s.key.letter) weight * 0.5f else weight)
        }
    }

    private fun runPendingLearn() { pendingLearn?.invoke(); pendingLearn = null }

    /** 지우기를 눌렀을 때: 자동 고침을 되돌리는 경우가 아니면 배운다 */
    private fun runPendingLearnIfNotUndo() {
        if (lastCorrection == null || word.isNotEmpty()) runPendingLearn()
    }

    // ── 기능 글쇠 ──────────────────────────────────────────
    fun onShift() {
        val now = clock()
        shift = when {
            mode != Mode.LETTERS -> shift
            shift == ShiftState.ONCE && now - lastShiftTap < 400 -> ShiftState.LOCK
            shift == ShiftState.OFF -> ShiftState.ONCE
            else -> ShiftState.OFF
        }
        if (mode == Mode.SYMBOLS) mode = Mode.SYMBOLS2 else if (mode == Mode.SYMBOLS2) mode = Mode.SYMBOLS
        lastShiftTap = now
        onStateChanged()
    }

    fun onLang() {
        finishWord("", correct = false)
        lang = if (lang == Lang.KO) Lang.EN else Lang.KO
        mode = Mode.LETTERS
        shift = ShiftState.OFF
        refreshAutoCap()
        updateSuggestions()
        onStateChanged()
    }

    /** 숫자 패드 열고 닫기 */
    fun onNumpad() {
        finishWord("", correct = false)
        mode = if (mode == Mode.NUMPAD) Mode.LETTERS else Mode.NUMPAD
        onStateChanged()
    }

    fun onMode() {
        finishWord("", correct = false)
        mode = if (mode == Mode.LETTERS) Mode.SYMBOLS else Mode.LETTERS
        onStateChanged()
    }

    /** 영어 문장 첫 글자 대문자 */
    private fun refreshAutoCap() {
        if (lang != Lang.EN || !field.autoCap || shift == ShiftState.LOCK || field.secret) return
        val before = sink.textBefore(3) ?: return
        val start = before.isEmpty() || before.trimEnd().let { it.isEmpty() || it.last() in ".!?\n" } && before.lastOrNull()?.isWhitespace() != false
        shift = if (start) ShiftState.ONCE else if (shift == ShiftState.ONCE && word.isEmpty()) ShiftState.OFF else shift
    }

    /** 커서·선택이 바뀜 (사용자가 옮겼거나 앱이 글을 바꿈): 낱말을 치는 중이 아니면 예측을 다시 */
    fun onSelectionChanged(hasSelection: Boolean) {
        if (word.isNotEmpty()) return
        if (hasSelection) {
            if (bar.items.isNotEmpty()) { bar = SuggestionBar(); onSuggestions(bar) }
        } else {
            val old = bar
            updateSuggestions(notifyIfSame = false, old = old)
        }
    }

    // ── 다음 낱말 예측 ─────────────────────────────────────
    /**
     * 커서 바로 앞 낱말의 글쇠: 그 낱말 뒤에 한 칸만 띄웠을 때만 (문장부호·줄바꿈 뒤는 새 문장으로 본다).
     * 입력칸의 글을 읽으므로, 커서를 옮겼거나 자동으로 고친 뒤에도 실제 앞 낱말을 쓴다.
     */
    private fun contextKey(): String? {
        if (field.secret) return null
        val n = 40
        val before = sink.textBefore(n) ?: return null
        if (before.length < 2 || before.last() != ' ') return null
        val body = before.dropLast(1)
        val w = body.takeLastWhile { it.isLetter() || it == '\'' || it == '’' }
        if (w.isEmpty() || w.length > 30) return null
        val start = body.length - w.length
        if (start == 0 && before.length >= n) return null   // 낱말이 잘렸을 수 있다
        if (start > 0 && !body[start - 1].isWhitespace() && body[start - 1] !in "\"“‘(['") return null
        return when (lang) {
            Lang.KO -> Hangul.toKeys(w)?.takeIf { it.isNotEmpty() }
            Lang.EN -> w.filter { it != '\'' && it != '’' }.lowercase().takeIf { k -> k.isNotEmpty() && k.all { it in 'a'..'z' } }
        }
    }

    private fun wordOf(key: String): String = dicts(lang)?.wordOf(key) ?: when (lang) {
        Lang.KO -> Hangul.composeKeys(key)
        Lang.EN -> Dictionary.CONTRACTIONS[key] ?: key
    }

    /** 띄어 쓴 뒤: 앞 낱말 다음에 내가 자주 쓴 말 (두 번 넘게 쓴 것만) */
    private fun predictions(): SuggestionBar {
        if (!settings.suggestions || field.secret || field.incognito) return SuggestionBar()
        val ctx = contextKey() ?: return SuggestionBar()
        val items = bigrams.next(lang, ctx, max = 3).map { Suggestion(wordOf(it.first), it.first, false) }.distinctBy { it.word }
        if (items.isEmpty()) return SuggestionBar()
        // 가장 자주 쓴 말을 가운데에
        val ordered = if (items.size == 3) listOf(items[1], items[0], items[2]) else items
        return SuggestionBar(ordered, null, predicted = true)
    }

    // ── 추천 낱말 ──────────────────────────────────────────
    private fun updateSuggestions(notifyIfSame: Boolean = true, old: SuggestionBar? = null) {
        val dict = dicts(lang)
        if (word.isEmpty()) {
            bar = predictions()
            if (notifyIfSame || bar != old) onSuggestions(bar)
            return
        }
        if (!settings.suggestions || field.secret || dict == null) {
            bar = SuggestionBar(); onSuggestions(bar); return
        }
        val key = Decoder.keysOf(word)
        val typed = composing
        val items = ArrayList<Suggestion>()
        fun add(s: Suggestion) { if (items.none { it.word == s.word } && items.size < 3) items += s }

        val fix = if (canCorrect && word.size >= 2) decoder.autoCorrect(word, typed, lang, dict, userWords, layout) else null
        val corrections = decoder.candidates(word, lang, dict, userWords, layout).filter { it.key != key }.take(2)
        // 앞 낱말 다음에 내가 쓰던 말이 지금 친 글쇠로 시작하면 먼저 (한 번 쓴 것도)
        val following = if (field.incognito) emptyList() else context?.let { bigrams.next(lang, it, prefix = key, max = 2, min = 1) }.orEmpty()
        val completions = (following + userWords.completions(lang, key, 2) + dict.completions(key, 4))
            .filter { it.first != key }.distinctBy { it.first }
        // 가운데: 스페이스를 누르면 들어갈 말 (고칠 말이 있으면 그 말, 아니면 친 그대로)
        val center = fix?.let { Suggestion(it.to, it.toKey, false) } ?: Suggestion(typed, key, true)
        val left = if (fix != null) Suggestion(typed, key, true) else null
        val others = following.filter { it.first != key }.map { Suggestion(Decoder.matchCase(dict.wordOf(it.first), typed, lang), it.first, false) } +
            corrections.map { Suggestion(Decoder.matchCase(it.word, typed, lang), it.key, false) } +
            completions.map { Suggestion(Decoder.matchCase(dict.wordOf(it.first), typed, lang), it.first, false) }
        val list = ArrayList<Suggestion>()
        left?.let { list += it }
        list += center
        others.forEach { o -> if (list.none { it.word == o.word }) list += o }
        list.forEach { add(it) }
        // 가운데 칸에 오도록 순서를 맞춘다: [다른 후보, 가운데, 다른 후보]
        val ordered = if (items.size == 3) {
            val c = items.indexOfFirst { it.word == center.word }
            val rest = items.filterIndexed { i, _ -> i != c }
            listOf(rest[0], items[c], rest[1])
        } else items
        bar = SuggestionBar(ordered, ordered.indexOfFirst { it.word == center.word }.takeIf { it >= 0 })
        onSuggestions(bar)
    }
}
