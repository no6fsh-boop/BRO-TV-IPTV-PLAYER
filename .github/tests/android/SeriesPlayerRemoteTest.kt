package com.brotv.iptv.qa

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.brotv.iptv.ui.screens.library.SeriesFullPlayer
import com.brotv.iptv.ui.theme.BroTvTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SeriesPlayerRemoteTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var player: ExoPlayer

    @Before fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(instrumentation.targetContext).build()
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BroTvTheme { SeriesFullPlayer(player, "QA episode", false, true, {}, {}, {}) }
        }
        compose.mainClock.advanceTimeBy(100)
    }

    @After fun teardown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player.release() }
    }

    @Test fun remoteActivityRestartsAutoHideDeadline() {
        compose.mainClock.advanceTimeBy(4_400)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("حجم الفيديو").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5_100)
        compose.onNodeWithText("حجم الفيديو").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("حجم الفيديو").assertIsDisplayed()
    }

    @Test fun focusedControlAndDialogReceiveRemoteOk() {
        compose.onNodeWithText("حجم الفيديو")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("حجم الفيديو").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("16:9").assertIsDisplayed()
        // The player must not intercept the dialog's directional keys or OK.
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("16:9").assertDoesNotExist()
        compose.onNodeWithText("حجم الفيديو").assertIsDisplayed()
    }
}
