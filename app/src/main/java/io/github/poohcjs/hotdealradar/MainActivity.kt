package io.github.poohcjs.hotdealradar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.poohcjs.hotdealradar.data.targetReached
import io.github.poohcjs.hotdealradar.ui.AppViewModel
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.screens.CommunityScreen
import io.github.poohcjs.hotdealradar.ui.screens.DealsScreen
import io.github.poohcjs.hotdealradar.ui.screens.DetailScreen
import io.github.poohcjs.hotdealradar.ui.screens.SavedScreen
import io.github.poohcjs.hotdealradar.ui.screens.SearchScreen
import io.github.poohcjs.hotdealradar.ui.screens.SettingsScreen
import io.github.poohcjs.hotdealradar.ui.theme.HotdealTheme
import io.github.poohcjs.hotdealradar.work.DealCheckWorker

enum class Tab(val label: String, val icon: Int) {
    DEALS("핫딜", R.drawable.ic_fire),
    COMMUNITY("커뮤니티", R.drawable.ic_forum),
    SEARCH("검색", R.drawable.ic_search),
    SAVED("찜", R.drawable.ic_heart),
    SETTINGS("설정", R.drawable.ic_settings),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val pendingItem = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingItem.value = intent?.getStringExtra(EXTRA_ITEM)
        setContent { HotdealTheme { App(vm, pendingItem) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ITEM)?.let { pendingItem.value = it }
    }

    override fun onStop() {
        super.onStop()
        vm.markVisit()
    }

    companion object {
        const val EXTRA_ITEM = "item"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel, pendingItem: androidx.compose.runtime.MutableState<String?>) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val favs by vm.favs.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.DEALS) }
    val stack = remember { mutableStateListOf<String>() } // 열린 상세 화면들 (항목 key)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotify() {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if ((s.notify || s.notifyTarget) && !DealCheckWorker.canNotify(context)) askNotify()
    }
    val index = state.index
    LaunchedEffect(pendingItem.value, index) {
        val key = pendingItem.value
        if (key != null && index != null) {
            if (index.find(key) != null) stack.add(key)
            pendingItem.value = null
        }
    }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val reachedCount = if (index != null) favs.keys.count { targetReached(it, favs, index) } else 0

    Scaffold(
        bottomBar = {
            if (stack.isEmpty()) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = {
                                BadgedBox(badge = { if (t == Tab.SAVED && reachedCount > 0) Badge { Text("$reachedCount") } }) {
                                    Icon(painterResource(t.icon), t.label)
                                }
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val top = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        val detailKey = stack.lastOrNull()
        val detail = detailKey?.let { index?.find(it) }
        if (detail != null && index != null) {
            DetailScreen(
                x = detail, index = index, s = s, favs = favs, contentPadding = top,
                onBack = { stack.removeAt(stack.lastIndex) }, onFav = vm::toggleFav, onTarget = vm::setTarget,
                onOpen = { stack.add(it.key) },
            )
            return@Scaffold
        }
        if (tab == Tab.SETTINGS) {
            SettingsScreen(index, s, top, vm::updateSettings, ::askNotify)
            return@Scaffold
        }
        PullToRefreshBox(isRefreshing = state.loading && index != null, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(top)) {
            when {
                index == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                index == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyNote("핫딜을 불러오지 못했어요", "${state.error ?: ""}\n인터넷 연결을 확인하고 다시 시도해 주세요.")
                    TextButton(onClick = vm::refresh) { Text("다시 시도") }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    if (state.error != null) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "새로 받지 못해 ${if (state.fromCache) "저장해 둔" else "이전"} 내용을 보여 주고 있어요 (${state.error})",
                                fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (index.file.demo) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Text("예시 데이터예요. 수집기가 실제로 돌면 진짜 핫딜로 바뀌어요.", fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                    val pad = PaddingValues(0.dp)
                    when (tab) {
                        Tab.DEALS -> DealsScreen(index, s, favs, vm.newCutoff, vm.firstVisit, pad, { stack.add(it.key) }, vm::toggleFav)
                        Tab.COMMUNITY -> CommunityScreen(index, s, pad, { v -> vm.updateSettings { it.copy(hideEnded = v) } }) { stack.add(it.key) }
                        Tab.SEARCH -> SearchScreen(index, s, favs, vm.recentSearches, { vm.recentSearches = it }, pad, { stack.add(it.key) }, vm::toggleFav)
                        Tab.SAVED -> SavedScreen(index, s, favs, pad, { stack.add(it.key) }, vm::toggleFav)
                        Tab.SETTINGS -> Unit
                    }
                }
            }
        }
    }
}

