package com.brotv.iptv.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brotv.iptv.BroTvApplication
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.local.SecureCredentialStore
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.ui.screens.favorites.FavoritesScreen
import com.brotv.iptv.ui.screens.home.HomeScreen
import com.brotv.iptv.ui.screens.library.MediaLibraryScreen
import com.brotv.iptv.ui.screens.library.SeriesDetailsScreen
import com.brotv.iptv.ui.screens.live.LiveTvScreen
import com.brotv.iptv.ui.screens.login.LoginScreen
import com.brotv.iptv.ui.screens.settings.SettingsScreen

@Composable
fun BroTvNavGraph(
    credentialStore: SecureCredentialStore,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val app = context.applicationContext as BroTvApplication
    val preferences = remember { AppPreferences(context.applicationContext) }
    val repository = remember { IptvRepository(context.applicationContext) }
    val startDestination = if (credentialStore.loadActiveProfile() != null) BroTvDestinations.HOME else BroTvDestinations.LOGIN

    fun contentNavigate(target: String) {
        navController.navigate(target) { launchSingleTop = true }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(BroTvDestinations.LOGIN) {
            LoginScreen(
                credentialStore = credentialStore,
                repository = repository,
                onLoginSuccess = {
                    navController.navigate(BroTvDestinations.HOME) {
                        popUpTo(BroTvDestinations.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(BroTvDestinations.HOME) {
            HomeScreen(
                profile = credentialStore.loadActiveProfile(),
                repository = repository,
                preferences = preferences,
                onNavigate = ::contentNavigate,
                onOpenSettings = { contentNavigate(BroTvDestinations.SETTINGS) },
                onOpenLanguage = { contentNavigate(BroTvDestinations.SETTINGS) },
                onChangePlaylist = { contentNavigate(BroTvDestinations.LOGIN) },
                onChannelFormat = { contentNavigate(BroTvDestinations.SETTINGS) },
            )
        }

        composable(BroTvDestinations.LIVE_TV) {
            val profile = credentialStore.loadActiveProfile()
            if (profile != null) LiveTvScreen(profile, repository, preferences, app.player, ::contentNavigate)
            else MissingProfileRedirect(navController)
        }

        composable(BroTvDestinations.CATCH_UP) {
            val profile = credentialStore.loadActiveProfile()
            if (profile != null) LiveTvScreen(profile, repository, preferences, app.player, ::contentNavigate, archiveOnly = true)
            else MissingProfileRedirect(navController)
        }

        composable(BroTvDestinations.MOVIES) {
            val profile = credentialStore.loadActiveProfile()
            if (profile != null) MediaLibraryScreen(
                isSeries = false,
                profile = profile,
                repository = repository,
                preferences = preferences,
                player = app.player,
                onNavigate = ::contentNavigate,
                onOpenSeries = {},
            ) else MissingProfileRedirect(navController)
        }

        composable(BroTvDestinations.SERIES) {
            val profile = credentialStore.loadActiveProfile()
            if (profile != null) MediaLibraryScreen(
                isSeries = true,
                profile = profile,
                repository = repository,
                preferences = preferences,
                player = app.player,
                onNavigate = ::contentNavigate,
                onOpenSeries = { item -> contentNavigate(BroTvDestinations.seriesDetails(item.id)) },
            ) else MissingProfileRedirect(navController)
        }

        composable(BroTvDestinations.FAVORITES) {
            val profile = credentialStore.loadActiveProfile()
            if (profile != null) FavoritesScreen(profile, repository, preferences, app.player, ::contentNavigate)
            else MissingProfileRedirect(navController)
        }

        composable(BroTvDestinations.SETTINGS) {
            SettingsScreen(credentialStore.loadActiveProfile(), repository, preferences, ::contentNavigate)
        }

        composable(
            route = BroTvDestinations.SERIES_DETAILS,
            arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val profile = credentialStore.loadActiveProfile()
            val seriesId = backStackEntry.arguments?.getInt("seriesId") ?: 0
            if (profile != null) SeriesDetailsScreen(seriesId, profile, repository, preferences, app.player) { navController.popBackStack() }
            else MissingProfileRedirect(navController)
        }
    }
}

/**
 * Root cause fix (application bug, contributes to QA failures #10/#11 and to
 * "Stability"): every content destination above used the pattern
 * `if (profile != null) Screen(...)` with **no else branch** - meaning that
 * whenever no active profile could be loaded (session cleared, corrupted
 * EncryptedSharedPreferences, or simply a QA harness that reached this route
 * before completing login), the composable rendered **nothing at all**: a
 * fully blank screen with zero focusable elements and no way for a D-pad
 * user, or an automated QA driver, to do anything except mash BACK.
 *
 * This redirects back to Login instead of rendering a dead end, and shows a
 * one-frame message so the transition isn't a silent flash.
 */
@Composable
private fun MissingProfileRedirect(navController: NavHostController) {
    LaunchedEffect(Unit) {
        navController.navigate(BroTvDestinations.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0E14)), contentAlignment = Alignment.Center) {
        Text("لم يتم العثور على حساب نشط، يتم إعادة التوجيه لتسجيل الدخول...", color = Color.White)
    }
}
