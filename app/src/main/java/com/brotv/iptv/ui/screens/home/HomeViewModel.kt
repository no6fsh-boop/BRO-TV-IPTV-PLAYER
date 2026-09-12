package com.brotv.iptv.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeHeaderState(
    val time: String,
    val date: String,
    val cityName: String? = null,
    val temperatureCelsius: Int? = null,
    val subscriptionDaysLeft: Int? = null,
)

/**
 * Owns only the fast-changing header info (clock ticks every minute).
 * Weather + subscription-days-left are nullable on purpose: they only render
 * "عندما يمكن الحصول عليها من المزود" (when the provider actually exposes
 * them) — wire the real Xtream/weather calls in `refreshFromProviders()`
 * during the data-layer phase, this must stay non-blocking on Home's first
 * paint (cached data shows instantly, then updates in the background).
 */
class HomeViewModel : ViewModel() {

    var header by mutableStateOf(buildHeader(cityName = null, tempC = null, daysLeft = null))
        private set

    init {
        viewModelScope.launch {
            while (true) {
                header = header.copy(time = currentTime(), date = currentDate())
                delay(30_000)
            }
        }
        // TODO(data phase): refreshFromProviders() — read cached weather/
        // subscription info immediately, then kick a background refresh.
    }

    private fun buildHeader(cityName: String?, tempC: Int?, daysLeft: Int?) = HomeHeaderState(
        time = currentTime(),
        date = currentDate(),
        cityName = cityName,
        temperatureCelsius = tempC,
        subscriptionDaysLeft = daysLeft,
    )

    private fun currentTime(): String =
        SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())

    private fun currentDate(): String =
        SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date())
}
