package com.brotv.iptv.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.brotv.iptv.R
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.PlaylistProfile
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.navigation.BroTvDestinations
import com.brotv.iptv.ui.theme.BroTvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HomeCard(val route: String?, val icon: String, val label: String, val action: (() -> Unit)? = null)

@Composable
fun HomeScreen(
    profile: PlaylistProfile?,
    repository: IptvRepository,
    preferences: AppPreferences,
    onNavigate: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLanguage: () -> Unit,
    onChangePlaylist: () -> Unit,
    onChannelFormat: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var daysLeft by remember { mutableStateOf<Int?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    LaunchedEffect(profile) { daysLeft = profile?.let { repository.getSubscriptionDaysLeft(it) } }

    // Root cause fix for QA failure #9 ("Home refresh button — button could
    // not be reached"): the card labelled "التحديث" (Refresh, icon "↻")
    // never actually refreshed anything - its action silently navigated to
    // Live TV instead (`onNavigate(BroTvDestinations.LIVE_TV)`), so a QA
    // script driving it toward a refresh outcome could never reach one; the
    // button existed but its target behaviour did not. It now performs a
    // real, in-place, force refresh of the subscription/header info and the
    // Live TV cache (so re-entering Live TV afterwards shows fresh data),
    // shows a loading state on the card, and restores D-pad focus to itself
    // afterwards instead of moving the user away from Home.
    fun refreshHome() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            if (profile != null) {
                daysLeft = runCatching { repository.getSubscriptionDaysLeft(profile) }.getOrNull() ?: daysLeft
                runCatching { repository.getLiveCategories(profile, forceRefresh = true) }
                runCatching { repository.getLiveStreams(profile, forceRefresh = true) }
            }
            now = System.currentTimeMillis()
            refreshing = false
            runCatching { refreshFocusRequester.requestFocus() }
        }
    }

    val timePattern = if (preferences.use24HourClock) "HH:mm" else "hh:mm a"
    val time = remember(now, preferences.use24HourClock) { SimpleDateFormat(timePattern, Locale("ar")).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date(now)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(BroTvColors.BackgroundNight)
            .padding(8.dp)
            .border(2.dp, BroTvColors.Gold, RoundedCornerShape(16.dp))
    ) {
        Image(painterResource(R.drawable.bg_login_night), null, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xB90A0E14)))

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Image(painterResource(R.drawable.brotv_logo_gold), "BRO PLUS TV", Modifier.height(58.dp).widthIn(max = 300.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("عالم من الترفيه", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("بين يديك", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.padding(top = 5.dp).width(34.dp).height(3.dp).background(BroTvColors.Gold))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth().height(86.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfoBlock(Modifier.weight(1f), time, date)
                    DividerBar()
                    InfoBlock(Modifier.weight(1f), "☁  الرياض", "الطقس")
                    DividerBar()
                    InfoBlock(
                        Modifier.weight(1f),
                        "♛  ${daysLeft?.let { "$it يوم" } ?: "—"}",
                        if (daysLeft != null) "باقي على الاشتراك" else "مدة الاشتراك غير متاحة",
                        accent = true,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            val main = listOf(
                HomeCard(BroTvDestinations.FAVORITES, "☆", "المفضلة"),
                HomeCard(BroTvDestinations.LIVE_TV, "▣", "القنوات"),
                HomeCard(BroTvDestinations.MOVIES, "▤", "الأفلام"),
                HomeCard(BroTvDestinations.SERIES, "▥", "المسلسلات"),
                HomeCard(null, "↻", if (refreshing) "جارِ التحديث..." else "التحديث") { refreshHome() },
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    main.forEach { card ->
                        val isRefreshCard = card.route == null
                        HomeMainCard(
                            icon = card.icon,
                            label = card.label,
                            modifier = Modifier.weight(1f).height(182.dp)
                                .let { if (isRefreshCard) it.focusRequester(refreshFocusRequester) else it },
                        ) {
                            card.action?.invoke() ?: card.route?.let(onNavigate)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShortcutCard("☷", "تنسيق القنوات", Modifier.weight(1f), onChannelFormat)
                    ShortcutCard("▷", "تغيير قائمة التشغيل", Modifier.weight(1f), onChangePlaylist)
                    ShortcutCard("⚙", "الإعدادات", Modifier.weight(1f), onOpenSettings)
                    ShortcutCard("◎", "اللغة", Modifier.weight(1f), onOpenLanguage)
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(modifier: Modifier, headline: String, caption: String, accent: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(headline, color = if (accent) BroTvColors.Gold else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(caption, color = if (accent) Color.White else BroTvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable private fun DividerBar() {
    Box(Modifier.width(1.dp).fillMaxHeight(.62f).background(BroTvColors.Divider))
}

@Composable private fun HomeMainCard(icon: String, label: String, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .background(if (focused) Color(0xED171B1F) else Color(0xE5111A24), RoundedCornerShape(14.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) BroTvColors.Gold else Color(0xFF34404D), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, color = Color.White, fontWeight = FontWeight.Light, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

@Composable private fun ShortcutCard(icon: String, label: String, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(66.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .background(Color(0xE5111A24), RoundedCornerShape(11.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) BroTvColors.Gold else Color(0xFF34404D), RoundedCornerShape(11.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Text(icon, color = if (focused) BroTvColors.Gold else Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
