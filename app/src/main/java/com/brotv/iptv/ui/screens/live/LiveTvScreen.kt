package com.brotv.iptv.ui.screens.live

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.*
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.navigation.BroTvDestinations
import com.brotv.iptv.ui.components.BroTvTopNavBar
import com.brotv.iptv.ui.theme.BroTvColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SmartChannelGroup(val key: String, val displayName: String, val sources: List<LiveChannel>) { val primary get() = sources.first() }

@Composable
fun LiveTvScreen(
    profile: PlaylistProfile,
    repository: IptvRepository,
    preferences: AppPreferences,
    player: ExoPlayer,
    onNavigate: (String) -> Unit,
    archiveOnly: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val playerHandler = remember(player) { Handler(player.applicationLooper) }

    // Media3 requires playback mutations to run on the player application looper.
    fun runOnPlayerThread(block: () -> Unit) {
        if (Looper.myLooper() == player.applicationLooper) block() else playerHandler.post(block)
    }
    val categoryListState = rememberLazyListState(initialFirstVisibleItemIndex = preferences.lastLiveCategoryIndex())
    val channelListState = rememberLazyListState(initialFirstVisibleItemIndex = preferences.lastLiveChannelIndex())
    var categories by remember { mutableStateOf<List<IptvCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<IptvCategory?>(null) }
    var allGroups by remember { mutableStateOf<List<SmartChannelGroup>>(emptyList()) }
    var visibleGroups by remember { mutableStateOf<List<SmartChannelGroup>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<SmartChannelGroup?>(null) }
    var currentSourceIndex by remember { mutableIntStateOf(0) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(false) }
    var sourceMenuVisible by remember { mutableStateOf(false) }
    var previewFocused by remember { mutableStateOf(false) }
    var sourceNotice by remember { mutableStateOf<String?>(null) }
    var failedSourceUrl by remember { mutableStateOf<String?>(null) }
    var recoveryAttempts by remember { mutableIntStateOf(0) }
    val fullscreenFocusRequester = remember { FocusRequester() }
    var favoritesVersion by remember { mutableIntStateOf(0) }
    var hiddenVersion by remember { mutableIntStateOf(0) }
    var rescueJob by remember { mutableStateOf<Job?>(null) }
    var isCatchupPlayback by remember { mutableStateOf(false) }

    fun currentSource(): LiveChannel? = selectedGroup?.sources?.getOrNull(currentSourceIndex)
    fun loadEpg(source: LiveChannel) { scope.launch { epg = repository.getShortEpg(profile, source.id, if (archiveOnly) 40 else 4) } }

    fun playSource(group: SmartChannelGroup, sourceIndex: Int, rescued: Boolean = false) {
        val source = group.sources.getOrNull(sourceIndex.coerceIn(0, group.sources.lastIndex)) ?: return
        selectedGroup = group; currentSourceIndex = group.sources.indexOf(source); loadEpg(source)
        if (failedSourceUrl != source.streamUrl) { failedSourceUrl = source.streamUrl; recoveryAttempts = 0 }
        preferences.saveLiveSelection(selectedCategory?.id, group.key)
        if (archiveOnly) return
        isCatchupPlayback = false
        runOnPlayerThread {
            player.setMediaItem(MediaItem.fromUri(source.streamUrl))
            player.prepare()
            player.playWhenReady = true
        }
        sourceNotice = if (rescued) "تم التبديل تلقائيًا إلى مصدر احتياطي" else null
    }

    fun playGroup(group: SmartChannelGroup) {
        val preferred = preferences.preferredLiveSource(group.key)
        val index = preferred?.let { id -> group.sources.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        playSource(group, index)
    }

    fun tryNextSource() {
        if (archiveOnly || isCatchupPlayback) return
        val group = selectedGroup ?: return
        recoveryAttempts = 0
        if (currentSourceIndex + 1 < group.sources.size) playSource(group, currentSourceIndex + 1, true)
        else sourceNotice = "تعذر الاتصال بالبث بعد عدة محاولات"
    }

    fun recoverPlayback() {
        if (archiveOnly || isCatchupPlayback) return
        val source = currentSource() ?: return
        if (recoveryAttempts < 2) {
            recoveryAttempts += 1
            sourceNotice = "إعادة الاتصال بالبث… (${recoveryAttempts}/2)"
            rescueJob?.cancel()
            rescueJob = scope.launch {
                delay(1_500L * recoveryAttempts)
                if (currentSource()?.streamUrl == source.streamUrl) {
                    runOnPlayerThread {
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
            }
        } else tryNextSource()
    }

    DisposableEffect(player, selectedGroup?.key, currentSourceIndex, archiveOnly, isCatchupPlayback) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = recoverPlayback()
            override fun onPlaybackStateChanged(state: Int) {
                rescueJob?.cancel()
                if (state == Player.STATE_BUFFERING && !archiveOnly && !isCatchupPlayback) rescueJob = scope.launch { delay(15_000); runOnPlayerThread { if (player.playbackState == Player.STATE_BUFFERING) recoverPlayback() } }
                else if (state == Player.STATE_READY) { recoveryAttempts = 0; sourceNotice = null; val g=selectedGroup; val s=currentSource(); if (g!=null&&s!=null&&!isCatchupPlayback) preferences.savePreferredLiveSource(g.key,s.id) }
            }
        }
        runOnPlayerThread { player.addListener(listener) }
        onDispose { runOnPlayerThread { player.removeListener(listener) }; rescueJob?.cancel() }
    }

    fun applyCategory(category: IptvCategory, groups: List<SmartChannelGroup> = allGroups, restoreGroupKey: String? = null) {
        selectedCategory = category
        visibleGroups = groups.filter { g -> g.sources.any { it.categoryId == category.id } }
        val target = restoreGroupKey?.let { key -> visibleGroups.firstOrNull { it.key == key } } ?: visibleGroups.firstOrNull()
        preferences.saveLiveSelection(category.id, target?.key)
        if (target != null) playGroup(target) else selectedGroup = null
    }

    suspend fun loadLive(forceRefresh: Boolean, keepSelection: Boolean = true, replaySelection: Boolean = true) {
        // Cache-first: never block the live screen on re-entry. Only an explicit refresh may show loading.
        if (forceRefresh && visibleGroups.isEmpty()) loading = true
        error = null
        runCatching {
            val hiddenCats = preferences.hiddenCategoryIds()
            val loadedCategories = repository.getLiveCategories(profile, forceRefresh).filter { it.id !in hiddenCats }
            val quality = preferences.selectedQualities(); val hidden = preferences.hiddenLiveIds()
            val streams = repository.getLiveStreams(profile, null, forceRefresh).filter { ch -> (!archiveOnly || ch.tvArchive) && ch.id !in hidden && ch.quality in quality }
            preferences.sortCategories("live", loadedCategories) to smartGroupChannels(streams)
        }.onSuccess { (cats, groups) ->
            categories = cats; allGroups = groups
            val wantedCategory = if (keepSelection) selectedCategory?.id ?: preferences.lastLiveCategoryId() else null
            val category = cats.firstOrNull { it.id == wantedCategory } ?: cats.firstOrNull()
            if (category != null) {
                selectedCategory = category
                visibleGroups = groups.filter { g -> g.sources.any { it.categoryId == category.id } }
                val wantedGroup = if (keepSelection) selectedGroup?.key ?: preferences.lastLiveGroupKey() else null
                val target = wantedGroup?.let { key -> visibleGroups.firstOrNull { it.key == key } } ?: visibleGroups.firstOrNull()
                preferences.saveLiveSelection(category.id, target?.key)
                if (target != null) {
                    if (replaySelection || selectedGroup == null) playGroup(target)
                    else selectedGroup = target
                } else selectedGroup = null
            } else { visibleGroups=groups; selectedGroup=groups.firstOrNull() }
        }.onFailure { error = it.message ?: "تعذر الاتصال بالمزود" }
        loading = false
    }

    LaunchedEffect(archiveOnly, hiddenVersion) {
        // Re-entry is cache-only. Do not hit the provider again and do not restart the current stream.
        loadLive(false, true, replaySelection = selectedGroup == null)
    }
    DisposableEffect(Unit) { onDispose { preferences.saveLiveListPositions(categoryListState.firstVisibleItemIndex, channelListState.firstVisibleItemIndex) } }

    val shownGroups = remember(visibleGroups, query, favoritesVersion, hiddenVersion) { visibleGroups.filter { g -> query.isBlank() || g.displayName.contains(query,true) || g.sources.any { it.name.contains(query,true) } } }
    val categoryWidth = when (preferences.liveLayoutMode) { 1 -> 205.dp; 2 -> 275.dp; else -> 235.dp }
    val channelWidth = when (preferences.liveLayoutMode) { 1 -> 315.dp; 2 -> 390.dp; else -> 350.dp }

    Column(Modifier.fillMaxSize().background(BroTvColors.BackgroundNight)) {
        BroTvTopNavBar(if (archiveOnly) BroTvDestinations.CATCH_UP else BroTvDestinations.LIVE_TV, onNavigate, { query = it })
        when {
            loading && visibleGroups.isEmpty() -> StatusText("جاري تحميل القنوات...")
            error != null && visibleGroups.isEmpty() -> StatusText(error ?: "حدث خطأ")
            // Defensive empty-state: a successful load with zero channels in
            // the current category previously rendered two empty scroll
            // columns with nothing to focus. Now surfaces a clear message.
            categories.isEmpty() -> StatusText("لا توجد فئات قنوات متاحة حاليًا")
            else -> Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(categoryWidth).fillMaxHeight()) {
                    Text(if (archiveOnly) "مجلدات إعادة البث" else "المجلدات", color = Color.White, modifier = Modifier.padding(8.dp))
                    LazyColumn(state = categoryListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(categories, key = { _, it -> it.id }) { index, category ->
                            FolderRow(category, selectedCategory?.id == category.id, index, categoryListState, scope) { applyCategory(category) }
                        }
                    }
                }
                Column(Modifier.width(channelWidth).fillMaxHeight()) {
                    Text("القنوات", color = Color.White, modifier = Modifier.padding(8.dp))
                    LazyColumn(state = channelListState, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        itemsIndexed(shownGroups, key = { _, it -> it.key }) { index, group ->
                            ChannelRow(group, selectedGroup?.key == group.key, group.sources.any { it.id in preferences.favoriteLiveIds() }, index, channelListState, scope) {
                                if (!archiveOnly && selectedGroup?.key == group.key) { fullscreen = true; overlayVisible = true; sourceMenuVisible = false }
                                else playGroup(group)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        Modifier.fillMaxWidth().then(if (archiveOnly) Modifier.height(245.dp) else Modifier.weight(1f))
                            .onFocusChanged { previewFocused = it.isFocused }.focusable()
                            .background(Color.Black, RoundedCornerShape(11.dp))
                            .border(if (previewFocused) 3.dp else 1.dp, if (previewFocused) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(11.dp))
                            .clickable { if (selectedGroup != null && !archiveOnly) { fullscreen = true; overlayVisible = true } },
                    ) {
                        if (!archiveOnly) PlayerSurface(player) else Text("اختر برنامجًا من الأرشيف", color=BroTvColors.TextSecondary, modifier=Modifier.align(Alignment.Center))
                        if (previewFocused) Text("OK لملء الشاشة", color=BroTvColors.Gold, modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    selectedGroup?.let { group ->
                        val ch = currentSource() ?: group.primary
                        Text(group.displayName, color=Color.White, fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text("${qualityLabel(ch.quality)} • ${if(group.sources.size>1) "${group.sources.size} مصادر" else "مصدر واحد"}", color=BroTvColors.TextSecondary)
                        sourceNotice?.let { Text(it, color=BroTvColors.Gold, maxLines=1) }
                        if (archiveOnly) {
                            Spacer(Modifier.height(8.dp)); Text("البرامج المتاحة", color=Color.White)
                            LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(epg, key={_,it->(it.start?:"")+it.title}) { _, entry -> EpgRow(entry) {
                                    scope.launch {
                                        val url = repository.catchupUrl(profile, ch, entry)
                                        if (url == null) sourceNotice = "المزود لم يرسل رابط أرشيف صالح"
                                        else {
                                            isCatchupPlayback = true; fullscreen = true; overlayVisible = true
                                            runOnPlayerThread {
                                                player.setMediaItem(MediaItem.fromUri(url))
                                                player.prepare()
                                                player.playWhenReady = true
                                            }
                                        }
                                    }
                                } }
                            }
                        } else {
                            epg.getOrNull(0)?.let { Text("الآن: ${it.title}", color=Color.White, maxLines=1) }
                            epg.getOrNull(1)?.let { Text("التالي: ${it.title}", color=BroTvColors.TextSecondary, maxLines=1) }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.padding(top=8.dp)) {
                            val fav = group.sources.any { it.id in preferences.favoriteLiveIds() }
                            ActionButton(if(fav) "إزالة المفضلة" else "إضافة للمفضلة") { group.sources.forEach { s -> val has=s.id in preferences.favoriteLiveIds(); if(fav&&has)preferences.toggleFavoriteLive(s.id); if(!fav&&!has)preferences.toggleFavoriteLive(s.id) }; favoritesVersion++ }
                            if(group.sources.size>1) ActionButton("الجودة") { sourceMenuVisible = true }
                            ActionButton("تحديث") { scope.launch { loadLive(true, true) } }
                            ActionButton("إخفاء") { group.sources.forEach { s -> if(s.id !in preferences.hiddenLiveIds()) preferences.toggleHiddenLive(s.id) }; hiddenVersion++ }
                        }
                    }
                }
            }
        }
    }

    if (sourceMenuVisible && !fullscreen) {
        SourceMenu(selectedGroup, currentSourceIndex, onSelect = { idx -> selectedGroup?.let { playSource(it,idx) }; sourceMenuVisible=false }, onDismiss={sourceMenuVisible=false})
    }

    if (fullscreen) {
        LaunchedEffect(fullscreen, overlayVisible, sourceMenuVisible, selectedGroup?.key) {
            if (fullscreen && !sourceMenuVisible) { delay(60); runCatching { fullscreenFocusRequester.requestFocus() } }
            if (overlayVisible && !sourceMenuVisible) { delay(4_500); overlayVisible = false }
        }
        BackHandler {
            when {
                sourceMenuVisible -> { sourceMenuVisible=false; overlayVisible=true }
                overlayVisible -> overlayVisible=false
                else -> { fullscreen=false; if(isCatchupPlayback) player.pause() }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(fullscreenFocusRequester).focusable().onPreviewKeyEvent { event ->
            if (sourceMenuVisible) false
            else if(event.nativeKeyEvent.action!=KeyEvent.ACTION_DOWN) false else when(event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (overlayVisible && selectedGroup?.sources?.isNotEmpty() == true) sourceMenuVisible = true
                    else overlayVisible = true
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { overlayVisible=true; true }
                else -> false
            }
        }) {
            PlayerSurface(player)
            if (overlayVisible) selectedGroup?.let { group ->
                FullscreenInfoOverlay(group, currentSource() ?: group.primary, epg, currentSourceIndex, onQuality={ sourceMenuVisible=true })
            }
            if (sourceMenuVisible) SourceMenu(selectedGroup,currentSourceIndex,{ idx -> selectedGroup?.let { playSource(it,idx) }; sourceMenuVisible=false; overlayVisible=true },{sourceMenuVisible=false; overlayVisible=true})
        }
    }
}

private fun ensureVisible(state: LazyListState, index: Int, scope: kotlinx.coroutines.CoroutineScope) {
    val visible = state.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return
    val first=visible.first().index; val last=visible.last().index
    if(index < first || index > last) scope.launch { state.animateScrollToItem(index) }
}

@Composable private fun BoxScope.FullscreenInfoOverlay(group: SmartChannelGroup, source: LiveChannel, epg: List<EpgEntry>, sourceIndex: Int, onQuality:()->Unit) {
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(26.dp).background(Color(0xE8141A24), RoundedCornerShape(15.dp)).border(1.dp,BroTvColors.Gold.copy(.5f),RoundedCornerShape(15.dp)).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text(group.displayName,color=Color.White,fontWeight=FontWeight.Bold)
                Text("${qualityLabel(source.quality)} • المصدر ${sourceIndex+1}/${group.sources.size}",color=BroTvColors.Gold)
                epg.firstOrNull()?.let { Text("الآن: ${it.title}${it.start?.let { s -> " • $s" }.orEmpty()}",color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis) }
                epg.getOrNull(1)?.let { Text("التالي: ${it.title}",color=BroTvColors.TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis) }
            }
            ActionButton("الجودة", onQuality)
        }
    }
}

@Composable private fun SourceMenu(group: SmartChannelGroup?, selectedIndex: Int, onSelect:(Int)->Unit, onDismiss:()->Unit) {
    val g=group ?: return
    val menuRequester = remember(g.key) { FocusRequester() }
    var focusedIndex by remember(g.key, selectedIndex) { mutableIntStateOf(selectedIndex.coerceIn(0, g.sources.lastIndex.coerceAtLeast(0))) }
    LaunchedEffect(g.key, selectedIndex) {
        focusedIndex = selectedIndex.coerceIn(0, g.sources.lastIndex.coerceAtLeast(0))
        menuRequester.requestFocus()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .focusRequester(menuRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { focusedIndex = (focusedIndex - 1).coerceAtLeast(0); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { focusedIndex = (focusedIndex + 1).coerceAtMost(g.sources.lastIndex); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onSelect(focusedIndex); true }
                    KeyEvent.KEYCODE_BACK -> { onDismiss(); true }
                    else -> true
                }
            },
        contentAlignment=Alignment.Center
    ) {
        Column(Modifier.width(430.dp).background(BroTvColors.SurfaceElevatedHigh,RoundedCornerShape(14.dp)).border(2.dp,BroTvColors.Gold,RoundedCornerShape(14.dp)).padding(18.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("اختر الجودة / المصدر",color=Color.White,fontWeight=FontWeight.Bold)
            g.sources.forEachIndexed { idx, src ->
                SourceRow(
                    text = "${qualityLabel(src.quality)}  •  ${src.name}",
                    selected = idx==selectedIndex,
                    focused = idx==focusedIndex,
                    onClick = { onSelect(idx) }
                )
            }
            Text("↑ ↓ للتنقل   •   OK للاختيار   •   رجوع للإلغاء", color=BroTvColors.TextSecondary)
        }
    }
}

@Composable private fun SourceRow(text:String, selected:Boolean, focused:Boolean, onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).background(if(selected)BroTvColors.SurfaceElevated else Color.Transparent,RoundedCornerShape(8.dp)).border(if(focused||selected)2.dp else 1.dp,if(focused||selected)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(8.dp)).padding(11.dp), horizontalArrangement=Arrangement.SpaceBetween) {
        Text(text,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f)); if(selected) Text("✓",color=BroTvColors.Gold)
    }
}

