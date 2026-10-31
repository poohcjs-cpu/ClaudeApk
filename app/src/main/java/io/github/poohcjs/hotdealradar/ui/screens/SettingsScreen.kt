package io.github.poohcjs.hotdealradar.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.BuildConfig
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.ago
import io.github.poohcjs.hotdealradar.data.parseTime
import io.github.poohcjs.hotdealradar.ui.components.SectionTitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    index: Index?,
    s: Settings,
    contentPadding: PaddingValues,
    onChange: ((Settings) -> Settings) -> Unit,
    onAskNotificationPermission: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))

        SectionTitle("핫딜 기준")
        var th by remember(s.threshold) { mutableFloatStateOf(s.threshold.toFloat()) }
        Text("쇼핑몰 평균가보다 ${th.toInt()}% 이상 싸면 핫딜", fontWeight = FontWeight.Medium)
        Slider(
            value = th, onValueChange = { th = it }, valueRange = 5f..70f, steps = 12,
            onValueChangeFinished = { onChange { it.copy(threshold = th.toInt()) } },
        )

        if (index != null) {
            SectionTitle("관심 제품군")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                index.categories.filter { it.id != "etc" }.forEach { c ->
                    val on = c.id in s.interests
                    FilterChip(
                        selected = on, label = { Text("${c.icon} ${c.name}") },
                        onClick = { onChange { st -> st.copy(interests = if (on) st.interests - c.id else st.interests + c.id) } },
                    )
                }
            }
            ToggleRow("관심 제품군만 핫딜 탭에 보이기", "끄면 모든 제품군을 보여요", s.interestOnly) { v -> onChange { it.copy(interestOnly = v) } }
        }

        SectionTitle("보기")
        ToggleRow("가격 확인 필요 딜 숨기기", "평균보다 너무 싸서 옵션가·리퍼·오류일 수 있는 딜", s.hideSuspicious) { v -> onChange { it.copy(hideSuspicious = v) } }
        ToggleRow("끝난 딜 숨기기", null, s.hideEnded) { v -> onChange { it.copy(hideEnded = v) } }

        SectionTitle("알림")
        ToggleRow("새 핫딜 알림", "30분마다 확인해요. 배터리 절약 모드에서는 늦어질 수 있어요", s.notify) { v ->
            if (v) onAskNotificationPermission()
            onChange { it.copy(notify = v) }
        }
        if (s.notify) {
            var nth by remember(s.notifyThreshold) { mutableFloatStateOf(s.notifyThreshold.toFloat()) }
            Text("알림 기준: ${nth.toInt()}% 이상", fontSize = 14.sp)
            Slider(
                value = nth, onValueChange = { nth = it }, valueRange = 5f..70f, steps = 12,
                onValueChangeFinished = { onChange { it.copy(notifyThreshold = nth.toInt()) } },
            )
            ToggleRow("관심 제품군만 알림", "관심 제품군을 하나도 안 골랐으면 전체", s.notifyInterestsOnly) { v -> onChange { it.copy(notifyInterestsOnly = v) } }
        }
        ToggleRow("찜 목표가 도달 알림", null, s.notifyTarget) { v ->
            if (v) onAskNotificationPermission()
            onChange { it.copy(notifyTarget = v) }
        }

        if (index != null) {
            SectionTitle("출처와 수집 상태")
            Text(
                "수집기는 각 사이트의 robots.txt를 지키고, 막히면 우회하지 않고 쉬었다가 다시 시도해요. 끄면 그 출처의 딜을 이 기기에서 숨겨요.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            index.file.sources.forEach { src ->
                val hidden = src.id in s.hiddenSources
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dot = statusColor(if (src.enabled) src.status else "off")
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot ?: MaterialTheme.colorScheme.outline))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            "${index.srcName(src.id)} · ${src.board}" + (src.count?.let { " · 글 ${it}개" } ?: ""),
                            fontWeight = FontWeight.Medium, fontSize = 14.sp,
                        )
                        val msg = when {
                            !src.enabled -> "수집기 설정에서 꺼 두었어요"
                            src.status == "ok" -> "정상 · 마지막 성공 ${ago(parseTime(src.lastOk))}"
                            src.message.isNotBlank() -> src.message
                            src.status == "waiting" -> "아직 확인 전이에요"
                            else -> src.status
                        }
                        Text(msg, fontSize = 12.sp, color = dot ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = !hidden, onCheckedChange = { on ->
                        onChange { st -> st.copy(hiddenSources = if (on) st.hiddenSources - src.id else st.hiddenSources + src.id) }
                    })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            val malls = index.deals.map { it.mall }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
            val allMalls = (s.excludedMalls + malls).distinct()
            if (allMalls.isNotEmpty()) {
                SectionTitle("쇼핑몰 제외")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allMalls.take(30).forEach { m ->
                        val ex = m in s.excludedMalls
                        FilterChip(
                            selected = ex, label = { Text(if (ex) "✕ $m" else m) },
                            onClick = { onChange { st -> st.copy(excludedMalls = if (ex) st.excludedMalls - m else st.excludedMalls + m) } },
                        )
                    }
                }
            }
        }

        SectionTitle("데이터 주소")
        var url by remember(s.dataUrl) { mutableStateOf(s.dataUrl) }
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(BuildConfig.DEFAULT_DATA_URL, fontSize = 12.sp) },
            label = { Text("deals.json 주소 (비우면 기본값)") },
        )
        Row {
            TextButton(onClick = { onChange { it.copy(dataUrl = url.trim()) } }) { Text("저장") }
            TextButton(onClick = { url = ""; onChange { it.copy(dataUrl = "") } }) { Text("기본값으로") }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "핫딜 레이더 ${BuildConfig.VERSION_NAME} · 데이터는 GitHub Actions에서 30분마다 모아요. 찜·설정은 이 기기에만 저장돼요.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
