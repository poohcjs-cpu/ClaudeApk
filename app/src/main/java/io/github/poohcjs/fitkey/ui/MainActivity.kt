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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
                App(tick.intValue, intent?.getStringExtra("screen") ?: "home")
            }
        }
    }

    // 키보드 켜기·고르기 화면에서 돌아오면 상태를 다시 본다
    override fun onResume() { super.onResume(); tick.intValue++ }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        tick.intValue++
        // 1단계(켜기)를 하고 돌아오면, 2단계 '키보드 고르기' 목록을 바로 띄운다 (따로 찾지 않아도 되게)
        if (pickerAfterSettings && isEnabled(this) && !isSelected(this)) {
            pickerAfterSettings = false
            window.decorView.postDelayed({ if (hasWindowFocus()) showPicker(this) }, 450)
        }
    }
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

        /** 지금 쓰는 키보드 이름 (예: 삼성 키보드) */
        fun currentKeyboard(c: Context): String {
            val id = Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return "알 수 없음"
            val imm = c.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            return imm.inputMethodList.firstOrNull { it.id == id }?.loadLabel(c.packageManager)?.toString() ?: id.substringBefore('/')
        }

        /** 1단계 화면에서 돌아오면 고르기 목록을 바로 띄울지 */
        var pickerAfterSettings = false

        fun openKeyboardSettings(c: Context) {
            pickerAfterSettings = true
            c.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun showPicker(c: Context) = (c.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }
}

@Composable
private fun App(tick: Int, start: String = "home") {
    var screen by remember { mutableStateOf(start) }
    BackHandler(enabled = screen != "home") { screen = "home" }
    // Surface: 글자 기본색을 배경에 맞춘다 (어두운 화면에서 글자가 검게 나와 안 보이던 문제)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (screen) {
                "calibrate" -> {
                    var round by remember { mutableIntStateOf(0) }
                    androidx.compose.runtime.key(round) { CalibrationScreen(onRestart = { round++ }) { screen = "home" } }
                }
                "size" -> SizeScreen { screen = "home" }
                else -> HomeScreen(tick, onSize = { screen = "size" }) { screen = "calibrate" }
            }
        }
    }
}