private fun smartGroupChannels(channels: List<LiveChannel>): List<SmartChannelGroup> = channels.groupBy(::smartIdentityKey).map { (key,sources) -> val sorted=sources.sortedWith(compareByDescending<LiveChannel>{sourceScore(it)}.thenBy{it.name.length}.thenBy{it.id}); SmartChannelGroup(key,cleanDisplayName(sorted.first().name),sorted) }.sortedBy{it.displayName.lowercase()}
private fun smartIdentityKey(channel: LiveChannel): String { val epgId=channel.epgChannelId?.trim()?.lowercase()?.takeIf{it.isNotBlank()&&it!="null"}; if(epgId!=null)return "epg:$epgId"; val n=normalizeChannelName(channel.name); return if(n.isBlank())"id:${channel.id}" else "name:$n" }
private fun normalizeChannelName(value:String)=value.uppercase().replace(Regex("(?i)\\b(?:SERVER|SOURCE|SRC)\\s*[-_:]?\\s*\\d+\\b")," ").replace(Regex("(?i)\\b(?:BACKUP|BKP|ALT)\\s*\\d*\\b")," ").replace(Regex("(?i)\\b(?:4K|UHD|FHD|FULL\\s*HD|HD|SD|2160P?|1080P?|720P?|576P?|480P?|HEVC|H\\.?265|H\\.?264)\\b")," ").replace(Regex("[\\[\\]{}()|/_\\-]+")," ").replace(Regex("\\s+")," ").trim()
private fun cleanDisplayName(value:String)=value.replace(Regex("(?i)\\s*[|/_\\-]?\\s*\\b(?:SERVER|SOURCE|SRC)\\s*[-_:]?\\s*\\d+\\b")," ").replace(Regex("(?i)\\s*[|/_\\-]?\\s*\\b(?:BACKUP|BKP|ALT)\\s*\\d*\\b")," ").replace(Regex("\\s+")," ").trim(' ','-','|','_').ifBlank{value}
private fun sourceScore(c:LiveChannel)=when(c.quality){StreamQuality.Q4K->500;StreamQuality.FHD->400;StreamQuality.HD->300;StreamQuality.UNKNOWN->200;StreamQuality.SD->100}+(if(c.tvArchive)2 else 0)-(if(Regex("(?i)\\b(BACKUP|BKP|ALT)\\b").containsMatchIn(c.name))20 else 0)
private fun qualityLabel(q:StreamQuality)=when(q){StreamQuality.Q4K->"4K";StreamQuality.FHD->"FHD";StreamQuality.HD->"HD";StreamQuality.SD->"SD";StreamQuality.UNKNOWN->"Auto"}

