package com.example.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object ThemeState {
    var isDarkState by mutableStateOf<Boolean?>(null)
}

private val DarkColorScheme = darkColorScheme(
    primary = SupabaseGreen,
    onPrimary = Color(0xFF0C1912),
    primaryContainer = SupabaseGreenSubtleDark,
    onPrimaryContainer = SupabaseGreenLight,
    secondary = VaultTextSecondaryDark,
    onSecondary = Color.Black,
    background = VaultCanvasDark,
    onBackground = VaultTextPrimaryDark,
    surface = VaultCardDark,
    onSurface = VaultTextPrimaryDark,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = VaultTextSecondaryDark,
    outline = VaultCardBorderDark,
    outlineVariant = Color(0xFF333333)
)

private val LightColorScheme = lightColorScheme(
    primary = SupabaseGreenDark,
    onPrimary = Color.White,
    primaryContainer = SupabaseGreenSubtle,
    onPrimaryContainer = SupabaseGreenDarker,
    secondary = VaultTextSecondaryLight,
    onSecondary = Color.White,
    background = VaultCanvasLight,
    onBackground = VaultTextPrimaryLight,
    surface = VaultCardLight,
    onSurface = VaultTextPrimaryLight,
    surfaceVariant = VaultCanvasLight,
    onSurfaceVariant = VaultTextSecondaryLight,
    outline = VaultCardBorderLight,
    outlineVariant = Color(0xFFDED8D1)
)

@Composable
fun VaultTheme(
    darkTheme: Boolean = ThemeState.isDarkState ?: isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to adhere to Supabase brand theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}