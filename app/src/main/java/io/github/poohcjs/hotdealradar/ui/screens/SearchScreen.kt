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
import io.github.poohcjs.hotdealradar.data.LiveOption
import io.github.poohcjs.hotdealradar.data.LiveProduct
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.defaultLiveSort
import io.github.poohcjs.hotdealradar.data.isHot
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.search
import io.github.poohcjs.hotdealradar.data.sortLive
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.LiveSearchState
import io.github.poohcjs.hotdealradar.ui.components.DealCard
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.components.Pill
import io.github.poohcjs.hotdealradar.ui.components.ProductImage
import io.github.poohcjs.hotdealradar.ui.components.SortTabs
import io.github.poohcjs.hotdealradar.ui.components.Tag
import io.github.poohcjs.hotdealradar.ui.components.cardBg
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.HD
import io.github.poohcjs.hotdealradar.ui.theme.Warn

private val SUGGESTIONS = listOf("화장지", "생수 2L", "물티슈", "커피 캡슐", "에어팟", "세탁세제", "즉석밥")

/** 정렬에 맞는 대표 옵션: 단가순이면 단가가 가장 낮은 묶음, 가격순이면 가장 싼 묶음 */
fun pickOption(p: LiveProduct, sort: LiveSort): LiveOption? = when (sort) {
    LiveSort.UNIT -> p.bestUnitOption ?: p.options.minByOrNull { it.price }
    LiveSort.PRICE -> p.options.minByOrNull { it.price }
    LiveSort.RANK -> p.options.firstOrNull { it.pcode == p.pcode } ?: p.options.minByOrNull { it.price }
}

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
) {
    val p = HD.p
    var q by rememberSaveable { mutableStateOf(live.query) }
    var submitted by rememberSaveable { mutableStateOf(live.query) }
    var tab by rememberSaveable { mutableStateOf("live") }
    var sortName by rememberSaveable { mutableStateOf<String?>(null) }
    var hideUsed by rememberSaveable { mutableStateOf(true) }
    var recents by remember { mutableStateOf(recent) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val local = remember(submitted, index) { search(index, submitted).sortedByDescending { it.rate ?: -999.0 } }

    fun submit(text: String = q) {
        val t = text.trim()
        q = t
        if (t.length < 2) return
        submitted = t
        sortName = null
        recents = (listOf(t) + recents.filter { it != t }).take(8)
        onRecent(recents)
        onSearchLive(t)
        focus.clearFocus()
    }

    val liveList = live.results.filter { !(hideUsed && it.used) }
    val sort = sortName?.let { LiveSort.valueOf(it) } ?: defaultLiveSort(liveList)
    val sorted = sortLive(liveList, sort)

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

        if (submitted.isEmpty()) {
            item { SmallLabel("이런 걸 많이 찾아요") }
            item { ChipLine(SUGGESTIONS) { submit(it) } }
            if (recents.isNotEmpty()) {
                item { SmallLabel("최근 검색") }
                item { ChipLine(recents) { submit(it) } }
            }
            item {
                Text(
                    "검색하면 다나와에 실시간으로 물어 여러 쇼핑몰 중 가장 싼 값을 보여 줘요. 화장지·생수처럼 양이 다른 상품은 단가(예: 10m당, 1L당)로 비교해요.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = p.sub, modifier = Modifier.padding(20.dp),
                )
            }
            return@LazyColumn
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("실시간 최저가", tab == "live", { tab = "live" }, count = if (live.loading) null else sorted.size)
                Pill("핫딜·커뮤니티", tab == "local", { tab = "local" }, count = local.size)
            }
        }

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
                item { OtherSites(submitted) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            }
            sorted.isEmpty() -> {
                item { EmptyNote("검색 결과가 없어요", "다른 이름으로 찾아보거나 다나와에서 직접 볼 수 있어요.", "🔎") }
                item { OtherSites(submitted) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            }
            else -> {
                val top = sorted.first()
                val topOpt = pickOption(top, sort)
                item { BestPick(top, topOpt, sort, index) { onOpenLive(topOpt?.pcode ?: top.pcode) } }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SortTabs(LiveSort.entries.map { it.name to it.label }, sort.name, { sortName = it })
                        Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "다나와 ${live.results.size}개 상품 · ${ago(live.searchedAt)} 확인", fontSize = 12.sp, color = p.faint,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (hideUsed) "✓ 중고·리퍼 뺌" else "중고·리퍼 포함", fontSize = 12.sp, color = p.sub, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { hideUsed = !hideUsed }.padding(4.dp),
                        )
                    }
                }
                items(sorted.drop(1), key = { "live-" + it.pcode }) { prod ->
                    val opt = pickOption(prod, sort)
                    LiveRow(prod, opt, sort, index, fav = (opt?.pcode ?: prod.pcode) in favs) { onOpenLive(opt?.pcode ?: prod.pcode) }
                }
                item { OtherSites(submitted) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

fun danawaSearchUrl(q: String) = "https://search.danawa.com/?query=" + Uri.encode(q.trim())

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

fun naverShoppingUrl(q: String) = "https://search.shopping.naver.com/ns/search?query=" + Uri.encode(q.trim())

/** 다나와에 없거나 더 찾아보고 싶을 때: 같은 검색어로 네이버쇼핑·다나와를 바로 연다 (앱이 대신 읽지 않고 사용자가 직접 봄) */
@Composable
private fun OtherSites(q: String, open: (String) -> Unit) {
    val p = HD.p
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("다른 곳에서도 찾아보기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Spacer(Modifier.height(3.dp))
        Text("다나와에 없는 상품은 네이버쇼핑에 있는 경우가 많아요", fontSize = 12.5.sp, color = p.sub)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "N  네이버쇼핑에서 보기", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.4f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF03C75A))
                    .clickable { open(naverShoppingUrl(q)) }.padding(vertical = 15.dp),
            )
            Text(
                "다나와  ↗", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .background(p.card).clickable { open(danawaSearchUrl(q)) }.padding(vertical = 15.dp),
            )
        }
    }
}

