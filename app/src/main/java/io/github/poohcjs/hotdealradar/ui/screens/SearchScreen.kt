package io.github.poohcjs.hotdealradar.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
        item {
            Column(Modifier.padding(16.dp)) {
                Text("검색", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("제품 이름 (예: 에어팟, 990 PRO)") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { commit() }),
                )
            }
        }
        if (q.isBlank() && recents.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(recents) { r -> AssistChip(onClick = { q = r }, label = { Text(r) }) }
                }
            }
        }
        if (q.isBlank()) {
            item {
                Text(
                    "지켜보는 제품 ${index.products.size}개와 커뮤니티 글 ${index.posts.size}개에서 찾아요.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                )
            }
        } else if (q.trim().length >= 2) {
            if (results.isEmpty()) {
                item { EmptyNote("찾는 제품이 없어요", "다나와에서 바로 찾아볼 수 있어요.") }
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
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://search.danawa.com/?query=" + Uri.encode(q.trim()))))
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("다나와에서 \"${q.trim()}\" 찾기") }
            }
        }
    }
}
