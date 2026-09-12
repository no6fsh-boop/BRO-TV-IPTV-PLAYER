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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.*
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.ui.components.BroTvBrandLogo
import com.brotv.iptv.ui.theme.BroTvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailsScreen(seriesId:Int, profile:PlaylistProfile, repository:IptvRepository, preferences:AppPreferences, player:ExoPlayer, onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    var details by remember{mutableStateOf<SeriesDetails?>(null)}
    var season by remember{mutableIntStateOf(1)}
    var order by remember{mutableIntStateOf(0)}
    var playingEpisode by remember{mutableStateOf<SeriesEpisode?>(null)}
    var watchedVersion by remember{mutableIntStateOf(0)}
    var favoriteVersion by remember{mutableIntStateOf(0)}
    var sortMenu by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}

    LaunchedEffect(seriesId){scope.launch{runCatching{val item=repository.getSeries(profile,null).firstOrNull{it.id==seriesId}?:SeriesItem(seriesId,"مسلسل","");repository.getSeriesDetails(profile,item)}.onSuccess{details=it;val last=preferences.lastEpisodeForSeries(seriesId);season=it.episodesBySeason.entries.firstOrNull{(_,eps)->eps.any{ep->ep.id==last}}?.key?:it.episodesBySeason.keys.minOrNull()?:1}.onFailure{error=it.message?:"تعذر تحميل تفاصيل المسلسل"}}}

    val allEpisodes=remember(details){details?.episodesBySeason?.values?.flatten().orEmpty().sortedWith(compareBy<SeriesEpisode>{it.season}.thenBy{it.episodeNum})}
    val currentEpisodes=remember(details,season,order,watchedVersion){val base=details?.episodesBySeason?.get(season).orEmpty().sortedBy{it.episodeNum};if(order==1)base.reversed() else base}

    fun saveCurrent(){playingEpisode?.let{ep->preferences.saveEpisodeProgress(seriesId,ep.id,player.currentPosition,player.duration);watchedVersion++}}
    fun playEpisode(ep:SeriesEpisode){if(playingEpisode!=null&&playingEpisode?.id!=ep.id)saveCurrent();playingEpisode=ep;season=ep.season;player.setMediaItem(MediaItem.fromUri(ep.streamUrl));player.prepare();player.seekTo(preferences.loadPosition("episode:${ep.id}"));player.playWhenReady=true}
    fun adjacent(delta:Int){val cur=playingEpisode?:return;val idx=allEpisodes.indexOfFirst{it.id==cur.id};allEpisodes.getOrNull(idx+delta)?.let(::playEpisode)}

    if(playingEpisode!=null){val ep=playingEpisode!!;SeriesFullPlayer(player,ep.title,hasPrevious=allEpisodes.indexOfFirst{it.id==ep.id}>0,hasNext=allEpisodes.indexOfFirst{it.id==ep.id} in 0 until allEpisodes.lastIndex,onPrevious={adjacent(-1)},onNext={adjacent(1)},onClose={saveCurrent();player.pause();playingEpisode=null});return}

    Box(Modifier.fillMaxSize().background(BroTvColors.BackgroundNight)) {
        details?.item?.backdrop?.let { AsyncImage(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color(0xE20A0E14)))
        Row(Modifier.fillMaxSize().padding(26.dp),horizontalArrangement=Arrangement.spacedBy(28.dp)){
            Column(Modifier.width(310.dp).fillMaxHeight()){
                AsyncImage(details?.item?.poster,details?.item?.name,Modifier.fillMaxWidth().height(405.dp).background(BroTvColors.SurfaceElevated,RoundedCornerShape(12.dp)).border(1.dp,BroTvColors.Divider,RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
                Spacer(Modifier.height(10.dp));Text("المواسم",color=Color.White,fontWeight=FontWeight.Bold)
                LazyColumn(Modifier.heightIn(max=145.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){items(details?.episodesBySeason?.keys?.sorted().orEmpty()){s->SmallButton(if(s==season)"الموسم $s ✓" else "الموسم $s"){season=s}}}
                Spacer(Modifier.height(8.dp));val isFav=seriesId in preferences.favoriteSeriesIds();SmallButton(if(isFav)"♥ في المفضلة" else "♡ إضافة للمفضلة"){preferences.toggleFavoriteSeries(seriesId);favoriteVersion++};Spacer(Modifier.height(7.dp));SmallButton("رجوع",onBack)
            }
            Column(Modifier.weight(1f).fillMaxHeight()){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){Text(details?.item?.name?:"تفاصيل المسلسل",color=Color.White,fontWeight=FontWeight.Bold,style=androidx.compose.material3.MaterialTheme.typography.headlineMedium);Spacer(Modifier.weight(1f));BroTvBrandLogo(compact=true,modifier=Modifier.width(150.dp))}
                Text(listOfNotNull(details?.item?.rating?.let{"IMDb $it"},details?.item?.year,details?.item?.genre).joinToString("  •  "),color=BroTvColors.Gold)
                Text(details?.item?.plot.orEmpty(),color=BroTvColors.TextSecondary,maxLines=4,overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    val lastId=preferences.lastEpisodeForSeries(seriesId);SmallButton(if(lastId!=null)"استكمال المشاهدة" else "بدء المشاهدة"){val target=lastId?.let{id->allEpisodes.firstOrNull{it.id==id}}?:allEpisodes.firstOrNull{it.id !in preferences.watchedEpisodeIds()}?:allEpisodes.firstOrNull();target?.let(::playEpisode)}
                    SmallButton("ترتيب الحلقات ▾"){sortMenu=true}
                }
                if(sortMenu){Row(Modifier.padding(top=7.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){SmallButton("الأقدم أولاً"){order=0;sortMenu=false};SmallButton("الأحدث أولاً"){order=1;sortMenu=false};SmallButton("إلغاء"){sortMenu=false}}}
                Spacer(Modifier.height(10.dp));error?.let{Text(it,color=BroTvColors.TextSecondary)}
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(currentEpisodes,key={it.id}){episode->val pos=preferences.loadPosition("episode:${episode.id}");EpisodeRow(episode,episode.id in preferences.watchedEpisodeIds(),pos){playEpisode(episode)}}}
            }
        }
    }
}

private enum class VideoSizeMode(val label:String){DEFAULT("الحجم الأصلي"),RATIO_16_9("16:9"),RATIO_4_5("4:5"),RATIO_9_16("9:16"),FILL("ملء الشاشة")}

@Composable private fun SeriesFullPlayer(player:ExoPlayer,title:String,hasPrevious:Boolean,hasNext:Boolean,onPrevious:()->Unit,onNext:()->Unit,onClose:()->Unit){
    var overlay by remember{mutableStateOf(true)};var subtitleMenu by remember{mutableStateOf(false)};var sizeMenu by remember{mutableStateOf(false)};var sizeMode by remember{mutableStateOf(VideoSizeMode.DEFAULT)};var playing by remember{mutableStateOf(player.isPlaying)}
    val playerFocusRequester = remember { FocusRequester() }
    val subtitleLanguages=remember(player.currentMediaItem,player.currentTracks){player.currentTracks.groups.filter{it.type==C.TRACK_TYPE_TEXT}.flatMap{g->(0 until g.length).mapNotNull{i->g.getTrackFormat(i).language}}.distinct()}
    DisposableEffect(player){val l=object:Player.Listener{override fun onIsPlayingChanged(v:Boolean){playing=v}};player.addListener(l);onDispose{player.removeListener(l)}}
    LaunchedEffect(overlay,subtitleMenu,sizeMenu){if(!subtitleMenu&&!sizeMenu){delay(60);runCatching{playerFocusRequester.requestFocus()}};if(overlay&&!subtitleMenu&&!sizeMenu){delay(5000);overlay=false}}
    BackHandler{when{subtitleMenu->{subtitleMenu=false;overlay=true};sizeMenu->{sizeMenu=false;overlay=true};overlay->overlay=false;else->onClose()}}
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(playerFocusRequester).focusable().onPreviewKeyEvent{e->if(e.nativeKeyEvent.action!=KeyEvent.ACTION_DOWN)false else when(e.nativeKeyEvent.keyCode){KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_UP->{overlay=true;true};else->false}}){
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
            val surfaceModifier=when(sizeMode){VideoSizeMode.RATIO_16_9->Modifier.fillMaxWidth().aspectRatio(16f/9f);VideoSizeMode.RATIO_4_5->Modifier.fillMaxHeight().aspectRatio(4f/5f);VideoSizeMode.RATIO_9_16->Modifier.fillMaxHeight().aspectRatio(9f/16f);else->Modifier.fillMaxSize()}
            AndroidView(factory={ctx->PlayerView(ctx).apply{this.player=player;useController=false;isFocusable=false;layoutParams=ViewGroup.LayoutParams(-1,-1)}},update={v->v.player=player;v.resizeMode=when(sizeMode){VideoSizeMode.FILL->AspectRatioFrameLayout.RESIZE_MODE_FILL;else->AspectRatioFrameLayout.RESIZE_MODE_FIT}},modifier=surfaceModifier)
        }
        if(overlay)Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xDD0A0E14)).padding(18.dp)){Text(title,color=Color.White,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(10.dp));CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp,Alignment.CenterHorizontally)){
            PlayerControl("الحلقة التالية",hasNext,onNext);PlayerControl("الترجمة",subtitleLanguages.isNotEmpty()){subtitleMenu=true};PlayerControl("↻15",true){player.seekTo((player.currentPosition+15000).coerceAtMost(player.duration.takeIf{it>0}?:Long.MAX_VALUE))};PlayerControl(if(playing)"Ⅱ" else "▶",true){if(player.isPlaying)player.pause() else player.play()};PlayerControl("15↺",true){player.seekTo((player.currentPosition-15000).coerceAtLeast(0))};PlayerControl("حجم الفيديو",true){sizeMenu=true};PlayerControl("الحلقة السابقة",hasPrevious,onPrevious)
        }}}
        if(subtitleMenu)ChoicePanel("الترجمة",listOf("إيقاف الترجمة")+subtitleLanguages,{choice->val b=player.trackSelectionParameters.buildUpon();if(choice=="إيقاف الترجمة")b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true) else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setPreferredTextLanguage(choice);player.trackSelectionParameters=b.build();subtitleMenu=false;overlay=true},{subtitleMenu=false;overlay=true})
        if(sizeMenu)ChoicePanel("حجم الفيديو",VideoSizeMode.entries.map{it.label},{choice->sizeMode=VideoSizeMode.entries.first{it.label==choice};sizeMenu=false;overlay=true},{sizeMenu=false;overlay=true})
    }
}

