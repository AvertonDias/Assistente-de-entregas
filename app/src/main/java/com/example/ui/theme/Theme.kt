package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BentoPrimaryDarkTheme,
    onPrimary = Color(0xFF003258),
    primaryContainer = BentoPrimaryContainerDarkTheme,
    onPrimaryContainer = BentoOnPrimaryContainerDarkTheme,
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF00354E),
    background = BentoBackgroundDark,
    onBackground = BentoTextPrimaryDark,
    surface = BentoSurfaceDark,
    onSurface = BentoTextPrimaryDark,
    surfaceVariant = BentoSurfaceCardDark,
    onSurfaceVariant = BentoTextSecondaryDark,
    outline = BentoBorderDark,
    outlineVariant = BentoBorderMutedDark,
    error = ErrorRedDarkTheme,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorRedContainerDarkTheme,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LightColorScheme = lightColorScheme(
    primary = BentoPrimary,
    onPrimary = Color.White,
    primaryContainer = BentoPrimaryContainer,
    onPrimaryContainer = BentoOnPrimaryContainer,
    secondary = BentoPrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = BentoBackgroundLight,
    onBackground = BentoTextPrimary,
    surface = BentoSurfaceLight,
    onSurface = BentoTextPrimary,
    surfaceVariant = BentoSurfaceCard,
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoBorder,
    outlineVariant = BentoBorderMuted,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun DeliveryTheme(
    themeMode: String = "LIGHT", // Force LIGHT theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = false

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
