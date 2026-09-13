package com.brotv.iptv.qa

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.model.PlaylistProfile
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.ui.screens.home.HomeScreen
import com.brotv.iptv.ui.theme.BroTvTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeRemoteRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteRefreshShowsLoadingFetchesAllContentAndKeepsFocus() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val preferences = AppPreferences(context).apply { uiLanguage = "ar" }
        val actions = CopyOnWriteArrayList<String>()
        val releaseResponse = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action")
                if (action == null) return MockResponse().setBody("""{"user_info":{"exp_date":"2000000000"}}""")
                actions.add(action)
                if (!releaseResponse.await(15, TimeUnit.SECONDS)) return MockResponse().setResponseCode(504)
                return MockResponse().setBody("[]")
            }
        }
        server.start()
        try {
            var navigations = 0
            compose.setContent {
                BroTvTheme {
                    HomeScreen(
                        PlaylistProfile("refresh-qa", "demo", "demo", server.url("/").toString()),
                        IptvRepository(context), preferences,
                        { navigations++ }, {}, {}, {}, {},
                    )
                }
            }
            compose.onNodeWithText("المفضلة")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            repeat(4) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
            compose.onNodeWithText("التحديث").assertIsDisplayed().assertIsFocused()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("جاري التحديث…").assertIsDisplayed()
            releaseResponse.countDown()
            val expected = setOf("get_live_categories", "get_live_streams", "get_vod_categories",
                "get_vod_streams", "get_series_categories", "get_series")
            compose.waitUntil(15_000) { actions.toSet().containsAll(expected) }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("جاري التحديث…").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("التحديث").assertIsDisplayed().assertIsFocused()
            assertEquals(expected, actions.toSet())
            assertEquals(6, actions.size)
            assertEquals(0, navigations)
        } finally {
            releaseResponse.countDown()
            server.shutdown()
        }
    }
}
