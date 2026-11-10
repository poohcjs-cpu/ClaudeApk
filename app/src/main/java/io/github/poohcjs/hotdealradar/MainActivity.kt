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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import io.github.poohcjs.hotdealradar.ui.screens.LiveProductScreen
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
    private val pendingShare = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingItem.value = intent?.getStringExtra(EXTRA_ITEM)
        pendingShare.value = sharedText(intent)
        setContent { HotdealTheme { App(vm, pendingItem, pendingShare) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ITEM)?.let { pendingItem.value = it }
        sharedText(intent)?.let { pendingShare.value = it }
    }

    override fun onStop() {
        super.onStop()
        vm.markVisit()
    }

    /** 다른 앱에서 '공유 → 핫딜 레이더'로 보낸 글 */
    private fun sharedText(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_SEND && it.type?.startsWith("text/") == true }
            ?.let { it.getStringExtra(Intent.EXTRA_TEXT) ?: it.getStringExtra(Intent.EXTRA_SUBJECT) }

    companion object {
        const val EXTRA_ITEM = "item"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    vm: AppViewModel,
    pendingItem: androidx.compose.runtime.MutableState<String?>,
    pendingShare: androidx.compose.runtime.MutableState<String?> = mutableStateOf(null),
) {
    val p = HD.p
    val state by vm.state.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val favs by vm.favs.collectAsStateWithLifecycle()
    val liveSearch by vm.liveSearch.collectAsStateWithLifecycle()
    val livePages by vm.livePages.collectAsStateWithLifecycle()
    val linkState by vm.link.collectAsStateWithLifecycle()
    // 보던 화면 유지: 탭·열린 상세 화면을 화면 회전·앱 정리 뒤에도 되살리고(rememberSaveable),
    // 앱을 완전히 닫았어도 30분 안에 다시 열면 이어서 보여 준다(restoredNav)
    var tab by rememberSaveable { mutableStateOf(vm.restoredNav?.first?.let { n -> Tab.entries.firstOrNull { it.name == n } } ?: Tab.DEALS) }
    val stack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        vm.restoredNav?.second.orEmpty().toMutableStateList()
    } // 열린 상세 화면들 (항목 key)
    val screens = rememberSaveableStateHolder() // 탭·상세 화면마다 스크롤 위치를 따로 기억
    fun pop() {
        if (stack.isEmpty()) return
        screens.removeState("d:" + stack.removeAt(stack.lastIndex))
    }
    LaunchedEffect(tab, stack.toList()) { vm.saveNav(tab.name, stack.toList()) }

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
        if (key != null && key.startsWith("live:")) {
            stack.add(key)
            pendingItem.value = null
        } else if (key != null && index != null) {
            if (index.find(key) != null) stack.add(key)
            pendingItem.value = null
        }
    }
    LaunchedEffect(pendingShare.value) {
        if (pendingShare.value != null) {
            stack.clear()
            tab = Tab.SEARCH
        }
    }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    // 데이터가 바뀌어 사라진 딜을 가리키고 있으면 닫는다 (빈 화면에 갇히지 않게)
    LaunchedEffect(index, stack.toList()) {
        if (index != null) while (stack.isNotEmpty() && !stack.last().startsWith("live:") && index.find(stack.last()) == null) pop()
    }

    val reachedCount = if (index != null) favs.keys.count { targetReached(it, favs, index) } else 0

    Scaffold(
        containerColor = p.bg,
        bottomBar = { if (stack.isEmpty()) BottomBar(tab, reachedCount) { tab = it } },
    ) { padding ->
        val detailKey = stack.lastOrNull()
        if (detailKey != null && detailKey.startsWith("live:")) {
            val pcode = detailKey.removePrefix("live:")
            screens.SaveableStateProvider("d:$detailKey") {
                LiveProductScreen(
                    pcode = pcode, product = vm.liveProducts[pcode], state = livePages[pcode], index = index, favs = favs,
                    contentPadding = padding, onLoad = vm::loadLivePage, onBack = ::pop,
                    onOption = { stack[stack.lastIndex] = "live:$it" },
                    onFav = { name, price, image -> vm.toggleFavLive(pcode, name, price, image) },
                    onOpenDeal = { stack.add(it.key) },
                    withShipping = s.liveWithShipping,
                )
            }
            return@Scaffold
        }
        val detail = detailKey?.let { index?.find(it) }
        if (detail != null && index != null) {
            screens.SaveableStateProvider("d:$detailKey") {
                DetailScreen(
                    x = detail, index = index, s = s, favs = favs, contentPadding = padding,
                    onBack = ::pop, onFav = vm::toggleFav, onTarget = vm::setTarget,
                    onOpen = { stack.add(it.key) },
                )
            }
            return@Scaffold
        }
        if (tab == Tab.SETTINGS) {
            screens.SaveableStateProvider("t:SETTINGS") { SettingsScreen(index, s, padding, vm::updateSettings, ::askNotify) }
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
                    screens.SaveableStateProvider("t:${tab.name}") { when (tab) {
                        Tab.DEALS -> DealsScreen(index, s, favs, vm.newCutoff, vm.firstVisit, pad, { stack.add(it.key) }, vm::toggleFav)
                        Tab.COMMUNITY -> CommunityScreen(index, s, pad, { v -> vm.updateSettings { it.copy(hideEnded = v) } }) { stack.add(it.key) }
                        Tab.SEARCH -> SearchScreen(
                            index, s, favs, vm.recentSearches, { vm.recentSearches = it }, liveSearch, vm::searchLive,
                            { stack.add("live:$it") }, pad, { stack.add(it.key) }, vm::toggleFav,
                            onSettings = vm::updateSettings, incomingQuery = pendingShare.value,
                            onIncomingHandled = { pendingShare.value = null },
                            link = linkState, onOpenLink = vm::openLink, onClearLink = vm::clearLink,
                            onRememberMall = vm::rememberLinkMall, onWatch = vm::watchKeyword,
                        )
                        Tab.SAVED -> SavedScreen(index, s, favs, pad, { item ->
                            // 실시간 검색에서 찜한 제품처럼 모아 둔 데이터에 없으면 실시간 화면으로
                            if (index.find(item.key) != null) stack.add(item.key) else item.pcode?.let { stack.add("live:$it") }
                        }, { item -> if (index.find(item.key) != null) vm.toggleFav(item) else item.pcode?.let { vm.toggleFavLive(it, item.name, item.price, item.image) } })
                        Tab.SETTINGS -> Unit
                    } }
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
