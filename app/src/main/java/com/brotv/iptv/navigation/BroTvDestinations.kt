package com.brotv.iptv.navigation

object BroTvDestinations {
    const val LOGIN = "login"
    const val HOME = "home"
    const val LIVE_TV = "live_tv"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val CATCH_UP = "catch_up"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val SERIES_DETAILS = "series_details/{seriesId}"
    fun seriesDetails(seriesId: Int) = "series_details/$seriesId"
}
