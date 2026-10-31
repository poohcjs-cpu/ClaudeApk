package io.github.poohcjs.hotdealradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.poohcjs.hotdealradar.HotdealApp
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.work.DealCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadState(
    val index: Index? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fromCache: Boolean = false,
    val fetchedAt: Long? = null,
)

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

    fun markVisit() {
        prefs.lastVisit = System.currentTimeMillis()
    }

    fun updateSettings(change: (Settings) -> Settings) {
        val before = prefs.settings.value
        prefs.updateSettings(change)
        val after = prefs.settings.value
        if ((before.notify || before.notifyTarget) != (after.notify || after.notifyTarget)) {
            DealCheckWorker.schedule(getApplication(), after.notify || after.notifyTarget)
        }
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
