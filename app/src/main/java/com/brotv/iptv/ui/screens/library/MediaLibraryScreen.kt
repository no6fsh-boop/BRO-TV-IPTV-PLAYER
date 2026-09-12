package com.brotv.iptv.ui.screens.library

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.*
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.navigation.BroTvDestinations
import com.brotv.iptv.ui.components.BroTvTopNavBar
import com.brotv.iptv.ui.theme.BroTvColors
import kotlinx.coroutines.launch

private enum class LibraryMode { CATEGORY, CONTINUE, FAVORITES }

@Composable
fun MediaLibraryScreen(
    isSeries: Boolean,
    profile: PlaylistProfile,
    repository: IptvRepository,
    preferences: AppPreferences,
    player: ExoPlayer,
    onNavigate: (String) -> Unit,
    onOpenSeries: (SeriesItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<IptvCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<IptvCategory?>(null) }
    var movies by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<SeriesItem>>(emptyList()) }
    var selectedMovie by remember { mutableStateOf<VodItem?>(null) }
    var selectedSeries by remember { mutableStateOf<SeriesItem?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(MediaSort.DEFAULT) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreenMovie by remember { mutableStateOf(false) }
    var favoriteVersion by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(LibraryMode.CATEGORY) }

    fun setResults(movieItems: List<VodItem> = emptyList(), seriesItems: List<SeriesItem> = emptyList()) {
        movies = movieItems; series = seriesItems
        selectedMovie = movieItems.firstOrNull(); selectedSeries = seriesItems.firstOrNull()
    }

    fun loadCategory(category: IptvCategory) {
        mode = LibraryMode.CATEGORY; selectedCategory = category; loading = true; error = null
        scope.launch {
            runCatching {
                if (isSeries) setResults(seriesItems = repository.getSeries(profile, category.id))
                else setResults(movieItems = repository.getVodStreams(profile, category.id))
            }.onFailure { error = it.message ?: "تعذر تحميل المحتوى" }
            loading = false
        }
    }

    fun loadSpecial(target: LibraryMode) {
        mode = target; selectedCategory = null; loading = true; error = null
        scope.launch {
            runCatching {
                if (isSeries) {
                    val all = repository.getSeries(profile, null)
                    val ids = if (target == LibraryMode.FAVORITES) preferences.favoriteSeriesIds() else preferences.seriesResumeIds()
                    setResults(seriesItems = all.filter { it.id in ids })
                } else {
                    val all = repository.getVodStreams(profile, null)
                    val ids = if (target == LibraryMode.FAVORITES) preferences.favoriteMovieIds() else preferences.movieResumeIds()
                    setResults(movieItems = all.filter { it.id in ids })
                }
            }.onFailure { error = it.message ?: "تعذر تحميل المحتوى" }
            loading = false
        }
    }

    LaunchedEffect(isSeries) {
        loading = true; error = null
        runCatching { if (isSeries) repository.getSeriesCategories(profile) else repository.getVodCategories(profile) }
            .onSuccess { list ->
                categories = preferences.sortCategories(if (isSeries) "series" else "movies", list.filterNot { it.id in preferences.hiddenCategoryIds() })
                categories.firstOrNull()?.let(::loadCategory) ?: run { loading = false }
            }.onFailure { error = it.message ?: "تعذر الاتصال بالمزود"; loading = false }
    }

    val shownMovies = remember(movies, query, sort, favoriteVersion) { sortMovies(movies.filter { query.isBlank() || it.name.contains(query, true) }, sort) }
    val shownSeries = remember(series, query, sort, favoriteVersion) { sortSeries(series.filter { query.isBlank() || it.name.contains(query, true) }, sort) }

    if (fullscreenMovie && selectedMovie != null) {
    val movie = selectedMovie!!
    FullPlayer(
        player = player,
        title = movie.name,
        seekSeconds = preferences.seekSeconds,
        onFavorite = { preferences.toggleFavoriteMovie(movie.id); favoriteVersion++ },
        onClose = {
            preferences.saveMovieProgress(movie.id, player.currentPosition, player.duration)
            player.pause(); fullscreenMovie = false
            if (mode == LibraryMode.CONTINUE) loadSpecial(LibraryMode.CONTINUE)
        },
    )
    return
}

    Column(Modifier.fillMaxSize().background(BroTvColors.BackgroundNight)) {
        BroTvTopNavBar(if (isSeries) BroTvDestinations.SERIES else BroTvDestinations.MOVIES, onNavigate, { query = it })
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(260.dp).fillMaxHeight()) {
                Text(if (isSeries) "المسلسلات" else "الأفلام", color = Color.White, modifier = Modifier.padding(8.dp))
                SpecialRow("استكمال المشاهدة", mode == LibraryMode.CONTINUE) { loadSpecial(LibraryMode.CONTINUE) }
                SpecialRow("المفضلة", mode == LibraryMode.FAVORITES) { loadSpecial(LibraryMode.FAVORITES) }
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(categories, key = { it.id }) { category -> SpecialRow(category.name, mode == LibraryMode.CATEGORY && selectedCategory?.id == category.id) { loadCategory(category) } }
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                val selectedName = if (isSeries) selectedSeries?.name else selectedMovie?.name
                val selectedPlot = if (isSeries) selectedSeries?.plot else selectedMovie?.plot
                val selectedRating = if (isSeries) selectedSeries?.rating else selectedMovie?.rating
                val selectedYear = if (isSeries) selectedSeries?.year else selectedMovie?.year
                Row(Modifier.fillMaxWidth().height(150.dp).background(BroTvColors.SurfaceElevated, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedName ?: "اختر عنصرًا", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(selectedRating?.let { "IMDb $it" }, selectedYear).joinToString("  •  "), color = BroTvColors.Gold)
                        Text(selectedPlot.orEmpty(), color = BroTvColors.TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    SortButton(sort) { sort = nextSort(sort) }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("جاري التحميل...", color = BroTvColors.TextSecondary) }
                    error != null && movies.isEmpty() && series.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = BroTvColors.TextSecondary) }
                    // Defensive empty-state (was previously missing entirely):
                    // an empty-but-successful result rendered a bare
                    // zero-item grid with nothing to focus or read. Now shows
                    // a message instead of a silent blank area.
                    isSeries && shownSeries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد مسلسلات في هذه الفئة حاليًا", color = BroTvColors.TextSecondary) }
                    !isSeries && shownMovies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد أفلام في هذه الفئة حاليًا", color = BroTvColors.TextSecondary) }
                    isSeries -> LazyVerticalGrid(columns = GridCells.Adaptive(if (preferences.compactLibraryLayout) 125.dp else 150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(shownSeries, key = { it.id }) { item ->
                            PosterCard(item.name, item.poster, item.id in preferences.favoriteSeriesIds(), selectedSeries?.id == item.id, preferences.compactLibraryLayout, { selectedSeries = item }) { onOpenSeries(item) }
                        }
                    }
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(if (preferences.compactLibraryLayout) 125.dp else 150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(shownMovies, key = { it.id }) { item ->
                            PosterCard(item.name, item.poster, item.id in preferences.favoriteMovieIds(), selectedMovie?.id == item.id, preferences.compactLibraryLayout, { selectedMovie = item }) {
                                selectedMovie = item
                                player.setMediaItem(MediaItem.fromUri(item.streamUrl)); player.prepare()
                                player.seekTo(preferences.loadPosition("movie:${item.id}")); player.playWhenReady = true
                                fullscreenMovie = true
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun PosterCard(title: String, poster: String?, favorite: Boolean, selected: Boolean, compact: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val cardWidth = if (compact) 125.dp else 150.dp
    val cardHeight = if (compact) 170.dp else 205.dp
    Column(Modifier.width(cardWidth).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(cardHeight).background(BroTvColors.SurfaceElevated, RoundedCornerShape(8.dp))
            .border(if (focused || selected) 3.dp else 1.dp, if (focused || selected) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(8.dp))) {
            AsyncImage(model = poster, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (favorite) Text("♥", color = BroTvColors.Gold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable private fun SpecialRow(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .background(if (selected) BroTvColors.SurfaceElevatedHigh else BroTvColors.SurfaceElevated, RoundedCornerShape(7.dp))
        .border(if (focused || selected) 2.dp else 1.dp, if (focused || selected) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(7.dp)).padding(10.dp)) {
        Text(text, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun SortButton(sort: MediaSort, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .border(if (focused) 2.dp else 1.dp, if (focused) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(7.dp)).padding(10.dp)) {
        Text("الترتيب: ${sortLabel(sort)}", color = Color.White)
    }
}

@Composable
fun FullPlayer(player: ExoPlayer, title: String, seekSeconds: Int, onFavorite: (() -> Unit)? = null, onClose: () -> Unit) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    val playerFocusRequester = remember { FocusRequester() }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener { override fun onIsPlayingChanged(value: Boolean) { playing = value } }
        player.addListener(listener); onDispose { player.removeListener(listener) }
    }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
    repeat(3) { attempt ->
        kotlinx.coroutines.delay(if (attempt == 0) 60 else 140)
        runCatching { playerFocusRequester.requestFocus() }
    }
}
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(playerFocusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { player.seekTo((player.currentPosition - seekSeconds * 1000L).coerceAtLeast(0)); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { player.seekTo((player.currentPosition + seekSeconds * 1000L).coerceAtMost(player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.isPlaying) player.pause() else player.play(); true }
                    else -> false
                }
            },
    ) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false; isFocusable = false; layoutParams = ViewGroup.LayoutParams(-1, -1) } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xBB0A0E14)).padding(18.dp)) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PlayerButton("−${seekSeconds}s") { player.seekTo((player.currentPosition - seekSeconds * 1000L).coerceAtLeast(0)) }
                Spacer(Modifier.width(10.dp)); PlayerButton(if (playing) "إيقاف مؤقت" else "تشغيل") { if (player.isPlaying) player.pause() else player.play() }
                Spacer(Modifier.width(10.dp)); PlayerButton("+${seekSeconds}s") { player.seekTo((player.currentPosition + seekSeconds * 1000L).coerceAtMost(player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)) }
                onFavorite?.let { Spacer(Modifier.width(10.dp)); PlayerButton("♥", it) }
                Spacer(Modifier.width(10.dp)); PlayerButton("إغلاق", onClose)
            }
        }
    }
}

