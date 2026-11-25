package com.pedro.halo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

data class HaloColors(
    val dark: Boolean,
    val accent: Color,
    val panel: Color,        // frosted panels (drawer, folders)
    val card: Color,         // solid cards (settings, menus)
    val background: Color,   // settings background
    val text: Color,
    val subText: Color,
    val scrim: Color,
    val chip: Color,
)

val LocalHalo = staticCompositionLocalOf {
    HaloColors(false, Color(0xFF3D7BFF), Color(0xCCFFFFFF), Color.White, Color(0xFFF4F4F8),
        Color(0xFF111114), Color(0xFF6B6B74), Color(0x66000000), Color(0xFFE9E9EF))
}

@Composable
fun isDark(s: LauncherSettings): Boolean = when (s.themeMode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun HaloTheme(s: LauncherSettings, content: @Composable () -> Unit) {
    val dark = isDark(s)
    val accent = Color(s.accent)
    val c = if (dark) HaloColors(true, accent, Color(0xB8151518), Color(0xFF1C1C20), Color(0xFF0F0F12),
        Color(0xFFF2F2F5), Color(0xFF9A9AA3), Color(0x99000000), Color(0xFF2A2A30))
    else HaloColors(false, accent, Color(0xC8F7F7FA), Color.White, Color(0xFFF4F4F8),
        Color(0xFF111114), Color(0xFF6B6B74), Color(0x55000000), Color(0xFFE9E9EF))
    val scheme = if (dark) darkColorScheme(primary = accent, secondary = accent, surface = c.card, background = c.background)
    else lightColorScheme(primary = accent, secondary = accent, surface = c.card, background = c.background)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalHalo provides c) { content() }
    }
}

fun composeIconShape(shape: IconShape): Shape = when (shape) {
    IconShape.CIRCLE -> CircleShape
    IconShape.ROUNDED -> RoundedCornerShape(22)
    IconShape.SQUIRCLE -> GenericShape { size, _ ->
        val p = Shapes.path(IconShape.SQUIRCLE, size.width).asComposePath()
        addPath(p)
    }
}
