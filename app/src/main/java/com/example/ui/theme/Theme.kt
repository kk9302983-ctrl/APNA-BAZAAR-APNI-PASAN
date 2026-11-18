package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ApnaBazaarLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = PureWhite,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = FreshTeal,
    onSecondary = PureWhite,
    secondaryContainer = FreshTealContainer,
    onSecondaryContainer = InkPrimary,
    tertiary = SaffronAccent,
    onTertiary = PureWhite,
    tertiaryContainer = SaffronContainer,
    onTertiaryContainer = OnSaffronContainer,
    background = SoftCanvas,
    onBackground = InkPrimary,
    surface = PureWhite,
    onSurface = InkPrimary,
    surfaceVariant = CardNeutral,
    onSurfaceVariant = InkSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = DangerCoral,
    onError = PureWhite,
    errorContainer = DangerContainer,
    onErrorContainer = DangerCoral
)

val ApnaBazaarShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ApnaBazaarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApnaBazaarLightColorScheme,
        typography = Typography,
        shapes = ApnaBazaarShapes,
        content = content
    )
}
