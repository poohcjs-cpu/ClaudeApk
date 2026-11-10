package io.github.poohcjs.hotdealradar.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.ArchiveSearch
import io.github.poohcjs.hotdealradar.data.LinkReader
import io.github.poohcjs.hotdealradar.data.LiveProduct
import io.github.poohcjs.hotdealradar.data.LiveSource
import io.github.poohcjs.hotdealradar.data.PriceHistory
import io.github.poohcjs.hotdealradar.data.PriceWatch
import io.github.poohcjs.hotdealradar.data.savingVsMarket
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.defaultLiveSort
import io.github.poohcjs.hotdealradar.data.isHot
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.pickOption
import io.github.poohcjs.hotdealradar.data.search
import io.github.poohcjs.hotdealradar.data.shownCost
import io.github.poohcjs.hotdealradar.data.shownUnit
import io.github.poohcjs.hotdealradar.data.sortLive
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.LinkState
import io.github.poohcjs.hotdealradar.ui.LiveSearchState
import io.github.poohcjs.hotdealradar.ui.components.ChipOption
import io.github.poohcjs.hotdealradar.ui.components.ChipRow
import io.github.poohcjs.hotdealradar.ui.components.DealCard
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.components.Pill
import io.github.poohcjs.hotdealradar.ui.components.ProductImage
import io.github.poohcjs.hotdealradar.ui.components.SortTabs
import io.github.poohcjs.hotdealradar.ui.components.Tag
import io.github.poohcjs.hotdealradar.ui.components.cardBg
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.Discover
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.HD
import io.github.poohcjs.hotdealradar.ui.theme.Warn

private val SUGGESTIONS = listOf("화장지", "생수 2L", "물티슈", "커피 캡슐", "기저귀", "세탁세제", "즉석밥")

/** 분류 칩: 가장 많은 분류가 절반 이상이면 처음부터 그 분류만 보여 준다 (치약을 찾는데 치약 짜개가 섞이지 않게) */
fun dominantCategory(list: List<LiveProduct>): String? {
    val counts = list.filter { it.category.isNotBlank() }.groupingBy { it.category }.eachCount()
    val top = counts.maxByOrNull { it.value } ?: return null
    return top.key.takeIf { counts.size > 1 && top.value * 2 >= list.size }
}

/** 다른 앱에서 공유한 글('상품명 https://…')에서 검색어만 남긴다 */
fun queryFromShared(text: String): String =
    text.replace(Regex("""https?://\S+"""), " ")
        .replace(Regex("""[\[\]【】()（）<>{}"'|]"""), " ")
        .replace(Regex("""\s+"""), " ").trim()
        .split(' ').take(8).joinToString(" ")