@Composable private fun PlayerButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).background(BroTvColors.SurfaceElevatedHigh, RoundedCornerShape(7.dp))
        .border(if (focused) 2.dp else 1.dp, if (focused) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(7.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) { Text(text, color = Color.White) }
}

private fun nextSort(current: MediaSort): MediaSort = when (current) { MediaSort.DEFAULT -> MediaSort.NEWEST; MediaSort.NEWEST -> MediaSort.OLDEST; MediaSort.OLDEST -> MediaSort.AZ; MediaSort.AZ -> MediaSort.ZA; MediaSort.ZA -> MediaSort.DEFAULT }
private fun sortLabel(sort: MediaSort) = when (sort) { MediaSort.DEFAULT -> "افتراضي"; MediaSort.NEWEST -> "الأحدث"; MediaSort.OLDEST -> "الأقدم"; MediaSort.AZ -> "A-Z"; MediaSort.ZA -> "Z-A" }
private fun sortMovies(items: List<VodItem>, sort: MediaSort) = when (sort) { MediaSort.DEFAULT -> items; MediaSort.NEWEST -> items.sortedByDescending { it.addedEpoch }; MediaSort.OLDEST -> items.sortedBy { it.addedEpoch }; MediaSort.AZ -> items.sortedBy { it.name.lowercase() }; MediaSort.ZA -> items.sortedByDescending { it.name.lowercase() } }
private fun sortSeries(items: List<SeriesItem>, sort: MediaSort) = when (sort) { MediaSort.DEFAULT -> items; MediaSort.NEWEST -> items.sortedByDescending { it.addedEpoch }; MediaSort.OLDEST -> items.sortedBy { it.addedEpoch }; MediaSort.AZ -> items.sortedBy { it.name.lowercase() }; MediaSort.ZA -> items.sortedByDescending { it.name.lowercase() } }
