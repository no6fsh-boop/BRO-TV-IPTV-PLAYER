from pathlib import Path

path = Path("app/src/main/java/com/brotv/iptv/ui/screens/live/LiveTvScreen.kt")
s = path.read_text(encoding="utf-8")
if "fun runOnPlayerThread(block: () -> Unit)" in s:
    print("LiveTvScreen already patched")
    raise SystemExit(0)

s = s.replace(
    "package com.brotv.iptv.ui.screens.live\n\nimport android.view.KeyEvent",
    "package com.brotv.iptv.ui.screens.live\n\nimport android.os.Handler\nimport android.os.Looper\nimport android.view.KeyEvent",
)
s = s.replace(
    "    val scope = rememberCoroutineScope()\n",
    """    val scope = rememberCoroutineScope()\n    val playerHandler = remember(player) { Handler(player.applicationLooper) }\n\n    // Media3 requires playback mutations to run on the player application looper.\n    fun runOnPlayerThread(block: () -> Unit) {\n        if (Looper.myLooper() == player.applicationLooper) block() else playerHandler.post(block)\n    }\n""",
    1,
)
s = s.replace(
    "        player.setMediaItem(MediaItem.fromUri(source.streamUrl)); player.prepare(); player.playWhenReady = true; isCatchupPlayback = false\n",
    """        isCatchupPlayback = false\n        runOnPlayerThread {\n            player.setMediaItem(MediaItem.fromUri(source.streamUrl))\n            player.prepare()\n            player.playWhenReady = true\n        }\n""",
    1,
)
s = s.replace(
    """                if (currentSource()?.streamUrl == source.streamUrl) {\n                    player.prepare()\n                    player.playWhenReady = true\n                }\n""",
    """                if (currentSource()?.streamUrl == source.streamUrl) {\n                    runOnPlayerThread {\n                        player.prepare()\n                        player.playWhenReady = true\n                    }\n                }\n""",
    1,
)
s = s.replace(
    "if (state == Player.STATE_BUFFERING && !archiveOnly && !isCatchupPlayback) rescueJob = scope.launch { delay(15_000); if (player.playbackState == Player.STATE_BUFFERING) recoverPlayback() }",
    "if (state == Player.STATE_BUFFERING && !archiveOnly && !isCatchupPlayback) rescueJob = scope.launch { delay(15_000); runOnPlayerThread { if (player.playbackState == Player.STATE_BUFFERING) recoverPlayback() } }",
    1,
)
s = s.replace(
    "        player.addListener(listener)\n        onDispose { player.removeListener(listener); rescueJob?.cancel() }\n",
    "        runOnPlayerThread { player.addListener(listener) }\n        onDispose { runOnPlayerThread { player.removeListener(listener) }; rescueJob?.cancel() }\n",
    1,
)
s = s.replace(
    '                                    scope.launch { val url=repository.catchupUrl(profile,ch,entry); if(url==null) sourceNotice="المزود لم يرسل رابط أرشيف صالح" else { player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.playWhenReady=true; isCatchupPlayback=true; fullscreen=true; overlayVisible=true } }\n',
    '''                                    scope.launch {\n                                        val url = repository.catchupUrl(profile, ch, entry)\n                                        if (url == null) sourceNotice = "المزود لم يرسل رابط أرشيف صالح"\n                                        else {\n                                            isCatchupPlayback = true; fullscreen = true; overlayVisible = true\n                                            runOnPlayerThread {\n                                                player.setMediaItem(MediaItem.fromUri(url))\n                                                player.prepare()\n                                                player.playWhenReady = true\n                                            }\n                                        }\n                                    }\n''',
    1,
)

required = [
    "import android.os.Handler",
    "fun runOnPlayerThread(block: () -> Unit)",
    "runOnPlayerThread {\n            player.setMediaItem(MediaItem.fromUri(source.streamUrl))",
]
missing = [needle for needle in required if needle not in s]
if missing:
    raise SystemExit(f"Patch failed; missing markers: {missing}")

path.write_text(s, encoding="utf-8")
print("Patched LiveTvScreen ExoPlayer thread access")
