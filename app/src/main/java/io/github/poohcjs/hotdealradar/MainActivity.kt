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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.HD
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
    val p = HD.p
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
        containerColor = p.bg,
        bottomBar = { if (stack.isEmpty()) BottomBar(tab, reachedCount) { tab = it } },
    ) { padding ->
        val detailKey = stack.lastOrNull()
        val detail = detailKey?.let { index?.find(it) }
        if (detail != null && index != null) {
            DetailScreen(
                x = detail, index = index, s = s, favs = favs, contentPadding = padding,
                onBack = { stack.removeAt(stack.lastIndex) }, onFav = vm::toggleFav, onTarget = vm::setTarget,
                onOpen = { stack.add(it.key) },
            )
            return@Scaffold
        }
        if (tab == Tab.SETTINGS) {
            SettingsScreen(index, s, padding, vm::updateSettings, ::askNotify)
            return@Scaffold
        }
        PullToRefreshBox(isRefreshing = state.loading && index != null, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                index == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Brand, strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("핫딜을 불러오고 있어요", color = p.sub, fontSize = 14.sp)
                    }
                }
                index == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyNote("핫딜을 불러오지 못했어요", "${state.error ?: ""}\n인터넷 연결을 확인하고 다시 시도해 주세요.", "📡")
                    Text(
                        "다시 시도", color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(p.brandBrush).clickable { vm.refresh() }
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                    )
                }
                else -> Column(Modifier.fillMaxSize()) {
                    if (state.error != null) {
                        Text(
                            "새로 받지 못해 ${if (state.fromCache) "저장해 둔" else "이전"} 내용을 보여 주고 있어요",
                            fontSize = 12.5.sp, color = p.sub,
                            modifier = Modifier.fillMaxWidth().background(p.brandSoft).padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    if (index.file.demo) {
                        Text(
                            "예시 데이터예요. 수집기가 실제로 돌면 진짜 핫딜로 바뀌어요.", fontSize = 12.5.sp, color = p.sub,
                            modifier = Modifier.fillMaxWidth().background(p.tile).padding(horizontal = 20.dp, vertical = 8.dp),
                        )
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

/** 아래 탭 막대: 선택된 탭은 브랜드 색 아이콘과 굵은 글자 */
@Composable
fun BottomBar(tab: Tab, savedBadge: Int, onTab: (Tab) -> Unit) {
    val p = HD.p
    Column(Modifier.fillMaxWidth().background(p.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { t ->
                val on = t == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTab(t) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Icon(
                            painterResource(t.icon), t.label, tint = if (on) Brand else p.faint,
                            modifier = Modifier.size(24.dp),
                        )
                        if (t == Tab.SAVED && savedBadge > 0) {
                            Text(
                                "$savedBadge", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(15.dp).clip(CircleShape).background(Brand)
                                    .padding(top = 1.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label, fontSize = 11.sp, color = if (on) p.ink else p.faint,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
