package com.brotv.iptv.ui.screens.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.brotv.iptv.ui.components.BroTvTopNavBar
import com.brotv.iptv.ui.theme.BroTvColors

/**
 * Every content page (favorites, live TV, movies, series, catch-up) shares
 * this shell: the fixed top nav bar + a body area below it. The body is a
 * placeholder here — Live TV gets the 3-column layout, Movies/Series get the
 * sidebar + poster grid, etc. in upcoming phases — but routing between all
 * five pages is fully wired starting now via [onNavigate].
 */
@Composable
fun ContentPageScaffold(
    route: String,
    title: String,
    onNavigate: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(BroTvColors.BackgroundNight)) {
        BroTvTopNavBar(
            currentRoute = route,
            onNavigate = onNavigate,
            onSearchSubmit = onSearchSubmit,
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$title — قيد البناء في المرحلة القادمة", color = BroTvColors.TextSecondary)
        }
    }
}
