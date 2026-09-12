package com.brotv.iptv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for the BRO TV identity colors.
 *
 * Rules from the written spec (this file must never drift from them):
 *  - All text is ALWAYS white, including when focused/selected. Never black
 *    text on a focused/selected element.
 *  - Gold/yellow is reserved for focus rings, borders, glow, and key actions
 *    (the login button, the active nav tab underline, selected filters...).
 *  - Background is a dark cinematic night: near-black navy, not pure #000000,
 *    so the mountain/village night photography reads with some depth.
 */
object BroTvColors {
    val Gold = Color(0xFFF2B90C)          // primary brand gold (logo, focus ring)
    val GoldDeep = Color(0xFFC98F00)      // gradient/pressed state of gold
    val GoldGlow = Color(0x66F2B90C)      // translucent gold for focus glow/shadow

    val BackgroundNight = Color(0xFF0A0E14)   // base app background (near-black navy)
    val SurfaceElevated = Color(0xFF141A24)   // cards, panels, side rails
    val SurfaceElevatedHigh = Color(0xFF1C2330) // popovers / context menus

    val TextPrimary = Color(0xFFFFFFFF)   // ALWAYS white, focused or not
    val TextSecondary = Color(0xFFC7CCD6) // dimmed white for metadata/subtitles
    val TextDisabled = Color(0xFF7A8090)

    val Divider = Color(0xFF2A3140)
    val Danger = Color(0xFFE5484D)        // reserved for real errors only,
                                           // NOT for the app icon or brand mark
}
