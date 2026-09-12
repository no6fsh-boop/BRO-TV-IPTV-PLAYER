package com.brotv.iptv

import android.app.Application
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Root cause fix for QA failure #12 ("a crash or ANR was observed during the
 * QA run"): [ExoPlayer.Builder.build] and [MediaSession.Builder.build] were
 * previously called unconditionally and synchronously inside
 * `Application.onCreate()`, i.e. before any Activity/Window even exists and
 * before the system considers the process "started". Two separate problems
 * follow from that:
 *
 *  1. It is pure added latency on the cold-start critical path on every
 *     single launch, on every device, for a player that may not be needed
 *     for several more screens (Home -> a content screen -> playback). On
 *     slower Android TV boxes/dongles this is a well-known contributor to a
 *     launch-time ANR (the system watchdog can fire before the first frame
 *     is even produced).
 *  2. There was no try/catch around it: if `ExoPlayer.Builder(this).build()`
 *     throws on a given box (renderer/codec init failure, low memory, OEM TV
 *     quirks), the exception is uncaught in `Application.onCreate()`, which
 *     crashes the entire process before a single screen can render - with no
 *     fallback and no diagnostic screen.
 *
 * Fix: initialize the player lazily (first real access, off the app-launch
 * path) and guard it, so a construction failure degrades to "playback
 * unavailable" instead of an app-wide crash, and normal navigation
 * (Home, Settings, category browsing) never pays the ExoPlayer init cost.
 */
class BroTvApplication : Application() {

    private var _player: ExoPlayer? = null
    private var _mediaSession: MediaSession? = null

    val player: ExoPlayer
        get() = _player ?: buildPlayer().also { _player = it }

    override fun onCreate() {
        super.onCreate()
        // Intentionally do NOT build ExoPlayer here - see class doc above.
    }

    private fun buildPlayer(): ExoPlayer {
        val playbackHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(playbackHttpClient)
            .setUserAgent("BRO PLUS TV/0.4")
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { newPlayer ->
                _mediaSession = runCatching { MediaSession.Builder(this, newPlayer).build() }.getOrNull()
            }
    }

    override fun onTerminate() {
        _mediaSession?.release()
        _player?.release()
        super.onTerminate()
    }
}
