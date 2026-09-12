package com.brotv.iptv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.brotv.iptv.R
import com.brotv.iptv.navigation.BroTvDestinations
import com.brotv.iptv.ui.theme.BroTvColors

@Composable
fun BroTvTopNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFieldFocus = remember { FocusRequester() }

    Row(
        modifier = modifier.fillMaxWidth().background(BroTvColors.BackgroundNight).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.brotv_logo_gold),
            contentDescription = "BROTV+",
            modifier = Modifier.height(38.dp).width(100.dp),
        )
        Spacer(Modifier.width(28.dp))
        TAB_ORDER.forEach { (route, label) ->
            NavTab(label, route == currentRoute) { if (route != currentRoute) onNavigate(route) }
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.weight(1f))
        if (!isSearchOpen) {
            FocusBox(width = 46.dp, onClick = { isSearchOpen = true }) {
                Text("⌕", color = Color.White)
            }
        } else {
            LaunchedEffect(Unit) { searchFieldFocus.requestFocus() }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث...", color = BroTvColors.TextSecondary) },
                singleLine = true,
                modifier = Modifier.width(250.dp).focusRequester(searchFieldFocus),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = BroTvColors.SurfaceElevatedHigh,
                    unfocusedContainerColor = BroTvColors.SurfaceElevatedHigh,
                    focusedIndicatorColor = BroTvColors.Gold,
                    unfocusedIndicatorColor = BroTvColors.Divider,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    onSearchSubmit(searchQuery)
                    isSearchOpen = false
                }),
            )
        }
    }
}

@Composable
private fun NavTab(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .border(if (focused) 2.dp else 0.dp, BroTvColors.Gold, RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color.White)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.height(2.dp).width(46.dp).background(if (active) BroTvColors.Gold else Color.Transparent))
    }
}

@Composable
fun FocusBox(
    width: androidx.compose.ui.unit.Dp? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(BroTvColors.SurfaceElevated, RoundedCornerShape(8.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private val TAB_ORDER = listOf(
    BroTvDestinations.FAVORITES to "المفضلة",
    BroTvDestinations.LIVE_TV to "البث المباشر",
    BroTvDestinations.MOVIES to "الأفلام",
    BroTvDestinations.SERIES to "المسلسلات",
    BroTvDestinations.CATCH_UP to "إعادة البث",
)
