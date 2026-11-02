package io.github.poohcjs.hotdealradar.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.DealSort
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.dealVisible
import io.github.poohcjs.hotdealradar.data.inHomeScope
import io.github.poohcjs.hotdealradar.data.interestActive
import io.github.poohcjs.hotdealradar.data.isHot
import io.github.poohcjs.hotdealradar.data.isNew
import io.github.poohcjs.hotdealradar.data.parseTime
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.sortDeals
import io.github.poohcjs.hotdealradar.ui.components.ChipOption
import io.github.poohcjs.hotdealradar.ui.components.ChipRow
import io.github.poohcjs.hotdealradar.ui.components.DealCard
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.components.SectionHeader
import io.github.poohcjs.hotdealradar.ui.components.SortTabs
import io.github.poohcjs.hotdealradar.ui.components.TopDealCard
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.HD

fun originLabel(x: Item, index: Index): String = when (x.kind) {
    ItemKind.PRODUCT -> if (x.postedAt != null) "다나와 · ${ago(x.postedAt)} 확인" else "다나와"
    ItemKind.MARKET -> "다나와 가격비교 발굴 · ${ago(x.detectedAt)}"
    else -> {
        val ms = x.mentions.ifEmpty { null }
        val first = index.srcName(ms?.first()?.source ?: x.source)
        val more = (ms?.size ?: 1) - 1
        "$first${if (more > 0) " 외 ${more}곳" else ""} · ${ago(x.postedAt)}"
    }
}

fun catIcon(index: Index, id: String) = index.categories.firstOrNull { it.id == id }?.icon ?: "🛒"

@Composable
fun DealsScreen(
    index: Index,
    s: Settings,
    favs: Map<String, Fav>,
    newCutoff: Long,
    firstVisit: Boolean,
    contentPadding: PaddingValues,
    onOpen: (Item) -> Unit,
    onFav: (Item) -> Unit,
) {
    val p = HD.p
    var cat by rememberSaveable { mutableStateOf("all") }
    var kind by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf(DealSort.DISCOUNT) }

    val scoped = index.deals.filter { dealVisible(it, s) && inHomeScope(it, s) }
    val hot = scoped.filter { isHot(it, s) }
    val live = hot.filter { !it.ended }
    val top = live.filter { !it.suspicious }.sortedByDescending { it.rate ?: 0.0 }.take(5)
    val byKind = when (kind) {
        "community" -> hot.filter { it.kind == ItemKind.COMMUNITY }
        "market" -> hot.filter { it.kind == ItemKind.MARKET }
        else -> hot
    }
    val catIds = (if (interestActive(s)) index.categories.filter { it.id in s.interests } else index.categories)
        .map { it.id }.filter { id -> byKind.any { d -> d.cat == id } }
    val shown = sortDeals(if (cat == "all") byKind else byKind.filter { it.cat == cat }, sort, newCutoff)
    val newN = live.count { isNew(it, newCutoff) }
    val maxRate = live.maxOfOrNull { it.rate ?: 0.0 }
    val avgRate = live.mapNotNull { it.rate }.takeIf { it.isNotEmpty() }?.average()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        // 머리
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(p.brandBrush), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_fire), null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("핫딜 레이더", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, letterSpacing = (-0.5).sp)
                Spacer(Modifier.weight(1f))
                Text("${ago(parseTime(index.file.generatedAt))} 업데이트", fontSize = 12.sp, color = p.faint)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(26.dp)).background(p.hero).padding(22.dp),
            ) {
                Text(
                    "쇼핑몰 평균보다 ${s.threshold}% 이상 싸고\n다른 곳보다도 싼 딜만 골랐어요",
                    color = Color.White.copy(alpha = 0.72f), fontSize = 13.5.sp, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("오늘의 핫딜", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                    Spacer(Modifier.width(8.dp))
                    Text("${live.size}", color = Color(0xFFFF8A5B), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Text("개", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat("최대 할인", maxRate?.let { "${pct(it)}%" } ?: "-", Modifier.weight(1f))
                    HeroStat("평균 할인", avgRate?.let { "${pct(it)}%" } ?: "-", Modifier.weight(1f))
                    HeroStat(
                        if (firstVisit) "최근 3시간" else "새로 뜬 딜", "${newN}개", Modifier.weight(1f),
                        onClick = if (newN > 0) ({ sort = DealSort.NEW }) else null,
                    )
                }
            }
        }

        if (top.size >= 2) {
            item { SectionHeader("지금 가장 싼 TOP ${top.size}", "평균가 대비 할인율이 가장 큰 딜") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(top, key = { _, x -> "top-" + x.key }) { i, x ->
                        TopDealCard(
                            x, catIcon(index, x.cat), i + 1, x.pcode != null && x.pcode in favs,
                            if (x.pcode != null) ({ onFav(x) }) else null,
                        ) { onOpen(x) }
                    }
                }
            }
        }

        item { SectionHeader("전체 핫딜", if (interestActive(s)) "관심 제품군 ${s.interests.size}개만 보고 있어요" else null) }
        item {
            ChipRow(
                listOf(ChipOption("all", "전체", byKind.size)) +
                    catIds.map { id -> ChipOption(id, "${catIcon(index, id)} ${index.categories.first { it.id == id }.name}", byKind.count { it.cat == id }) },
                cat, { cat = it },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortTabs(listOf("all" to "모두", "community" to "커뮤니티", "market" to "가격비교 발굴"), kind, { kind = it })
                Spacer(Modifier.weight(1f))
            }
        }
        item {
            SortTabs(
                DealSort.entries.map { it.name to it.label }, sort.name, { sort = DealSort.valueOf(it) },
                Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
            )
        }
        if (index.deals.isEmpty() && index.posts.isEmpty()) {
            item { EmptyNote("핫딜을 찾을 준비를 하고 있어요", "수집기가 처음 돌고 있어요. 1시간쯤 뒤에 다시 열어 보세요.", "⏳") }
        } else if (shown.isEmpty()) {
            item {
                EmptyNote(
                    "지금은 조건에 맞는 핫딜이 없어요",
                    "새 딜은 1시간마다 들어와요. 커뮤니티 탭에서 모든 글을, 검색 탭에서 실시간 최저가를 볼 수 있어요.", "🛒",
                )
            }
        }
        items(shown, key = { it.key }) { x ->
            DealCard(
                x = x, catIcon = catIcon(index, x.cat), origin = originLabel(x, index), hot = true,
                isNew = isNew(x, newCutoff) && !x.ended, fav = x.pcode != null && x.pcode in favs,
                onFav = if (x.pcode != null) ({ onFav(x) }) else null, onClick = { onOpen(x) },
            )
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(Brand))
            }
        }
    }
}
