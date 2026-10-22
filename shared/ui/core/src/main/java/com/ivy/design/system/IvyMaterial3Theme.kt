package com.ivy.design.system

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.ivy.design.system.colors.IvyColors

private object NoIndication : Indication {
    private object NoIndicationInstance : IndicationInstance {
        override fun ContentDrawScope.drawIndication() {
            drawContent()
        }
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        return NoIndicationInstance
    }
}

private object NoRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor(): Color = Color.Transparent

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(0f, 0f, 0f, 0f)
}

@Composable
fun IvyMaterial3Theme(
    isTrueBlack: Boolean,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalRippleTheme provides NoRippleTheme
    ) {
        MaterialTheme(
            colorScheme = if (dark) ivyDarkColorScheme(isTrueBlack) else ivyLightColorScheme(),
            content = content,
        )
    }
}

private fun ivyLightColorScheme(): ColorScheme = ColorScheme(
    primary = IvyColors.Purple.primary,
    onPrimary = IvyColors.White,
    primaryContainer = IvyColors.Purple.light,
    onPrimaryContainer = IvyColors.White,
    inversePrimary = IvyColors.Purple.dark,
    secondary = IvyColors.Green.primary,
    onSecondary = IvyColors.White,
    secondaryContainer = IvyColors.Green.light,
    onSecondaryContainer = IvyColors.White,
    tertiary = IvyColors.Green.primary,
    onTertiary = IvyColors.White,
    tertiaryContainer = IvyColors.Green.light,
    onTertiaryContainer = IvyColors.White,

    error = IvyColors.Red.primary,
    onError = IvyColors.White,
    errorContainer = IvyColors.Red.light,
    onErrorContainer = IvyColors.White,

    background = IvyColors.White,
    onBackground = IvyColors.Black,
    surface = IvyColors.White,
    onSurface = IvyColors.Black,
    surfaceVariant = IvyColors.ExtraLightGray,
    onSurfaceVariant = IvyColors.Black,
    surfaceTint = IvyColors.Black,
    inverseSurface = IvyColors.DarkGray,
    inverseOnSurface = IvyColors.White,

    outline = IvyColors.Gray,
    outlineVariant = IvyColors.DarkGray,
    scrim = IvyColors.ExtraDarkGray.copy(alpha = 0.8f)
)

private fun ivyDarkColorScheme(isTrueBlack: Boolean): ColorScheme = ColorScheme(
    primary = IvyColors.Purple.primary,
    onPrimary = IvyColors.White,
    primaryContainer = IvyColors.Purple.light,
    onPrimaryContainer = IvyColors.White,
    inversePrimary = IvyColors.Purple.dark,
    secondary = IvyColors.Green.primary,
    onSecondary = IvyColors.White,
    secondaryContainer = IvyColors.Green.light,
    onSecondaryContainer = IvyColors.White,
    tertiary = IvyColors.Green.primary,
    onTertiary = IvyColors.White,
    tertiaryContainer = IvyColors.Green.light,
    onTertiaryContainer = IvyColors.White,

    error = IvyColors.Red.primary,
    onError = IvyColors.White,
    errorContainer = IvyColors.Red.light,
    onErrorContainer = IvyColors.White,

    background = if (isTrueBlack) IvyColors.TrueBlack else IvyColors.Black,
    onBackground = IvyColors.White,
    surface = if (isTrueBlack) IvyColors.TrueBlack else IvyColors.Black,
    onSurface = IvyColors.White,
    surfaceVariant = IvyColors.ExtraDarkGray,
    onSurfaceVariant = IvyColors.White,
    surfaceTint = IvyColors.White,
    inverseSurface = IvyColors.LightGray,
    inverseOnSurface = if (isTrueBlack) IvyColors.TrueBlack else IvyColors.Black,

    outline = IvyColors.Gray,
    outlineVariant = IvyColors.LightGray,
    scrim = IvyColors.ExtraLightGray.copy(alpha = 0.8f)
)