@Composable private fun PlayerSurface(player:ExoPlayer){AndroidView(factory={ctx->PlayerView(ctx).apply{this.player=player;useController=false;isFocusable=false;layoutParams=ViewGroup.LayoutParams(-1,-1)}},update={it.player=player},modifier=Modifier.fillMaxSize())}
@Composable private fun FolderRow(category:IptvCategory,selected:Boolean,index:Int,state:LazyListState,scope:kotlinx.coroutines.CoroutineScope,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused;if(it.isFocused)ensureVisible(state,index,scope)}.clickable(onClick=onClick).background(if(selected)BroTvColors.SurfaceElevatedHigh else BroTvColors.SurfaceElevated,RoundedCornerShape(8.dp)).border(if(focused||selected)2.dp else 1.dp,if(focused||selected)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(8.dp)).padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(category.name,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));category.count?.let{Text(it.toString(),color=BroTvColors.TextSecondary)}}}
@Composable private fun ChannelRow(group:SmartChannelGroup,selected:Boolean,favorite:Boolean,index:Int,state:LazyListState,scope:kotlinx.coroutines.CoroutineScope,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused;if(it.isFocused)ensureVisible(state,index,scope)}.clickable(onClick=onClick).background(if(selected)BroTvColors.SurfaceElevatedHigh else BroTvColors.SurfaceElevated,RoundedCornerShape(7.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(7.dp)).padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){if(favorite)Text("♥ ",color=BroTvColors.Gold);Text(group.displayName,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));if(group.sources.size>1)Text("${group.sources.size}",color=BroTvColors.Gold)}}
@Composable private fun EpgRow(entry:EpgEntry,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused}.clickable(onClick=onClick).background(BroTvColors.SurfaceElevated,RoundedCornerShape(7.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(7.dp)).padding(9.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(entry.title,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));entry.start?.let{Text(it.takeLast(8),color=BroTvColors.TextSecondary)}}}
@Composable private fun ActionButton(text:String,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Box(Modifier.onFocusChanged{focused=it.isFocused}.clickable(onClick=onClick).background(BroTvColors.SurfaceElevatedHigh,RoundedCornerShape(7.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(7.dp)).padding(horizontal=12.dp,vertical=8.dp)){Text(text,color=Color.White)}}
@Composable private fun StatusText(text:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(text,color=BroTvColors.TextSecondary)}}
