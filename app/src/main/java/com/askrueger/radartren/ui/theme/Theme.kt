package com.askrueger.radartren.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NavyLight,
    primaryContainer = DeepNavy,
    onPrimaryContainer = IceBlue,
    secondary = SlateLight,
    secondaryContainer = NightRaised,
    onSecondaryContainer = IceBlue,
    tertiary = SignalLight,
    background = Night,
    surface = NightSurface,
    surfaceVariant = NightRaised,
    surfaceContainer = NightSurface,
    surfaceContainerLow = NightSurface,
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = NightRaised,
)

private val LightColorScheme = lightColorScheme(
    primary = Navy,
    primaryContainer = IceBlue,
    onPrimaryContainer = DeepNavy,
    secondary = Slate,
    secondaryContainer = Mist,
    onSecondaryContainer = DeepNavy,
    tertiary = Signal,
    background = Paper,
    surface = Paper,
    surfaceVariant = Mist,
    surfaceContainer = PaperRaised,
    surfaceContainerLow = PaperRaised,
    surfaceContainerHigh = Mist,
    surfaceContainerHighest = Mist,
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
