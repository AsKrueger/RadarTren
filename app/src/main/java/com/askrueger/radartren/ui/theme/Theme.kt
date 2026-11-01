package com.askrueger.radartren.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NavyLight,
    secondary = SlateLight,
    tertiary = SignalLight,
)

private val LightColorScheme = lightColorScheme(
    primary = Navy,
    secondary = Slate,
    tertiary = Signal,
)

/** A fixed palette rather than dynamic color: urgency colors must read the same on every phone. */
@Composable
fun RadarTrenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
