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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.LiveProduct
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.ui.LivePageState
import io.github.poohcjs.hotdealradar.ui.components.HeartButton
import io.github.poohcjs.hotdealradar.ui.components.MallPriceRow
import io.github.poohcjs.hotdealradar.ui.components.ProductPhoto
import io.github.poohcjs.hotdealradar.ui.components.SectionTitle
import io.github.poohcjs.hotdealradar.ui.components.Tag
import io.github.poohcjs.hotdealradar.ui.components.cardBg
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.Discover
import io.github.poohcjs.hotdealradar.ui.theme.Good
import io.github.poohcjs.hotdealradar.ui.theme.HD

/** 실시간 검색에서 연 상품: 다나와(또는 에누리) 상품 페이지의 쇼핑몰별 가격을 지금 읽어 보여 준다 */
@Composable
fun LiveProductScreen(
    pcode: String,
    product: LiveProduct?,
    state: LivePageState?,
    index: Index?,
    favs: Map<String, Fav>,
    contentPadding: PaddingValues,
    onLoad: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onOption: (String) -> Unit,
    onFav: (name: String, price: Long?, image: String) -> Unit,
    onOpenDeal: (Item) -> Unit,
    withShipping: Boolean = true,
) {
    val p = HD.p
    val context = LocalContext.current
    fun open(url: String) {
        if (url.startsWith("http")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    LaunchedEffect(pcode) { onLoad(pcode, false) }

    val page = state?.page
    val option = product?.options?.firstOrNull { it.pcode == pcode }
    val name = page?.name?.ifBlank { null } ?: product?.name.orEmpty()
    val image = page?.image?.ifBlank { null } ?: product?.image.orEmpty()
    // 배송비 포함으로 볼 때는 상품가+배송비 순서로 다시 줄 세운다
    val offers = page?.offers.orEmpty().let { list -> if (withShipping) list.sortedBy { it.total } else list }
    val cheapest = offers.firstOrNull()
    val price = cheapest?.let { if (withShipping) it.total else it.price } ?: option?.cost(withShipping) ?: product?.cost(withShipping)
    val unit = option?.unit ?: product?.unit
    // 에누리 가격비교 묶음('en:…')이면 에누리 화면, 아니면 다나와 화면
    val enuri = io.github.poohcjs.hotdealradar.data.EnuriParser.isEnuri(pcode)
    val siteName = if (enuri) "에누리" else "다나와"
    val danawaUrl = if (enuri) io.github.poohcjs.hotdealradar.data.EnuriParser.catalogUrl(pcode) else "https://prod.danawa.com/info/?pcode=$pcode"
    val deal = index?.dealsByPcode?.get(pcode)?.filter { !it.ended }?.minByOrNull { it.price ?: Long.MAX_VALUE }
    val topInset = contentPadding.calculateTopPadding()
    val bottomInset = contentPadding.calculateBottomPadding()
    val isFav = pcode in favs

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ProductPhoto(image, "🛒", 80, 40.dp, Modifier.fillMaxWidth().height(300.dp + topInset))
            Column(
                Modifier.offset(y = (-26).dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(p.bg),
            ) {
                Column(Modifier.fillMaxWidth().background(p.card).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag("실시간 $siteName 최저가", Discover)
                        if (product?.overseas == true) Tag("해외구매")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(name.ifBlank { "상품 정보를 불러오고 있어요" }, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = p.ink, letterSpacing = (-0.4).sp)
                    if (option != null && option.label.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(option.label, fontSize = 14.sp, color = p.sub)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (withShipping) "지금 최저가 (배송비 포함)" else "지금 최저가", fontSize = 13.sp, color = p.sub)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (price != null) won(price) else "-", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, letterSpacing = (-1).sp)
                        Text("원", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(bottom = 3.dp))
                        if (cheapest != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(cheapest.mall, fontSize = 14.sp, color = p.sub, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                    val malls = page?.offerCount ?: option?.malls ?: product?.malls
                    Text(
                        listOfNotNull(
                            unit?.let { "${it.value}원 / ${it.per}" },
                            malls?.let { "쇼핑몰 ${it}곳 비교" },
                            cheapest?.shipping?.ifBlank { null },
                        ).joinToString(" · "),
                        fontSize = 13.5.sp, color = Good, fontWeight = FontWeight.SemiBold,
                    )
                    if (offers.size >= 2) {
                        val v = { o: io.github.poohcjs.hotdealradar.data.LiveOffer -> if (withShipping) o.total else o.price }
                        val avg = offers.map(v).average().toLong()
                        val save = avg - v(offers.first())
                        if (save > 0) {
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.brandSoft).padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("💰", fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "여기 보이는 쇼핑몰 평균(${won(avg)}원)보다 ${won(save)}원 싸요",
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (p.dark) Color(0xFFFFB4A3) else Brand,
                                )
                            }
                        }
                    }
                    if (deal != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Brand.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .clickable { onOpenDeal(deal) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🔥", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("커뮤니티 핫딜 진행 중", fontSize = 13.sp, color = p.sub)
                                Text("${deal.mall} ${won(deal.price)}원 · 평균보다 ${pct(deal.rate)}% 쌈", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Brand)
                            }
                            Text("보기 ›", fontSize = 13.sp, color = p.sub)
                        }
                    }
                }

                val options = product?.options.orEmpty()
                if (options.size > 1) {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).cardBg(20.dp, 1.dp).padding(vertical = 18.dp)) {
                        SectionTitle("구성 고르기", Modifier.padding(horizontal = 18.dp))
                        val bestUnit = options.filter { it.unit != null }.minByOrNull { it.unit!!.value }
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(options, key = { it.pcode }) { o ->
                                val on = o.pcode == pcode
                                Column(
                                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) p.ink else p.tile)
                                        .border(1.dp, if (on) p.ink else p.line, RoundedCornerShape(14.dp))
                                        .clickable { if (!on) onOption(o.pcode) }.padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    val fg = if (on) (if (p.dark) Color.Black else Color.White) else p.ink
                                    Text(o.label.ifBlank { "기본" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                                    Text("${won(o.price)}원", fontSize = 13.sp, color = fg)
                                    if (o.unit != null) {
                                        Text(
                                            "${o.unit.value}원/${o.unit.per}" + if (o == bestUnit) " 최저" else "", fontSize = 11.5.sp,
                                            color = if (o == bestUnit) Good else fg.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).cardBg(20.dp, 1.dp).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("쇼핑몰별 가격", Modifier.weight(1f))
                        if (!(state?.loading ?: true)) {
                            Text("새로고침", fontSize = 13.sp, color = p.sub, modifier = Modifier.clickable { onLoad(pcode, true) }.padding(4.dp))
                        }
                    }
                    when {
                        state == null || (state.loading && page == null) -> Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Brand, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("쇼핑몰 가격을 읽고 있어요", color = p.sub, fontSize = 14.sp)
                        }
                        state.error != null && offers.isEmpty() -> Text(state.error, color = p.sub, fontSize = 13.5.sp, lineHeight = 19.sp)
                        offers.isEmpty() -> Text("판매처 목록을 읽지 못했어요. ${siteName}에서 직접 확인해 주세요.", color = p.sub, fontSize = 13.5.sp)
                        else -> {
                            val v = { o: io.github.poohcjs.hotdealradar.data.LiveOffer -> if (withShipping) o.total else o.price }
                            val min = offers.minOf(v)
                            val max = offers.maxOf(v)
                            val avg = offers.map(v).average().toLong()
                            offers.take(15).forEachIndexed { i, o ->
                                Box(Modifier.clickable { open(o.link.ifBlank { danawaUrl }) }) {
                                    MallPriceRow(
                                        if (i == 0) "👑 ${o.mall}" else o.mall, v(o),
                                        if (withShipping && (o.fee ?: 0) > 0) "상품 ${won(o.price)} + ${o.shipping}" else o.shipping,
                                        min, max, i == 0, avg,
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "쇼핑몰을 누르면 그 쇼핑몰 상품으로 가요 · 세로선은 평균" + if (withShipping) " · 배송비 포함 금액" else "",
                                fontSize = 11.5.sp, color = p.faint, lineHeight = 16.sp,
                            )
                        }
                    }
                }

                val cards = page?.cardOffers.orEmpty()
                if (cards.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).cardBg(20.dp, 1.dp).padding(18.dp)) {
                        SectionTitle("카드 할인가 (참고)")
                        Text("해당 카드로 결제할 때만 이 가격이에요. 위 최저가 비교에는 넣지 않았어요.", fontSize = 12.sp, color = p.sub, lineHeight = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        cards.take(6).forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { open(c.link.ifBlank { danawaUrl }) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.mall, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                                    Text("💳 ${c.condition}", fontSize = 12.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${won(c.price)}원", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(100.dp + bottomInset))
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = topInset + 8.dp, start = 12.dp, end = 12.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                    .border(1.dp, Color(0x14000000), CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_back), "뒤로", tint = Color(0xFF15171C), modifier = Modifier.size(21.dp)) }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(p.card)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + bottomInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, p.line, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { HeartButton(isFav, { onFav(name, price, image) }, size = 44.dp) }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(p.brandBrush)
                    .clickable { open(cheapest?.link?.ifBlank { null } ?: danawaUrl) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (cheapest != null) "${cheapest.mall.substringBefore(" (")}에서 사기" else "${siteName}에서 보기",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(R.drawable.ic_open), null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.height(54.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, p.line, RoundedCornerShape(16.dp))
                    .clickable { open(danawaUrl) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(siteName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink) }
        }
    }
}
