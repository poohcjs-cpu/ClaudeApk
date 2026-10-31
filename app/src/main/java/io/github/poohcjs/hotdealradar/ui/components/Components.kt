package io.github.poohcjs.hotdealradar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.HistoryPoint
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Tier
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.refLabel
import io.github.poohcjs.hotdealradar.data.tier
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.theme.Bad
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.Discover
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.HD
import io.github.poohcjs.hotdealradar.ui.theme.Warn

// ── 바탕 ───────────────────────────────────────────────────

/** 부드러운 그림자가 있는 흰 카드 */
fun Modifier.card(radius: Dp = 20.dp, elevation: Dp = 3.dp): Modifier = composedCard(RoundedCornerShape(radius), elevation)

private fun Modifier.composedCard(shape: Shape, elevation: Dp): Modifier =
    this.shadow(elevation, shape, clip = false, ambientColor = Color(0x14101828), spotColor = Color(0x1F101828))

@Composable
fun Modifier.cardBg(radius: Dp = 20.dp, elevation: Dp = 3.dp): Modifier {
    val p = HD.p
    val shape = RoundedCornerShape(radius)
    return this
        .then(if (p.dark) Modifier else Modifier.card(radius, elevation))
        .clip(shape)
        .background(p.card)
        .then(if (p.dark) Modifier.border(1.dp, p.line, shape) else Modifier)
}

// ── 알약 모양 선택 칩 ────────────────────────────────────────

data class ChipOption(val id: String, val label: String, val count: Int? = null, val dot: Color? = null)

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, count: Int? = null, dot: Color? = null) {
    val p = HD.p
    val bg = if (selected) p.ink else p.card
    val fg = if (selected) (if (p.dark) Color.Black else Color.White) else p.ink
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, p.line, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(5.dp))
            Text("$count", color = if (selected) fg.copy(alpha = 0.7f) else p.faint, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ChipRow(options: List<ChipOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.id }) { o -> Pill(o.label, o.id == selected, { onSelect(o.id) }, o.count, o.dot) }
    }
}

