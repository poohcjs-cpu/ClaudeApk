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
import io.github.poohcjs.hotdealradar.data.ArchiveClient
import io.github.poohcjs.hotdealradar.data.ArchiveItem
import io.github.poohcjs.hotdealradar.data.ArchiveSearch
import io.github.poohcjs.hotdealradar.data.LinkInfo
import io.github.poohcjs.hotdealradar.data.LiveSource
import io.github.poohcjs.hotdealradar.data.Mall
import io.github.poohcjs.hotdealradar.data.MallDirectory
import io.github.poohcjs.hotdealradar.data.PriceHistory
import io.github.poohcjs.hotdealradar.data.QueryCleaner
import io.github.poohcjs.hotdealradar.data.UnitPrice
import io.github.poohcjs.hotdealradar.data.linkQuery
import io.github.poohcjs.hotdealradar.data.liveDeals
import io.github.poohcjs.hotdealradar.data.mergeSources
import io.github.poohcjs.hotdealradar.data.productWords
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import io.github.poohcjs.hotdealradar.work.DealCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 사이트 하나의 검색 상태 */
data class SiteState(val loading: Boolean = true, val count: Int = 0, val failed: Boolean = false)

data class LiveSearchState(
    /** 실제로 검색한 말 (다듬은 뒤) */
    val query: String = "",
    val loading: Boolean = false,
    val results: List<LiveProduct> = emptyList(),
    val error: String? = null,
    val searchedAt: Long? = null,
    /** 2~3쪽을 이어서 읽는 중 */
    val loadingMore: Boolean = false,
    /** 사용자가 쓴 말이 다듬어졌으면 그 원래 말 ('가장 싼 화장지' → '화장지') */
    val asked: String? = null,
    val sites: Map<LiveSource, SiteState> = emptyMap(),
    /** 함께 검색한 공식몰 이름 */
    val malls: List<String> = emptyList(),
    /** 커뮤니티 핫딜 기록 (null: 아직 읽는 중) */
    val history: PriceHistory? = null,
)

