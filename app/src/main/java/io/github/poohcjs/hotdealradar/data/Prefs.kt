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
    // 기본값 근거(2026-11 실제 수집 데이터): 다나와에서 가장 싼 판매처는 평소에도 나머지 평균보다 10% 안팎 싸다(중간값 10.3%).
    // 그래서 '평균보다 5%'는 평소 최저가 수준이라 거의 모든 제품이 걸린다. 평균보다 10% 이상 싸면서
    // 다른 어느 쇼핑몰보다도 싼 딜만 핫딜로 본다.
    val schema: Int = 0,                         // 설정 형식 버전 (옛 기본값을 새 기본값으로 옮길 때 사용)
    val threshold: Int = 10,                     // 핫딜 기준 할인율(%) 5~70, 쇼핑몰 평균가 대비
    val interests: List<String> = emptyList(),   // 관심 제품군
    val interestOnly: Boolean = true,            // 관심 제품군만 핫딜 탭에 보이기
    val excludedMalls: List<String> = emptyList(),
    val hiddenSources: List<String> = emptyList(),
    val hideSuspicious: Boolean = true,          // '가격 확인 필요'(평균보다 60% 넘게 쌈) 숨기기
    val hideEnded: Boolean = true,
    val onlyCheapest: Boolean = true,            // 다른 쇼핑몰이 같거나 더 싸게 파는 딜은 핫딜로 치지 않기
    val notify: Boolean = true,                  // 새 핫딜 알림
    val notifyThreshold: Int = 15,               // 알림 기준(%) — 화면 기준보다 조금 높여 정말 좋은 딜만 알림
    val notifyInterestsOnly: Boolean = true,
    val notifyTarget: Boolean = true,            // 찜 목표가 도달 알림
    val dataUrl: String = "",                    // 비우면 기본 주소
    val liveWithShipping: Boolean = true,        // 실시간 검색: 배송비 포함 가격으로 비교
    val keywords: List<String> = emptyList(),    // 관심 키워드: 이 말이 들어간 새 커뮤니티 딜이 올라오면 알림
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
    // 앱이 직접 확인한 다나와 가격 (수집기가 지켜보지 않는 제품도 추적하려고)
    val lastPrice: Long? = null,
    val lastMall: String = "",
    val checkedAt: Long = 0,
    val lowestSeen: Long? = null,
) {
    fun withPrice(price: Long, mall: String, at: Long = System.currentTimeMillis()) =
        copy(lastPrice = price, lastMall = mall, checkedAt = at, lowestSeen = minOf(lowestSeen ?: price, price))
}

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("hotdeal", Context.MODE_PRIVATE)
    private val favSerializer = MapSerializer(String.serializer(), Fav.serializer())

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _favs = MutableStateFlow(readFavs())
    val favs: StateFlow<Map<String, Fav>> = _favs.asStateFlow()

    private fun readSettings(): Settings {
        val stored = sp.getString(K_SETTINGS, null)
            ?.let { runCatching { JSON.decodeFromString(Settings.serializer(), it) }.getOrNull() }
            ?: return Settings(schema = SCHEMA)
        if (stored.schema >= SCHEMA) return stored.clamp()
        // 첫 버전의 기본값(30%)을 그대로 쓰던 사람은 새 기본값으로 옮긴다. 직접 바꾼 값은 그대로 둔다.
        val migrated = stored.copy(
            schema = SCHEMA,
            threshold = if (stored.threshold == 30) 10 else stored.threshold,
            notifyThreshold = if (stored.notifyThreshold == 30) 15 else stored.notifyThreshold,
        ).clamp()
        sp.edit { putString(K_SETTINGS, JSON.encodeToString(Settings.serializer(), migrated)) }
        return migrated
    }

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
        const val SCHEMA = 2
        private const val K_SETTINGS = "settings"
        private const val K_FAVS = "favs"
        private const val K_VISIT = "lastVisit"
        private const val K_NOTIFIED = "notified"
        private const val K_TARGET_NOTIFIED = "targetNotified"
        private const val K_RECENT = "recentSearches"
    }
}
