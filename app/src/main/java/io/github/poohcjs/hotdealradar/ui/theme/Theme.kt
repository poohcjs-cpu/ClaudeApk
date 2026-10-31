package io.github.poohcjs.hotdealradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.poohcjs.hotdealradar.R

// ── 글꼴: HDR Sans (Pretendard 한글 2,350자 부분집합, 가변 굵기) ─────────────
val HdrSans = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.hdr_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

// ── 색 ─────────────────────────────────────────────────────
val Brand = Color(0xFFFF4B2B)
val BrandDeep = Color(0xFFE5291A)
val Good = Color(0xFF12A15E)
val Warn = Color(0xFFE08A00)
val Bad = Color(0xFFE5402B)
val Discover = Color(0xFF3D6DF2)
val Gold = Color(0xFFFF9F0A)

@Immutable
data class Palette(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val sub: Color,
    val faint: Color,
    val line: Color,
    val tile: Color,        // 상품 사진 바탕
    val brandSoft: Color,
    val goodSoft: Color,
    val hero: Brush,        // 머리 카드 바탕
    val brandBrush: Brush,
    val legendBrush: Brush,
    val dark: Boolean,
)

private val LightPalette = Palette(
    bg = Color(0xFFF3F4F6),
    card = Color.White,
    ink = Color(0xFF15171C),
    sub = Color(0xFF5F6670),
    faint = Color(0xFF9AA0A8),
    line = Color(0xFFEBEDF0),
    tile = Color(0xFFF6F7F9),
    brandSoft = Color(0xFFFFEEEA),
    goodSoft = Color(0xFFE7F6EE),
    hero = Brush.linearGradient(listOf(Color(0xFF1B1E26), Color(0xFF2B2230), Color(0xFF3A1F22))),
    brandBrush = Brush.horizontalGradient(listOf(Color(0xFFFF7A3D), Color(0xFFFF3B30))),
    legendBrush = Brush.horizontalGradient(listOf(Color(0xFFFFB321), Color(0xFFFF5A1F))),
    dark = false,
)

private val DarkPalette = Palette(
    bg = Color(0xFF0D0E11),
    card = Color(0xFF17191E),
    ink = Color(0xFFF3F4F6),
    sub = Color(0xFFA7ADB6),
    faint = Color(0xFF6E747D),
    line = Color(0xFF24272D),
    tile = Color(0xFFFFFFFF),
    brandSoft = Color(0xFF3A1A14),
    goodSoft = Color(0xFF10291D),
    hero = Brush.linearGradient(listOf(Color(0xFF1E2130), Color(0xFF2A1D2B), Color(0xFF3B1C1E))),
    brandBrush = Brush.horizontalGradient(listOf(Color(0xFFFF7A3D), Color(0xFFFF3B30))),
    legendBrush = Brush.horizontalGradient(listOf(Color(0xFFFFB321), Color(0xFFFF5A1F))),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.hdr(letter: Float = -0.02f) = copy(fontFamily = HdrSans, letterSpacing = letter.em)
    return Typography(
        displayLarge = base.displayLarge.hdr(-0.03f),
        displayMedium = base.displayMedium.hdr(-0.03f),
        displaySmall = base.displaySmall.hdr(-0.03f),
        headlineLarge = base.headlineLarge.hdr(-0.03f).copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.hdr(-0.03f).copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.hdr(-0.025f).copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.hdr(-0.025f).copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.hdr().copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.hdr().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.hdr(-0.01f),
        bodyMedium = base.bodyMedium.hdr(-0.01f).copy(lineHeight = 21.sp),
        bodySmall = base.bodySmall.hdr(-0.005f),
        labelLarge = base.labelLarge.hdr(-0.01f).copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.hdr(-0.005f),
        labelSmall = base.labelSmall.hdr(0f),
    )
}

@Composable
fun HotdealTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = Brand, onPrimary = Color.White, primaryContainer = p.brandSoft, onPrimaryContainer = Color(0xFFFFD9D2),
            secondary = p.sub, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink,
            surfaceVariant = p.line, onSurfaceVariant = p.sub, outline = p.line, outlineVariant = p.line,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.line,
        )
    } else {
        lightColorScheme(
            primary = Brand, onPrimary = Color.White, primaryContainer = p.brandSoft, onPrimaryContainer = BrandDeep,
            secondary = p.sub, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink,
            surfaceVariant = p.tile, onSurfaceVariant = p.sub, outline = p.line, outlineVariant = p.line,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.line,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}

object HD {
    val p: Palette @Composable get() = LocalPalette.current
}
