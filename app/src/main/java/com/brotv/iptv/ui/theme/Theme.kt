package com.brotv.iptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Design baseline is 1920x1080. We do NOT build separate 4K layouts: 4K panels
 * simply render this same layout at a higher density (dp values stay fixed),
 * so element positions never move and the UI never "flips" between
 * resolutions. Only bitmap assets (logo, posters, channel logos) need @xhdpi
 * variants for crispness at 4K — see res/drawable-xhdpi, drawable-xxhdpi.
 */
private val BroTvDarkColorScheme = darkColorScheme(
    primary = BroTvColors.Gold,
    onPrimary = BroTvColors.TextPrimary,
    secondary = BroTvColors.GoldDeep,
    onSecondary = BroTvColors.TextPrimary,
    background = BroTvColors.BackgroundNight,
    onBackground = BroTvColors.TextPrimary,
    surface = BroTvColors.SurfaceElevated,
    onSurface = BroTvColors.TextPrimary,
    error = BroTvColors.Danger,
    onError = BroTvColors.TextPrimary,
)

@Composable
fun BroTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BroTvDarkColorScheme,
        typography = BroTvTypography,
        content = content,
    )
}
