package io.github.poohcjs.hotdealradar.ui.screens

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.ui.components.ChipOption
import io.github.poohcjs.hotdealradar.ui.components.ChipRow
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.components.PostRow
import io.github.poohcjs.hotdealradar.ui.theme.Bad
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.Warn

fun statusColor(status: String): Color? = when (status) {
    "ok" -> Good
    "off" -> null
    "waiting" -> Color(0xFF9A958E)
    "unavailable", "warn", "empty", "parse_error", "error" -> Warn
    else -> Bad
}

@Composable
fun CommunityScreen(
    index: Index,
    s: Settings,
    contentPadding: PaddingValues,
    onHideEnded: (Boolean) -> Unit,
    onOpen: (Item) -> Unit,
) {
    var src by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("new") }
    var cmpOnly by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable { mutableIntStateOf(60) }

    val srcs = index.file.sources.filter { it.id != "danawa" && (it.enabled || index.posts.any { p -> p.source == it.id }) }
    val base = index.posts.filter { !(s.hideEnded && it.ended) && (!cmpOnly || it.rate != null) }
    val list = (if (src == "all") base else base.filter { it.source == src }).let { l ->
        when (sort) {
            "discount" -> l.sortedByDescending { it.rate ?: -999.0 }
            "hot" -> l.sortedByDescending { (it.votes ?: 0) * 3 + (it.comments ?: 0) }
            else -> l.sortedByDescending { it.postedAt ?: 0 }
        }
    }
    val checked = list.count { it.rate != null }
    val hotN = list.count { (it.rate ?: -1.0) >= s.threshold }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("커뮤니티", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "핫딜 게시판에 최근 ${index.file.params.keepHours}시간 동안 올라온 글이에요. 다나와에서 같은 제품을 찾으면 다른 쇼핑몰 평균가와 비교한 결과를 붙였어요.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ChipRow(
                listOf(ChipOption("all", "전체", base.size)) + srcs.map { st ->
                    ChipOption(st.id, index.srcName(st.id), base.count { it.source == st.id }, statusColor(if (st.enabled) st.status else "off"))
                },
                src, { src = it; limit = 60 },
            )
        }
        item {
            ChipRow(listOf(ChipOption("new", "최신순"), ChipOption("discount", "할인율"), ChipOption("hot", "반응순")), sort, { sort = it })
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = cmpOnly, onCheckedChange = { cmpOnly = it })
                Text("비교된 글만", fontSize = 13.sp)
                Spacer(Modifier.padding(8.dp))
                Checkbox(checked = s.hideEnded, onCheckedChange = onHideEnded)
                Text("끝난 딜 숨기기", fontSize = 13.sp)
            }
        }
        item {
            Text(
                "글 ${list.size}개 · 비교됨 ${checked}개 · 기준(${s.threshold}%) 이상 ${hotN}개",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (list.isEmpty()) item { EmptyNote("글이 없어요", "다른 게시판을 고르거나 필터를 풀어 보세요.") }
        items(list.take(limit), key = { it.key }) { p ->
            PostRow(p, index.srcName(p.source), s.threshold) { onOpen(index.itemForPost(p)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (list.size > limit) {
            item {
                androidx.compose.material3.TextButton(onClick = { limit += 60 }, modifier = Modifier.fillMaxWidth()) {
                    Text("더 보기 (${list.size - limit}개 남음)")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
