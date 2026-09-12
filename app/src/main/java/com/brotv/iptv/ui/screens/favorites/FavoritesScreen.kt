package com.brotv.iptv.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.LiveChannel
import com.brotv.iptv.data.model.PlaylistProfile
import com.brotv.iptv.data.model.SeriesItem
import com.brotv.iptv.data.model.VodItem
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.navigation.BroTvDestinations
import com.brotv.iptv.ui.components.BroTvTopNavBar
import com.brotv.iptv.ui.screens.library.FullPlayer
import com.brotv.iptv.ui.theme.BroTvColors

private sealed class FavoriteItem {
    abstract val id: Int; abstract val name: String
    data class Live(val value: LiveChannel) : FavoriteItem() { override val id = value.id; override val name = value.name }
    data class Movie(val value: VodItem) : FavoriteItem() { override val id = value.id; override val name = value.name }
    data class Series(val value: SeriesItem) : FavoriteItem() { override val id = value.id; override val name = value.name }
}

@Composable
fun FavoritesScreen(
    profile: PlaylistProfile,
    repository: IptvRepository,
    preferences: AppPreferences,
    player: ExoPlayer,
    onNavigate: (String) -> Unit,
) {
    var rows by remember { mutableStateOf<List<FavoriteItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf<FavoriteItem?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(version) {
        loading = true
        val liveIds = preferences.favoriteLiveIds(); val movieIds = preferences.favoriteMovieIds(); val seriesIds = preferences.favoriteSeriesIds()
        val live = runCatching { repository.getLiveStreams(profile, null).filter { it.id in liveIds }.map { FavoriteItem.Live(it) } }.getOrDefault(emptyList())
        val movies = runCatching { repository.getVodStreams(profile, null).filter { it.id in movieIds }.map { FavoriteItem.Movie(it) } }.getOrDefault(emptyList())
        val series = runCatching { repository.getSeries(profile, null).filter { it.id in seriesIds }.map { FavoriteItem.Series(it) } }.getOrDefault(emptyList())
        rows = live + movies + series; loading = false
    }

    val shown = remember(rows, query) { rows.filter { query.isBlank() || it.name.contains(query, true) } }

    Column(Modifier.fillMaxSize().background(BroTvColors.BackgroundNight)) {
        BroTvTopNavBar(BroTvDestinations.FAVORITES, onNavigate, { query = it })
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            Text("المفضلة", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp))
            if (loading) Text("جاري التحميل...", color = BroTvColors.TextSecondary)
            else if (shown.isEmpty()) Text("لا توجد عناصر في المفضلة", color = BroTvColors.TextSecondary)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it::class.simpleName + it.id }) { item ->
                    FavoriteRow(typeLabel(item), item.name, onClick = {
                        when (item) {
                            is FavoriteItem.Live -> { player.setMediaItem(MediaItem.fromUri(item.value.streamUrl)); player.prepare(); player.playWhenReady = true; playing = item }
                            is FavoriteItem.Movie -> { player.setMediaItem(MediaItem.fromUri(item.value.streamUrl)); player.prepare(); player.seekTo(preferences.loadPosition("movie:${item.id}")); player.playWhenReady = true; playing = item }
                            is FavoriteItem.Series -> onNavigate(BroTvDestinations.seriesDetails(item.id))
                        }
                    }, onRemove = {
                        when (item) { is FavoriteItem.Live -> preferences.toggleFavoriteLive(item.id); is FavoriteItem.Movie -> preferences.toggleFavoriteMovie(item.id); is FavoriteItem.Series -> preferences.toggleFavoriteSeries(item.id) }
                        version++
                    })
                }
            }
        }
    }

    playing?.let { item ->
        FullPlayer(player, item.name, preferences.seekSeconds, onFavorite = {
            when (item) { is FavoriteItem.Live -> preferences.toggleFavoriteLive(item.id); is FavoriteItem.Movie -> preferences.toggleFavoriteMovie(item.id); is FavoriteItem.Series -> Unit }
            version++
        }) {
            if (item is FavoriteItem.Movie) preferences.saveMovieProgress(item.id, player.currentPosition, player.duration)
            player.pause(); playing = null
        }
    }
}

@Composable
private fun FavoriteRow(type: String, name: String, onClick: () -> Unit, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .background(BroTvColors.SurfaceElevated, RoundedCornerShape(8.dp)).border(if (focused) 2.dp else 1.dp, if (focused) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(name, color = Color.White); Text(type, color = BroTvColors.TextSecondary) }
        Box(Modifier.clickable(onClick = onRemove).border(1.dp, BroTvColors.Divider, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) { Text("إزالة", color = BroTvColors.Gold) }
    }
}

private fun typeLabel(item: FavoriteItem) = when (item) { is FavoriteItem.Live -> "بث مباشر"; is FavoriteItem.Movie -> "فيلم"; is FavoriteItem.Series -> "مسلسل" }
