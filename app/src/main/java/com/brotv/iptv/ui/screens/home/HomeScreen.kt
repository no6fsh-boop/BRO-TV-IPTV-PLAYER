package com.brotv.iptv.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brotv.iptv.R
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.PlaylistProfile
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.navigation.BroTvDestinations
import com.brotv.iptv.ui.components.BroTvBrandLogo
import com.brotv.iptv.ui.theme.BroTvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HomeCard(
    val route: String?,
    val icon: String,
    val label: String,
    val action: (() -> Unit)? = null,
)

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
    val favoriteFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    LaunchedEffect(profile) { daysLeft = profile?.let { repository.getSubscriptionDaysLeft(it) } }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { favoriteFocusRequester.requestFocus() }
    }

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
    val time = remember(now, preferences.use24HourClock) {
        SimpleDateFormat(timePattern, Locale.ENGLISH).format(Date(now))
    }
    val date = remember(now) {
        SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date(now))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BroTvColors.BackgroundNight)
            .padding(7.dp)
            .border(2.dp, BroTvColors.Gold, RoundedCornerShape(14.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_login_night),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color(0x86030A11)))

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            HomeHeader()
            Spacer(Modifier.height(6.dp))
            HomeInfoStrip(time = time, date = date, daysLeft = daysLeft)
            Spacer(Modifier.height(12.dp))

            val main = listOf(
                HomeCard(BroTvDestinations.FAVORITES, "☆", "المفضلة"),
                HomeCard(BroTvDestinations.LIVE_TV, "▣", "القنوات"),
                HomeCard(BroTvDestinations.MOVIES, "▤", "الأفلام"),
                HomeCard(BroTvDestinations.SERIES, "▥", "المسلسلات"),
                HomeCard(null, "↻", if (refreshing) "جارِ التحديث..." else "التحديث") { refreshHome() },
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    main.forEachIndexed { index, card ->
                        val focusModifier = when {
                            index == 0 -> Modifier.focusRequester(favoriteFocusRequester)
                            card.route == null -> Modifier.focusRequester(refreshFocusRequester)
                            else -> Modifier
                        }
                        HomeMainCard(
                            icon = card.icon,
                            label = card.label,
                            modifier = Modifier
                                .weight(1f)
                                .height(176.dp)
                                .then(focusModifier),
                        ) {
                            card.action?.invoke() ?: card.route?.let(onNavigate)
                        }
                    }
                }
            }

            Spacer(Modifier.height(13.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
private fun HomeHeader() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().height(112.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(330.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(BroTvColors.Gold, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("2", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 27.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "الرئيسية",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                BroTvBrandLogo(modifier = Modifier.width(220.dp))
            }

            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.padding(top = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "عالم من الترفيه",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "بين يديك",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(38.dp)
                        .height(3.dp)
                        .background(BroTvColors.Gold, RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
private fun HomeInfoStrip(time: String, date: String, daysLeft: Int?) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 300.dp, end = 70.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoBlock(
                modifier = Modifier.weight(1f),
                headline = time,
                caption = date,
            )
            DividerBar()
            WeatherBlock(Modifier.weight(1f))
            DividerBar()
            SubscriptionBlock(Modifier.weight(1f), daysLeft)
        }
    }
}

@Composable
private fun InfoBlock(
    modifier: Modifier,
    headline: String,
    caption: String,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            headline,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            caption,
            color = Color.White.copy(alpha = .90f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeatherBlock(modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("☁", color = Color.White, fontSize = 36.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("الرياض", color = Color.White, fontWeight = FontWeight.Bold)
            Text("—°C", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SubscriptionBlock(modifier: Modifier, daysLeft: Int?) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("♛", color = BroTvColors.Gold, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text("باقي على الاشتراك", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                daysLeft?.let { "$it يوم" } ?: "—",
                color = BroTvColors.Gold,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun DividerBar() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight(.70f)
            .background(Color.White.copy(alpha = .24f)),
    )
}

@Composable
private fun HomeMainCard(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .background(
                if (focused) Color(0xE61A1A14) else Color(0xE20A1621),
                RoundedCornerShape(13.dp),
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) BroTvColors.Gold else BroTvColors.Divider,
                RoundedCornerShape(13.dp),
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            icon,
            color = Color.White,
            fontWeight = FontWeight.Light,
            fontSize = 48.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShortcutCard(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(58.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .background(Color(0xEC0A1621), RoundedCornerShape(10.dp))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) BroTvColors.Gold else BroTvColors.Divider,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            icon,
            color = if (focused) BroTvColors.Gold else Color.White,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
