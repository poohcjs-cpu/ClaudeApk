package io.github.poohcjs.hotdealradar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** 사용자 설정. 웹앱(localStorage)과 같은 항목을 기기에 저장한다. */
@Serializable
data class Settings(
    val threshold: Int = 30,                     // 핫딜 기준 할인율(%) 5~70
    val interests: List<String> = emptyList(),   // 관심 제품군
    val interestOnly: Boolean = true,            // 관심 제품군만 핫딜 탭에 보이기
    val excludedMalls: List<String> = emptyList(),
    val hiddenSources: List<String> = emptyList(),
    val hideSuspicious: Boolean = true,          // '가격 확인 필요'(평균보다 60% 넘게 쌈) 숨기기
    val hideEnded: Boolean = true,
    val notify: Boolean = true,                  // 새 핫딜 알림
    val notifyThreshold: Int = 30,               // 알림 기준(%)
    val notifyInterestsOnly: Boolean = true,
    val notifyTarget: Boolean = true,            // 찜 목표가 도달 알림
    val dataUrl: String = "",                    // 비우면 기본 주소
) {
    fun clamp() = copy(threshold = threshold.coerceIn(5, 70), notifyThreshold = notifyThreshold.coerceIn(5, 70))
}

/** 찜: 제품(pcode) 단위. 목표가를 정하면 진행 중인 딜·다나와 최저가 중 싼 값으로 추적한다. */
@Serializable
data class Fav(
    val savedPrice: Long? = null,
    val savedAt: Long = 0,
    val target: Long? = null,
    val name: String = "",
    val mall: String = "",
    val cat: String = "etc",
    val image: String = "",
)

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("hotdeal", Context.MODE_PRIVATE)
    private val favSerializer = MapSerializer(String.serializer(), Fav.serializer())

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _favs = MutableStateFlow(readFavs())
    val favs: StateFlow<Map<String, Fav>> = _favs.asStateFlow()

    private fun readSettings(): Settings =
        sp.getString(K_SETTINGS, null)?.let { runCatching { JSON.decodeFromString(Settings.serializer(), it) }.getOrNull() }?.clamp()
            ?: Settings()

    private fun readFavs(): Map<String, Fav> =
        sp.getString(K_FAVS, null)?.let { runCatching { JSON.decodeFromString(favSerializer, it) }.getOrNull() } ?: emptyMap()

    fun updateSettings(change: (Settings) -> Settings) {
        val next = change(_settings.value).clamp()
        _settings.value = next
        sp.edit { putString(K_SETTINGS, JSON.encodeToString(Settings.serializer(), next)) }
    }

    fun updateFavs(change: (Map<String, Fav>) -> Map<String, Fav>) {
        val next = change(_favs.value)
        _favs.value = next
        sp.edit { putString(K_FAVS, JSON.encodeToString(favSerializer, next)) }
    }

    /** 앱을 마지막으로 떠난 시각 — 이후 발견된 딜에 NEW 표시 */
    var lastVisit: Long
        get() = sp.getLong(K_VISIT, 0L)
        set(v) = sp.edit { putLong(K_VISIT, v) }

    /** 이미 알림을 보낸 딜 id (최근 것만 유지) */
    var notifiedIds: Set<String>
        get() = sp.getStringSet(K_NOTIFIED, emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet(K_NOTIFIED, v.toList().takeLast(400).toSet()) }

    /** 목표가 알림을 보낸 제품 → 그때 가격 (같은 가격으로 반복 알림 방지) */
    var targetNotified: Map<String, Long>
        get() = sp.getString(K_TARGET_NOTIFIED, null)
            ?.let { runCatching { JSON.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), it) }.getOrNull() }
            ?: emptyMap()
        set(v) = sp.edit { putString(K_TARGET_NOTIFIED, JSON.encodeToString(MapSerializer(String.serializer(), Long.serializer()), v)) }

    var recentSearches: List<String>
        get() = sp.getString(K_RECENT, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit { putString(K_RECENT, v.take(8).joinToString("\n")) }

    companion object {
        private const val K_SETTINGS = "settings"
        private const val K_FAVS = "favs"
        private const val K_VISIT = "lastVisit"
        private const val K_NOTIFIED = "notified"
        private const val K_TARGET_NOTIFIED = "targetNotified"
        private const val K_RECENT = "recentSearches"
    }
}