/** 작은 글자 정렬 선택 (텍스트 탭) */
@Composable
fun SortTabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = HD.p
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { (id, label) ->
            val on = id == selected
            Row(Modifier.clickable { onSelect(id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (on) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Brand))
                    Spacer(Modifier.width(5.dp))
                }
                Text(label, fontSize = 13.sp, color = if (on) p.ink else p.faint, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

// ── 상품 사진 ───────────────────────────────────────────────

/** 상품 사진. 사진을 받기 전이나 못 받으면 제품군 그림을 보여 준다 */
@Composable
fun ProductPhoto(url: String, catIcon: String, emojiSize: Int, padding: Dp, modifier: Modifier = Modifier) {
    val p = HD.p
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.background(if (loaded) Color.White else p.tile), contentAlignment = Alignment.Center) {
        if (!loaded) Text(catIcon, fontSize = emojiSize.sp)
        if (url.isNotBlank()) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Fit,
                onSuccess = { loaded = true }, onError = { loaded = false },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
fun ProductImage(url: String, catIcon: String, size: Dp, radius: Dp = 16.dp, modifier: Modifier = Modifier) {
    ProductPhoto(url, catIcon, (size.value * 0.36f).toInt(), size * 0.08f, modifier.size(size).clip(RoundedCornerShape(radius)))
}

// ── 할인 표시 ───────────────────────────────────────────────

@Composable
fun TierBadge(t: Tier?, modifier: Modifier = Modifier) {
    if (t == null) return
    val p = HD.p
    val shape = RoundedCornerShape(6.dp)
    val (bg, fg) = when (t) {
        Tier.LEGEND -> p.legendBrush to Color.White
        Tier.HOT -> p.brandBrush to Color.White
        Tier.GOOD -> Brush.linearGradient(listOf(p.goodSoft, p.goodSoft)) to Good
    }
    Text(
        (if (t == Tier.LEGEND) "🔥 " else "") + t.label,
        color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(shape).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun Tag(text: String, color: Color = HD.p.sub, filled: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text, color = if (filled) Color.White else color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(shape).background(if (filled) color else color.copy(alpha = 0.1f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** -23% 형태의 큰 할인율 */
@Composable
fun DiscountText(rate: Double?, size: Int = 20, color: Color = Brand) {
    if (rate == null) return
    Text(
        if (rate >= 0) "${pct(rate)}%" else "+${pct(-rate)}%",
        color = if (rate >= 0) color else Bad, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
    )
}

@Composable
fun PriceText(price: Long?, size: Int = 18) {
    Text(
        if (price != null) "${won(price)}원" else "가격 미상",
        color = HD.p.ink, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 1,
    )
}

@Composable
fun HeartButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val p = HD.p
    Box(
        modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (on) R.drawable.ic_heart else R.drawable.ic_heart_outline),
            contentDescription = if (on) "찜 해제" else "찜하기",
            tint = if (on) Brand else p.faint,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

/** "최저가보다 3% 쌈" 처럼 다음으로 싼 곳과의 차이 */
@Composable
fun VsBestLine(x: Item) {
    val v = x.vsBest ?: return
    val p = HD.p
    val (text, color) = when {
        v >= 1.0 -> (if (x.kind == ItemKind.MARKET || x.kind == ItemKind.PRODUCT) "2위 쇼핑몰보다 ${pct(v)}% 쌈" else "다른 곳 최저가보다 ${pct(v)}% 쌈") to Good
        v > -1.0 -> "다른 곳도 비슷한 값" to p.faint
        else -> "다른 곳이 ${pct(-v)}% 더 쌈" to Warn
    }
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

// ── 핫딜 카드 ───────────────────────────────────────────────

/** 목록 한 줄 */
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
    val p = HD.p
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .cardBg(18.dp, 2.dp)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            ProductImage(x.image, catIcon, 92.dp, 14.dp)
            if (isNew) {
                Text(
                    "NEW", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(5.dp)).background(Brand).padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (hot) TierBadge(tier(x.rate))
                if (x.kind == ItemKind.MARKET) Tag("발굴", Discover)
                if (x.suspicious) Tag("확인 필요", Warn)
                if (x.ended) Tag("종료")
                Text(x.mall, fontSize = 12.sp, color = p.sub, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                x.name.ifBlank { x.title }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = p.ink,
                textDecoration = if (x.ended) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                DiscountText(x.rate, 19)
                if (x.rate != null) Spacer(Modifier.width(6.dp))
                PriceText(x.price, 19)
            }
            if (x.referencePrice != null) {
                Text(
                    "${refLabel(x)} ${won(x.referencePrice)}원", fontSize = 12.sp, color = p.faint,
                    textDecoration = TextDecoration.LineThrough, maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    VsBestLine(x)
                    Text(origin, fontSize = 11.5.sp, color = p.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onFav != null) HeartButton(fav, onFav, size = 32.dp)
            }
        }
    }
}

/** 맨 위 가로로 넘기는 큰 카드 */
@Composable
fun TopDealCard(x: Item, catIcon: String, rank: Int, fav: Boolean, onFav: (() -> Unit)?, onClick: () -> Unit) {
    val p = HD.p
    Column(
        Modifier
            .width(212.dp)
            .cardBg(22.dp, 4.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            ProductPhoto(x.image, catIcon, 52, 14.dp, Modifier.fillMaxWidth().height(150.dp))
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$rank", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xD915171C))
                        .padding(top = 3.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.width(6.dp))
                TierBadge(tier(x.rate))
            }
            if (onFav != null) {
                HeartButton(
                    fav, onFav, size = 34.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
                )
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(x.mall, fontSize = 12.sp, color = p.sub, maxLines = 1, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                x.name.ifBlank { x.title }, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
                fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = p.ink,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                DiscountText(x.rate, 20)
                Spacer(Modifier.width(6.dp))
                PriceText(x.price, 18)
            }
            if (x.referencePrice != null) {
                Text("평균 ${won(x.referencePrice)}원", fontSize = 11.5.sp, color = p.faint, textDecoration = TextDecoration.LineThrough)
            }
        }
    }
}

// ── 커뮤니티 글 ─────────────────────────────────────────────

fun sourceColor(id: String): Color = when (id) {
    "ppomppu", "ppomppu_overseas" -> Color(0xFF5B6CFF)
    "ruliweb" -> Color(0xFF1E88E5)
    "coolenjoy" -> Color(0xFF00A3A3)
    "damoang" -> Color(0xFF8E5CF7)
    "quasarzone" -> Color(0xFFFF8A00)
    "arcalive" -> Color(0xFF20B26B)
    "clien" -> Color(0xFF2D9CDB)
    "fmkorea" -> Color(0xFF3A7BD5)
    "danawa" -> Brand
    else -> Color(0xFF8A9099)
}

@Composable
fun SourceAvatar(id: String, name: String, size: Dp = 36.dp) {
    val c = sourceColor(id)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(c.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), color = c, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun PostRow(x: Item, srcName: String, threshold: Int, onClick: () -> Unit) {
    val p = HD.p
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SourceAvatar(x.source, srcName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                x.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.5.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (x.ended) TextDecoration.LineThrough else null,
                color = if (x.ended) p.faint else p.ink,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val (label, color) = verdictChip(x, threshold)
                Tag(label, color, filled = (x.rate ?: -1.0) >= threshold && !x.suspicious)
                Text(
                    listOfNotNull(
                        srcName, ago(x.postedAt).ifBlank { null },
                        x.votes?.takeIf { it > 0 }?.let { "추천 $it" },
                        x.comments?.takeIf { it > 0 }?.let { "댓글 $it" },
                    ).joinToString(" · "),
                    fontSize = 12.sp, color = p.faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun verdictChip(x: Item, threshold: Int): Pair<String, Color> {
    val muted = HD.p.sub
    val r = x.rate
    return when {
        r != null && x.suspicious -> "평균보다 ${pct(r)}%↓ · 확인 필요" to Warn
        r != null && r >= threshold -> "평균보다 ${pct(r)}%↓" to Brand
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

// ── 그래프 ─────────────────────────────────────────────────

/** 최저가(실선, 아래 채움)·평균가(점선) 그래프 */
@Composable
fun PriceChart(points: List<HistoryPoint>, modifier: Modifier = Modifier) {
    val p = HD.p
    val pts = points.filter { it.lowest != null }
    if (pts.size < 2) {
        Text("가격 기록이 아직 부족해요. 며칠 지나면 그래프가 보여요.", fontSize = 13.sp, color = p.sub, modifier = modifier)
        return
    }
    val values = pts.flatMap { listOfNotNull(it.lowest, it.average) }
    val min = values.min().toFloat()
    val max = values.max().toFloat().let { if (it == min) min + 1 else it }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (pts.size - 1)
            fun y(v: Long) = h - (v - min) / (max - min) * (h * 0.8f) - h * 0.1f
            for (k in 0..3) {
                val gy = h * k / 3
                drawLine(p.line, Offset(0f, gy), Offset(w, gy), strokeWidth = 2f)
            }
            fun path(sel: (HistoryPoint) -> Long?): Path {
                val path = Path()
                var started = false
                pts.forEachIndexed { i, pt ->
                    val v = sel(pt) ?: return@forEachIndexed
                    if (!started) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                    started = true
                }
                return path
            }
            val low = path { it.lowest }
            val fill = Path().apply {
                addPath(low)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Brand.copy(alpha = 0.22f), Brand.copy(alpha = 0f))))
            drawPath(path { it.average }, p.faint, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))))
            drawPath(low, Brand, style = Stroke(width = 5f, cap = StrokeCap.Round))
            val last = pts.last().lowest!!
            drawCircle(Brand, 8f, Offset(x(pts.size - 1), y(last)))
            drawCircle(Color.White, 4f, Offset(x(pts.size - 1), y(last)))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pts.first().date.substring(5).replace('-', '.'), fontSize = 11.sp, color = p.faint)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 3.dp).background(Brand))
                Text(" 최저가   ", fontSize = 11.sp, color = p.sub)
                Box(Modifier.size(10.dp, 2.dp).background(p.faint))
                Text(" 평균가", fontSize = 11.sp, color = p.sub)
            }
            Text(pts.last().date.substring(5).replace('-', '.'), fontSize = 11.sp, color = p.faint)
        }
    }
}

/** 쇼핑몰별 가격 막대 */
@Composable
fun MallPriceRow(mall: String, price: Long, shipping: String, min: Long, max: Long, highlight: Boolean, average: Long?) {
    val p = HD.p
    val span = (max - min).coerceAtLeast(1)
    val frac = 0.28f + 0.72f * ((price - min).toFloat() / span)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mall, modifier = Modifier.weight(1f), fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium, color = if (highlight) Brand else p.ink,
            )
            if (shipping.isNotBlank()) {
                Text(shipping, fontSize = 11.sp, color = p.faint, maxLines = 1)
                Spacer(Modifier.width(8.dp))
            }
            Text("${won(price)}원", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (highlight) Brand else p.ink)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(p.line)) {
            Box(
                Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape)
                    .background(if (highlight) p.brandBrush else Brush.linearGradient(listOf(p.faint.copy(alpha = 0.55f), p.faint.copy(alpha = 0.55f)))),
            )
            if (average != null && average in min..max) {
                val af = 0.28f + 0.72f * ((average - min).toFloat() / span)
                Box(Modifier.fillMaxWidth(af).fillMaxHeight()) {
                    Box(Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight().background(p.ink.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

// ── 그 밖 ──────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    val p = HD.p
    Column(modifier.padding(horizontal = 20.dp).padding(top = 26.dp, bottom = 12.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, letterSpacing = (-0.4).sp)
        if (sub != null) {
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 13.sp, color = p.sub)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = HD.p.ink, letterSpacing = (-0.3).sp,
        modifier = modifier.padding(top = 4.dp, bottom = 10.dp),
    )
}

@Composable
fun EmptyNote(title: String, body: String, emoji: String = "🔍") {
    val p = HD.p
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 32.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.5.sp, color = p.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp)
    }
}

/** 카드 안 정보 묶음 (상세·설정) */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).cardBg(20.dp, 2.dp).padding(18.dp)) { content() }
}
