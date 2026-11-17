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
    surface = Color(0xFF0B0B0E),
    surfaceElevated = Color(0xFF141418),
    border = Color(0xFF202026),
    textPrimary = Color(0xFFF4F4F6),
    textSecondary = Color(0xFF9898A3),
    textMuted = Color(0xFF60606B),
    accent = Color(0xFF3B82F6),
    gradeHigh = Color(0xFF10B981),
    gradeMid = Color(0xFF38BDF8),
    gradeLowMid = Color(0xFFF59E0B),
    gradeLow = Color(0xFFEF4444),
    absenceMotivated = Color(0xFF3B82F6),
    absenceUnmotivated = Color(0xFFEF4444),
    isDark = true
)

val DarkSlatePalette = SimePalette(
    background = Color(0xFF0F1115),
    surface = Color(0xFF161920),
    surfaceElevated = Color(0xFF1E222B),
    border = Color(0xFF2A2F3B),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF6B7280),
    accent = Color(0xFF3B82F6),
    gradeHigh = Color(0xFF10B981),
    gradeMid = Color(0xFF38BDF8),
    gradeLowMid = Color(0xFFF59E0B),
    gradeLow = Color(0xFFEF4444),
    absenceMotivated = Color(0xFF3B82F6),
    absenceUnmotivated = Color(0xFFEF4444),
    isDark = true
)

val LightPalette = SimePalette(
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF0F2F5),
    border = Color(0xFFE2E5EC),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF4B5563),
    textMuted = Color(0xFF9CA3AF),
    accent = Color(0xFF2563EB),
    gradeHigh = Color(0xFF059669),
    gradeMid = Color(0xFF0284C7),
    gradeLowMid = Color(0xFFD97706),
    gradeLow = Color(0xFFDC2626),
    absenceMotivated = Color(0xFF2563EB),
    absenceUnmotivated = Color(0xFFDC2626),
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
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
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
