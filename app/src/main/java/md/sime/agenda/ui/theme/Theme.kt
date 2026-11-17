package md.sime.agenda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.ThemeMode

@Immutable
data class SimePalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val gradeHigh: Color,
    val gradeMid: Color,
    val gradeLowMid: Color,
    val gradeLow: Color,
    val absenceMotivated: Color,
    val absenceUnmotivated: Color,
    val isDark: Boolean
)

val AmoledPalette = SimePalette(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0B0E),
    surfaceElevated = Color(0xFF121318),
    border = Color(0xFF22242C),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA0A3AD),
    textMuted = Color(0xFF636672),
    accent = Color(0xFFE4E4E9),
    gradeHigh = Color(0xFFF5F5F7),
    gradeMid = Color(0xFFD4D4D8),
    gradeLowMid = Color(0xFFC8A97E),
    gradeLow = Color(0xFFD96B6B),
    absenceMotivated = Color(0xFF9496A1),
    absenceUnmotivated = Color(0xFFD96B6B),
    isDark = true
)

val DarkSlatePalette = SimePalette(
    background = Color(0xFF090A0D),
    surface = Color(0xFF111318),
    surfaceElevated = Color(0xFF181B22),
    border = Color(0xFF262A34),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA0A3AD),
    textMuted = Color(0xFF686C78),
    accent = Color(0xFFE4E4E9),
    gradeHigh = Color(0xFFF5F5F7),
    gradeMid = Color(0xFFD4D4D8),
    gradeLowMid = Color(0xFFC8A97E),
    gradeLow = Color(0xFFD96B6B),
    absenceMotivated = Color(0xFF9496A1),
    absenceUnmotivated = Color(0xFFD96B6B),
    isDark = true
)

val LightPalette = SimePalette(
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEAECEF),
    border = Color(0xFFDCDFE6),
    textPrimary = Color(0xFF0F1115),
    textSecondary = Color(0xFF525662),
    textMuted = Color(0xFF888C99),
    accent = Color(0xFF18191E),
    gradeHigh = Color(0xFF0F1115),
    gradeMid = Color(0xFF3F434E),
    gradeLowMid = Color(0xFF9A6E32),
    gradeLow = Color(0xFFB93838),
    absenceMotivated = Color(0xFF525662),
    absenceUnmotivated = Color(0xFFB93838),
    isDark = false
)

val LocalSimePalette = staticCompositionLocalOf { AmoledPalette }

object SimeTheme {
    val colors: SimePalette
        @Composable get() = LocalSimePalette.current
}

private val SimeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun SimeAgendaTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val palette = when (themeMode) {
        ThemeMode.AMOLED -> AmoledPalette
        ThemeMode.DARK -> DarkSlatePalette
        ThemeMode.LIGHT -> LightPalette
    }

    val m3Scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF090A0D),
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.gradeLow
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.gradeLow
        )
    }

    CompositionLocalProvider(LocalSimePalette provides palette) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = SimeTypography,
            content = content
        )
    }
}

fun gradeColor(grade: Int?, palette: SimePalette): Color {
    return when {
        grade == null -> palette.textSecondary
        grade >= 9 -> palette.gradeHigh
        grade >= 7 -> palette.gradeMid
        grade >= 5 -> palette.gradeLowMid
        else -> palette.gradeLow
    }
}
