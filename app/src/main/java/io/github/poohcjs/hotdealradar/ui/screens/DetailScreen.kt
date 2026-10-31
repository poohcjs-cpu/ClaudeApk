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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.poohcjs.hotdealradar.data.tier
import io.github.poohcjs.hotdealradar.data.verdictText
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.components.DiscountText
import io.github.poohcjs.hotdealradar.ui.components.HeartButton
import io.github.poohcjs.hotdealradar.ui.components.MallPriceRow
import io.github.poohcjs.hotdealradar.ui.components.PriceChart
import io.github.poohcjs.hotdealradar.ui.components.ProductPhoto
import io.github.poohcjs.hotdealradar.ui.components.SectionTitle
import io.github.poohcjs.hotdealradar.ui.components.SourceAvatar
import io.github.poohcjs.hotdealradar.ui.components.Tag
import io.github.poohcjs.hotdealradar.ui.components.TierBadge
import io.github.poohcjs.hotdealradar.ui.components.VsBestLine
import io.github.poohcjs.hotdealradar.ui.components.cardBg
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.Discover
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.HD
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
    val p = HD.p
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
    val isPostLike = x.kind == ItemKind.COMMUNITY || x.kind == ItemKind.POST
    val topInset = contentPadding.calculateTopPadding()
    val bottomInset = contentPadding.calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 사진
            val image = x.image.ifBlank { product?.image.orEmpty() }
            ProductPhoto(
                image, catIcon(index, x.cat), 84, 40.dp,
                Modifier.fillMaxWidth().height(320.dp + topInset).padding(top = 0.dp),
            )
            // 본문 시트
            Column(
                Modifier
                    .offset(y = (-26).dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(p.bg),
            ) {
                Column(Modifier.fillMaxWidth().background(p.card).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isHot(x, s)) TierBadge(tier(x.rate))
                        when (x.kind) {
                            ItemKind.MARKET -> Tag("가격비교 발굴", Discover)
                            ItemKind.PRODUCT -> Tag("다나와 제품")
                            else -> Tag(index.srcName(x.mentions.firstOrNull()?.source ?: x.source))
                        }
                        if (x.suspicious) Tag("가격 확인 필요", Warn)
                        if (x.ended) Tag("종료")
                        if (x.unitAdjusted) Tag("개당 가격")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        x.title.ifBlank { x.name }, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
                        color = p.ink, letterSpacing = (-0.4).sp,
                    )
                    if (x.danawaName.isNotBlank() && x.danawaName != x.title) {
                        Spacer(Modifier.height(6.dp))
                        Text("다나와 · ${x.danawaName}", fontSize = 13.sp, color = p.sub)
                    }
                    Spacer(Modifier.height(18.dp))
                    if (x.referencePrice != null) {
                        Text(
                            "${refLabel(x)} ${won(x.referencePrice)}원", fontSize = 14.sp, color = p.faint,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        DiscountText(x.rate, 30)
                        if (x.rate != null) Spacer(Modifier.width(10.dp))
                        Text(
                            if (x.price != null) won(x.price) else "가격 미상", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                            color = p.ink, letterSpacing = (-1).sp,
                        )
                        if (x.price != null) Text("원", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(x.mall.ifBlank { null }, x.shipping.ifBlank { null }).joinToString(" · "),
                        fontSize = 14.sp, color = p.sub,
                    )
                    val save = x.save
                    if (save != null && save > 0) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.brandSoft).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("💰", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${refLabel(x)}보다 ${won(save)}원 아껴요", fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                                    color = if (p.dark) Color(0xFFFFB4A3) else Brand,
                                )
                                VsBestLine(x)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(verdictText(x, s, index::srcName), fontSize = 14.sp, lineHeight = 21.sp, color = p.sub)
                }

                // 목표가
                val favPcode = x.pcode
                if (fav != null) {
                    DetailCard {
                        SectionTitle("목표가 알림")
                        var input by remember(fav.target) { mutableStateOf(fav.target?.toString().orEmpty()) }
                        val best = index.bestNow(favPcode)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.tile)
                                    .border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
                            ) {
                                if (input.isEmpty()) Text("이 가격 이하가 되면 알려 드려요", fontSize = 14.sp, color = p.faint)
                                BasicTextField(
                                    value = input, onValueChange = { v -> input = v.filter { it.isDigit() }.take(10) }, singleLine = true,
                                    textStyle = TextStyle(fontSize = 15.sp, color = p.ink, fontWeight = FontWeight.SemiBold),
                                    cursorBrush = SolidColor(Brand),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    visualTransformation = ThousandsTransformation,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "저장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.ink)
                                    .clickable { onTarget(favPcode, input.toLongOrNull()) }.padding(horizontal = 18.dp, vertical = 13.dp),
                            )
                        }
                        if (best != null) {
                            val reached = fav.target != null && best.price <= fav.target
                            Spacer(Modifier.height(10.dp))
                            Text(
                                (if (reached) "🎯 목표가 도달! " else "") + "지금 가장 싼 값 ${best.mall} ${won(best.price)}원 (${best.via})",
                                fontSize = 13.sp, color = if (reached) Brand else p.sub, fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // 쇼핑몰별 가격
                val offers = product?.offers.orEmpty()
                if (offers.isNotEmpty()) {
                    DetailCard {
                        SectionTitle("쇼핑몰별 가격")
                        val prices = offers.map { it.price } + listOfNotNull(x.price)
                        val min = prices.min()
                        val max = prices.max()
                        // 커뮤니티 딜은 딜 가격을 맨 위에 따로 보여 준다 (같은 쇼핑몰의 평소 가격과 헷갈리지 않게)
                        if (isPostLike && x.price != null) {
                            MallPriceRow("이 딜 · ${x.mall.ifBlank { "쇼핑몰" }}", x.price, x.shipping, min, max, true, x.referencePrice)
                        }
                        offers.forEach { o ->
                            val sameMall = o.mall == x.mall
                            MallPriceRow(
                                if (isPostLike && sameMall) "${o.mall} (평소 가격)" else o.mall, o.price, o.shipping, min, max,
                                !isPostLike && sameMall, x.referencePrice,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "세로선은 ${refLabel(x)}(${won(x.referencePrice)}원) · 판매처 ${product?.offerCount ?: offers.size}곳 중 ${offers.size}곳",
                            fontSize = 11.5.sp, color = p.faint,
                        )
                    }
                }

                if (product != null) {
                    DetailCard {
                        SectionTitle("가격 추이")
                        PriceChart(product.historyPoints())
                    }
                }

                if (x.mentions.isNotEmpty() && isPostLike) {
                    DetailCard {
                        SectionTitle("올라온 게시판")
                        x.mentions.forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(color = p.line)
                            Row(
                                Modifier.fillMaxWidth().clickable { open(m.url) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SourceAvatar(m.source, index.srcName(m.source), 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(index.srcName(m.source), modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                                Text(
                                    listOfNotNull(
                                        ago(parseTime(m.postedAt)).ifBlank { null },
                                        m.votes?.takeIf { it > 0 }?.let { "추천 $it" },
                                        m.comments?.takeIf { it > 0 }?.let { "댓글 $it" },
                                        if (m.ended) "종료" else null,
                                    ).joinToString(" · "),
                                    fontSize = 12.sp, color = p.faint,
                                )
                            }
                        }
                    }
                }

                val others = x.pcode?.let { pc ->
                    (index.dealsByPcode[pc].orEmpty() + index.posts.filter { it.pcode == pc && it.rate != null })
                        .filter { it.key != x.key && x.mentions.none { m -> "post:${m.id}" == it.key } }
                        .distinctBy { it.url }
                }.orEmpty()
                if (others.isNotEmpty()) {
                    DetailCard {
                        SectionTitle("같은 제품의 다른 딜")
                        others.take(8).forEachIndexed { i, o ->
                            if (i > 0) HorizontalDivider(color = p.line)
                            Row(Modifier.fillMaxWidth().clickable { onOpen(o) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.title.ifBlank { o.name }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = p.ink)
                                    Text("${o.mall} ${won(o.price)}원 · ${originLabel(o, index)}", fontSize = 12.sp, color = p.faint, maxLines = 1)
                                }
                                Spacer(Modifier.width(8.dp))
                                DiscountText(o.rate, 15)
                            }
                        }
                    }
                }

                val how = howText(x, index.file.params)
                if (how.isNotEmpty()) {
                    DetailCard {
                        SectionTitle("어떻게 비교했나요")
                        how.forEach { line ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(Good))
                                Spacer(Modifier.width(10.dp))
                                Text(line, fontSize = 13.5.sp, lineHeight = 20.sp, color = p.sub)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(100.dp + bottomInset))
            }
        }

        // 위 버튼
        Row(
            Modifier.fillMaxWidth().padding(top = topInset + 8.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(R.drawable.ic_back, "뒤로", onBack)
            Spacer(Modifier.weight(1f))
            RoundIcon(R.drawable.ic_share, "공유", ::share)
        }

        // 아래 구매 버튼
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(p.card)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + bottomInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canFav) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, p.line, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { HeartButton(fav != null, { onFav(x) }, size = 44.dp) }
                Spacer(Modifier.width(10.dp))
            }
            Row(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(p.brandBrush).clickable { open(x.url) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isPostLike) "딜 보러 가기" else "다나와에서 보기",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(R.drawable.ic_open), null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            if (isPostLike && x.pcode != null) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(54.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, p.line, RoundedCornerShape(16.dp))
                        .clickable { open("https://prod.danawa.com/info/?pcode=${x.pcode}") }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("다나와", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink) }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).cardBg(20.dp, 1.dp).padding(18.dp),
    ) { content() }
}

@Composable
private fun RoundIcon(icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
            .border(1.dp, Color(0x14000000), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), label, tint = Color(0xFF15171C), modifier = Modifier.size(21.dp)) }
}

/** 숫자 입력에 천 단위 쉼표를 보여 준다 (저장되는 값은 숫자만) */
private object ThousandsTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val out = raw.reversed().chunked(3).joinToString(",").reversed()
        val map = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val commasAfter = (raw.length - offset).let { right -> if (right <= 0) 0 else (right - 1) / 3 }
                return out.length - (raw.length - offset) - commasAfter
            }
            override fun transformedToOriginal(offset: Int): Int = out.take(offset).count { it != ',' }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out), map)
    }
}