/** 우리 핫딜 데이터에 같은 제품 딜이 있으면 그 딜 */
private fun liveDeal(index: Index, pcodes: Collection<String>): Item? =
    pcodes.flatMap { index.dealsByPcode[it].orEmpty() }.filter { !it.ended }.minByOrNull { it.price ?: Long.MAX_VALUE }

@Composable
private fun BestPick(prod: LiveProduct, opt: LiveOption?, sort: LiveSort, index: Index, onClick: () -> Unit) {
    val p = HD.p
    val price = opt?.price ?: prod.price
    val unit = opt?.unit ?: prod.unit
    val malls = opt?.malls ?: prod.malls
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(24.dp)).background(p.hero).clickable(onClick = onClick).padding(20.dp),
    ) {
        Text(
            if (sort == LiveSort.UNIT) "🏆 단가가 가장 싼 상품" else "🏆 가장 싼 상품",
            color = Color(0xFFFFB38F), fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
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
                    Text(won(price), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                    Text("원", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unit != null) HeroChip("${unit.value}원 / ${unit.per}")
            if (malls != null) HeroChip("쇼핑몰 ${malls}곳 비교")
            liveDeal(index, listOfNotNull(prod.pcode) + prod.options.map { it.pcode })?.let { d -> HeroChip("🔥 핫딜 ${pct(d.rate)}%↓") }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun LiveRow(prod: LiveProduct, opt: LiveOption?, sort: LiveSort, index: Index, fav: Boolean, onClick: () -> Unit) {
    val p = HD.p
    val price = opt?.price ?: prod.price
    val unit = opt?.unit ?: prod.unit
    val malls = opt?.malls ?: prod.malls
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).cardBg(18.dp, 2.dp).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductImage(prod.image, "🛒", 72.dp, 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text("${won(price)}원", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                if (unit != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${unit.value}원/${unit.per}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (sort == LiveSort.UNIT) Good else p.sub, modifier = Modifier.padding(bottom = 1.dp),
                    )
                }
            }
            if (malls != null) Text("쇼핑몰 ${malls}곳 중 최저가", fontSize = 11.5.sp, color = p.faint)
        }
    }
}
