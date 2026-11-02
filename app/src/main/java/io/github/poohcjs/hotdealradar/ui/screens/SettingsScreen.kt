package io.github.poohcjs.hotdealradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.BuildConfig
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.parseTime
import io.github.poohcjs.hotdealradar.ui.components.Pill
import io.github.poohcjs.hotdealradar.ui.components.SourceAvatar
import io.github.poohcjs.hotdealradar.ui.components.cardBg
import io.github.poohcjs.hotdealradar.ui.theme.Brand
import io.github.poohcjs.hotdealradar.ui.theme.HD

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    index: Index?,
    s: Settings,
    contentPadding: PaddingValues,
    onChange: ((Settings) -> Settings) -> Unit,
    onAskNotificationPermission: () -> Unit,
) {
    val p = HD.p
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        ScreenTitle("설정")

        Group("핫딜 기준") {
            var th by remember(s.threshold) { mutableFloatStateOf(s.threshold.toFloat()) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("쇼핑몰 평균보다 ", fontSize = 15.sp, color = p.ink)
                Text("${th.toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
                Text(" 이상 싸면", fontSize = 15.sp, color = p.ink)
            }
            BrandSlider(th, { th = it }, { onChange { it.copy(threshold = th.toInt()) } })
            Text(
                "추천 10%. 다나와에서 가장 싼 판매처는 평소에도 평균보다 10% 안팎 싸서, 이보다 낮추면 평소 가격도 핫딜로 섞여요.",
                fontSize = 12.5.sp, lineHeight = 18.sp, color = p.sub,
            )
            Divider()
            ToggleRow("다른 쇼핑몰보다도 싼 딜만", "다른 곳이 같은 값이거나 더 싸게 팔면 핫딜에서 빼요", s.onlyCheapest) { v -> onChange { it.copy(onlyCheapest = v) } }
            Divider()
            ToggleRow("가격 확인 필요 딜 숨기기", "평균보다 너무 싸서 옵션가·리퍼·오류일 수 있는 딜", s.hideSuspicious) { v -> onChange { it.copy(hideSuspicious = v) } }
            Divider()
            ToggleRow("끝난 딜 숨기기", null, s.hideEnded) { v -> onChange { it.copy(hideEnded = v) } }
        }

        if (index != null) {
            Group("관심 제품군") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    index.categories.filter { it.id != "etc" }.forEach { c ->
                        val on = c.id in s.interests
                        Pill("${c.icon} ${c.name}", on, { onChange { st -> st.copy(interests = if (on) st.interests - c.id else st.interests + c.id) } })
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("관심 제품군만 보기", "하나도 안 고르면 모든 제품군을 보여요", s.interestOnly) { v -> onChange { it.copy(interestOnly = v) } }
            }
        }

        Group("관심 키워드 알림") {
            Text(
                "이 말이 들어간 새 딜이 커뮤니티에 올라오면 알려 드리고, 핫딜 탭 위쪽에 모아 보여 줘요. 예: 닥터노아, 기저귀, 에어팟",
                fontSize = 12.5.sp, lineHeight = 18.sp, color = p.sub,
            )
            Spacer(Modifier.height(10.dp))
            var kw by remember { mutableStateOf("") }
            fun addKw() {
                val k = kw.trim()
                if (k.length >= 2 && s.keywords.none { it.equals(k, ignoreCase = true) }) onChange { it.copy(keywords = (it.keywords + k).take(20)) }
                kw = ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.tile)
                        .border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (kw.isEmpty()) Text("키워드 입력", fontSize = 14.sp, color = p.faint)
                    BasicTextField(
                        value = kw, onValueChange = { kw = it.take(20) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(Brand),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addKw() }),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "추가", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.ink).clickable { addKw() }.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            if (s.keywords.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.keywords.forEach { k -> Pill("$k  ✕", true, { onChange { it.copy(keywords = it.keywords - k) } }) }
                }
            }
        }

        Group("알림") {
            ToggleRow("새 핫딜 알림", "1시간마다 확인해요. 배터리 절약 모드에서는 늦어질 수 있어요", s.notify) { v ->
                if (v) onAskNotificationPermission()
                onChange { it.copy(notify = v) }
            }
            if (s.notify) {
                var nth by remember(s.notifyThreshold) { mutableFloatStateOf(s.notifyThreshold.toFloat()) }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("평균보다 ", fontSize = 14.sp, color = p.ink)
                    Text("${nth.toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
                    Text(" 이상 싸면 알림", fontSize = 14.sp, color = p.ink)
                }
                BrandSlider(nth, { nth = it }, { onChange { it.copy(notifyThreshold = nth.toInt()) } })
                ToggleRow("관심 제품군만 알림", null, s.notifyInterestsOnly) { v -> onChange { it.copy(notifyInterestsOnly = v) } }
            }
            Divider()
            ToggleRow("찜 목표가 도달 알림", null, s.notifyTarget) { v ->
                if (v) onAskNotificationPermission()
                onChange { it.copy(notifyTarget = v) }
            }
        }

        if (index != null) {
            Group("출처와 수집 상태") {
                Text(
                    "수집기는 각 사이트의 robots.txt를 지키고, 막히면 우회하지 않고 쉬었다가 다시 시도해요. 끄면 그 출처의 딜을 이 기기에서 숨겨요.",
                    fontSize = 12.5.sp, lineHeight = 18.sp, color = p.sub,
                )
                Spacer(Modifier.height(6.dp))
                index.file.sources.forEachIndexed { i, src ->
                    if (i > 0) Divider()
                    val hidden = src.id in s.hiddenSources
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SourceAvatar(src.id, index.srcName(src.id), 34.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(index.srcName(src.id), fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = p.ink)
                                Spacer(Modifier.width(6.dp))
                                val dot = statusColor(if (src.enabled) src.status else "off")
                                Box(Modifier.size(7.dp).clip(CircleShape).background(dot ?: p.faint))
                            }
                            val msg = when {
                                !src.enabled -> "수집기에서 꺼 두었어요"
                                src.status == "ok" -> "정상 · ${src.board}${src.count?.let { " · 글 ${it}개" } ?: ""} · ${ago(parseTime(src.lastOk))}"
                                src.message.isNotBlank() -> src.message
                                src.status == "waiting" -> "아직 확인 전이에요"
                                else -> src.status
                            }
                            Text(msg, fontSize = 12.sp, lineHeight = 16.sp, color = p.sub)
                        }
                        BrandSwitch(!hidden) { on ->
                            onChange { st -> st.copy(hiddenSources = if (on) st.hiddenSources - src.id else st.hiddenSources + src.id) }
                        }
                    }
                }
            }

            val malls = index.deals.map { it.mall }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
            val allMalls = (s.excludedMalls + malls).distinct()
            if (allMalls.isNotEmpty()) {
                Group("제외할 쇼핑몰") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allMalls.take(30).forEach { m ->
                            val ex = m in s.excludedMalls
                            Pill(if (ex) "✕ $m" else m, ex, { onChange { st -> st.copy(excludedMalls = if (ex) st.excludedMalls - m else st.excludedMalls + m) } })
                        }
                    }
                }
            }
        }

        Group("데이터 주소") {
            var url by remember(s.dataUrl) { mutableStateOf(s.dataUrl) }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.tile)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (url.isEmpty()) Text("비우면 기본 주소를 써요", fontSize = 13.sp, color = p.faint)
                BasicTextField(
                    value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp, color = p.ink), cursorBrush = SolidColor(Brand),
                )
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("저장", false, { onChange { it.copy(dataUrl = url.trim()) } })
                Pill("기본값으로", false, { url = ""; onChange { it.copy(dataUrl = "") } })
            }
        }

        Text(
            "핫딜 레이더 ${BuildConfig.VERSION_NAME}\n핫딜 데이터는 GitHub Actions에서 1시간마다 모으고, 검색은 다나와에 실시간으로 물어요. 찜·설정은 이 기기에만 저장돼요.\n글꼴: HDR Sans (Pretendard 기반, SIL OFL 1.1)",
            fontSize = 12.sp, lineHeight = 18.sp, color = p.faint, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val p = HD.p
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.sub, modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).cardBg(20.dp, 1.dp).padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(color = HD.p.line, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun BrandSlider(value: Float, onValue: (Float) -> Unit, onDone: () -> Unit) {
    val p = HD.p
    Slider(
        value = value, onValueChange = onValue, valueRange = 5f..70f, steps = 12, onValueChangeFinished = onDone,
        colors = SliderDefaults.colors(
            thumbColor = Brand, activeTrackColor = Brand, inactiveTrackColor = p.line,
            activeTickColor = Color.White.copy(alpha = 0.6f), inactiveTickColor = p.faint.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun BrandSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = HD.p
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Brand, checkedBorderColor = Brand,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = p.line, uncheckedBorderColor = p.line,
        ),
    )
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = HD.p
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.ink)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.sub)
            }
        }
        BrandSwitch(checked, onChange)
    }
}
