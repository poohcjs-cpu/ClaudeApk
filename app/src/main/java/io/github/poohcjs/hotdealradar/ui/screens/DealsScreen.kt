package io.github.poohcjs.hotdealradar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.poohcjs.hotdealradar.data.sortDeals
import io.github.poohcjs.hotdealradar.ui.components.ChipOption
import io.github.poohcjs.hotdealradar.ui.components.ChipRow
import io.github.poohcjs.hotdealradar.ui.components.DealCard
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote

fun originLabel(x: Item, index: Index): String = when (x.kind) {
    ItemKind.PRODUCT -> if (x.postedAt != null) "다나와 · ${ago(x.postedAt)} 확인" else "다나와"
    ItemKind.MARKET -> "가격비교 발굴 · ${ago(x.detectedAt)}부터"
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
    var cat by rememberSaveable { mutableStateOf("all") }
    var kind by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf(DealSort.DISCOUNT) }

    val scoped = index.deals.filter { dealVisible(it, s) && inHomeScope(it, s) }
    val hot = scoped.filter { isHot(it, s) }
    val byKind = when (kind) {
        "community" -> hot.filter { it.kind == ItemKind.COMMUNITY }
        "market" -> hot.filter { it.kind == ItemKind.MARKET }
        else -> hot
    }
    val catIds = (if (interestActive(s)) index.categories.filter { it.id in s.interests } else index.categories)
        .map { it.id }.filter { it != "etc" || byKind.any { d -> d.cat == "etc" } }
    val shown = sortDeals(if (cat == "all") byKind else byKind.filter { it.cat == cat }, sort, newCutoff)
    val live = hot.count { !it.ended }
    val newN = hot.count { !it.ended && isNew(it, newCutoff) }
    val commOn = index.file.sources.count { it.id != "danawa" && it.enabled && it.status == "ok" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val gen = parseTime(index.file.generatedAt)
                Text(
                    "${ago(gen)} 수집 · 커뮤니티 ${commOn}곳 + 다나와 · ${if (interestActive(s)) "관심 제품군 ${s.interests.size}개" else "전체 제품군"}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "쇼핑몰 평균가보다 ${s.threshold}% 이상 싼 핫딜 ${live}개",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                if (newN > 0 && sort != DealSort.NEW) {
                    TextButton(onClick = { sort = DealSort.NEW }, contentPadding = PaddingValues(0.dp)) {
                        Text("● ${if (firstVisit) "최근 3시간 사이" else "지난 방문 뒤"} 새로 뜬 핫딜 ${newN}개 보기")
                    }
                }
            }
        }
        item {
            ChipRow(
                listOf(ChipOption("all", if (interestActive(s)) "관심 전체" else "전체", byKind.size)) +
                    catIds.map { id -> ChipOption(id, "${catIcon(index, id)} ${index.categories.first { it.id == id }.name}", byKind.count { it.cat == id }) },
                cat, { cat = it },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val kinds = listOf("all" to "전체", "community" to "커뮤니티", "market" to "발굴")
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    kinds.forEachIndexed { i, (id, label) ->
                        SegmentedButton(
                            selected = kind == id, onClick = { kind = id },
                            shape = SegmentedButtonDefaults.itemShape(i, kinds.size), label = { Text(label, fontSize = 12.sp) },
                        )
                    }
                }
            }
        }
        item {
            ChipRow(DealSort.entries.map { ChipOption(it.name, it.label) }, sort.name, { sort = DealSort.valueOf(it) })
        }
        if (index.deals.isEmpty() && index.posts.isEmpty()) {
            item { EmptyNote("핫딜을 찾을 준비를 하고 있어요", "수집기가 처음 돌고 있어요. 30분쯤 뒤에 다시 열어 보세요.") }
        } else if (shown.isEmpty()) {
            item {
                EmptyNote(
                    "지금은 기준을 넘는 핫딜이 없어요",
                    "설정에서 핫딜 기준(지금 ${s.threshold}%)을 낮추거나 관심 제품군을 넓혀 보세요. 커뮤니티 탭에서 모든 글을 볼 수 있어요.",
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
        item { Spacer(Modifier.height(24.dp)) }
    }
}