/** 공유받은 상품 링크 */
data class LinkState(val url: String, val loading: Boolean = true, val info: LinkInfo? = null, val error: String? = null, val remembered: Boolean = false)

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

    // ── 실시간 최저가 검색 (다나와 + 에누리 + 공식몰 + 커뮤니티 기록) ─────────
    private val live = LiveSearchClient()
    private val archive = ArchiveClient(app) { repo.dataUrl().substringBeforeLast('/') }
    private val _liveSearch = MutableStateFlow(LiveSearchState())
    val liveSearch: StateFlow<LiveSearchState> = _liveSearch.asStateFlow()
    private val _livePages = MutableStateFlow<Map<String, LivePageState>>(emptyMap())
    val livePages: StateFlow<Map<String, LivePageState>> = _livePages.asStateFlow()
    private val _link = MutableStateFlow<LinkState?>(null)
    val link: StateFlow<LinkState?> = _link.asStateFlow()
    /** 검색 결과에서 연 상품 (상세 화면 머리에 바로 보여 줄 정보) */
    val liveProducts = HashMap<String, LiveProduct>()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var linkJob: kotlinx.coroutines.Job? = null

    /**
     * 다나와·에누리·검색어와 관련 있는 공식몰을 한꺼번에 찾고, 커뮤니티 핫딜 기록에서 지난 가격도 찾는다.
     * 먼저 온 결과부터 보여 주고, 다나와 2~3쪽·에누리 2쪽을 이어서 붙인다.
     */
    fun searchLive(query: String) = searchLive(query, retry = true, original = null)

    private fun searchLive(query: String, retry: Boolean, original: String?) {
        val typed = query.trim()
        val q = QueryCleaner.clean(typed)
        if (q.length < 2) return
        searchJob?.cancel()
        val sites = mapOf(LiveSource.DANAWA to SiteState(), LiveSource.ENURI to SiteState(), LiveSource.MALL to SiteState())
        _liveSearch.value = LiveSearchState(query = q, loading = true, asked = (original ?: typed).takeIf { it != q }, sites = sites)
        searchJob = viewModelScope.launch {
            var dn = emptyList<LiveProduct>()
            var en = emptyList<LiveProduct>()
            var ml = emptyList<LiveProduct>()
            var danawaError: Throwable? = null
            // 지금 진행 중인 커뮤니티 핫딜 (수집기가 모은 최근 72시간 글) — 가격비교 최저가보다 싼 경우가 많다
            val deals = _state.value.index?.posts?.let { liveDeals(it, q) }.orEmpty()
            if (deals.isNotEmpty()) _liveSearch.update { it.copy(sites = it.sites + (LiveSource.HOTDEAL to SiteState(false, deals.size))) }
            fun publish(source: LiveSource? = null, site: SiteState? = null) {
                val merged = mergeSources(dn, en, ml, listOfNotNull(linkProduct()) + deals)
                rememberProducts(merged)
                _liveSearch.update { st ->
                    val nextSites = if (source != null && site != null) st.sites + (source to site) else st.sites
                    val waiting = nextSites.filterKeys { it != LiveSource.MALL }.values.any { it.loading }
                    st.copy(
                        results = merged, sites = nextSites, searchedAt = System.currentTimeMillis(),
                        loading = merged.isEmpty() && waiting,
                        loadingMore = merged.isNotEmpty() && nextSites.values.any { it.loading },
                        error = if (merged.isEmpty() && !waiting && danawaError != null) liveError(danawaError!!) else null,
                    )
                }
                // 여러 낱말로 하나도 못 찾았으면 가장 긴 낱말 하나로 다시 (예: '전통 순천건봉국밥' → '순천건봉국밥')
                val st = _liveSearch.value
                if (retry && st.results.isEmpty() && st.sites.values.none { it.loading } && q.contains(' ')) {
                    val longest = q.split(' ').maxByOrNull { it.length }
                    if (longest != null && longest.length >= 2) searchLive(longest, retry = false, original = st.asked ?: q)
                }
            }
            // 다나와: 1쪽 → 2~3쪽
            launch {
                val first = runCatching { live.search(q, 1) }.getOrElse { e -> danawaError = e; publish(LiveSource.DANAWA, SiteState(false, 0, true)); return@launch }
                dn = first
                publish(LiveSource.DANAWA, SiteState(first.size >= 30, dn.size))
                if (first.size < 30) return@launch
                for (page in 2..MAX_PAGES) {
                    val more = runCatching { live.search(q, page) }.getOrNull().orEmpty()
                    val known = dn.map { it.pcode }.toHashSet()
                    val fresh = more.filter { it.pcode !in known }
                    if (fresh.isEmpty()) break
                    dn = dn + fresh
                    val done = page == MAX_PAGES || more.size < 30
                    publish(LiveSource.DANAWA, SiteState(!done, dn.size))
                    if (done) break
                }
                publish(LiveSource.DANAWA, SiteState(false, dn.size))
            }
            // 에누리: 1~2쪽
            launch {
                val first = runCatching { live.enuriSearch(q, 1) }.getOrElse { publish(LiveSource.ENURI, SiteState(false, 0, true)); return@launch }
                en = first
                publish(LiveSource.ENURI, SiteState(first.size >= 30, en.size))
                if (first.size >= 30) {
                    val more = runCatching { live.enuriSearch(q, 2) }.getOrNull().orEmpty()
                    val known = en.map { it.pcode }.toHashSet()
                    en = en + more.filter { it.pcode !in known }
                }
                publish(LiveSource.ENURI, SiteState(false, en.size))
            }
            // 공식몰: 검색어와 관련 있는 곳만
            launch {
                val known = MallDirectory.merge(prefs.userMalls, runCatching { archive.malls() }.getOrDefault(emptyList()))
                val picked = MallDirectory.match(q, known)
                _liveSearch.update { it.copy(malls = picked.map { (m, _) -> m.label }) }
                if (picked.isEmpty()) { publish(LiveSource.MALL, SiteState(false, 0)); return@launch }
                val found = picked.map { (mall, hit) ->
                    async {
                        val direct = runCatching { live.mallSearch(mall, q) }.getOrNull().orEmpty()
                        // 'OO국밥 6팩'처럼 길게 찾아 없으면 쇼핑몰과 맞은 낱말로 한 번 더
                        if (direct.isEmpty() && hit != q) runCatching { live.mallSearch(mall, hit) }.getOrNull().orEmpty() else direct
                    }
                }.awaitAll().flatten()
                ml = found
                publish(LiveSource.MALL, SiteState(false, ml.size))
            }
            // 커뮤니티 핫딜 기록 (최근 6개월)
            launch {
                val (items, since) = runCatching { archive.load(6) }.getOrDefault(emptyList<ArchiveItem>() to null)
                _liveSearch.update { it.copy(history = ArchiveSearch.history(items, q, since, 6)) }
            }
        }
    }

    /** 공유받은 링크 상품 (비교 목록에 함께 넣는다) */
    private fun linkProduct(): LiveProduct? {
        val info = _link.value?.info ?: return null
        val price = info.price ?: return null
        return LiveProduct(
            pcode = "link:" + info.url.hashCode().toUInt(), name = info.name, image = info.image, price = price, malls = null,
            unit = UnitPrice.estimate(info.name, price), options = emptyList(), used = false, overseas = false, rank = -1,
            mall = info.site + " (공유한 링크)", link = info.url, shippingFee = info.fee, total = info.total, source = LiveSource.MALL,
        )
    }

    /**
     * 다른 앱에서 공유한 링크: 그 상품 페이지를 한 번 읽어(가격·배송비) 앱이 찾은 최저가와 비교한다.
     * hint: 링크와 함께 온 상품명 (있으면 바로 그 이름으로 검색)
     */
    fun openLink(url: String, hint: String?) {
        linkJob?.cancel()
        _link.value = LinkState(url)
        val hintQuery = hint?.trim()?.takeIf { it.length >= 2 }
        if (hintQuery != null) searchLive(hintQuery)
        linkJob = viewModelScope.launch {
            runCatching { live.readLink(url) }
                .onSuccess { info ->
                    val known = (prefs.userMalls + MallDirectory.SEED).any { it.host == info.host }
                    _link.value = LinkState(url, false, info, if (info.price == null) "이 페이지에서 가격을 찾지 못했어요" else null, known)
                    val q = hintQuery ?: linkQuery(info.name)
                    if (q.length >= 2) searchLive(q)
                }
                .onFailure { e ->
                    _link.value = LinkState(url, false, null, if (e is LiveBlocked) e.message else "링크를 읽지 못했어요 (${e.javaClass.simpleName})")
                }
        }
    }

    fun clearLink() {
        linkJob?.cancel()
        _link.value = null
        _liveSearch.value.query.takeIf { it.isNotEmpty() }?.let { q ->
            _liveSearch.update { st -> st.copy(results = st.results.filter { !it.pcode.startsWith("link:") }) }
        }
    }

    /** 공유한 링크의 쇼핑몰을 기억해 다음 검색부터 함께 찾는다 (카페24 쇼핑몰만) */
    fun rememberLinkMall() {
        val st = _link.value ?: return
        val info = st.info ?: return
        if (info.platform != "cafe24") return
        val words = (productWords(info.site) + productWords(info.name)).toList().take(10)
        prefs.userMalls = listOf(Mall(info.host, info.site, "cafe24", words, user = true)) + prefs.userMalls.filter { it.host != info.host }
        _link.value = st.copy(remembered = true)
    }

    val watches: StateFlow<List<io.github.poohcjs.hotdealradar.data.PriceWatch>> = prefs.watches

    /** 가격 알림 걸기/풀기 (같은 검색어는 하나만) */
    fun toggleWatch(w: io.github.poohcjs.hotdealradar.data.PriceWatch) {
        prefs.updateWatches { list -> if (list.any { it.query == w.query }) list.filter { it.query != w.query } else listOf(w) + list }
        // 처음 거는 알림이면 바로 다음 확인부터 동작하도록 주기 작업을 보장
        DealCheckWorker.schedule(getApplication(), true)
    }

    fun removeWatch(query: String) = prefs.updateWatches { list -> list.filter { it.query != query } }

    /** 관심 키워드로 등록 → 이 말이 들어간 커뮤니티 핫딜이 올라오면 알림 */
    fun watchKeyword(word: String) {
        val w = word.trim()
        if (w.length < 2) return
        prefs.updateSettings { s -> if (w in s.keywords) s else s.copy(keywords = s.keywords + w) }
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
