package io.github.poohcjs.hotdealradar.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.howText
import io.github.poohcjs.hotdealradar.data.isHot
import io.github.poohcjs.hotdealradar.data.parseTime
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.refLabel
import io.github.poohcjs.hotdealradar.data.verdictText
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.components.PriceChart
import io.github.poohcjs.hotdealradar.ui.components.RateBadge
import io.github.poohcjs.hotdealradar.ui.components.SectionTitle
import io.github.poohcjs.hotdealradar.ui.components.Tag
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.Warn

@Composable
fun DetailScreen(
    x: Item,
    index: Index,
    s: Settings,
    favs: Map<String, Fav>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onFav: (Item) -> Unit,
    onTarget: (String, Long?) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val context = LocalContext.current
    fun open(url: String) {
        if (url.startsWith("http")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    fun share() {
        val text = buildString {
            append(x.name.ifBlank { x.title })
            x.price?.let { append("\n${x.mall} ${won(it)}원") }
            x.rate?.let { append(" (${refLabel(x)}보다 ${pct(it)}%↓)") }
            append("\n${x.url}")
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "공유"))
    }

    val product = x.pcode?.let { index.products[it] }
    val fav: Fav? = x.pcode?.let { favs[it] }
    val canFav = x.pcode != null && (x.kind != ItemKind.POST || x.rate != null)

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "뒤로") }
            Text(index.catName(x.cat), modifier = Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = ::share) { Icon(painterResource(R.drawable.ic_share), "공유") }
            if (canFav) {
                IconButton(onClick = { onFav(x) }) {
                    Icon(
                        painterResource(if (fav != null) R.drawable.ic_heart else R.drawable.ic_heart_outline),
                        if (fav != null) "찜 해제" else "찜하기",
                        tint = if (fav != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val image = x.image.ifBlank { product?.image.orEmpty() }
            if (image.isNotBlank()) {
                AsyncImage(
                    model = image, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when (x.kind) {
                    ItemKind.MARKET -> Tag("가격비교 발굴", Color(0xFF3A6FD8))
                    ItemKind.PRODUCT -> Tag("다나와 제품")
                    else -> Tag(index.srcName(x.mentions.firstOrNull()?.source ?: x.source))
                }
                if (x.suspicious) Tag("가격 확인 필요", Warn)
                if (x.ended) Tag("종료")
                if (x.unitAdjusted) Tag("개당 가격 비교")
            }
            Spacer(Modifier.height(6.dp))
            Text(x.title.ifBlank { x.name }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (x.danawaName.isNotBlank() && x.danawaName != x.title) {
                Text("다나와: ${x.danawaName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RateBadge(x.rate, isHot(x, s))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (x.price != null) "${won(x.price)}원" else "가격 미상",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(x.mall.ifBlank { null }, x.shipping.ifBlank { null }).joinToString(" · "),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (x.referencePrice != null) {
                Text(
                    "${refLabel(x)} ${won(x.referencePrice)}원" + (x.lowest?.let { " · 다나와 최저 ${won(it)}원${x.lowestMall?.let { m -> "($m)" } ?: ""}" } ?: ""),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(verdictText(x, s, index::srcName), modifier = Modifier.padding(12.dp), fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open(x.url) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_open), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (x.kind == ItemKind.COMMUNITY || x.kind == ItemKind.POST) "원문 글 보기" else "다나와에서 보기")
                }
                if (x.pcode != null && x.kind != ItemKind.MARKET && x.kind != ItemKind.PRODUCT) {
                    OutlinedButton(onClick = { open("https://prod.danawa.com/info/?pcode=${x.pcode}") }) { Text("다나와") }
                }
            }

            val favPcode = x.pcode
            if (fav != null) {
                SectionTitle("목표가")
                var input by remember(fav.target) { mutableStateOf(fav.target?.toString().orEmpty()) }
                val best = index.bestNow(favPcode)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { v -> input = v.filter { it.isDigit() }.take(10) },
                        singleLine = true, modifier = Modifier.weight(1f), label = { Text("이 가격 이하가 되면 알림 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    TextButton(onClick = { onTarget(favPcode, input.toLongOrNull()) }) { Text("저장") }
                }
                if (best != null) {
                    val reached = fav.target != null && best.price <= fav.target
                    Text(
                        "지금 가장 싼 값: ${best.mall} ${won(best.price)}원 (${best.via})" + if (reached) " · 목표가 도달!" else "",
                        fontSize = 13.sp, color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val offers = product?.offers.orEmpty()
            if (offers.isNotEmpty()) {
                SectionTitle("쇼핑몰별 가격")
                val ref = x.referencePrice
                offers.forEach { o ->
                    val isDealMall = o.mall == x.mall
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.mall, modifier = Modifier.weight(1f), fontWeight = if (isDealMall) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${won(o.price)}원", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            val sub = listOfNotNull(
                                o.shipping.ifBlank { null },
                                x.price?.let { p -> (o.price - p).let { d -> if (d > 0) "딜보다 +${won(d)}" else if (d < 0) "딜보다 ${won(d)}" else null } },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (ref != null) {
                    Text("평균선: ${won(ref)}원 · 판매처 ${product?.offerCount ?: offers.size}곳 중 ${offers.size}곳 표시", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            val history = product?.historyPoints().orEmpty()
            if (product != null) {
                SectionTitle("가격 추이")
                PriceChart(history)
            }

            if (x.mentions.size > 1 || (x.kind == ItemKind.COMMUNITY && x.mentions.isNotEmpty())) {
                SectionTitle("올라온 게시판")
                x.mentions.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open(m.url) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(index.srcName(m.source), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(
                            listOfNotNull(
                                ago(parseTime(m.postedAt)).ifBlank { null },
                                m.votes?.takeIf { it > 0 }?.let { "추천 $it" },
                                m.comments?.takeIf { it > 0 }?.let { "댓글 $it" },
                                if (m.ended) "종료" else null,
                            ).joinToString(" · "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val others = x.pcode?.let { pc ->
                (index.dealsByPcode[pc].orEmpty() + index.posts.filter { it.pcode == pc && it.rate != null })
                    .filter { it.key != x.key && x.mentions.none { m -> "post:${m.id}" == it.key } }
                    .distinctBy { it.url }
            }.orEmpty()
            if (others.isNotEmpty()) {
                SectionTitle("같은 제품의 다른 글")
                others.take(8).forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(o) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.title.ifBlank { o.name }, fontSize = 14.sp, maxLines = 1)
                            Text("${originLabel(o, index)} · ${o.mall} ${won(o.price)}원", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RateBadge(o.rate, isHot(o, s))
                    }
                }
            }

            val how = howText(x, index.file.params)
            if (how.isNotEmpty()) {
                SectionTitle("어떻게 비교했나요")
                how.forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(RoundedCornerShape(3.dp)).background(Good))
                        Spacer(Modifier.width(8.dp))
                        Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
