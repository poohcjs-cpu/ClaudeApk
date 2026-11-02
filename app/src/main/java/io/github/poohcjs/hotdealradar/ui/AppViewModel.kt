package io.github.poohcjs.hotdealradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.poohcjs.hotdealradar.HotdealApp
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.LiveBlocked
import io.github.poohcjs.hotdealradar.data.LivePage
import io.github.poohcjs.hotdealradar.data.LiveProduct
import io.github.poohcjs.hotdealradar.data.LiveSearchClient
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.work.DealCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<LiveProduct> = emptyList(),
    val error: String? = null,
    val searchedAt: Long? = null,
    /** 2~3쪽을 이어서 읽는 중 */
    val loadingMore: Boolean = false,
)

data class LivePageState(val loading: Boolean = true, val page: LivePage? = null, val error: String? = null)

data class LoadState(
    val index: Index? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fromCache: Boolean = false,
    val fetchedAt: Long? = null,
)

private const val MAX_PAGES = 3

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val hot = app as HotdealApp
    private val prefs = hot.prefs
    private val repo = hot.repository

    private val _state = MutableStateFlow(LoadState(loading = true))
    val state: StateFlow<LoadState> = _state.asStateFlow()
    val settings: StateFlow<Settings> = prefs.settings
    val favs: StateFlow<Map<String, Fav>> = prefs.favs

    /** 이 시각 이후 발견된 딜에 NEW. 처음 쓰면 최근 3시간 */
    val newCutoff: Long = prefs.lastVisit.takeIf { it > 0 } ?: (System.currentTimeMillis() - 3 * 3_600_000L)
    val firstVisit: Boolean = prefs.lastVisit == 0L

    init {
        viewModelScope.launch {
            repo.loadCached()?.let { cached -> _state.update { it.copy(index = Index(cached), fromCache = true) } }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.fetch() }
                .onSuccess { data ->
                    _state.value = LoadState(index = Index(data), fetchedAt = System.currentTimeMillis())
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "불러오지 못했어요") }
                }
        }
    }

    // ── 실시간 최저가 검색 (다나와) ─────────────────────────────
    private val live = LiveSearchClient()
    private val _liveSearch = MutableStateFlow(LiveSearchState())
    val liveSearch: StateFlow<LiveSearchState> = _liveSearch.asStateFlow()
    private val _livePages = MutableStateFlow<Map<String, LivePageState>>(emptyMap())
    val livePages: StateFlow<Map<String, LivePageState>> = _livePages.asStateFlow()
    /** 검색 결과에서 연 상품 (상세 화면 머리에 바로 보여 줄 정보) */
    val liveProducts = HashMap<String, LiveProduct>()

    private var searchJob: kotlinx.coroutines.Job? = null

    /** 1쪽을 먼저 보여 주고, 2~3쪽(최대 120개 안팎)을 이어서 붙인다 */
    fun searchLive(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        searchJob?.cancel()
        _liveSearch.value = LiveSearchState(query = q, loading = true)
        searchJob = viewModelScope.launch {
            val first = runCatching { live.search(q, 1) }.getOrElse { e ->
                _liveSearch.value = LiveSearchState(q, false, emptyList(), liveError(e))
                return@launch
            }
            rememberProducts(first)
            var all = first
            _liveSearch.value = LiveSearchState(q, false, all, null, System.currentTimeMillis(), loadingMore = first.size >= 30)
            if (first.size < 30) return@launch
            for (page in 2..MAX_PAGES) {
                val more = runCatching { live.search(q, page) }.getOrNull().orEmpty()
                val known = all.map { it.pcode }.toHashSet()
                val fresh = more.filter { it.pcode !in known }.mapIndexed { i, p -> p.copy(rank = all.size + i) }
                if (fresh.isEmpty()) break
                rememberProducts(fresh)
                all = all + fresh
                _liveSearch.value = _liveSearch.value.copy(results = all, loadingMore = page < MAX_PAGES && more.size >= 30)
                if (more.size < 30) break
            }
            _liveSearch.value = _liveSearch.value.copy(loadingMore = false)
        }
    }

    private fun rememberProducts(list: List<LiveProduct>) =
        list.forEach { p -> liveProducts[p.pcode] = p; p.options.forEach { o -> liveProducts.putIfAbsent(o.pcode, p) } }

    fun loadLivePage(pcode: String, force: Boolean = false) {
        val cur = _livePages.value[pcode]
        if (!force && cur != null && (cur.loading || cur.page != null)) return
        _livePages.value = _livePages.value + (pcode to LivePageState(loading = true, page = cur?.page))
        viewModelScope.launch {
            runCatching { live.product(pcode) }
                .onSuccess { page ->
                    _livePages.value = _livePages.value + (pcode to LivePageState(false, page, null))
                    // 찜한 제품이면 방금 본 가격을 기록해 둔다 (찜 탭·목표가 알림에 쓰임)
                    page.offers.minByOrNull { it.price }?.let { best ->
                        prefs.updateFavs { favs -> favs[pcode]?.let { favs + (pcode to it.withPrice(best.price, best.mall)) } ?: favs }
                    }
                }
                .onFailure { e -> _livePages.value = _livePages.value + (pcode to LivePageState(false, cur?.page, liveError(e))) }
        }
    }

    private fun liveError(e: Throwable): String {
        val why = when (e) {
            is LiveBlocked -> "다나와가 지금은 앱의 요청을 받지 않아요."
            is java.io.IOException -> "다나와에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."
            else -> "다나와 화면을 읽지 못했어요."
        }
        // 다시 생기면 원인을 알 수 있게 기술적인 내용도 작게 남긴다
        return "$why 아래 버튼으로 네이버쇼핑·다나와에서 바로 볼 수 있어요.\n(오류: ${e.javaClass.simpleName}${e.message?.let { ": " + it.take(80) } ?: ""})"
    }

    fun toggleFavLive(pcode: String, name: String, price: Long?, image: String) {
        prefs.updateFavs { favs ->
            if (pcode in favs) favs - pcode
            else {
                val best = _livePages.value[pcode]?.page?.offers?.minByOrNull { it.price }
                val fav = Fav(savedPrice = price, savedAt = System.currentTimeMillis(), name = name, image = image)
                favs + (pcode to (best?.let { fav.withPrice(it.price, it.mall) } ?: fav))
            }
        }
    }

    /** 30분 안에 보던 화면이 있으면 그대로 (앱이 정리됐다 다시 열렸을 때) */
    val restoredNav: Pair<String, List<String>>? = prefs.loadNav(30 * 60_000L)

    fun saveNav(tab: String, stack: List<String>) = prefs.saveNav(tab, stack)

    fun markVisit() {
        prefs.lastVisit = System.currentTimeMillis()
    }

    fun updateSettings(change: (Settings) -> Settings) {
        val before = prefs.settings.value
        prefs.updateSettings(change)
        val after = prefs.settings.value
        if (before.dataUrl != after.dataUrl) refresh()
    }

    fun toggleFav(item: Item) {
        val pcode = item.pcode ?: return
        prefs.updateFavs { favs ->
            if (pcode in favs) favs - pcode
            else {
                val p = _state.value.index?.products?.get(pcode)
                favs + (pcode to Fav(
                    savedPrice = item.price, savedAt = System.currentTimeMillis(),
                    name = p?.name?.ifBlank { null } ?: item.danawaName.ifBlank { item.name },
                    mall = item.mall, cat = item.cat, image = item.image.ifBlank { p?.image.orEmpty() },
                ))
            }
        }
    }

    fun setTarget(pcode: String, target: Long?) {
        prefs.updateFavs { favs -> favs[pcode]?.let { favs + (pcode to it.copy(target = target?.takeIf { t -> t > 0 })) } ?: favs }
    }

    var recentSearches: List<String>
        get() = prefs.recentSearches
        set(v) {
            prefs.recentSearches = v
        }

    fun canFav(item: Item) = item.pcode != null && item.kind != ItemKind.POST || (item.kind == ItemKind.POST && item.rate != null)
}
