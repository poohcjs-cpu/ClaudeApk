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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.isHot
import io.github.poohcjs.hotdealradar.data.search
import io.github.poohcjs.hotdealradar.ui.components.DealCard
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.components.Pill
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.HD

@Composable
fun SearchScreen(
    index: Index,
    s: Settings,
    favs: Map<String, Fav>,
    recent: List<String>,
    onRecent: (List<String>) -> Unit,
    contentPadding: PaddingValues,
    onOpen: (Item) -> Unit,
    onFav: (Item) -> Unit,
) {
    val p = HD.p
    var q by rememberSaveable { mutableStateOf("") }
    var recents by remember { mutableStateOf(recent) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val results = remember(q, index) { search(index, q).sortedByDescending { it.rate ?: -999.0 } }

    fun commit() {
        val t = q.trim()
        if (t.length >= 2) {
            recents = (listOf(t) + recents.filter { it != t }).take(8)
            onRecent(recents)
        }
        focus.clearFocus()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle("검색") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(p.card)
                    .border(1.5.dp, if (q.isNotEmpty()) p.ink else p.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = p.sub, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("제품 이름 (예: 에어팟, 990 PRO)", fontSize = 15.5.sp, color = p.faint)
                    BasicTextField(
                        value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 15.5.sp, color = p.ink, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Brand),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { commit() }),
                    )
                }
                if (q.isNotEmpty()) {
                    Text("지우기", fontSize = 13.sp, color = p.sub, modifier = Modifier.clickable { q = "" }.padding(start = 8.dp))
                }
            }
        }
        if (q.isBlank()) {
            if (recents.isNotEmpty()) {
                item {
                    Text("최근 검색", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp))
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recents) { r -> Pill(r, false, { q = r }) }
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "지켜보는 제품 ${index.products.size}개와 커뮤니티 글 ${index.posts.size}개에서 찾아요. 없으면 다나와에서 바로 찾아볼 수 있어요.",
                        fontSize = 13.5.sp, lineHeight = 20.sp, color = p.sub,
                    )
                }
            }
        } else if (q.trim().length >= 2) {
            item { Spacer(Modifier.height(10.dp)) }
            if (results.isEmpty()) {
                item { EmptyNote("찾는 제품이 없어요", "아래 버튼으로 다나와에서 바로 찾아볼 수 있어요.", "🔎") }
            }
            items(results, key = { it.key }) { x ->
                DealCard(
                    x = x, catIcon = catIcon(index, x.cat), origin = originLabel(x, index), hot = isHot(x, s), isNew = false,
                    fav = x.pcode != null && x.pcode in favs,
                    onFav = if (x.pcode != null && x.kind != ItemKind.POST) ({ onFav(x) }) else null,
                    onClick = { commit(); onOpen(if (x.kind == ItemKind.POST) index.itemForPost(x) else x) },
                )
            }
            item {
                Text(
                    "다나와에서 \"${q.trim()}\" 찾기  ↗", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink,
                    modifier = Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(16.dp))
                        .border(1.dp, p.line, RoundedCornerShape(16.dp)).background(p.card)
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://search.danawa.com/?query=" + Uri.encode(q.trim())))) }
                        .padding(vertical = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
