package io.github.poohcjs.fitkey.ime

import android.content.Context
import android.content.SharedPreferences
import io.github.poohcjs.fitkey.engine.Dictionary
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.TouchModel
import io.github.poohcjs.fitkey.engine.UserWords
import java.io.File
import java.util.zip.GZIPInputStream

/** 키보드 설정 */
data class KeySettings(
    /** 키보드 높이 배율 (1.0 = 기본) */
    val height: Float = 1.0f,
    val numberRow: Boolean = true,
    val vibrate: Boolean = true,
    val autoCorrect: Boolean = true,
    val suggestions: Boolean = true,
    val learn: Boolean = true,
    /** 글쇠 부분 너비 (1 = 전체, 0.75 정도 = 한 손 모드) */
    val width: Float = 1.0f,
    /** 좌우 위치 (-1 왼쪽 ~ 0 가운데 ~ 1 오른쪽) */
    val align: Float = 0f,
    /** 아래 여백 (dp): 키보드를 위로 올린다 */
    val bottomGap: Int = 0,
    /** 스페이스바를 옆으로 밀어 한/영 바꾸기 (끄면 밀어서 커서 이동) */
    val spaceSwipeLang: Boolean = true,
)

/**
 * 키보드(서비스)와 설정 앱(화면)이 함께 쓰는 저장소. 같은 앱 프로세스 안에서 하나만 있다.
 * 배운 터치 습관·내 낱말은 이 기기의 앱 전용 폴더에만 저장한다.
 */
object FitKeyStore {
    val touch = TouchModel()
    val userWords = UserWords()
    @Volatile private var ko: Dictionary? = null
    @Volatile private var en: Dictionary? = null
    private var app: Context? = null
    private var savedTouch = -1
    private var savedWords = -1
    private val listeners = mutableListOf<() -> Unit>()

    fun init(context: Context) {
        if (app != null) return
        val c = context.applicationContext
        app = c
        runCatching { touchFile(c).takeIf { it.exists() }?.readText()?.let { touch.load(it) } }
        runCatching { wordsFile(c).takeIf { it.exists() }?.readText()?.let { userWords.load(it) } }
        savedTouch = touch.version
        savedWords = userWords.version
        // 사전은 크니 따로 읽는다 (읽는 동안에도 입력은 된다: 추천·고치기만 잠시 없음)
        Thread {
            ko = load(c, Lang.KO)
            en = load(c, Lang.EN)
        }.apply { name = "fitkey-dict"; priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    private fun load(c: Context, lang: Lang): Dictionary? = runCatching {
        val name = if (lang == Lang.KO) "ko.tsv.gz" else "en.tsv.gz"
        c.assets.open(name).use { GZIPInputStream(it).bufferedReader().use { r -> Dictionary.read(lang, r) } }
    }.getOrNull()

    fun dict(lang: Lang): Dictionary? = if (lang == Lang.KO) ko else en

    private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("fitkey", Context.MODE_PRIVATE)

    fun settings(c: Context): KeySettings {
        val p = prefs(c)
        return KeySettings(
            height = p.getFloat("height", 1.0f), numberRow = p.getBoolean("numberRow", true), vibrate = p.getBoolean("vibrate", true),
            autoCorrect = p.getBoolean("autoCorrect", true), suggestions = p.getBoolean("suggestions", true), learn = p.getBoolean("learn", true),
            width = p.getFloat("width", 1.0f), align = p.getFloat("align", 0f), bottomGap = p.getInt("bottomGap", 0),
            spaceSwipeLang = p.getBoolean("spaceSwipeLang", true),
        )
    }

    fun saveSettings(c: Context, s: KeySettings) {
        // 키보드 크기가 바뀌면 배운 손 습관을 새 크기로 환산한다 (손가락이 빗나가는 실제 거리는 그대로라서)
        val old = settings(c)
        if (old.width != s.width || old.height != s.height) {
            touch.rescale(old.width / s.width, old.height / s.height)
            save()
        }
        prefs(c).edit().putFloat("height", s.height).putBoolean("numberRow", s.numberRow).putBoolean("vibrate", s.vibrate)
            .putBoolean("autoCorrect", s.autoCorrect).putBoolean("suggestions", s.suggestions).putBoolean("learn", s.learn)
            .putFloat("width", s.width).putFloat("align", s.align).putInt("bottomGap", s.bottomGap)
            .putBoolean("spaceSwipeLang", s.spaceSwipeLang).apply()
        listeners.toList().forEach { it() }
    }

    fun lastLang(c: Context): Lang = if (prefs(c).getString("lang", "KO") == "EN") Lang.EN else Lang.KO
    fun saveLang(c: Context, lang: Lang) = prefs(c).edit().putString("lang", lang.name).apply()

    /** 보정을 마친 횟수 (설정 화면에 보여 준다) */
    fun calibrations(c: Context): Int = prefs(c).getInt("calibrations", 0)
    fun markCalibrated(c: Context) = prefs(c).edit().putInt("calibrations", calibrations(c) + 1).apply()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /** 바뀐 것이 있을 때만 파일에 쓴다 */
    fun save() {
        val c = app ?: return
        if (touch.version != savedTouch) {
            runCatching { write(touchFile(c), touch.serialize()) }
            savedTouch = touch.version
        }
        if (userWords.version != savedWords) {
            runCatching { write(wordsFile(c), userWords.serialize()) }
            savedWords = userWords.version
        }
    }

    fun clearLearning() {
        touch.clear()
        userWords.clear()
        save()
        app?.let { prefs(it).edit().putInt("calibrations", 0).apply() }
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    private fun touchFile(c: Context) = File(c.filesDir, "touch.txt")
    private fun wordsFile(c: Context) = File(c.filesDir, "words.txt")
}
