package io.github.poohcjs.hotdealradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.ui.components.ChipOption
import io.github.poohcjs.hotdealradar.ui.components.ChipRow
import io.github.poohcjs.hotdealradar.ui.components.EmptyNote
import io.github.poohcjs.hotdealradar.ui.components.Pill
import io.github.poohcjs.hotdealradar.ui.components.PostRow
import io.github.poohcjs.hotdealradar.ui.components.SortTabs
import io.github.poohcjs.hotdealradar.ui.theme.Bad
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.HD
import io.github.poohcjs.hotdealradar.ui.theme.Warn

fun statusColor(status: String): Color? = when (status) {
    "ok" -> Good
    "off" -> null
    "waiting" -> Color(0xFF9A958E)
    "unavailable", "warn", "empty", "parse_error", "error" -> Warn
    else -> Bad
}

@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    val p = HD.p
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, letterSpacing = (-0.7).sp)
        if (sub != null) {
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 13.5.sp, lineHeight = 19.sp, color = p.sub)
        }
    }
}

@Composable
fun CommunityScreen(
    index: Index,
    s: Settings,
    contentPadding: PaddingValues,
    onHideEnded: (Boolean) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val p = HD.p
    var src by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("new") }
    var cmpOnly by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable { mutableIntStateOf(60) }

    val srcs = index.file.sources.filter { it.id != "danawa" && (it.enabled || index.posts.any { pp -> pp.source == it.id }) }
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
            ScreenTitle(
                "커뮤니티",
                "핫딜 게시판에 최근 ${index.file.params.keepHours}시간 동안 올라온 글이에요. 다나와에서 같은 제품을 찾으면 다른 쇼핑몰 평균가와 비교한 결과를 붙였어요.",
            )
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(if (cmpOnly) "✓ 비교된 글만" else "비교된 글만", cmpOnly, { cmpOnly = !cmpOnly })
                Pill(if (s.hideEnded) "✓ 끝난 딜 숨김" else "끝난 딜 숨김", s.hideEnded, { onHideEnded(!s.hideEnded) })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("글 ${list.size} · 비교 $checked · 기준 이상 $hotN", fontSize = 12.5.sp, color = p.faint, modifier = Modifier.weight(1f))
                SortTabs(listOf("new" to "최신순", "discount" to "할인율", "hot" to "반응순"), sort, { sort = it })
            }
        }
        if (list.isEmpty()) item { EmptyNote("글이 없어요", "다른 게시판을 고르거나 필터를 풀어 보세요.", "📭") }
        itemsIndexed(list.take(limit), key = { _, it -> it.key }) { i, pp ->
            Column(Modifier.fillMaxWidth().background(p.card)) {
                if (i > 0) HorizontalDivider(color = p.line, modifier = Modifier.padding(start = 68.dp))
                PostRow(pp, index.srcName(pp.source), s.threshold) { onOpen(index.itemForPost(pp)) }
            }
        }
        if (list.size > limit) {
            item {
                Text(
                    "더 보기 (${list.size - limit}개 남음)", textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = p.sub,
                    modifier = Modifier.fillMaxWidth().background(p.card).clickable { limit += 60 }.padding(18.dp),
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}
