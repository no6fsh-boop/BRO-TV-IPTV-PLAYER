package com.brotv.iptv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.IptvCategory
import com.brotv.iptv.data.model.PlaylistProfile
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.navigation.BroTvDestinations
import com.brotv.iptv.ui.theme.BroTvColors
import kotlinx.coroutines.launch

private data class SettingCardData(val icon:String,val title:String,val action:()->Unit)
private enum class CategoryKind { LIVE, VOD, SERIES }

@Composable
fun SettingsScreen(profile:PlaylistProfile?,repository:IptvRepository,preferences:AppPreferences,onNavigate:(String)->Unit){
    val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf<String?>(null)}
    var categoryKind by remember{mutableStateOf<CategoryKind?>(null)}
    var categories by remember{mutableStateOf<List<IptvCategory>>(emptyList())}
    var categoryLoading by remember{mutableStateOf(false)}
    var categoryVersion by remember{mutableIntStateOf(0)}
    // Bumped after every preferences write so `cards` (built below) re-reads
    // AppPreferences and reflects the new state (e.g. the parental-control
    // toggle label/icon) even though AppPreferences itself isn't observable.
    var prefsVersion by remember{mutableIntStateOf(0)}

    fun openCategories(kind:CategoryKind){categoryKind=kind;categoryLoading=true;scope.launch{categories=if(profile==null)emptyList() else runCatching{when(kind){CategoryKind.LIVE->repository.getLiveCategories(profile);CategoryKind.VOD->repository.getVodCategories(profile);CategoryKind.SERIES->repository.getSeriesCategories(profile)}}.getOrDefault(emptyList());categoryLoading=false}}

    // Real-bug fix: the settings grid previously always showed BOTH
    // "تعطيل الرقابة الأبوية" (disable) AND "الرقابة الأبوية" (enable) as two
    // permanently-visible cards regardless of the actual current state, which
    // is both a UX defect and, combined with the focus bug below, part of why
    // QA could never land on "the" enable/disable target reliably. This is
    // now a single toggle card whose icon/label reflects the live state.
    val parentalCard = if(preferences.parentalControlEnabled)
        SettingCardData("🔓","تعطيل الرقابة الأبوية"){preferences.parentalControlEnabled=false;message="تم تعطيل الرقابة الأبوية";prefsVersion++}
    else
        SettingCardData("🔒","تفعيل الرقابة الأبوية"){preferences.parentalControlEnabled=true;message="تم تفعيل الرقابة الأبوية";prefsVersion++}

    val cards= key(prefsVersion) { listOf(
        SettingCardData("文","تغيير اللغة"){message="لغة الواجهة الحالية: العربية"},
        parentalCard,
        SettingCardData("◉̸","إخفاء فئات المسلسلات"){openCategories(CategoryKind.SERIES)},
        SettingCardData("◉̸","إخفاء فئات الفيديو عند الطلب"){openCategories(CategoryKind.VOD)},
        SettingCardData("◉̸","إخفاء فئات القنوات المباشرة"){openCategories(CategoryKind.LIVE)},
        SettingCardData("◷","تنسيق الوقت"){preferences.use24HourClock=!preferences.use24HourClock;message=if(preferences.use24HourClock)"تم اعتماد نظام 24 ساعة" else "تم اعتماد نظام 12 ساعة";prefsVersion++},
        SettingCardData("▣","تنسيق البث المباشر"){preferences.liveLayoutMode=(preferences.liveLayoutMode+1)%3;message="تنسيق البث المباشر: ${preferences.liveLayoutMode+1}";prefsVersion++},
        SettingCardData("▦","تغيير التخطيط"){preferences.compactLibraryLayout=!preferences.compactLibraryLayout;message=if(preferences.compactLibraryLayout)"تم اعتماد التخطيط المضغوط" else "تم اعتماد التخطيط القياسي";prefsVersion++},
        SettingCardData("⌫","مسح تاريخ المسلسلات"){preferences.clearSeriesHistory();message="تم مسح تاريخ المسلسلات"},
        SettingCardData("⌫","مسح تاريخ الأفلام"){preferences.clearMovieHistory();message="تم مسح تاريخ الأفلام"},
        SettingCardData("⌫","مسح قنوات السجل"){preferences.clearLiveHistory();message="تم مسح سجل القنوات"},
    ) }

    // Root cause fix for QA failures #1,#2,#3,#4,#5,#6,#7,#8 (9 of the 12
    // failures - "target button could not be reached" / "option did not
    // appear after scrolling"): plain `androidx.compose.foundation`
    // LazyVerticalGrid does not reliably support D-pad / 2D focus search
    // across rows (this is exactly why Google ships a separate
    // `androidx.tv.foundation` grid for TV apps). Concretely here: (a)
    // nothing ever requested initial focus, so a fresh D-pad press had no
    // guaranteed starting point, and (b) the grid's default 2D focus search
    // does not reliably move focus down into row 2+ or bring an
    // off-screen row into view purely from focus - which is why the first
    // row (language) worked but every card from row 2 onward, including the
    // whole last row (the 3 clear-history cards, which additionally require
    // scrolling to reach), could not be reached with the remote.
    //
    // Fix: give every card a stable FocusRequester and an explicit
    // focusProperties(up/down/left/right) traversal graph derived from its
    // row/column, request initial focus on the first card, and explicitly
    // scroll the grid to bring the newly-focused row into view. This makes
    // D-pad navigation fully deterministic instead of relying on Compose's
    // default (grid-unreliable) focus search.
    val columns = 3
    val focusRequesters = remember(cards.size) { List(cards.size) { FocusRequester() } }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(Unit) { runCatching { focusRequesters.firstOrNull()?.requestFocus() } }

    Box(Modifier.fillMaxSize().background(BroTvColors.BackgroundNight).padding(horizontal=56.dp,vertical=30.dp)){
        Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                SettingCard("↩","رجوع",Modifier.width(150.dp).height(58.dp)){onNavigate(BroTvDestinations.HOME)}
                Text("الإعدادات",color=Color.White,fontWeight=FontWeight.Bold,style=androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(150.dp))
            }
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(columns=GridCells.Fixed(columns),state=gridState,modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                items(cards.size,key={cards[it].title}){index->
                    val card=cards[index]
                    val row=index/columns
                    val col=index%columns
                    val upIndex=(index-columns).takeIf{it>=0}
                    val downIndex=(index+columns).takeIf{it<cards.size}
                    val leftIndex=index.takeIf{col>0}?.minus(1)
                    val rightIndex=(index+1).takeIf{col<columns-1 && index+1<cards.size}
                    SettingCard(
                        icon=card.icon,
                        title=card.title,
                        modifier=Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                            .focusRequester(focusRequesters[index])
                            .focusProperties {
                                upIndex?.let{ up=focusRequesters[it] }
                                downIndex?.let{ down=focusRequesters[it] }
                                leftIndex?.let{ left=focusRequesters[it] }
                                rightIndex?.let{ right=focusRequesters[it] }
                            }
                            .onFocusChanged{ if(it.isFocused) scope.launch { gridState.animateScrollToItem((row-1).coerceAtLeast(0)) } },
                        onClick=card.action,
                    )
                }
            }
            message?.let{Text(it,color=BroTvColors.Gold,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=8.dp))}
        }
    }

    categoryKind?.let{kind->CategoryVisibilityPanel(title=when(kind){CategoryKind.LIVE->"فئات القنوات المباشرة";CategoryKind.VOD->"فئات الفيديو عند الطلب";CategoryKind.SERIES->"فئات المسلسلات"},categories=categories,loading=categoryLoading,hidden=preferences.hiddenCategoryIds(),version=categoryVersion,onToggle={id->preferences.toggleHiddenCategory(id);categoryVersion++},onDismiss={categoryKind=null})}
}

