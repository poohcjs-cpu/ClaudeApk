package io.github.poohcjs.hotdealradar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.isHot
import io.github.poohcjs.hotdealradar.data.targetReached
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.components.DealCard
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote

/** 찜한 제품을 화면용 항목으로: 진행 중인 딜이 가장 싸면 그 딜, 아니면 다나와 제품 */
fun savedItem(index: Index, pcode: String, fav: Fav): Item {
    val best = index.bestNow(pcode)
    best?.deal?.let { return it }
    index.products[pcode]?.let { return index.productItem(it) }
    return Item(
        key = "prod:$pcode", kind = ItemKind.PRODUCT, name = fav.name, title = fav.name, mall = fav.mall,
        price = fav.savedPrice, cat = fav.cat, image = fav.image, pcode = pcode,
        url = "https://prod.danawa.com/info/?pcode=$pcode",
    )
}

@Composable
fun SavedScreen(
    index: Index,
    s: Settings,
    favs: Map<String, Fav>,
    contentPadding: PaddingValues,
    onOpen: (Item) -> Unit,
    onFav: (Item) -> Unit,
) {
    val entries = favs.entries.sortedWith(
        compareByDescending<Map.Entry<String, Fav>> { targetReached(it.key, favs, index) }.thenByDescending { it.value.savedAt },
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("찜", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "제품 단위로 찜해요. 상세 화면에서 목표가를 정하면, 진행 중인 딜과 다나와 최저가 중 싼 값이 목표가 아래로 내려올 때 알려 드려요.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entries.isEmpty()) item { EmptyNote("찜한 제품이 없어요", "핫딜 카드의 ♡를 눌러 찜해 보세요.") }
        items(entries, key = { it.key }) { (pcode, fav) ->
            val x = savedItem(index, pcode, fav)
            val best = index.bestNow(pcode)
            val reached = targetReached(pcode, favs, index)
            val line = buildString {
                if (best != null) append("지금 ${won(best.price)}원 (${best.via})") else append("지금 가격 정보 없음")
                fav.savedPrice?.let { sp ->
                    if (best != null && best.price != sp) {
                        val d = best.price - sp
                        append(if (d < 0) " · 찜할 때보다 ${won(-d)}원 ↓" else " · 찜할 때보다 ${won(d)}원 ↑")
                    }
                }
                fav.target?.let { append(" · 목표 ${won(it)}원") }
            }
            DealCard(
                x = x, catIcon = catIcon(index, x.cat), origin = line, hot = isHot(x, s), isNew = false, fav = true,
                onFav = { onFav(x) }, onClick = { onOpen(x) },
            )
            if (reached) {
                Text(
                    "🎯 목표가에 닿았어요", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 6.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
