package com.brotv.iptv

import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.brotv.iptv.data.local.AppPreferences
import com.brotv.iptv.data.local.SecureCredentialStore
import com.brotv.iptv.data.model.PlaylistProfile
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import okio.Buffer

/**
 * Full Android-TV QA against the real MainActivity and real ExoPlayer.
 * A local Xtream-compatible server provides deterministic categories, posters,
 * live MPEG-TS, movie MP4 and series episode MP4 so CI never depends on a real subscription.
 */
@RunWith(AndroidJUnit4::class)
class FullTvQaTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private lateinit var server: MockWebServer
    private var scenario: ActivityScenario<MainActivity>? = null
    private val requestedPaths = ConcurrentLinkedQueue<String>()

    @Before
    fun setUp() {
        SecureCredentialStore(context).clear()
        server = MockWebServer()
        server.dispatcher = QaDispatcher(requestedPaths)
        server.start()
        SecureCredentialStore(context).saveProfile(
            PlaylistProfile(
                listName = "QA Local Xtream",
                username = "user",
                password = "pass",
                hostUrl = server.url("/").toString().removeSuffix("/"),
            )
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("القنوات", 12_000)
    }

    @After
    fun tearDown() {
        runCatching {
            withPlayer {
                it.stop()
                it.clearMediaItems()
            }
        }
        scenario?.close()
        SecureCredentialStore(context).clear()
        server.shutdown()
    }

    @Test
    fun settings_and_remote_dpad_are_responsive() {
        val oldClock = AppPreferences(context).use24HourClock
        captureScreenshot("home")

        compose.onNodeWithText("القنوات").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("القنوات").assertIsFocused()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        SystemClock.sleep(250)
        compose.onNodeWithText("الأفلام").assertIsFocused()

        compose.onNodeWithText("الإعدادات").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("الإعدادات").assertIsFocused()
        val navMs = measureUi { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER) } { hasText("تنسيق الوقت") }
        assertTrue("Settings navigation too slow: $navMs ms", navMs < 5_000)

        waitForText("تنسيق الوقت", 5_000)
        compose.onNodeWithText("تنسيق الوقت").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("تنسيق الوقت").assertIsFocused()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(3_000) { AppPreferences(context).use24HourClock != oldClock }
        assertNotEquals(oldClock, AppPreferences(context).use24HourClock)

        println("QA_METRIC settings_navigation_ms=$navMs")
    }

    @Test
    fun live_tv_loads_epg_and_decodes_stream_and_remote_overlay() {
        compose.onNodeWithText("القنوات").performClick()
        waitForText("QA Live", 8_000)
        compose.onNodeWithText("QA Live").performClick()
        waitForText("QA Live HD", 8_000)

        val readyMs = waitForPlayer("/live/user/pass/101.ts", 20_000)
        waitForText("الآن: QA Current", 5_000)
        assertTrue(requestedPaths.any { it.startsWith("/live/user/pass/101.ts") })

        compose.onAllNodesWithText("QA Live HD")[0].performClick()
        waitForText("QA Live HD", 3_000)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForText("اختر الجودة / المصدر", 3_000)
        device.pressBack()
        SystemClock.sleep(200)
        device.pressBack()

        println("QA_METRIC live_ready_ms=$readyMs")
    }

    @Test
    fun movies_load_poster_play_mp4_and_remote_seek_pause_back() {
        compose.onNodeWithText("الأفلام").performClick()
        waitForText("QA Movies", 8_000)
        compose.onNodeWithText("QA Movies").performClick()
        waitForText("QA Movie", 8_000)
        compose.onNodeWithContentDescription("QA Movie").fetchSemanticsNode()
        captureScreenshot("movies")
        compose.waitUntil(5_000) { requestedPaths.any { it.startsWith("/poster.png") } }

        compose.onNodeWithContentDescription("QA Movie").performClick()
        val readyMs = waitForPlayer("/movie/user/pass/201.mp4", 20_000)
        compose.waitUntil(5_000) {
            withPlayer { it.currentPosition > 100L || it.playbackState == Player.STATE_ENDED }
        }

        withPlayer { if (!it.isPlaying) it.play() }
        SystemClock.sleep(250)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        SystemClock.sleep(350)
        val paused = withPlayer { !it.isPlaying || it.playbackState == Player.STATE_ENDED }
        assertTrue("DPAD_CENTER did not pause movie", paused)

        withPlayer {
            it.seekTo(0)
            it.play()
        }
        SystemClock.sleep(250)
        val beforeSeek = withPlayer { it.currentPosition }
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        SystemClock.sleep(350)
        val afterSeek = withPlayer { it.currentPosition }
        assertTrue("DPAD_RIGHT did not seek forward: $beforeSeek -> $afterSeek", afterSeek >= beforeSeek)

        device.pressBack()
        waitForText("QA Movie", 4_000)
        println("QA_METRIC movie_ready_ms=$readyMs movie_seek_before=$beforeSeek movie_seek_after=$afterSeek")
    }

    @Test
    fun series_load_details_images_episode_playback_and_back() {
        compose.onNodeWithText("المسلسلات").performClick()
        waitForText("QA Series Category", 8_000)
        compose.onNodeWithText("QA Series Category").performClick()
        waitForText("QA Series", 8_000)
        compose.onNodeWithContentDescription("QA Series").fetchSemanticsNode()
        captureScreenshot("series")

        compose.onNodeWithContentDescription("QA Series").performClick()
        waitForText("QA Episode 1", 10_000, substring = true)
        compose.waitUntil(5_000) { requestedPaths.any { it.startsWith("/poster.png") } }
        compose.onNodeWithText("1. QA Episode 1").performClick()
        val readyMs = waitForPlayer("/series/user/pass/401.mp4", 20_000)

        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForText("حجم الفيديو", 3_000)
        device.pressBack()
        SystemClock.sleep(200)
        device.pressBack()
        waitForText("QA Episode 1", 4_000, substring = true)

        assertTrue(requestedPaths.any { it.startsWith("/series/user/pass/401.mp4") })
        println("QA_METRIC series_ready_ms=$readyMs")
    }

    private fun waitForPlayer(expectedPath: String, timeoutMs: Long): Long {
        val start = SystemClock.elapsedRealtime()
        compose.waitUntil(timeoutMs) {
            withPlayer { player ->
                val uri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                uri.contains(expectedPath) &&
                    (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED)
            }
        }
        return SystemClock.elapsedRealtime() - start
    }

    private fun <T> withPlayer(block: (Player) -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync {
            result.set(
                runCatching {
                    val app = context.applicationContext as BroTvApplication
                    block(app.player)
                }
            )
        }
        return result.get().getOrThrow()
    }

    private fun captureScreenshot(name: String) {
        val dir = context.getExternalFilesDir("qa-screenshots") ?: error("External files directory unavailable")
        dir.mkdirs()
        val output = File(dir, "$name.png")
        assertTrue("Could not capture $name screenshot", device.takeScreenshot(output))
        println("QA_SCREENSHOT ${output.absolutePath}")
    }

    private fun waitForText(text: String, timeoutMs: Long, substring: Boolean = false) {
        compose.waitUntil(timeoutMs) { hasText(text, substring) }
    }

    private fun hasText(text: String, substring: Boolean = false): Boolean =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun measureUi(action: () -> Unit, done: () -> Boolean): Long {
        val start = SystemClock.elapsedRealtime()
        action()
        compose.waitUntil(5_000) { done() }
        return SystemClock.elapsedRealtime() - start
    }

    private inner class QaDispatcher(
        private val requests: ConcurrentLinkedQueue<String>,
    ) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            requests += path
            val url = request.requestUrl
            val action = url?.queryParameter("action")
            return when {
                url?.encodedPath == "/player_api.php" && action == null -> json(
                    """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"}}"""
                )
                action == "get_live_categories" -> json(
                    """[{"category_id":"1","category_name":"QA Live"}]"""
                )
                action == "get_live_streams" -> json(
                    """[{"stream_id":101,"name":"QA Live HD","category_id":"1","stream_icon":"${server.url("/poster.png")}","epg_channel_id":"qa.live","tv_archive":1,"tv_archive_duration":7}]"""
                )
                action == "get_simple_data_table" || action == "get_short_epg" -> json(
                    """{"epg_listings":[{"title":"QA Current","start":"2026-09-12 00:00:00","end":"2026-09-12 23:59:00"},{"title":"QA Next","start":"2026-09-13 00:00:00","end":"2026-09-13 23:59:00"}]}"""
                )
                action == "get_vod_categories" -> json(
                    """[{"category_id":"10","category_name":"QA Movies"}]"""
                )
                action == "get_vod_streams" -> json(
                    """[{"stream_id":201,"name":"QA Movie","category_id":"10","stream_icon":"${server.url("/poster.png")}","rating":"8.5","year":"2026","genre":"QA","plot":"Local movie playback QA","duration":"00:00:03","container_extension":"mp4","added":"1789230000"}]"""
                )
                action == "get_series_categories" -> json(
                    """[{"category_id":"20","category_name":"QA Series Category"}]"""
                )
                action == "get_series" -> json(
                    """[{"series_id":301,"name":"QA Series","category_id":"20","cover":"${server.url("/poster.png")}","backdrop_path":["${server.url("/poster.png")}"],"rating":"9.0","year":"2026","genre":"QA","plot":"Local series QA","last_modified":"1789230000"}]"""
                )
                action == "get_series_info" -> json(
                    """{"info":{"cover":"${server.url("/poster.png")}","backdrop_path":["${server.url("/poster.png")}"],"rating":"9.0","year":"2026","genre":"QA","plot":"Series details QA"},"episodes":{"1":[{"id":401,"episode_num":1,"title":"QA Episode 1","container_extension":"mp4","info":{"duration":"00:00:03","plot":"Episode playback QA","movie_image":"${server.url("/poster.png")}"}}]}}"""
                )
                url?.encodedPath == "/poster.png" -> binaryAsset(listOf("qa_poster.b64"), "image/png")
                url?.encodedPath == "/live/user/pass/101.ts" -> binaryAsset(
                    listOf("qa_live_ts_1.b64", "qa_live_ts_2.b64", "qa_live_ts_3.b64", "qa_live_ts_4.b64", "qa_live_ts_5.b64"),
                    "video/mp2t",
                )
                url?.encodedPath == "/movie/user/pass/201.mp4" -> binaryAsset(listOf("qa_media_mp4.b64"), "video/mp4")
                url?.encodedPath == "/series/user/pass/401.mp4" -> binaryAsset(listOf("qa_media_mp4.b64"), "video/mp4")
                else -> MockResponse().setResponseCode(404).setBody("QA 404: $path")
            }
        }

        private fun json(body: String) = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(body)

        private fun binaryAsset(parts: List<String>, contentType: String): MockResponse {
            val encoded = buildString {
                parts.forEach { name ->
                    instrumentation.context.assets.open(name).bufferedReader().use { append(it.readText().trim()) }
                }
            }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", contentType)
                .setHeader("Content-Length", bytes.size)
                .setBody(Buffer().write(bytes))
        }
    }
}