@Composable
private fun HomeScreen(tick: Int, onSize: () -> Unit, onCalibrate: () -> Unit) {
    val c = LocalContext.current
    val enabled = remember(tick) { MainActivity.isEnabled(c) }
    val selected = remember(tick) { MainActivity.isSelected(c) }
    var s by remember { mutableStateOf(FitKeyStore.settings(c)) }
    fun update(n: KeySettings) { s = n; FitKeyStore.saveSettings(c, n) }
    val calibrated = remember(tick) { FitKeyStore.calibrations(c) }
    var learned by remember(tick) { mutableStateOf(Triple(FitKeyStore.touch.samples, FitKeyStore.userWords.size, FitKeyStore.bigrams.size)) }
    var tryText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("핏키보드", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("내 손가락이 실제로 누르는 자리를 배워서 오타를 줄이는 키보드", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        Card {
            Text("처음 한 번만 설정해 주세요", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("안드로이드 보안 규칙상 키보드는 설치만으로 바뀌지 않아요. 1 → 2 순서로 눌러 주세요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "지금 쓰는 키보드: ${remember(tick) { MainActivity.currentKeyboard(c) }}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) MainActivity.GOOD else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Step(
                1, "키보드 켜기",
                if (enabled) "켜져 있어요" else "‘키보드 목록 및 기본값’ 화면이 열려요. 아래쪽 키보드 목록에서 ‘핏키보드 (내 손에 맞춤)’ 오른쪽 스위치를 켜고, 안내 창이 나오면 ‘확인’을 누른 뒤 뒤로 돌아오세요.",
                enabled,
            ) { MainActivity.openKeyboardSettings(c) }
            Spacer(Modifier.height(8.dp))
            Step(
                2, "기본 키보드로 고르기",
                when {
                    selected -> "지금 핏키보드를 쓰고 있어요"
                    !enabled -> "1단계를 먼저 해 주세요 (켜지 않은 키보드는 목록에 안 나와요)"
                    else -> "‘키보드 선택’ 창이 떠요. 그 안에서 ‘핏키보드 (내 손에 맞춤)’ 글자를 한 번 누르면 끝이에요."
                },
                selected,
            ) {
                if (!enabled) {
                    android.widget.Toast.makeText(c, "먼저 1단계: 목록에서 핏키보드를 켜 주세요", android.widget.Toast.LENGTH_LONG).show()
                    MainActivity.openKeyboardSettings(c)
                } else MainActivity.showPicker(c)
            }
            if (enabled && !selected) {
                Text(
                    "‘키보드 선택’ 창이 안 뜨면: 1단계 버튼을 다시 눌러 열린 화면 맨 위 ‘기본 키보드’를 누르고 ‘핏키보드’를 고르세요.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
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
            Section("크기·모양")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(onClick = onSize).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("키보드 크기·위치", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "높이 ${(s.height * 100).toInt()}% · 너비 ${(s.width * 100).toInt()}%" +
                            (if (s.width < 0.95f) " · " + (if (s.align < -0.3f) "왼쪽" else if (s.align > 0.3f) "오른쪽" else "가운데") else "") +
                            (if (s.bottomGap > 0) " · 아래 여백 ${s.bottomGap}dp" else ""),
                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            OneHandRow(s) { update(it) }
            ChoiceRow("키보드 색", null, listOf("휴대폰 따라", "밝게", "어둡게"), s.theme) { update(s.copy(theme = it)) }
            Toggle("숫자 줄 보이기", "맨 위에 1~0 줄", s.numberRow) { update(s.copy(numberRow = it)) }
            Toggle("누른 글자 크게 보기", "누르는 동안 글쇠 위에 글자를 크게 띄워요", s.preview) { update(s.copy(preview = it)) }
            Toggle("글쇠에 기호 표시", "길게 누르면 나오는 기호(@ # ₩ …)를 글쇠 귀퉁이에 보여 줘요", s.hints) { update(s.copy(hints = it)) }

            Section("입력")
            Toggle("스페이스바 밀어서 한/영 전환", "끄면 밀어서 커서 이동, 123 글쇠 자리에 한/A 글쇠가 다시 나와요", s.spaceSwipeLang) { update(s.copy(spaceSwipeLang = it)) }
            Toggle("스페이스 두 번 눌러 마침표", "낱말 뒤에서 스페이스를 빠르게 두 번 누르면 ‘. ’이 들어가요", s.doubleSpacePeriod) { update(s.copy(doubleSpacePeriod = it)) }
            ChoiceRow("길게 누르기 시간", "기호가 원치 않게 자주 나오면 ‘길게’, 기호를 빨리 넣고 싶으면 ‘짧게’", listOf("짧게", "보통", "길게"), s.longPress) {
                update(s.copy(longPress = it))
            }
            Toggle("자동 오타 수정", "스페이스를 누를 때 확실한 오타만 고쳐요", s.autoCorrect) { update(s.copy(autoCorrect = it)) }
            Toggle("추천 낱말", "고칠 말·이어질 말, 띄어 쓴 뒤엔 내가 자주 이어 쓰던 다음 낱말", s.suggestions) { update(s.copy(suggestions = it)) }
            Toggle("쓰면서 배우기", "손 습관(지우고 다시 친 글쇠·고친 오타)과 자주 쓰는 말 순서를 이 폰 안에서 배워요", s.learn) { update(s.copy(learn = it)) }

            Section("소리·진동")
            ChoiceRow("누를 때 진동", null, listOf("끄기", "약하게", "보통", "강하게"), if (s.vibrate) s.vibeLevel.coerceIn(1, 3) else 0) {
                update(if (it == 0) s.copy(vibrate = false) else s.copy(vibrate = true, vibeLevel = it))
            }
            Toggle("누를 때 소리", "휴대폰이 소리 모드일 때만 나요 (무음·진동 모드에서는 조용히)", s.sound) { update(s.copy(sound = it)) }
        }

        Spacer(Modifier.height(14.dp))
        Card {
            Text("인증번호 자동 표시", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val otpOn = remember(tick) { androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName) }
            Text(
                if (otpOn) "켜져 있어요 · 문자나 카카오 알림톡으로 인증번호가 오면 키보드 맨 위에 ‘🔑 인증번호’가 떠요 (5분 동안, 누르면 입력)"
                else "문자·카카오 알림톡·은행 앱 알림으로 온 인증번호를 키보드 맨 위에 띄워, 한 번 눌러 넣을 수 있어요. 핏키보드에 ‘알림 접근’을 허용해야 해요.",
                fontSize = 13.sp, color = if (otpOn) MainActivity.GOOD else MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("알림에서 인증번호 숫자만 꺼내 5분 동안 기억해요. 알림 글은 저장하지 않고, 인터넷 권한이 없어 어디로도 보내지 않아요.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            if (!otpOn) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val cn = android.content.ComponentName(c, io.github.poohcjs.fitkey.ime.OtpListener::class.java).flattenToString()
                    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn)
                    runCatching { c.startActivity(detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("알림 접근 허용하기") }
                Text("‘제한된 설정’이라 허용할 수 없다고 나오면: ‘앱 정보 열기’ → 오른쪽 위 ⋮ → ‘제한된 설정 허용’을 누른 뒤 다시 해 주세요. (스토어 밖에서 설치한 앱에 안드로이드가 거는 안전장치예요)",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                OutlinedButton(onClick = {
                    c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("앱 정보 열기") }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card {
            Text("배운 것", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("터치 ${learned.first}번 · 내 낱말 ${learned.second}개 · 이어 쓰는 말 ${learned.third}쌍", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text("모두 이 휴대폰에만 저장돼요. 이 앱은 인터넷 권한이 없어 입력한 글을 밖으로 보낼 수 없고, 비밀번호 칸·시크릿 모드에서는 아무것도 배우거나 기억하지 않아요. " +
                "‘이어 쓰는 말’은 같은 순서로 두 번 넘게 쓴 낱말을 띄어 쓴 뒤 키보드 맨 위에 먼저 보여 줘요.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ FitKeyStore.clearLearning(); learned = Triple(0, 0, 0) }) { Text("배운 것 모두 지우기") }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "한/영 전환: 스페이스바를 옆으로 밀기 · 숫자 패드: 123 글쇠 · 다른 키보드(삼성 키보드 등)로 바꾸기: 123 글쇠를 길게 누르거나 화면 아래 키보드 아이콘 · 커서 이동: 스페이스바를 길게 누른 뒤 밀기 · 키보드 맨 위: 방금 복사·잘라낸 글(1분 동안, 누르면 붙여넣기), 인증번호, 설정 · 치다가 오타가 나면 노린 말이 맨 위 가운데에 파랗게 떠요(누르면 바뀜)\n\n" +
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
private fun CalibrationScreen(onRestart: () -> Unit = {}, onDone: () -> Unit) {
    val c = LocalContext.current
    val settings = remember { FitKeyStore.settings(c) }
    // 보정 결과로 고른 크기·위치 (자동으로 적용하고, 되돌릴 수 있다)
    var look by remember { mutableStateOf(settings) }
    var advice by remember { mutableStateOf<io.github.poohcjs.fitkey.engine.SizeAdvisor.Advice?>(null) }
    val layout = remember { KeyboardLayout.letters(settings.numberRow) }
    val cal = remember { Calibration(FitKeyStore.touch, layout) }
    var version by remember { mutableIntStateOf(0) }
    var justDone by remember { mutableStateOf(false) }
    val before = remember { FitKeyStore.touch.samples }
    val bump = { version++ }
    androidx.compose.runtime.LaunchedEffect(version) {
        if (cal.done && advice == null && cal.samples.isNotEmpty()) {
            val (sx, sy) = FitKeyStore.touch.averageSpreads() ?: return@LaunchedEffect
            val a = io.github.poohcjs.fitkey.engine.SizeAdvisor.advise(
                io.github.poohcjs.fitkey.engine.SizeAdvisor.Look(settings.height, settings.width, settings.align, settings.bottomGap),
                sx, sy, cal.keyStats(), cal.letterRows,
            )
            advice = a
            if (a.changed) {
                val n = settings.copy(height = a.to.height, width = a.to.width, align = a.to.align, bottomGap = a.to.bottomGap)
                FitKeyStore.saveSettings(c, n)
                look = n
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("내 손에 맞추기", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("아래 문장을 평소처럼 쳐 주세요. 틀리면 지우고 고쳐도 되고, 오타가 남아 있어도 ‘다음 문장’을 누르면 돼요. 일부러 정확히 누르려고 애쓰지 않는 게 좋아요. 처음 3문장 뒤에는 덜 잰 글쇠·자주 빗나가는 글쇠가 많은 문장을 골라 내고, 모든 글쇠를 충분히 재면 끝나요 (보통 8~12문장).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            Spacer(Modifier.height(18.dp))
            version.let { }
            if (!cal.done) {
                val sent = cal.current!!
                Text("문장 ${cal.sentence + 1}" + if (justDone) "  ✓ 앞 문장 배웠어요" else "", fontSize = 12.5.sp,
                    color = if (justDone) MainActivity.GOOD else MaterialTheme.colorScheme.onSurfaceVariant)
                // 진행: 글쇠 26개 중 충분히 잰 글쇠 (부족한 글쇠가 많이 든 문장을 골라 내요)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { cal.covered.toFloat() / cal.totalLetters }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                Text(
                    "충분히 잰 글쇠 ${cal.covered} / ${cal.totalLetters}" + if (cal.sentence >= 3) " · 부족하거나 자주 빗나가는 글쇠가 많은 문장을 골라 내고 있어요" else "",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(sent.text, fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                // 친 글 (입력칸처럼)
                val typed = cal.typedText()
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = if (cal.onTrack) MaterialTheme.colorScheme.onSurface else MainActivity.ORANGE)) { append(typed) }
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("|") }
                        },
                        fontSize = 20.sp, lineHeight = 28.sp,
                    )
                }
                Text(
                    when {
                        typed.isEmpty() -> "여기에 친 글이 나와요. 다음에 칠 글쇠는 주황 테두리로 보여 줘요."
                        !cal.onTrack -> "문장과 달라요. 지우고 고치거나, 그대로 두고 계속 쳐도 괜찮아요."
                        else -> "좋아요, 계속 쳐 주세요."
                    },
                    fontSize = 12.5.sp, color = if (!cal.onTrack) MainActivity.ORANGE else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { cal.next(); justDone = true; bump() }, enabled = cal.enough) { Text("다음 문장 →") }
                    OutlinedButton(onClick = { cal.skip(); justDone = false; bump() }) { Text("건너뛰기") }
                }
                if (cal.sentence >= 3) {
                    Text("여기까지로 저장하기", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { cal.finish(); bump() }.padding(vertical = 10.dp))
                }
            } else {
                val n = cal.samples.size
                val afterMiss = remember(cal.samples.size) { cal.missesAfter() }
                Text("다 됐어요 👍", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (n == 0) {
                    Text("배울 터치가 없었어요. 문장을 쳐 본 뒤 ‘다음 문장’을 눌러 주세요.", fontSize = 14.sp)
                } else {
                    Text("글쇠 ${n}번 중 화면의 글쇠를 벗어난 터치: ${cal.misses}번 (${pct(cal.misses, n)}%)", fontSize = 14.sp)
                    Text("배운 습관으로 다시 판정하면: ${afterMiss}번 (${pct(afterMiss, n)}%)", fontSize = 14.sp, color = MainActivity.GOOD, fontWeight = FontWeight.Bold)
                }
                // 글쇠별 결과: 보정이 많이 필요한 글자부터
                val reports = remember(cal.samples.size) { cal.report().filter { it.n > 0 } }
                val worst = reports.filter { it.misses > 0 || kotlin.math.hypot(it.offset.first, it.offset.second) > 0.15f }.take(6)
                if (worst.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("보정이 많이 필요한 글자", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    worst.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(keyName(r.letter), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                            Text(
                                "${r.n}번 중 ${r.misses}번 빗나감" + (direction(r.offset)?.let { " · $it 치우침" } ?: ""),
                                fontSize = 13.5.sp, color = if (r.missRate >= 0.25f) MainActivity.ORANGE else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Text("키보드 색: 초록 = 잘 맞음, 노랑 = 가끔 빗나감, 주황 = 자주 빗나감. 주황 선은 실제로 누르는 쪽이에요.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                }
                advice?.let { a -> SizeAdviceCard(a, applied = look != settings, onUndo = {
                    FitKeyStore.saveSettings(c, settings); look = settings
                }, onApply = {
                    val n = settings.copy(height = a.to.height, width = a.to.width, align = a.to.align, bottomGap = a.to.bottomGap)
                    FitKeyStore.saveSettings(c, n); look = n
                }, onRestart = { FitKeyStore.save(); onRestart() }) }
                Text("아래 키보드의 주황 선은 글쇠마다 실제로 누르는 쪽이에요. 앞으로 이 쪽을 그 글쇠로 알아들어요. 쓰면서도 계속 배워요.",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                Button(onClick = { FitKeyStore.save(); if (n > 0) FitKeyStore.markCalibrated(c); onDone() }) { Text("저장하고 돌아가기") }
                Text("(터치 ${FitKeyStore.touch.samples - before}번을 새로 배웠어요)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        AndroidView(
            factory = { ctx ->
                KeyboardView(ctx).apply {
                    this.layout = layout
                    io.github.poohcjs.fitkey.ime.FitKeyService.applyLook(this, settings)
                    stripHint = "보정 중 · 위 문장을 쳐 주세요"
                    onAspect = { FitKeyStore.touch.aspect = it }
                    listener = object : KeyboardView.Listener {
                        override fun onKey(key: Key, ux: Float, uy: Float) {
                            val moved = cal.press(key, ux, uy)
                            if (moved) justDone = true else if (key.kind == io.github.poohcjs.fitkey.engine.KeyKind.LETTER) justDone = false
                            bump()
                            refreshCalibrationView(this@apply, cal)
                        }
                        override fun onBackspace() { cal.backspace(); bump(); refreshCalibrationView(this@apply, cal) }
                    }
                    refreshCalibrationView(this, cal)
                }
            },
            update = { v ->
                version.let { }
                io.github.poohcjs.fitkey.ime.FitKeyService.applyLook(v, look)
                refreshCalibrationView(v, cal)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 보정 결과로 맞춘 크기·위치 */
@Composable
private fun SizeAdviceCard(a: io.github.poohcjs.fitkey.engine.SizeAdvisor.Advice, applied: Boolean, onUndo: () -> Unit, onApply: () -> Unit, onRestart: () -> Unit) {
    Spacer(Modifier.height(14.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(14.dp)) {
        val now = Math.round(a.missNow * 1000) / 10f
        val after = Math.round(a.missAfter * 1000) / 10f
        if (!a.changed) {
            Text(if (a.reasons.isEmpty()) "지금 크기·위치가 손에 잘 맞아요 👍" else "크기는 지금이 알맞아요", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("흩어짐으로 계산한 빗나감 약 $now%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            a.reasons.forEach { Text("· $it", fontSize = 13.sp, lineHeight = 18.sp) }
            return@Column
        }
        Text(if (applied) "키보드 크기·위치를 손에 맞게 바꿨어요" else "추천 크기·위치", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        a.reasons.forEach { Text("· $it", fontSize = 13.sp, lineHeight = 18.sp) }
        Text(
            "높이 ${Math.round(a.from.height * 100)}% → ${Math.round(a.to.height * 100)}% · 너비 ${Math.round(a.from.width * 100)}% → ${Math.round(a.to.width * 100)}%" +
                (if (a.to.bottomGap != a.from.bottomGap) " · 아래 여백 ${a.from.bottomGap} → ${a.to.bottomGap}dp" else ""),
            fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
        if (a.missAfter < a.missNow) {
            Text("예상 빗나감 $now% → $after%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MainActivity.GOOD, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (applied) OutlinedButton(onClick = onUndo) { Text("되돌리기") } else Button(onClick = onApply) { Text("추천대로 바꾸기") }
            if (applied) Button(onClick = onRestart) { Text("바뀐 크기로 다시 재기") }
        }
        if (applied) Text("바뀐 크기에서 한 번 더 재면 습관을 더 정확히 배워요 (배운 습관은 새 크기에 맞게 이미 환산했어요).",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 글쇠 이름: 한글 자모와 영문 (예: ㅔ p) */
private fun keyName(letter: Char): String = "${io.github.poohcjs.fitkey.engine.Hangul.jamoOf(letter) ?: letter} $letter"

/** 치우친 방향 (글쇠 너비의 8% 넘게 치우칠 때만) */
private fun direction(o: Pair<Float, Float>): String? {
    val h = when { o.first > 0.08f -> "오른쪽"; o.first < -0.08f -> "왼쪽"; else -> null }
    val v = when { o.second > 0.08f -> "아래"; o.second < -0.08f -> "위"; else -> null }
    return listOfNotNull(h, v).joinToString(" ").ifEmpty { null }?.let { "$it(으)로" }
}

private fun refreshCalibrationView(v: KeyboardView, cal: Calibration) {
    if (cal.done) {
        v.target = null
        v.targetShift = false
        v.offsets = FitKeyStore.touch.offsets()
        v.missRates = cal.report().filter { it.n > 0 }.associate { it.letter to it.missRate }
        v.lang = Lang.KO
        v.shift = ShiftState.OFF
        return
    }
    val sent = cal.current ?: return
    v.lang = sent.lang
    v.shift = if (cal.shift) ShiftState.ONCE else ShiftState.OFF
    val nk = cal.nextKey()?.takeIf { it != ' ' }
    // 쌍자음·ㅒ·ㅖ는 먼저 Shift에 테두리
    v.targetShift = nk != null && nk.isUpperCase() && !cal.shift
    v.target = if (v.targetShift) null else nk?.lowercaseChar()
}

/** 키보드 크기·위치: 삼성 키보드처럼 파란 테두리의 손잡이를 끌어서 맞춘다 */
@Composable
private fun SizeScreen(onDone: () -> Unit) {
    val c = LocalContext.current
    var s by remember { mutableStateOf(FitKeyStore.settings(c)) }
    fun update(n: KeySettings) { s = n; FitKeyStore.saveSettings(c, n) }
    val layout = remember(s.numberRow) { KeyboardLayout.letters(s.numberRow) }
    var fine by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("키보드 크기·위치", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "아래 키보드의 파란 손잡이를 끌어서 맞추세요.\n· 위·아래 손잡이: 높이\n· 왼쪽·오른쪽 손잡이: 너비\n· 가운데 ✥: 키보드 옮기기 (위·아래·옆)\n" +
                    "다 되면 ‘확인’을 누르세요. 키보드를 쓰다가도 맨 위 왼쪽 ⚙를 누르면 이 화면으로 올 수 있어요.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("빠른 선택", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Preset("기본") { update(s.copy(height = 1f, width = 1f, align = 0f, bottomGap = 0)) }
                Preset("크게") { update(s.copy(height = 1.2f, width = 1f, align = 0f)) }
            }
            OneHandRow(s) { update(it) }
            Text(
                "지금: 높이 ${(s.height * 100).toInt()}% · 너비 ${(s.width * 100).toInt()}%" + (if (s.bottomGap > 0) " · 아래 여백 ${s.bottomGap}dp" else ""),
                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
            Toggle("숫자 줄 보이기", "맨 위에 1~0 줄", s.numberRow) { update(s.copy(numberRow = it)) }
            Text(if (fine) "세밀하게 조절 ▴" else "세밀하게 조절 ▾", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { fine = !fine }.padding(vertical = 8.dp))
            if (fine) {
                Text("높이 ${(s.height * 100).toInt()}%", fontSize = 14.sp)
                Slider(s.height, { update(s.copy(height = Math.round(it * 20) / 20f)) }, valueRange = 0.75f..1.5f)
                Text("너비 ${(s.width * 100).toInt()}%", fontSize = 14.sp)
                Slider(s.width, { update(s.copy(width = Math.round(it * 20) / 20f)) }, valueRange = 0.6f..1.0f)
                Text("좌우 위치", fontSize = 14.sp)
                Slider(s.align, { update(s.copy(align = Math.round(it * 4) / 4f)) }, valueRange = -1f..1f)
                Text("아래 여백 ${s.bottomGap}dp", fontSize = 14.sp)
                Slider(s.bottomGap.toFloat(), { update(s.copy(bottomGap = Math.round(it / 4f) * 4)) }, valueRange = 0f..120f)
            }
            Text("크기를 많이 바꿨다면 ‘내 손에 맞추기’를 한 번 더 하면 좋아요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
        }
        AndroidView(
            factory = { ctx ->
                KeyboardView(ctx).apply {
                    this.layout = layout
                    vibrate = s.vibrate
                    adjusting = true
                    listener = object : KeyboardView.Listener {
                        override fun onKey(key: Key, ux: Float, uy: Float) {}
                        override fun onAdjust(height: Float, width: Float, align: Float, gap: Int, done: Boolean) {
                            update(s.copy(height = height, width = width, align = align, bottomGap = gap))
                            if (done) onDone()
                        }
                    }
                }
            },
            update = { v ->
                if (v.layout !== layout) v.layout = layout
                v.heightScale = s.height
                v.widthScale = s.width
                v.align = s.align
                v.bottomGap = s.bottomGap
                v.spaceSwipeLang = s.spaceSwipeLang
                v.theme = s.theme
                v.hints = s.hints
                if (!v.adjusting) v.adjusting = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 한 손 모드: 0 끄기, -1 왼손, 1 오른손, null 그 밖 (가운데에 좁게 등 손잡이로 맞춘 모양) */
private fun oneHandSide(s: KeySettings): Int? = when {
    s.width >= 0.95f -> 0
    s.align <= -0.5f -> -1
    s.align >= 0.5f -> 1
    else -> null
}

/** 한 손 모드 켜고 끄기. 켤 때는 전에 맞춘 좁은 너비를 그대로, 없으면 80% */
@Composable
private fun OneHandRow(s: KeySettings, update: (KeySettings) -> Unit) {
    val narrow = if (s.width < 0.95f) s.width else 0.8f
    ChoiceRow(
        "한 손 모드", "키보드를 좁혀 한쪽으로 모아요. 빈 쪽을 눌러도 키보드가 옮겨지지 않고 가장 가까운 글쇠로 쳐져요.",
        listOf("끄기", "왼손", "오른손"), when (oneHandSide(s)) { 0 -> 0; -1 -> 1; 1 -> 2; else -> -1 },
    ) {
        update(when (it) { 1 -> s.copy(width = narrow, align = -1f); 2 -> s.copy(width = narrow, align = 1f); else -> s.copy(width = 1f, align = 0f) })
    }
}

/** 제목 + 고르는 단추 줄 (selected: 고른 것의 순서, 없으면 -1) */
@Composable
private fun ChoiceRow(title: String, desc: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, fontSize = 14.5.sp)
        if (desc != null) Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o -> Choice(o, i == selected) { onSelect(i) } }
        }
    }
}

/** 설정 안의 작은 묶음 제목 */
@Composable
private fun Section(title: String) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Preset(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun pct(a: Int, n: Int) = if (n == 0) 0 else Math.round(a * 1000f / n) / 10f