@Composable
fun SearchScreen(
    index: Index,
    s: Settings,
    favs: Map<String, Fav>,
    recent: List<String>,
    onRecent: (List<String>) -> Unit,
    live: LiveSearchState,
    onSearchLive: (String) -> Unit,
    onOpenLive: (String) -> Unit,
    contentPadding: PaddingValues,
    onOpen: (Item) -> Unit,
    onFav: (Item) -> Unit,
    onSettings: ((Settings) -> Settings) -> Unit = {},
    incomingQuery: String? = null,
    onIncomingHandled: () -> Unit = {},
    link: LinkState? = null,
    onOpenLink: (url: String, hint: String?) -> Unit = { _, _ -> },
    onClearLink: () -> Unit = {},
    onRememberMall: () -> Unit = {},
    onWatch: (String) -> Unit = {},
    watches: List<PriceWatch> = emptyList(),
    onToggleWatch: (PriceWatch) -> Unit = {},
) {
    val p = HD.p
    var q by rememberSaveable { mutableStateOf(live.query) }
    var submitted by rememberSaveable { mutableStateOf(live.query) }
    var tab by rememberSaveable { mutableStateOf("live") }
    var sortName by rememberSaveable { mutableStateOf<String?>(null) }
    var catPick by rememberSaveable(submitted) { mutableStateOf<String?>(null) } // null = 자동(주요 분류)
    var hideUsed by rememberSaveable { mutableStateOf(true) }
    var recents by remember { mutableStateOf(recent) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val local = remember(submitted, index) { search(index, submitted).sortedByDescending { it.rate ?: -999.0 } }
    val ship = s.liveWithShipping

    fun submit(text: String = q) {
        val t = text.trim()
        q = t
        if (t.length < 2) return
        submitted = t
        sortName = null
        tab = "live"
        recents = (listOf(t) + recents.filter { it != t }).take(8)
        onRecent(recents)
        onSearchLive(t)
        focus.clearFocus()
    }

    // 앱이 정리됐다 다시 열려 검색 결과가 사라졌으면 같은 검색어로 다시 찾는다 (보던 화면 유지)
    LaunchedEffect(Unit) {
        if (submitted.isNotEmpty() && live.query != submitted && !live.loading && incomingQuery == null) onSearchLive(submitted)
    }

    // 다른 앱에서 '공유 → 핫딜 레이더'로 들어온 상품명
    LaunchedEffect(incomingQuery) {
        val url = LinkReader.findUrl(incomingQuery)
        val t = incomingQuery?.let { queryFromShared(it) }
        if (url != null) {
            // 링크: 그 상품 페이지 가격을 읽어 앱이 찾은 최저가와 비교 (함께 온 상품명이 있으면 그 이름으로 검색)
            tab = "live"
            sortName = null
            focus.clearFocus()
            onOpenLink(url, t?.takeIf { it.length >= 2 })
        } else if (!t.isNullOrBlank()) submit(t)
        if (incomingQuery != null) onIncomingHandled()
    }

    // 검색어가 앱 안에서 바뀌면(말 다듬기·링크 상품 이름·결과 없을 때 다시 찾기) 입력칸도 맞춘다
    LaunchedEffect(live.query) {
        if (live.query.isNotBlank() && live.query != submitted) {
            submitted = live.query
            q = live.query
        }
    }

    val base = live.results.filter { !(hideUsed && it.used) }
    val auto = dominantCategory(base)
    val cat = catPick ?: auto ?: ""
    // 분류를 모르는 결과(공식몰·공유 링크 등)는 분류를 골라도 남긴다
    val inCat = if (cat.isEmpty()) base else base.filter { it.category == cat || it.category.isBlank() }
    val sort = sortName?.let { LiveSort.valueOf(it) } ?: defaultLiveSort(inCat)
    val sorted = sortLive(inCat, sort, ship)
    val cats = base.filter { it.category.isNotBlank() }.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }
    // 가장 싼 결과가 다나와·에누리 최저가(누구나 쉽게 찾는 값)보다 얼마나 싼지
    val saving = remember(inCat, ship) { savingVsMarket(inCat, ship) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle("검색", "여러 쇼핑몰 가격을 지금 바로 비교해 가장 싼 곳을 찾아요") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(p.card)
                    .border(1.5.dp, if (q.isNotEmpty()) p.ink else p.line, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = p.sub, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
                    if (q.isEmpty()) Text("무엇을 싸게 살까요? (예: 화장지)", fontSize = 15.5.sp, color = p.faint)
                    BasicTextField(
                        value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 15.5.sp, color = p.ink, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Brand),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                    )
                }
                Text(
                    "검색", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (q.trim().length >= 2) p.brandBrush else SolidColor(p.faint))
                        .clickable(enabled = q.trim().length >= 2) { submit() }.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (link != null) item { LinkCard(link, live, ship, onClearLink, onRememberMall) }

        if (submitted.isEmpty()) {
            if (link != null) { item { Spacer(Modifier.height(28.dp)) }; return@LazyColumn }
            item { SmallLabel("이런 걸 많이 찾아요") }
            item { ChipLine(SUGGESTIONS) { submit(it) } }
            if (recents.isNotEmpty()) {
                item { SmallLabel("최근 검색") }
                item { ChipLine(recents) { submit(it) } }
            }
            item {
                Text(
                    "검색하면 다나와·에누리 두 가격비교 사이트와 관련 공식몰을 한꺼번에 찾아 가장 싼 곳을 보여 줘요. 배송비를 포함하고, 양이 다른 상품은 단가(10g당, 100ml당 등)로 비교해요. 뽐뿌·루리웹·클리앙 핫딜 기록으로 지금 가격이 싼 편인지도 알려 줘요.\n\n광고나 다른 쇼핑몰에서 본 상품 링크를 ‘공유 → 핫딜 레이더’로 보내면, 그 가격이 정말 싼지 바로 비교해요.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = p.sub, modifier = Modifier.padding(20.dp),
                )
            }
            return@LazyColumn
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("실시간 최저가", tab == "live", { tab = "live" }, count = if (live.loading) null else base.size)
                Pill("핫딜·커뮤니티", tab == "local", { tab = "local" }, count = local.size)
            }
        }
        if (tab == "live") item { SourcesLine(live) }

        if (tab == "local") {
            if (local.isEmpty()) item { EmptyNote("모아 둔 핫딜에는 없어요", "‘실시간 최저가’에서 지금 가격을 볼 수 있어요.", "🗂️") }
            items(local, key = { it.key }) { x ->
                DealCard(
                    x = x, catIcon = catIcon(index, x.cat), origin = originLabel(x, index), hot = isHot(x, s), isNew = false,
                    fav = x.pcode != null && x.pcode in favs,
                    onFav = if (x.pcode != null && x.kind != ItemKind.POST) ({ onFav(x) }) else null,
                    onClick = { onOpen(if (x.kind == ItemKind.POST) index.itemForPost(x) else x) },
                )
            }
            item { Spacer(Modifier.height(28.dp)) }
            return@LazyColumn
        }

        val openUrl = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        when {
            live.loading -> item {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Brand, strokeWidth = 3.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("여러 쇼핑몰 가격을 비교하고 있어요", color = p.sub, fontSize = 14.sp)
                }
            }
            live.error != null -> {
                item { EmptyNote("지금은 실시간 가격을 못 불러왔어요", live.error, "📡") }
                item { OtherSites(submitted, openUrl) }
            }
            base.isEmpty() -> {
                item { EmptyNote("검색 결과가 없어요", "다른 이름으로 찾아보거나 네이버쇼핑·다나와에서 직접 볼 수 있어요.", "🔎") }
                item { OtherSites(submitted, openUrl) }
            }
            else -> {
                fun openProduct(prod: LiveProduct) {
                    // 쇼핑몰 개별 상품은 다나와 상품 페이지가 없으니 그 쇼핑몰로 바로 간다
                    if (prod.isListing) openUrl(prod.link) else onOpenLive(pickOption(prod, sort, ship)?.pcode ?: prod.pcode)
                }
                if (cats.size > 1) {
                    item {
                        ChipRow(
                            listOf(ChipOption("", "전체", base.size)) + cats.take(8).map { ChipOption(it.key, it.key, it.value) },
                            cat, { catPick = it }, Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                if (sorted.isNotEmpty()) {
                    val top = sorted.first()
                    item { BestPick(top, sort, ship, index, saving?.takeIf { sort == LiveSort.UNIT }) { openProduct(top) } }
                }
                // 지금 사도 될까? — 커뮤니티 핫딜 기록과 비교 + 지금 최저가보다 싸지면 알림
                item {
                    val best = remember(inCat, ship) { sortLive(inCat.filter { !it.used }, LiveSort.UNIT, ship).firstOrNull() }
                    val hist = remember(live.history, inCat) { live.history?.let { ArchiveSearch.withPeers(it, inCat) } }
                    val bu = best?.let { shownUnit(it, LiveSort.UNIT, ship) }
                    val bc = best?.let { shownCost(it, LiveSort.UNIT, ship) }
                    val watch = watches.firstOrNull { it.query == live.query }
                    PastDeals(hist, bu, bc, live.query, watch, openUrl) {
                        onToggleWatch(watch ?: PriceWatch(live.query, bu?.per, bu?.value, bc, best?.mall ?: best?.source?.label.orEmpty(), System.currentTimeMillis()))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SortTabs(LiveSort.entries.map { it.name to it.label }, sort.name, { sortName = it })
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            (if (cat.isNotEmpty()) "‘$cat’ " else "") + "${sorted.size}개 · ${ago(live.searchedAt)} 확인" + if (live.loadingMore) " · 더 찾는 중…" else "",
                            fontSize = 12.sp, color = p.faint, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Toggle(if (ship) "✓ 배송비 포함" else "배송비 빼고", ship) { onSettings { it.copy(liveWithShipping = !ship) } }
                        Toggle(if (hideUsed) "✓ 중고 뺌" else "중고 포함", hideUsed) { hideUsed = !hideUsed }
                    }
                }
                items(sorted.drop(1), key = { "live-" + it.pcode }) { prod ->
                    LiveRow(prod, sort, ship, index, fav = (pickOption(prod, sort, ship)?.pcode ?: prod.pcode) in favs) { openProduct(prod) }
                }
                if (live.loadingMore) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Brand, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("다음 쪽 결과를 더 찾고 있어요", fontSize = 13.sp, color = p.sub)
                        }
                    }
                }
                item { OtherSites(submitted, openUrl) }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

fun danawaSearchUrl(q: String) = "https://search.danawa.com/?query=" + Uri.encode(q.trim())

fun naverShoppingUrl(q: String) = "https://search.shopping.naver.com/ns/search?query=" + Uri.encode(q.trim())

fun enuriSearchUrl(q: String) = "https://price.enuri.com/search?keyword=" + Uri.encode(q.trim())

/** 네이버 통합검색으로 '○○ 공식몰' (사용자가 직접 보는 화면) */
fun officialMallSearchUrl(q: String) = "https://m.search.naver.com/search.naver?query=" + Uri.encode(q.trim() + " 공식몰")

@Composable
private fun Toggle(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.sp, color = if (on) HD.p.ink else HD.p.sub, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

@Composable
private fun SmallLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HD.p.ink, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun ChipLine(words: List<String>, onClick: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(words) { w -> Pill(w, false, { onClick(w) }) }
    }
}

/** 더 찾아보고 싶을 때: 같은 검색어로 네이버쇼핑·공식몰 검색·다나와·에누리를 바로 연다 (앱이 대신 읽지 않고 사용자가 직접 봄) */
@Composable
private fun OtherSites(q: String, open: (String) -> Unit) {
    val p = HD.p
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("다른 곳에서도 찾아보기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Spacer(Modifier.height(3.dp))
        Text("쿠팡·네이버 전용 상품은 앱이 읽을 수 없어요. 공식몰을 찾으면 링크를 앱으로 공유해 주세요 — 다음부터 함께 비교해요", fontSize = 12.5.sp, color = p.sub, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "N  네이버쇼핑", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF03C75A))
                    .clickable { open(naverShoppingUrl(q)) }.padding(vertical = 15.dp),
            )
            Text(
                "공식몰 찾기 ↗", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .background(p.card).clickable { open(officialMallSearchUrl(q)) }.padding(vertical = 15.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "다나와 ↗", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.sub, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .clickable { open(danawaSearchUrl(q)) }.padding(vertical = 12.dp),
            )
            Text(
                "에누리 ↗", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.sub, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .clickable { open(enuriSearchUrl(q)) }.padding(vertical = 12.dp),
            )
        }
    }
}

