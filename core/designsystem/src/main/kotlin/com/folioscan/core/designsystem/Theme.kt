package com.folioscan.core.designsystem

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folioscan.core.model.ThemePreference

// Precision Document Studio Palette
val SlateInk = Color(0xFF0F172A)
val SlateSurfaceDark = Color(0xFF1E293B)
val SlateContainerDark = Color(0xFF273549)
val PaperAlabaster = Color(0xFFF8FAFC)
val PaperPure = Color(0xFFFFFFFF)
val PaperMuted = Color(0xFFF1F5F9)
val CobaltPrimary = Color(0xFF1D4ED8)
val CobaltLight = Color(0xFF60A5FA)
val CobaltContainer = Color(0xFFDBEAFE)
val CobaltOnContainer = Color(0xFF1E3A8A)
val EmeraldScanLock = Color(0xFF10B981)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonDanger = Color(0xFFDC2626)
val SlateOutline = Color(0xFFCBD5E1)
val SlateOutlineDark = Color(0xFF475569)

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = PaperPure,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = CobaltOnContainer,
    secondary = Color(0xFF0F766E),
    onSecondary = PaperPure,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = AmberWarning,
    onTertiary = SlateInk,
    background = PaperAlabaster,
    onBackground = SlateInk,
    surface = PaperPure,
    onSurface = SlateInk,
    surfaceVariant = PaperMuted,
    onSurfaceVariant = Color(0xFF475569),
    outline = SlateOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = CrimsonDanger,
    onError = PaperPure
)

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = CobaltLight,
    onPrimary = SlateInk,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF2DD4BF),
    onSecondary = SlateInk,
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = AmberWarning,
    onTertiary = SlateInk,
    background = SlateInk,
    onBackground = PaperAlabaster,
    surface = SlateSurfaceDark,
    onSurface = PaperAlabaster,
    surfaceVariant = SlateContainerDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = SlateOutlineDark,
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = SlateInk
)

@Immutable
data class FolioSemanticColors(
    val scanLockGreen: Color,
    val qualityWarningAmber: Color,
    val vaultAccent: Color
)

val LocalFolioSemanticColors = staticCompositionLocalOf {
    FolioSemanticColors(
        scanLockGreen = EmeraldScanLock,
        qualityWarningAmber = AmberWarning,
        vaultAccent = CobaltPrimary
    )
}

@Immutable
object FolioSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val cardCorner = 12.dp
    val chipCorner = 10.dp
    val sheetCorner = 24.dp
    val cardElevation = 2.dp
    val minTouchTarget = 48.dp
}

val PlusJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

val FolioTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val FolioShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(FolioSpacing.cardCorner),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(FolioSpacing.sheetCorner)
)

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun FolioScanTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val semantic = FolioSemanticColors(
        scanLockGreen = EmeraldScanLock,
        qualityWarningAmber = AmberWarning,
        vaultAccent = colorScheme.primary
    )
    val reduceMotion = rememberReduceMotionEnabled()

    CompositionLocalProvider(
        LocalFolioSemanticColors provides semantic,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FolioTypography,
            shapes = FolioShapes,
            content = content
        )
    }
}
