package com.brotv.iptv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// Every style defaults to TextPrimary (white). Screens must never override
// color to black for a focused/selected state — only the container gets the
// gold ring/glow, the label stays white on top of it.
val BroTvTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = BroTvColors.TextPrimary),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BroTvColors.TextPrimary),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = BroTvColors.TextPrimary),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, color = BroTvColors.TextPrimary),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = BroTvColors.TextSecondary),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = BroTvColors.TextPrimary),
)
