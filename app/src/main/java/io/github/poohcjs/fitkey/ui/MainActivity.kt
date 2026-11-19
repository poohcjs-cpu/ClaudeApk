package io.github.poohcjs.fitkey.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.poohcjs.fitkey.engine.Key
import io.github.poohcjs.fitkey.engine.KeyboardLayout
import io.github.poohcjs.fitkey.engine.Lang
import io.github.poohcjs.fitkey.engine.ShiftState
import io.github.poohcjs.fitkey.ime.FitKeyStore
import io.github.poohcjs.fitkey.ime.KeySettings
import io.github.poohcjs.fitkey.ime.KeyboardView

/** 설치 안내·설정·터치 보정 화면 */
class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FitKeyStore.init(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = BRAND_DARK) else lightColorScheme(primary = BRAND)) {
                App(tick.intValue)
            }
        }
    }

    // 키보드 켜기·고르기 화면에서 돌아오면 상태를 다시 본다
    override fun onResume() { super.onResume(); tick.intValue++ }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) tick.intValue++ }
    override fun onPause() { FitKeyStore.save(); super.onPause() }

    companion object {
        val BRAND = Color(0xFF2F6BFF)
        val BRAND_DARK = Color(0xFF5C8BFF)
        val ORANGE = Color(0xFFFF6B3D)
        val GOOD = Color(0xFF12A150)

        fun isEnabled(c: Context): Boolean =
            (c.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.any { it.packageName == c.packageName }

        fun isSelected(c: Context): Boolean =
            Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(c.packageName + "/") == true
    }
}

@Composable
private fun App(tick: Int) {
    var screen by remember { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") { screen = "home" }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (screen) {
            "calibrate" -> CalibrationScreen { screen = "home" }
            else -> HomeScreen(tick) { screen = "calibrate" }
        }
    }
}