@Composable private fun SettingCard(icon:String,title:String,modifier:Modifier,onClick:()->Unit){
    var focused by remember{mutableStateOf(false)}
    Row(modifier.onFocusChanged{focused=it.isFocused}.clickable(onClick=onClick).background(if(focused)BroTvColors.SurfaceElevatedHigh else BroTvColors.SurfaceElevated,RoundedCornerShape(11.dp)).border(if(focused)3.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(11.dp)).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
        Text(icon,color=if(focused)BroTvColors.Gold else Color.White,style=androidx.compose.material3.MaterialTheme.typography.headlineMedium);Text(title,color=Color.White,fontWeight=if(focused)FontWeight.Bold else FontWeight.Normal,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

@Composable private fun CategoryVisibilityPanel(title:String,categories:List<IptvCategory>,loading:Boolean,hidden:Set<String>,version:Int,onToggle:(String)->Unit,onDismiss:()->Unit){
    Box(Modifier.fillMaxSize().background(Color(0x99000000)),contentAlignment=Alignment.Center){
        Column(Modifier.width(650.dp).heightIn(max=620.dp).background(BroTvColors.SurfaceElevatedHigh,RoundedCornerShape(15.dp)).border(2.dp,BroTvColors.Gold,RoundedCornerShape(15.dp)).padding(18.dp)){
            Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text("اضغط OK لإظهار/إخفاء الفئة",color=BroTvColors.TextSecondary);Spacer(Modifier.height(12.dp))
            if(loading)Text("جاري التحميل...",color=BroTvColors.TextSecondary) else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){items(categories,key={it.id}){cat->CategoryRow(cat,cat.id in hidden){onToggle(cat.id)}}}
            Spacer(Modifier.height(10.dp));SettingCard("↩","رجوع",Modifier.fillMaxWidth().height(54.dp),onDismiss)
        }
    }
}

@Composable private fun CategoryRow(category:IptvCategory,isHidden:Boolean,onClick:()->Unit){var focused by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused}.clickable(onClick=onClick).background(BroTvColors.SurfaceElevated,RoundedCornerShape(8.dp)).border(if(focused)2.dp else 1.dp,if(focused)BroTvColors.Gold else BroTvColors.Divider,RoundedCornerShape(8.dp)).padding(11.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(category.name,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));Text(if(isHidden)"مخفي" else "ظاهر",color=if(isHidden)BroTvColors.TextSecondary else BroTvColors.Gold)}}
