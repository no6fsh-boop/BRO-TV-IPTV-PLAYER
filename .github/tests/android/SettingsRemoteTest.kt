package com.brotv.iptv.qa

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.ui.screens.settings.SettingsScreen
import com.brotv.iptv.ui.theme.BroTvTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Compose focus traversal and real SharedPreferences; no tap coordinates. */
class SettingsRemoteTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = AppPreferences(context)

    @Before fun reset() {
        assertTrue(context.getSharedPreferences("brotv_local_state", Context.MODE_PRIVATE).edit().clear().commit())
    }

    @After fun cleanUp() = reset()

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun focused(title: String) {
        compose.onNodeWithText(title).assertIsDisplayed().assertIsFocused()
    }

    @Test fun remoteReachesSettingsAndClearsSeededHistory() {
        val prefs = preferences
        prefs.saveLiveSelection("1", "demo")
        prefs.saveLiveListPositions(2, 3)
        prefs.saveMovieProgress(1101, 12_000, 100_000)
        prefs.saveEpisodeProgress(2101, 21111, 12_000, 100_000)
        prefs.markEpisodeWatched(21112)
        prefs.toggleFavoriteMovie(1101)
        compose.setContent {
            BroTvTheme {
                SettingsScreen(null, IptvRepository(context), prefs) {}
            }
        }
        // Establish the starting focus once, then traverse exclusively by D-pad.
        compose.onNodeWithText("تغيير اللغة")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.DirectionRight)
        focused("تعطيل الرقابة الأبوية")
        key(Key.DirectionRight)
        focused("الرقابة الأبوية")
        key(Key.DirectionCenter)
        assertTrue(preferences.parentalControlEnabled)
        key(Key.DirectionLeft)
        focused("تعطيل الرقابة الأبوية")
        key(Key.DirectionCenter)
        assertFalse(preferences.parentalControlEnabled)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        focused("إخفاء فئات القنوات المباشرة")
        key(Key.DirectionDown)
        focused("تغيير التخطيط")
        key(Key.DirectionCenter)
        assertTrue(preferences.compactLibraryLayout)
        key(Key.DirectionLeft)
        focused("تنسيق البث المباشر")
        key(Key.DirectionCenter)
        assertEquals(1, preferences.liveLayoutMode)
        key(Key.DirectionLeft)
        focused("تنسيق الوقت")
        key(Key.DirectionCenter)
        assertTrue(preferences.use24HourClock)
        key(Key.DirectionDown)
        focused("مسح تاريخ المسلسلات")
        key(Key.DirectionCenter)
        assertTrue(preferences.seriesResumeIds().isEmpty())
        assertTrue(preferences.watchedEpisodeIds().isEmpty())
        assertNull(preferences.lastEpisodeForSeries(2101))
        assertEquals(0L, preferences.loadPosition("episode:21111"))
        assertEquals(setOf(1101), preferences.movieResumeIds())
        key(Key.DirectionRight)
        focused("مسح تاريخ الأفلام")
        key(Key.DirectionCenter)
        assertTrue(preferences.movieResumeIds().isEmpty())
        assertEquals(0L, preferences.loadPosition("movie:1101"))
        assertEquals("demo", preferences.lastLiveGroupKey())
        key(Key.DirectionRight)
        focused("مسح قنوات السجل")
        key(Key.DirectionCenter)
        assertNull(preferences.lastLiveCategoryId())
        assertNull(preferences.lastLiveGroupKey())
        assertEquals(0, preferences.lastLiveCategoryIndex())
        assertEquals(0, preferences.lastLiveChannelIndex())
        assertEquals(setOf(1101), preferences.favoriteMovieIds())
        // An idle editor commit flushes pending apply writes before disk inspection.
        assertTrue(context.getSharedPreferences("brotv_local_state", Context.MODE_PRIVATE).edit().commit())
        val xml = java.io.File(context.applicationInfo.dataDir, "shared_prefs/brotv_local_state.xml").readText()
        listOf("position:movie:", "position:episode:", "episode_series:", "last_episode:",
            "watched_episodes", "live_last_category", "live_last_group", "live_cat_index", "live_channel_index")
            .forEach { assertFalse("Persisted history remains: $it", xml.contains(it)) }
    }
}
