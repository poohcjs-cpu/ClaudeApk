package io.github.poohcjs.hotdealradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.targetReached
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.components.HeartButton
import io.github.poohcjs.hotdealradar.ui.components.ProductImage
import io.github.poohcjs.hotdealradar.ui.components.Tag
import io.github.poohcjs.hotdealradar.ui.components.cardBg
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.HD

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
    val p = HD.p
    val entries = favs.entries.sortedWith(
        compareByDescending<Map.Entry<String, Fav>> { targetReached(it.key, favs, index) }.thenByDescending { it.value.savedAt },
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ScreenTitle(
                "찜",
                "제품 단위로 찜해요. 상세 화면에서 목표가를 정하면, 진행 중인 딜과 다나와 최저가 중 싼 값이 목표가 아래로 내려올 때 알려 드려요.",
            )
        }
        if (entries.isEmpty()) item { EmptyNote("찜한 제품이 없어요", "핫딜 카드의 ♡를 눌러 찜해 보세요.", "💝") }
        items(entries, key = { it.key }) { (pcode, fav) ->
            val x = savedItem(index, pcode, fav)
            val best = index.bestNow(pcode)
            val reached = targetReached(pcode, favs, index)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).cardBg(18.dp, 2.dp).clickable { onOpen(x) }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    ProductImage(x.image.ifBlank { fav.image }, catIcon(index, x.cat), 72.dp, 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (reached) Tag("🎯 목표가 도달", Brand, filled = true)
                        if (reached) Spacer(Modifier.height(5.dp))
                        Text(fav.name.ifBlank { x.name }, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        if (best != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${won(best.price)}원", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                                Spacer(Modifier.width(6.dp))
                                Text("${best.mall} · ${best.via}", fontSize = 12.sp, color = p.faint, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        } else {
                            Text("지금 가격 정보가 없어요", fontSize = 13.sp, color = p.faint)
                        }
                        val d = fav.savedPrice?.let { sp -> best?.price?.minus(sp) }
                        if (d != null && d != 0L) {
                            Text(
                                if (d < 0) "찜할 때보다 ${won(-d)}원 내렸어요" else "찜할 때보다 ${won(d)}원 올랐어요",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (d < 0) Good else p.sub,
                            )
                        }
                    }
                    HeartButton(true, { onFav(x) }, size = 32.dp)
                }
                val target = fav.target
                if (target != null && best != null) {
                    Spacer(Modifier.height(12.dp))
                    val start = (fav.savedPrice ?: best.price).coerceAtLeast(target + 1)
                    val progress = ((start - best.price).toFloat() / (start - target)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(p.line)) {
                        Box(Modifier.fillMaxWidth(if (reached) 1f else progress.coerceAtLeast(0.04f)).fillMaxHeight().clip(CircleShape).background(p.brandBrush))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (reached) "목표가 ${won(target)}원에 닿았어요" else "목표가 ${won(target)}원까지 ${won(best.price - target)}원 남았어요",
                        fontSize = 12.sp, color = p.sub,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}
