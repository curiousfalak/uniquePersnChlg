package com.example.uniquepersnchlg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FaceCollageColorScheme = lightColorScheme(
    primary = HotPink,
    onPrimary = CloudWhite,
    primaryContainer = HotPinkLight,
    onPrimaryContainer = HotPinkDark,
    secondary = InkBlack,
    onSecondary = CloudWhite,
    background = OffWhite,
    onBackground = InkBlack,
    surface = CloudWhite,
    onSurface = InkBlack,
    surfaceVariant = Divider,
    onSurfaceVariant = SoftGray,
    error = HotPinkDark,
    onError = CloudWhite
)

@Composable
fun FaceCollageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FaceCollageColorScheme,
        typography = FaceCollageTypography,
        shapes = FaceCollageShapes,
        content = content
    )
}