@Composable
private fun HomeScreen(tick: Int, onCalibrate: () -> Unit) {
    val c = LocalContext.current
    val enabled = remember(tick) { MainActivity.isEnabled(c) }
    val selected = remember(tick) { MainActivity.isSelected(c) }
    var s by remember { mutableStateOf(FitKeyStore.settings(c)) }
    fun update(n: KeySettings) { s = n; FitKeyStore.saveSettings(c, n) }
    val calibrated = remember(tick) { FitKeyStore.calibrations(c) }
    var learned by remember(tick) { mutableStateOf(FitKeyStore.touch.samples to FitKeyStore.userWords.size) }
    var tryText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("핏키보드", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("내 손가락이 실제로 누르는 자리를 배워서 오타를 줄이는 키보드", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        Card {
            Text("처음 한 번만 설정해 주세요", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("안드로이드 보안 규칙상 키보드는 설치만으로 바뀌지 않아요. 아래 두 단계를 누르면 돼요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Step(1, "키보드 켜기", if (enabled) "켜져 있어요" else "목록에서 ‘핏키보드’를 켜 주세요 (보안 안내가 나오면 확인)", enabled) {
                c.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Spacer(Modifier.height(8.dp))
            Step(2, "기본 키보드로 고르기", if (selected) "지금 핏키보드를 쓰고 있어요" else "나오는 목록에서 ‘핏키보드’를 고르세요", selected, enabledStep = enabled) {
                (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
            Spacer(Modifier.height(8.dp))
            Step(3, "내 손에 맞추기 (터치 보정)", if (calibrated > 0) "${calibrated}번 했어요 · 쓸수록 계속 배워요" else "문장 3개를 쳐 보면 손 습관을 재요 (2분)", calibrated > 0, enabledStep = true, onClick = onCalibrate)
        }

        Spacer(Modifier.height(14.dp))
        Card {
            Text("써 보기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(tryText, { tryText = it }, Modifier.fillMaxWidth(), placeholder = { Text("여기를 눌러 쳐 보세요") }, minLines = 2)
            Text("스페이스를 누르면 오타를 고쳐요. 고친 게 틀렸으면 바로 지우기를 누르면 원래대로 돌아가고, 그 말은 다음부터 고치지 않아요.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(14.dp))
        Card {
            Text("설정", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("키보드 높이 ${(s.height * 100).toInt()}%", fontSize = 14.sp)
            Text("글쇠가 클수록 덜 틀려요. 삼성 키보드와 비슷하게 쓰던 크기로 맞추거나 조금 키워 보세요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(s.height, { update(s.copy(height = (it * 20).toInt() / 20f)) }, valueRange = 0.8f..1.4f, steps = 11)
            Toggle("숫자 줄 보이기", "맨 위에 1~0 줄", s.numberRow) { update(s.copy(numberRow = it)) }
            Toggle("자동 오타 수정", "스페이스를 누를 때 확실한 오타만 고쳐요", s.autoCorrect) { update(s.copy(autoCorrect = it)) }
            Toggle("추천 낱말", "키보드 위에 고칠 말·이어질 말을 보여 줘요", s.suggestions) { update(s.copy(suggestions = it)) }
            Toggle("쓰면서 배우기", "지우고 다시 친 글쇠, 고친 오타로 손 습관을 계속 배워요", s.learn) { update(s.copy(learn = it)) }
            Toggle("누를 때 진동", null, s.vibrate) { update(s.copy(vibrate = it)) }
        }

        Spacer(Modifier.height(14.dp))
        Card {
            Text("배운 것", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("터치 ${learned.first}번 · 내 낱말 ${learned.second}개", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text("모두 이 휴대폰에만 저장돼요. 이 앱은 인터넷 권한이 없어 입력한 글을 밖으로 보낼 수 없고, 비밀번호 칸에서는 아무것도 배우거나 기억하지 않아요.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ FitKeyStore.clearLearning(); learned = 0 to 0 }) { Text("배운 것 모두 지우기") }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "한/A 글쇠를 길게 누르면 다른 키보드(삼성 키보드 등)로 바로 바꿀 수 있어요. 스페이스를 누른 채 옆으로 밀면 커서가 움직여요.\n\n" +
                "사전: FrequencyWords (OpenSubtitles 2018, Hermit Dave) · CC BY-SA 4.0",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp)) { content() }
}

@Composable
private fun Step(n: Int, title: String, desc: String, done: Boolean, enabledStep: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = enabledStep, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (done) MainActivity.GOOD else if (enabledStep) MaterialTheme.colorScheme.primary else Color.Gray),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "$n", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(desc, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
        }
        if (!done && enabledStep) Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Toggle(title: String, desc: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp)
            if (desc != null) Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange)
    }
}

@Composable
private fun CalibrationScreen(onDone: () -> Unit) {
    val c = LocalContext.current
    val settings = remember { FitKeyStore.settings(c) }
    val layout = remember { KeyboardLayout.letters(settings.numberRow) }
    val cal = remember { Calibration(FitKeyStore.touch, layout) }
    var version by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    val before = remember { FitKeyStore.touch.samples }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("내 손에 맞추기", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("평소처럼 편하게 쳐 주세요. 빨리 칠 필요도, 정확히 누르려고 애쓸 필요도 없어요. 평소 습관 그대로가 좋아요.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            Spacer(Modifier.height(20.dp))
            version.let { }
            if (!cal.done) {
                val sent = cal.current!!
                Text("${cal.sentence + 1} / ${cal.sentences.size} · ${cal.doneKeys}/${cal.totalKeys}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val typed = cal.typedChars
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MainActivity.GOOD)) { append(sent.text.take(typed)) }
                        withStyle(SpanStyle(color = MainActivity.ORANGE, fontWeight = FontWeight.Bold)) { append(sent.text.drop(typed).take(1)) }
                        append(sent.text.drop(typed + 1))
                    },
                    fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                val nt = cal.next
                Text(
                    if (nt?.key == ' ') "다음: 스페이스" else "다음: 주황 테두리 글쇠" + if (nt != null && nt.key.isUpperCase()) " (쌍자음은 Shift 없이 그 글쇠만)" else "",
                    fontSize = 13.sp, color = if (flash) MainActivity.ORANGE else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val n = cal.samples.size
                val afterMiss = remember(cal.samples.size) { cal.missesAfter() }
                Text("다 됐어요 👍", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("글쇠 ${n}번 중 화면의 글쇠를 벗어난 터치: ${cal.misses}번 (${pct(cal.misses, n)}%)", fontSize = 14.sp)
                Text("배운 습관으로 다시 판정하면: ${afterMiss}번 (${pct(afterMiss, n)}%)", fontSize = 14.sp, color = MainActivity.GOOD, fontWeight = FontWeight.Bold)
                val spread = FitKeyStore.touch.averageSpread()
                if (spread > 0.4f && settings.height < 1.2f) {
                    Text("손가락 터치가 넓게 흩어지는 편이에요. 설정에서 키보드 높이를 조금 키우면 더 정확해져요.",
                        fontSize = 13.sp, color = MainActivity.ORANGE, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Text("아래 키보드의 주황 선은 글쇠마다 실제로 누르는 쪽이에요. 앞으로 이 쪽을 그 글쇠로 알아들어요. 쓰면서도 계속 배워요.",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                Button(onClick = { FitKeyStore.save(); FitKeyStore.markCalibrated(c); onDone() }, colors = ButtonDefaults.buttonColors()) { Text("저장하고 돌아가기") }
                Text("(터치 ${FitKeyStore.touch.samples - before}번을 새로 배웠어요)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        AndroidView(
            factory = { ctx ->
                KeyboardView(ctx).apply {
                    this.layout = layout
                    heightScale = settings.height
                    vibrate = settings.vibrate
                    stripHint = "보정 중: 표시된 문장을 쳐 주세요"
                    onAspect = { FitKeyStore.touch.aspect = it }
                    listener = object : KeyboardView.Listener {
                        override fun onKey(key: Key, ux: Float, uy: Float) {
                            val ok = cal.tap(key, ux, uy)
                            flash = !ok
                            version++
                            refreshCalibrationView(this@apply, cal)
                        }
                    }
                    refreshCalibrationView(this, cal)
                }
            },
            update = { v -> version.let { }; refreshCalibrationView(v, cal) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun refreshCalibrationView(v: KeyboardView, cal: Calibration) {
    if (cal.done) {
        v.target = null
        v.offsets = FitKeyStore.touch.offsets()
        v.lang = Lang.KO
        v.shift = ShiftState.OFF
        return
    }
    val sent = cal.current ?: return
    val t = cal.next
    v.lang = sent.lang
    v.shift = if (t != null && t.key.isUpperCase()) ShiftState.ONCE else ShiftState.OFF
    v.target = t?.key?.takeIf { it != ' ' }?.lowercaseChar()
}

private fun pct(a: Int, n: Int) = if (n == 0) 0 else Math.round(a * 1000f / n) / 10f