@Composable private fun ChoicePanel(title:String,options:List<String>,onSelect:(String)->Unit,onDismiss:()->Unit){
    if(options.isEmpty()) return
    val requester=remember{FocusRequester()}
    var focusedIndex by remember(options){mutableIntStateOf(0)}
    LaunchedEffect(options){focusedIndex=focusedIndex.coerceIn(0,options.lastIndex);delay(40);runCatching{requester.requestFocus()}}
    Box(
        Modifier.fillMaxSize().background(Color(0x77000000)).focusRequester(requester).focusable().onPreviewKeyEvent{e->
            if(e.nativeKeyEvent.action!=KeyEvent.ACTION_DOWN)return@onPreviewKeyEvent true
            when(e.nativeKeyEvent.keyCode){
                KeyEvent.KEYCODE_DPAD_UP->{focusedIndex=(focusedIndex-1).coerceAtLeast(0);true}
                KeyEvent.KEYCODE_DPAD_DOWN->{focusedIndex=(focusedIndex+1).coerceAtMost(options.lastIndex);true}
                KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT->true
                KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER->{onSelect(options[focusedIndex]);true}
                KeyEvent.KEYCODE_BACK->{onDismiss();true}
                else->true
            }
        },contentAlignment=Alignment.Center
    ){
        Column(Modifier.width(390.dp).background(BroTvColors.SurfaceElevatedHigh,RoundedCornerShape(13.dp)).border(2.dp,BroTvColors.Gold,RoundedCornerShape(13.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Text(title,color=Color.White,fontWeight=FontWeight.Bold)
            options.forEachIndexed{i,o->ModalChoiceRow(o,i==focusedIndex){onSelect(o)}}
            Text("↑ ↓ للتنقل   •   OK للاختيار   •   رجوع للإلغاء",color=BroTvColors.TextSecondary)
        }
    }
}
@Composable private fun ModalChoiceRow(text:String,focused:Boolean,onClick:()->Unit){
    Box(Modifier.fillMaxWidth().clickable(onClick=onClick).background(if(focused)BroTvColors.SurfaceElevated else Color.Transparent,RoundedCornerShape(7.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(7.dp)).padding(horizontal=11.dp,vertical=8.dp)){Text(text,color=Color.White)}
}
@Composable private fun PlayerControl(text:String,enabled:Boolean,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Box(Modifier.onFocusChanged{focused=it.isFocused}.clickable(enabled=enabled,onClick=onClick).background(BroTvColors.SurfaceElevatedHigh,RoundedCornerShape(8.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(8.dp)).padding(horizontal=11.dp,vertical=9.dp)){Text(text,color=if(enabled)Color.White else BroTvColors.TextDisabled)}}
@Composable private fun EpisodeRow(ep:SeriesEpisode,watched:Boolean,positionMs:Long,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused}.clickable(onClick=onClick).background(BroTvColors.SurfaceElevated,RoundedCornerShape(9.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(9.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(ep.image,null,Modifier.size(120.dp,68.dp).background(Color.Black),contentScale=ContentScale.Crop);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("${ep.episodeNum}. ${ep.title}",color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis);val resume=if(positionMs>5000)" • متابعة من ${formatMs(positionMs)}" else "";Text((ep.duration?:"")+resume,color=BroTvColors.TextSecondary);ep.plot?.let{Text(it,color=BroTvColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)}};Text(if(watched)"تمت المشاهدة" else "جديد",color=if(watched)BroTvColors.TextSecondary else BroTvColors.Gold)}
}
@Composable private fun SmallButton(text:String,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Box(Modifier.onFocusChanged{focused=it.isFocused}.clickable(onClick=onClick).background(BroTvColors.SurfaceElevatedHigh,RoundedCornerShape(7.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(7.dp)).padding(horizontal=11.dp,vertical=8.dp)){Text(text,color=Color.White)}}
private fun formatMs(ms:Long):String{val total=ms/1000;val h=total/3600;val m=(total%3600)/60;val s=total%60;return if(h>0)"%d:%02d:%02d".format(h,m,s) else "%02d:%02d".format(m,s)}
