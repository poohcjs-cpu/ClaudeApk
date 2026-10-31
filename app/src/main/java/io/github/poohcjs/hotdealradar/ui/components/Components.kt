package io.github.poohcjs.hotdealradar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.HistoryPoint
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.refLabel
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.theme.Bad
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.Warn

data class ChipOption(val id: String, val label: String, val count: Int? = null, val dot: Color? = null)

@Composable
fun ChipRow(options: List<ChipOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(options, key = { it.id }) { o ->
            FilterChip(
                selected = o.id == selected,
                onClick = { onSelect(o.id) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (o.dot != null) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(o.dot))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(o.label)
                        if (o.count != null) {
                            Spacer(Modifier.width(4.dp))
                            Text("${o.count}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun Thumb(url: String, catIcon: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(catIcon, fontSize = 26.sp)
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp).background(Color.White))
        }
    }
}

@Composable
fun RateBadge(rate: Double?, hot: Boolean, modifier: Modifier = Modifier) {
    if (rate == null) return
    val bg = if (hot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (hot) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            if (rate >= 0) "${pct(rate)}%↓" else "+${pct(-rate)}%",
            color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** 핫딜 목록 한 줄 */
@Composable
fun DealCard(
    x: Item,
    catIcon: String,
    origin: String,
    hot: Boolean,
    isNew: Boolean,
    fav: Boolean,
    onFav: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Thumb(x.image, catIcon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isNew) Tag("NEW", MaterialTheme.colorScheme.primary)
                    if (x.kind == ItemKind.MARKET) Tag("가격비교 발굴", Color(0xFF3A6FD8))
                    if (x.suspicious) Tag("가격 확인 필요", Warn)
                    if (x.ended) Tag("종료")
                    if (x.mall.isNotBlank()) Text(x.mall, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    x.name.ifBlank { x.title }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    textDecoration = if (x.ended) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RateBadge(x.rate, hot)
                    Spacer(Modifier.width(8.dp))
                    Text(if (x.price != null) "${won(x.price)}원" else "가격 미상", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (x.referencePrice != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${refLabel(x)} ${won(x.referencePrice)}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(origin, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (onFav != null) {
                IconButton(onClick = onFav, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painterResource(if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline),
                        contentDescription = if (fav) "찜 해제" else "찜하기",
                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 커뮤니티 글 한 줄 */
@Composable
fun PostRow(x: Item, srcName: String, threshold: Int, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            x.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (x.ended) TextDecoration.LineThrough else null,
            color = if (x.ended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$srcName · ${ago(x.postedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val (label, color) = verdictChip(x, threshold)
            Tag(label, color)
            if ((x.votes ?: 0) > 0) Text("추천 ${x.votes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if ((x.comments ?: 0) > 0) Text("댓글 ${x.comments}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun verdictChip(x: Item, threshold: Int): Pair<String, Color> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val r = x.rate
    return when {
        r != null && x.suspicious -> "평균보다 ${pct(r)}%↓ · 확인 필요" to Warn
        r != null && r >= threshold -> "평균보다 ${pct(r)}%↓" to MaterialTheme.colorScheme.primary
        r != null && r >= 3 -> "평균보다 ${pct(r)}%↓" to Good
        r != null && r > -3 -> "평균 수준" to muted
        r != null -> "평균보다 비쌈" to Bad
        x.verifyStatus == "pending" -> "비교 대기" to muted
        x.verifyStatus == "nomatch" -> "같은 제품 못 찾음" to muted
        x.verifyStatus == "skipped" -> shortReason(x.verifyReason) to muted
        x.verifyStatus == "error" -> "비교 오류" to muted
        else -> "비교 안 함" to muted
    }
}

private fun shortReason(reason: String?): String = when {
    reason == null -> "비교 안 함"
    "상품권" in reason -> "상품권·쿠폰"
    "디지털" in reason -> "디지털 콘텐츠"
    "해외" in reason -> "해외 딜"
    "가격을 찾지" in reason -> "가격 없음"
    "끝난" in reason -> "종료"
    "무료" in reason -> "무료"
    "여러 개" in reason -> "가격 여러 개"
    else -> "비교 안 함"
}

/** 최저가·평균가 꺾은선 그래프 */
@Composable
fun PriceChart(points: List<HistoryPoint>, modifier: Modifier = Modifier) {
    val lowColor = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val pts = points.filter { it.lowest != null }
    if (pts.size < 2) {
        Text("가격 기록이 아직 부족해요. 며칠 지나면 그래프가 보여요.", fontSize = 13.sp, color = avgColor, modifier = modifier)
        return
    }
    val values = pts.flatMap { listOfNotNull(it.lowest, it.average) }
    val min = values.min().toFloat()
    val max = values.max().toFloat().let { if (it == min) min + 1 else it }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = if (pts.size == 1) w / 2 else w * i / (pts.size - 1)
            fun y(v: Long) = h - (v - min) / (max - min) * (h * 0.85f) - h * 0.075f
            for (k in 0..3) {
                val gy = h * k / 3
                drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            fun line(sel: (HistoryPoint) -> Long?, color: Color, dashed: Boolean) {
                val path = Path()
                var started = false
                pts.forEachIndexed { i, p ->
                    val v = sel(p) ?: return@forEachIndexed
                    if (!started) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                    started = true
                }
                drawPath(path, color, style = Stroke(width = 4f, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null))
            }
            line({ it.average }, avgColor, true)
            line({ it.lowest }, lowColor, false)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pts.first().date.substring(5), fontSize = 11.sp, color = avgColor)
            Text("— 최저가  - - 평균가 · ${won(min.toLong())}~${won(max.toLong())}원", fontSize = 11.sp, color = avgColor)
            Text(pts.last().date.substring(5), fontSize = 11.sp, color = avgColor)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun EmptyNote(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