/** 우리 핫딜 데이터에 같은 제품 딜이 있으면 그 딜 */
private fun liveDeal(index: Index, pcodes: Collection<String>): Item? =
    pcodes.flatMap { index.dealsByPcode[it].orEmpty() }.filter { !it.ended }.minByOrNull { it.price ?: Long.MAX_VALUE }

private fun shippingText(fee: Long?) = when {
    fee == null -> "배송비 확인 필요"
    fee == 0L -> "무료배송"
    else -> "배송비 ${won(fee)}원"
}

/** '상품가 1,540원 + 배송비 3,000원' (배송비 포함으로 볼 때 차이가 있으면) */
private fun breakdown(prod: LiveProduct, sort: LiveSort, ship: Boolean): String? {
    if (!ship) return null
    val opt = pickOption(prod, sort, true)
    val price = opt?.price ?: prod.price
    val cost = opt?.cost(true) ?: prod.cost(true)
    return if (cost > price) "상품가 ${won(price)}원 + 배송비 ${won(cost - price)}원" else null
}

@Composable
private fun BestPick(prod: LiveProduct, sort: LiveSort, ship: Boolean, index: Index, saving: Pair<LiveProduct, Int>? = null, onClick: () -> Unit) {
    val p = HD.p
    val opt = pickOption(prod, sort, ship)
    val cost = shownCost(prod, sort, ship)
    val unit = shownUnit(prod, sort, ship)
    val malls = opt?.malls ?: prod.malls
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(24.dp)).background(p.hero).clickable(onClick = onClick).padding(20.dp),
    ) {
        Text(
            when (prod.source) {
                LiveSource.HOTDEAL -> "🔥 지금 진행 중인 핫딜이 가장 싸요"
                LiveSource.MALL -> "🏆 공식몰이 가장 싸요"
                else -> if (sort == LiveSort.UNIT) "🏆 단가가 가장 싼 상품" else "🏆 가장 싼 상품"
            } + if (ship) " · 배송비 포함" else "",
            color = Color(0xFFFFB38F), fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        if (saving != null) {
            val mu = shownUnit(saving.first, LiveSort.UNIT, ship)
            Spacer(Modifier.height(6.dp))
            Text(
                "다나와·에누리 최저가(${saving.first.mall ?: saving.first.source.label}, ${mu?.let { "${won(it.value)}원/${it.per}" } ?: ""})보다 ${saving.second}% 싸요",
                color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Good.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductImage(prod.image, "🛒", 84.dp, 16.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(prod.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                if (opt != null && opt.label.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(opt.label, color = Color.White.copy(alpha = 0.65f), fontSize = 12.5.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(won(cost), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                    Text("원", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                }
                breakdown(prod, sort, ship)?.let { Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp) }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unit != null) item { HeroChip("${if (unit.estimated) "약 " else ""}${unit.value}원 / ${unit.per}") }
            if (malls != null) item { HeroChip("${prod.source.label} 쇼핑몰 ${malls}곳 비교") }
            if (prod.isListing) item { HeroChip("${prod.mall} · ${shippingText(prod.shippingFee)}") }
            liveDeal(index, listOf(prod.pcode) + prod.options.map { it.pcode })?.let { d -> item { HeroChip("🔥 핫딜 ${pct(d.rate)}%↓") } }
        }
        prod.unitNote?.let {
            Spacer(Modifier.height(8.dp))
            Text("ⓘ $it", color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun LiveRow(prod: LiveProduct, sort: LiveSort, ship: Boolean, index: Index, fav: Boolean, onClick: () -> Unit) {
    val p = HD.p
    val opt = pickOption(prod, sort, ship)
    val cost = shownCost(prod, sort, ship)
    val unit = shownUnit(prod, sort, ship)
    val malls = opt?.malls ?: prod.malls
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).cardBg(18.dp, 2.dp).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductImage(prod.image, "🛒", 72.dp, 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (prod.source == LiveSource.HOTDEAL) Tag("🔥 핫딜", Brand, filled = true)
                else if (prod.source == LiveSource.MALL) Tag("공식몰", Good, filled = true)
                else if (prod.source == LiveSource.ENURI) Tag("에누리")
                if (prod.isListing) Tag(prod.mall!!, Discover)
                if (opt != null && opt.label.isNotBlank()) Tag(opt.label)
                if (prod.overseas) Tag("해외구매", Warn)
                if (prod.used) Tag("중고·리퍼", Warn)
                if (fav) Tag("찜", Brand)
                liveDeal(index, listOf(prod.pcode) + prod.options.map { it.pcode })?.let { Tag("핫딜 ${pct(it.rate)}%↓", Brand, filled = true) }
            }
            Spacer(Modifier.height(4.dp))
            Text(prod.name, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${won(cost)}원", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                if (unit != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${if (unit.estimated) "약 " else ""}${unit.value}원/${unit.per}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (sort == LiveSort.UNIT) Good else p.sub, modifier = Modifier.padding(bottom = 1.dp),
                    )
                }
            }
            breakdown(prod, sort, ship)?.let { Text(it, fontSize = 11.5.sp, color = p.sub) }
            if (malls != null) Text("${prod.source.label} 쇼핑몰 ${malls}곳 중 최저가", fontSize = 11.5.sp, color = p.faint)
            if (prod.isListing) Text("${prod.mall} · ${shippingText(prod.shippingFee)} · 누르면 쇼핑몰로", fontSize = 11.5.sp, color = p.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            prod.unitNote?.let { Text("ⓘ $it", fontSize = 11.sp, color = p.faint, lineHeight = 15.sp) }
        }
    }
}

/** 어디까지 찾았는지: 다나와 120 · 에누리 40 · 공식몰 2 (읽는 중이면 …) */
@Composable
private fun SourcesLine(live: LiveSearchState) {
    val p = HD.p
    if (live.sites.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (src in LiveSource.entries) {
                val st = live.sites[src] ?: continue
                if (src == LiveSource.MALL && live.malls.isEmpty()) continue
                val text = when {
                    st.failed -> "${src.label} ✕"
                    st.loading && st.count == 0 -> "${src.label} …"
                    st.loading -> "${src.label} ${st.count}+"
                    else -> "${src.label} ${st.count}"
                }
                Text(
                    text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (st.failed) p.faint else p.sub,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.tile).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        val notes = buildList {
            live.asked?.let { add("‘$it’ → ‘${live.query}’로 찾았어요") }
            if (live.malls.isNotEmpty()) add("공식몰 함께 확인: " + live.malls.joinToString(", "))
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            notes.forEach { Text(it, fontSize = 11.5.sp, color = p.faint, lineHeight = 16.sp) }
        }
    }
}

/** 공유받은 링크: 그 가격이 정말 싼지 앱이 찾은 다른 곳과 비교 */
@Composable
private fun LinkCard(link: LinkState, live: LiveSearchState, ship: Boolean, onClose: () -> Unit, onRemember: () -> Unit) {
    val p = HD.p
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp).cardBg(20.dp, 2.dp).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔗 공유한 상품", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Brand, modifier = Modifier.weight(1f))
            Text("닫기", fontSize = 12.5.sp, color = p.sub, modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        val info = link.info
        when {
            link.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Brand, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("링크의 가격을 읽고 있어요", fontSize = 13.sp, color = p.sub)
            }
            info == null -> Text(
                (link.error ?: "링크를 읽지 못했어요") + "\n상품 이름으로 검색하면 다른 곳 가격과 비교할 수 있어요.",
                fontSize = 13.sp, color = p.sub, lineHeight = 18.sp,
            )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                }) {
                    ProductImage(info.image, "🔗", 64.dp, 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.site, fontSize = 12.sp, color = p.sub, maxLines = 1)
                        Text(info.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                        if (info.price != null) {
                            Text(
                                "${won(if (ship) info.total else info.price)}원" + (info.ship?.let { r -> " · ${if (info.fee == 0L) "무료배송" else "배송비 ${won(info.fee)}원"}" + (r.freeOver?.let { " (${won(it)}원 이상 무료)" } ?: "") } ?: " · 배송비 확인 필요"),
                                fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = p.ink,
                            )
                        } else Text(link.error ?: "가격을 찾지 못했어요", fontSize = 12.5.sp, color = p.sub)
                    }
                }
                // 비교: 공유한 상품 vs 앱이 찾은 다른 곳 (같은 단위가 있으면 단가로)
                val mine = live.results.firstOrNull { it.pcode.startsWith("link:") }
                val others = live.results.filter { !it.pcode.startsWith("link:") && !it.used }
                if (mine != null && others.isNotEmpty() && !live.loading) {
                    val mu = shownUnit(mine, LiveSort.UNIT, ship)
                    val best = sortLive(others, LiveSort.UNIT, ship).firstOrNull { o -> mu != null && shownUnit(o, LiveSort.UNIT, ship)?.per == mu.per }
                    Spacer(Modifier.height(10.dp))
                    if (mu != null && best != null) {
                        val bu = shownUnit(best, LiveSort.UNIT, ship)!!
                        val (title, color) = when {
                            bu.value > mu.value * 1.03 -> "이 링크가 앱이 찾은 다른 곳보다 ${((bu.value - mu.value) * 100 / bu.value)}% 싸요 👍" to Good
                            mu.value > bu.value * 1.03 -> "다른 곳이 ${((mu.value - bu.value) * 100 / mu.value)}% 더 싸요 — ${best.mall ?: best.source.label}" to Brand
                            else -> "다른 곳과 거의 같은 가격이에요" to p.ink
                        }
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                        Text(
                            "단가 비교 · 이 링크 ${if (mu.estimated) "약 " else ""}${won(mu.value)}원/${mu.per} vs 가장 싼 다른 곳 ${if (bu.estimated) "약 " else ""}${won(bu.value)}원/${bu.per}",
                            fontSize = 12.sp, color = p.sub, lineHeight = 17.sp,
                        )
                        mine.unitNote?.let { Text("ⓘ $it", fontSize = 11.sp, color = p.faint, lineHeight = 15.sp) }
                    } else {
                        Text("양(용량·개수)을 맞춰 비교하기 어려워요. 아래 목록에서 직접 비교해 보세요.", fontSize = 12.5.sp, color = p.sub, lineHeight = 17.sp)
                    }
                }
                if (info.platform == "cafe24") {
                    Spacer(Modifier.height(10.dp))
                    if (link.remembered) Text("✓ ${info.site}도 다음 검색부터 함께 찾아봐요", fontSize = 12.5.sp, color = Good, fontWeight = FontWeight.SemiBold)
                    else Text(
                        "＋ 이 쇼핑몰도 다음 검색부터 함께 찾기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Brand,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Brand.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onRemember).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 커뮤니티 핫딜 기록 + '지금 사도 될까?' */
@Composable
private fun PastDeals(
    history: PriceHistory?, bestUnit: io.github.poohcjs.hotdealradar.data.UnitPrice?, bestCost: Long?,
    query: String, watch: PriceWatch?, open: (String) -> Unit, onToggleWatch: () -> Unit,
) {
    val p = HD.p
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp).cardBg(20.dp, 1.dp).padding(16.dp)) {
        Text("지금 사도 될까?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Spacer(Modifier.height(6.dp))
        when {
            history == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Brand, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("뽐뿌·루리웹·클리앙 핫딜 기록을 찾고 있어요", fontSize = 12.5.sp, color = p.sub)
            }
            history.deals.isEmpty() -> Text(
                "최근 커뮤니티 핫딜 기록에는 없어요." + (history.since?.let { " (기록은 ${it.replace('-', '.')}부터 매시간 쌓고 있어요)" } ?: ""),
                fontSize = 12.5.sp, color = p.sub, lineHeight = 17.sp,
            )
            else -> {
                val v = ArchiveSearch.verdict(history, bestUnit, bestCost)
                if (v != null) {
                    Text(v.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (v.good) Good else Brand)
                    Text(v.detail, fontSize = 12.sp, color = p.sub, lineHeight = 17.sp)
                    Spacer(Modifier.height(10.dp))
                }
                Text("커뮤니티에 올라온 핫딜 가격", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                Spacer(Modifier.height(4.dp))
                history.deals.take(5).forEach { d ->
                    val it = d.item
                    Row(
                        Modifier.fillMaxWidth().clickable { open(it.u) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(it.n, fontSize = 13.sp, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${it.date} · ${it.sourceName}" + (if (it.m.isNotBlank()) " · ${it.m}" else "") + (if (it.ended) " · 종료" else ""),
                                fontSize = 11.5.sp, color = p.faint, maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${won(it.total)}원", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                            d.unit?.let { u -> Text("${if (u.estimated) "약 " else ""}${won(u.value)}원/${u.per}", fontSize = 11.sp, color = Good) }
                        }
                    }
                }
                if (history.deals.size > 5) Text("외 ${history.deals.size - 5}건", fontSize = 11.5.sp, color = p.faint)
            }
        }
        Spacer(Modifier.height(12.dp))
        val baseText = bestUnit?.let { "${won(it.value)}원/${it.per}" } ?: bestCost?.let { "${won(it)}원" }
        if (watch != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🔔 ${watch.unit?.let { "${won(it)}원/${watch.per}" } ?: watch.cost?.let { "${won(it)}원" } ?: "지금 최저가"}보다 싼 핫딜이 올라오거나 값이 내려가면 알려 드려요",
                    fontSize = 12.5.sp, color = Good, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.weight(1f),
                )
                Text("끄기", fontSize = 12.5.sp, color = p.sub, modifier = Modifier.clickable(onClick = onToggleWatch).padding(6.dp))
            }
        } else if (baseText != null) {
            Text(
                "🔔 지금 최저가($baseText)보다 싸지면 알림", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Brand,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Brand.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleWatch).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text("커뮤니티 핫딜을 매시간, 다나와·에누리·공식몰을 6시간마다 다시 확인해요", fontSize = 11.5.sp, color = p.faint)
        }
    }
}
