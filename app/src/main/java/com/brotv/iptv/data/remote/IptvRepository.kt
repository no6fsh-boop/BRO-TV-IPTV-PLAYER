package com.brotv.iptv.data.remote

import android.content.Context
import android.util.Xml
import com.brotv.iptv.BuildConfig
import com.brotv.iptv.data.local.BroTvDatabase
import com.brotv.iptv.data.local.CacheEntry
import com.brotv.iptv.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.Reader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class IptvRepository(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    private data class M3uPlaylist(val channels: List<LiveChannel>, val xmltvUrl: String?)

    private val gson = Gson()
    private val cacheDao = context?.let { BroTvDatabase.get(it).cacheDao() }
    private val m3uCache = mutableMapOf<String, M3uPlaylist>()
    private val listMemoryCache = ConcurrentHashMap<String, String>()
    private val xmltvCache = mutableMapOf<String, Map<String, List<EpgEntry>>>()

    suspend fun authenticate(profile: PlaylistProfile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (profile.isDemo()) return@runCatching
            require(profile.isComplete()) { "بيانات الدخول غير مكتملة" }
            if (profile.isM3u()) {
                // M3U loading performs blocking OkHttp I/O. Keep the whole authentication
                // path on Dispatchers.IO so a login can never block the TV UI thread.
                val playlist = loadM3u(profile.hostUrl)
                require(playlist.channels.isNotEmpty()) { "القائمة لا تحتوي على قنوات قابلة للقراءة" }
            } else {
                val root = requestJsonBlocking(profile, null).asJsonObject
                val info = root.getAsJsonObject("user_info") ?: error("استجابة غير صالحة من السيرفر")
                val auth = info.int("auth") ?: 0
                val status = info.string("status")
                require(auth == 1 && (status.isBlank() || status.equals("Active", true))) {
                    if (status.isNotBlank()) "حالة الاشتراك: $status" else "اسم المستخدم أو كلمة المرور غير صحيحة"
                }
            }
        }
    }

    suspend fun getLiveCategories(profile: PlaylistProfile, forceRefresh: Boolean = false): List<IptvCategory> = withContext(Dispatchers.IO) {
        if (profile.isDemo()) return@withContext DemoContent.liveCategories
        val real = if (profile.isM3u()) {
            val channels = m3uChannels(profile)
            channels.groupBy { it.categoryId.ifBlank { "m3u" } }
                .map { (id, list) -> IptvCategory(id, id.ifBlank { "القنوات" }, list.size) }
                .sortedBy { it.name.lowercase() }
        } else {
            cachedList("${profile.cacheId()}:live_categories", IptvCategory::class.java, forceRefresh) {
                requestArray(profile, "get_live_categories").map { obj ->
                    IptvCategory(obj.string("category_id"), obj.string("category_name").ifBlank { "بدون اسم" })
                }
            }
        }
        if (QaConfig.INCLUDE_DEMO_CONTENT) real + DemoContent.liveCategories else real
    }

    suspend fun getLiveStreams(profile: PlaylistProfile, categoryId: String? = null, forceRefresh: Boolean = false): List<LiveChannel> = withContext(Dispatchers.IO) {
        if (profile.isDemo()) {
            return@withContext DemoContent.liveChannels.filter { categoryId.isNullOrBlank() || it.categoryId == categoryId }
        }
        val real = if (profile.isM3u()) {
            val all = m3uChannels(profile)
            if (categoryId.isNullOrBlank()) all else all.filter { it.categoryId == categoryId }
        } else {
            cachedList("${profile.cacheId()}:live:${categoryId.orEmpty()}", LiveChannel::class.java, forceRefresh) {
                requestArray(profile, "get_live_streams", categoryId).mapNotNull { obj ->
                    val id = obj.int("stream_id") ?: return@mapNotNull null
                    val name = obj.string("name").ifBlank { "قناة $id" }
                    LiveChannel(
                        id = id,
                        name = name,
                        categoryId = obj.string("category_id"),
                        icon = obj.stringOrNull("stream_icon"),
                        epgChannelId = obj.stringOrNull("epg_channel_id"),
                        streamUrl = liveUrl(profile, id),
                        tvArchive = obj.int("tv_archive") == 1,
                        archiveDurationDays = obj.int("tv_archive_duration") ?: 0,
                        quality = classifyQuality(name),
                    )
                }
            }
        }
        if (!QaConfig.INCLUDE_DEMO_CONTENT) return@withContext real
        val demo = DemoContent.liveChannels.filter { categoryId.isNullOrBlank() || it.categoryId == categoryId }
        real + demo
    }

    suspend fun getVodCategories(profile: PlaylistProfile): List<IptvCategory> = withContext(Dispatchers.IO) {
        if (profile.isM3u()) return@withContext emptyList()
        cachedList("${profile.cacheId()}:vod_categories", IptvCategory::class.java) {
            requestArray(profile, "get_vod_categories").map { IptvCategory(it.string("category_id"), it.string("category_name")) }
        }
    }

    suspend fun getVodStreams(profile: PlaylistProfile, categoryId: String? = null): List<VodItem> = withContext(Dispatchers.IO) {
        if (profile.isM3u()) return@withContext emptyList()
        cachedList("${profile.cacheId()}:vod:${categoryId.orEmpty()}", VodItem::class.java) {
            requestArray(profile, "get_vod_streams", categoryId).mapNotNull { obj ->
                val id = obj.int("stream_id") ?: return@mapNotNull null
                val extension = obj.string("container_extension").ifBlank { "mp4" }
                VodItem(
                    id = id,
                    name = obj.string("name").ifBlank { "فيلم $id" },
                    categoryId = obj.string("category_id"),
                    poster = obj.stringOrNull("stream_icon"),
                    rating = obj.stringOrNull("rating"),
                    year = obj.stringOrNull("year"),
                    genre = obj.stringOrNull("genre"),
                    plot = obj.stringOrNull("plot"),
                    duration = obj.stringOrNull("duration"),
                    extension = extension,
                    addedEpoch = obj.long("added") ?: 0L,
                    streamUrl = movieUrl(profile, id, extension),
                )
            }
        }
    }

    suspend fun getSeriesCategories(profile: PlaylistProfile): List<IptvCategory> = withContext(Dispatchers.IO) {
        if (profile.isDemo()) return@withContext DemoContent.seriesCategories
        val real = if (profile.isM3u()) emptyList() else cachedList("${profile.cacheId()}:series_categories", IptvCategory::class.java) {
            requestArray(profile, "get_series_categories").map { IptvCategory(it.string("category_id"), it.string("category_name")) }
        }
        if (QaConfig.INCLUDE_DEMO_CONTENT) real + DemoContent.seriesCategories else real
    }

    suspend fun getSeries(profile: PlaylistProfile, categoryId: String? = null): List<SeriesItem> = withContext(Dispatchers.IO) {
        if (profile.isDemo()) return@withContext DemoContent.series.filter { categoryId.isNullOrBlank() || it.categoryId == categoryId }
        val real = if (profile.isM3u()) emptyList() else cachedList("${profile.cacheId()}:series:${categoryId.orEmpty()}", SeriesItem::class.java) {
            requestArray(profile, "get_series", categoryId).mapNotNull { obj ->
                val id = obj.int("series_id") ?: return@mapNotNull null
                val backdrops = obj.getAsJsonArray("backdrop_path")
                SeriesItem(
                    id = id,
                    name = obj.string("name").ifBlank { "مسلسل $id" },
                    categoryId = obj.string("category_id"),
                    poster = obj.stringOrNull("cover"),
                    backdrop = backdrops?.firstOrNull()?.asString,
                    rating = obj.stringOrNull("rating"),
                    year = obj.stringOrNull("releaseDate") ?: obj.stringOrNull("year"),
                    genre = obj.stringOrNull("genre"),
                    plot = obj.stringOrNull("plot"),
                    addedEpoch = obj.long("last_modified") ?: 0L,
                )
            }
        }
        if (!QaConfig.INCLUDE_DEMO_CONTENT) return@withContext real
        val demo = DemoContent.series.filter { categoryId.isNullOrBlank() || it.categoryId == categoryId }
        real + demo
    }

    suspend fun getSeriesDetails(profile: PlaylistProfile, series: SeriesItem): SeriesDetails = withContext(Dispatchers.IO) {
        // Demo series are served locally even when merged into a real provider's
        // list (see getSeries) - they are never real series_ids the provider knows.
        if (profile.isDemo() || series.categoryId == "demo_series") return@withContext DemoContent.seriesDetails(series)
        if (profile.isM3u()) return@withContext SeriesDetails(series, emptyMap())
        val root = requestJson(profile, "get_series_info", extra = mapOf("series_id" to series.id.toString())).asJsonObject
        val info = root.getAsJsonObject("info")
        val enriched = series.copy(
            poster = info?.stringOrNull("cover") ?: series.poster,
            backdrop = info?.getAsJsonArray("backdrop_path")?.firstOrNull()?.asString ?: series.backdrop,
            rating = info?.stringOrNull("rating") ?: series.rating,
            year = info?.stringOrNull("releaseDate") ?: series.year,
            genre = info?.stringOrNull("genre") ?: series.genre,
            plot = info?.stringOrNull("plot") ?: series.plot,
        )
        val episodesObj = root.getAsJsonObject("episodes")
        val bySeason = linkedMapOf<Int, List<SeriesEpisode>>()
        episodesObj?.entrySet()?.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }?.forEach { (seasonKey, element) ->
            val season = seasonKey.toIntOrNull() ?: return@forEach
            bySeason[season] = element.asJsonArray.mapNotNull { item ->
                val obj = item.asJsonObject
                val id = obj.int("id") ?: return@mapNotNull null
                val ext = obj.string("container_extension").ifBlank { "mp4" }
                val infoObj = obj.getAsJsonObject("info")
                SeriesEpisode(
                    id = id,
                    episodeNum = obj.int("episode_num") ?: 0,
                    title = obj.string("title").ifBlank { "الحلقة ${obj.int("episode_num") ?: ""}" },
                    season = season,
                    extension = ext,
                    duration = infoObj?.stringOrNull("duration"),
                    plot = infoObj?.stringOrNull("plot"),
                    image = infoObj?.stringOrNull("movie_image"),
                    streamUrl = seriesUrl(profile, id, ext),
                )
            }
        }
        SeriesDetails(enriched, bySeason)
    }

    suspend fun getSubscriptionDaysLeft(profile: PlaylistProfile): Int? = withContext(Dispatchers.IO) {
        if (profile.isDemo()) return@withContext 30
        if (profile.isM3u()) return@withContext null
        runCatching {
            val info = requestJson(profile, null).asJsonObject.getAsJsonObject("user_info") ?: return@runCatching null
            val exp = info.long("exp_date") ?: return@runCatching null
            ((exp - System.currentTimeMillis() / 1000L) / 86400L).coerceAtLeast(0L).toInt()
        }.getOrNull()
    }

    suspend fun getShortEpg(profile: PlaylistProfile, streamId: Int, limit: Int = 12): List<EpgEntry> = withContext(Dispatchers.IO) {
        if (profile.isDemo() || DemoContent.isDemoChannelId(streamId)) return@withContext DemoContent.epgFor(streamId).take(limit)
        if (profile.isM3u()) {
            val channel = m3uChannels(profile).firstOrNull { it.id == streamId } ?: return@withContext emptyList()
            return@withContext getM3uEpg(profile, channel).take(limit)
        }
        runCatching {
            val root = requestJson(profile, "get_simple_data_table", extra = mapOf("stream_id" to streamId.toString())).asJsonObject
            val entries = root.getAsJsonArray("epg_listings") ?: requestJson(profile, "get_short_epg", extra = mapOf("stream_id" to streamId.toString(), "limit" to limit.toString())).asJsonObject.getAsJsonArray("epg_listings")
            entries?.map { e ->
                val o = e.asJsonObject
                EpgEntry(
                    title = decodeBase64Maybe(o.string("title")),
                    start = o.stringOrNull("start"),
                    end = o.stringOrNull("end"),
                    description = decodeBase64Maybe(o.string("description")),
                    startTimestamp = o.long("start_timestamp"),
                    endTimestamp = o.long("stop_timestamp") ?: o.long("end_timestamp"),
                )
            }.orEmpty().take(limit)
        }.getOrDefault(emptyList())
    }

    suspend fun catchupUrl(profile: PlaylistProfile, channel: LiveChannel, entry: EpgEntry): String? = withContext(Dispatchers.Default) {
        val startEpoch = entry.startTimestamp ?: parseFlexibleTime(entry.start)
        val endEpoch = entry.endTimestamp ?: parseFlexibleTime(entry.end)
        val durationMinutes = if (startEpoch != null && endEpoch != null) ((endEpoch - startEpoch) / 60L).coerceAtLeast(1L) else 60L
        if (profile.isM3u()) {
            val template = channel.catchupSource ?: return@withContext null
            val start = startEpoch ?: return@withContext null
            val end = endEpoch ?: (start + durationMinutes * 60L)
            return@withContext template
                .replace("{utc}", start.toString())
                .replace("{utcend}", end.toString())
                .replace("{duration}", (durationMinutes * 60L).toString())
                .replace("${'$'}{start}", start.toString())
                .replace("${'$'}{timestamp}", start.toString())
        }
        if (!channel.tvArchive) return@withContext null
        val startText = entry.start?.takeIf { it.length >= 16 }?.let {
            it.substring(0, 16).replace(' ', ':').let { v -> v.substring(0, 13) + "-" + v.substring(14, 16) }
        } ?: startEpoch?.let { epochToXtreamPath(it) } ?: return@withContext null
        "${profile.hostUrl.trimEnd('/')}/timeshift/${enc(profile.username)}/${enc(profile.password)}/$durationMinutes/$startText/${channel.id}.ts"
    }

    private suspend fun getM3uEpg(profile: PlaylistProfile, channel: LiveChannel): List<EpgEntry> {
        val playlist = loadM3u(profile.hostUrl)
        val epgUrl = playlist.xmltvUrl ?: return emptyList()
        val channelId = channel.epgChannelId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val map = xmltvCache[epgUrl] ?: withContext(Dispatchers.IO) { loadXmltv(epgUrl).also { xmltvCache[epgUrl] = it } }
        return map[channelId].orEmpty().sortedBy { it.startTimestamp ?: Long.MAX_VALUE }
    }

    private fun loadXmltv(url: String): Map<String, List<EpgEntry>> {
        val request = Request.Builder().url(url).header("User-Agent", "BROTV+/0.3").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("استجابة EPG فارغة")
            body.charStream().buffered().use { reader -> return parseXmltv(reader) }
        }
    }

    private fun parseXmltv(reader: Reader): Map<String, List<EpgEntry>> {
        val out = linkedMapOf<String, MutableList<EpgEntry>>()
        val parser = Xml.newPullParser().apply { setInput(reader) }
        var event = parser.eventType
        var channel: String? = null
        var start: String? = null
        var stop: String? = null
        var title: String? = null
        var desc: String? = null
        var currentTag: String? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (parser.name == "programme") {
                        channel = parser.getAttributeValue(null, "channel")
                        start = parser.getAttributeValue(null, "start")
                        stop = parser.getAttributeValue(null, "stop")
                        title = null; desc = null
                    }
                }
                XmlPullParser.TEXT -> when (currentTag) {
                    "title" -> if (channel != null) title = parser.text
                    "desc" -> if (channel != null) desc = parser.text
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme" && channel != null && !title.isNullOrBlank()) {
                        out.getOrPut(channel!!) { mutableListOf() } += EpgEntry(
                            title = title!!,
                            start = start,
                            end = stop,
                            description = desc,
                            startTimestamp = parseXmltvTime(start),
                            endTimestamp = parseXmltvTime(stop),
                        )
                        channel = null
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun requestArray(profile: PlaylistProfile, action: String, categoryId: String? = null): List<JsonObject> {
        val extra = if (categoryId.isNullOrBlank()) emptyMap() else mapOf("category_id" to categoryId)
        val element = requestJsonBlocking(profile, action, extra)
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
    }

    private suspend fun requestJson(profile: PlaylistProfile, action: String?, extra: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) { requestJsonBlocking(profile, action, extra) }

    private fun requestJsonBlocking(profile: PlaylistProfile, action: String?, extra: Map<String, String> = emptyMap()): JsonElement {
        val base = profile.hostUrl.trim().trimEnd('/')
        val params = linkedMapOf("username" to profile.username, "password" to profile.password)
        if (!action.isNullOrBlank()) params["action"] = action
        params.putAll(extra)
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return JsonParser.parseString(get("$base/player_api.php?$query"))
    }

    private fun m3uChannels(profile: PlaylistProfile): List<LiveChannel> = loadM3u(profile.hostUrl).channels

    private fun loadM3u(url: String): M3uPlaylist {
        m3uCache[url]?.let { return it }

        val out = mutableListOf<LiveChannel>()
        var xmltvUrl: String? = null
        var pendingName = ""
        var pendingLogo: String? = null
        var pendingEpgId: String? = null
        var pendingGroup = "القنوات"
        var pendingCatchup: String? = null
        var pendingCatchupDays = 0
        var id = 1

        // Parse directly from the response stream instead of body.string().lineSequence().toList().
        // Large provider playlists can be tens of MB; streaming avoids holding duplicate copies
        // of the entire M3U file in memory.
        val request = Request.Builder().url(url).header("User-Agent", "BROTV+/0.3").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("قائمة M3U فارغة")
            body.charStream().buffered().use { reader ->
                var index = 0
                while (true) {
                    val raw = reader.readLine() ?: break
                    val line = raw.trim().trimStart('\uFEFF')
                    if (index == 0 && line.startsWith("#EXTM3U", true)) {
                        xmltvUrl = attr(line, "x-tvg-url") ?: attr(line, "url-tvg")
                    } else if (line.startsWith("#EXTINF", true)) {
                        pendingName = line.substringAfterLast(',').trim().ifBlank { "قناة $id" }
                        pendingLogo = attr(line, "tvg-logo")
                        pendingEpgId = attr(line, "tvg-id")
                        pendingGroup = attr(line, "group-title") ?: "القنوات"
                        pendingCatchup = attr(line, "catchup-source")
                        pendingCatchupDays = attr(line, "catchup-days")?.toIntOrNull() ?: 0
                    } else if (line.startsWith("http://", true) || line.startsWith("https://", true)) {
                        val channelId = id++
                        out += LiveChannel(
                            id = channelId,
                            name = pendingName.ifBlank { "قناة $channelId" },
                            categoryId = pendingGroup,
                            icon = pendingLogo,
                            epgChannelId = pendingEpgId,
                            streamUrl = line,
                            tvArchive = !pendingCatchup.isNullOrBlank(),
                            archiveDurationDays = pendingCatchupDays,
                            catchupSource = pendingCatchup,
                            quality = classifyQuality(pendingName),
                        )
                        pendingName = ""; pendingLogo = null; pendingEpgId = null; pendingCatchup = null; pendingCatchupDays = 0
                    }
                    index++
                }
            }
        }

        return M3uPlaylist(out, xmltvUrl).also { m3uCache[url] = it }
    }

    private suspend fun <T> cachedList(key: String, clazz: Class<T>, forceRefresh: Boolean = false, loader: () -> List<T>): List<T> {
        val cachedJson = listMemoryCache[key] ?: cacheDao?.get(key)?.json?.also { listMemoryCache[key] = it }
        val cached = cachedJson?.let { json -> decodeList(json, clazz) }.orEmpty()
        if (!forceRefresh && cached.isNotEmpty()) return cached
        return runCatching {
            loader().also { list ->
                if (list.isNotEmpty()) {
                    val json = gson.toJson(list)
                    listMemoryCache[key] = json
                    cacheDao?.put(CacheEntry(key, json, System.currentTimeMillis()))
                }
            }
        }.getOrElse { error ->
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    private fun <T> decodeList(json: String, clazz: Class<T>): List<T> {
        val type = TypeToken.getParameterized(List::class.java, clazz).type
        return runCatching { gson.fromJson<List<T>>(json, type) }.getOrDefault(emptyList())
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "BROTV+/0.3").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun liveUrl(profile: PlaylistProfile, id: Int) = "${profile.hostUrl.trimEnd('/')}/live/${enc(profile.username)}/${enc(profile.password)}/$id.ts"
    private fun movieUrl(profile: PlaylistProfile, id: Int, ext: String) = "${profile.hostUrl.trimEnd('/')}/movie/${enc(profile.username)}/${enc(profile.password)}/$id.$ext"
    private fun seriesUrl(profile: PlaylistProfile, id: Int, ext: String) = "${profile.hostUrl.trimEnd('/')}/series/${enc(profile.username)}/${enc(profile.password)}/$id.$ext"
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        fun classifyQuality(name: String): StreamQuality {
            val n = name.uppercase()
            return when {
                "4K" in n || "UHD" in n || "2160" in n -> StreamQuality.Q4K
                "FHD" in n || "1080" in n -> StreamQuality.FHD
                "HD" in n || "720" in n -> StreamQuality.HD
                "SD" in n || "480" in n || "576" in n -> StreamQuality.SD
                else -> StreamQuality.UNKNOWN
            }
        }
    }
}

private fun PlaylistProfile.isM3u(): Boolean {
    val u = hostUrl.lowercase()
    return u.contains(".m3u") || u.contains("type=m3u") || (username.isBlank() && password.isBlank() && u.startsWith("http"))
}

/**
 * Root cause fix for QA failures #10 and #11 ("no selectable Demo channel/series
 * was found"): the app previously had ZERO local/demo content path — every
 * screen depended entirely on a real, reachable Xtream/M3U provider. QA and
 * screenshot/CI environments do not have real IPTV credentials, so Live TV and
 * Series always rendered an empty, nothing-to-select list.
 *
 * [DEMO_HOST_MARKER] is a reserved, non-HTTP sentinel host that can never
 * collide with a real user-entered provider URL (real providers must be
 * http(s) or an .m3u playlist). A profile pointing at it is recognized here
 * and served fully offline from [DemoContent] — production login/playback
 * logic for real profiles is completely untouched.
 */
/**
 * Single switch controlling whether the always-available "Demo" catalog
 * entries (below) are merged into real Live TV / Series results. QA needs a
 * guaranteed, reliably-named selectable item ("Demo News HD", "Demo Series
 * One") on every run, independent of whether a real Xtream/M3U provider is
 * configured or what content it happens to expose.
 *
 * This must be wired to `BuildConfig.DEBUG` (or a dedicated QA build type)
 * before shipping, so real end users never see these rows in production.
 */
object QaConfig {
    // Demo rows are useful for QA/debug builds but must never leak into release builds.
    val INCLUDE_DEMO_CONTENT: Boolean get() = BuildConfig.DEBUG
}

const val DEMO_HOST_MARKER = "demo://brotv.local"
fun demoPlaylistProfile(): PlaylistProfile = PlaylistProfile(
    listName = "BRO PLUS TV Demo",
    username = "demo",
    password = "demo",
    hostUrl = DEMO_HOST_MARKER,
)
fun PlaylistProfile.isDemo(): Boolean = hostUrl == DEMO_HOST_MARKER

private object DemoContent {
    val liveCategories = listOf(
        IptvCategory("demo_news", "قنوات إخبارية (Demo)", 2),
        IptvCategory("demo_sports", "قنوات رياضية (Demo)", 1),
    )
    val liveChannels = listOf(
        LiveChannel(id = 9001, name = "Demo News HD", categoryId = "demo_news", streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", quality = StreamQuality.HD),
        LiveChannel(id = 9002, name = "Demo News SD", categoryId = "demo_news", streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", quality = StreamQuality.SD),
        LiveChannel(id = 9003, name = "Demo Sports FHD", categoryId = "demo_sports", streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", quality = StreamQuality.FHD),
    )
    val seriesCategories = listOf(IptvCategory("demo_series", "مسلسلات (Demo)", 1))
    val series = listOf(
        SeriesItem(id = 9101, name = "Demo Series One", categoryId = "demo_series", plot = "مسلسل تجريبي لاختبار عرض التفاصيل والحلقات."),
    )
    fun seriesDetails(item: SeriesItem): SeriesDetails {
        val episodes = (1..3).map { num ->
            SeriesEpisode(
                id = 9200 + num,
                episodeNum = num,
                title = "الحلقة $num",
                season = 1,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                plot = "حلقة تجريبية رقم $num",
            )
        }
        return SeriesDetails(item, mapOf(1 to episodes))
    }
    fun epgFor(streamId: Int): List<EpgEntry> = listOf(
        EpgEntry(title = "برنامج Demo الحالي", start = "00:00"),
        EpgEntry(title = "برنامج Demo التالي", start = "01:00"),
    )
    fun isDemoChannelId(id: Int): Boolean = liveChannels.any { it.id == id }
}

private fun PlaylistProfile.cacheId(): String = (hostUrl.trimEnd('/') + "|" + username).hashCode().toString()
private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.stringOrNull(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.int(name: String): Int? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString?.toInt() }.getOrNull()
private fun JsonObject.long(name: String): Long? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString?.toLong() }.getOrNull()
private fun attr(line: String, name: String): String? = Regex("$name=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
private fun decodeBase64Maybe(value: String): String = runCatching {
    val bytes = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
    String(bytes, Charsets.UTF_8).takeIf { it.any(Char::isLetterOrDigit) } ?: value
}.getOrDefault(value)
private fun parseFlexibleTime(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyyMMddHHmmss Z", "yyyyMMddHHmmssZ")
    for (pattern in patterns) {
        val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).parse(value.trim())?.time?.div(1000L) }.getOrNull()
        if (parsed != null) return parsed
    }
    return value.toLongOrNull()
}
private fun parseXmltvTime(value: String?): Long? = parseFlexibleTime(value)
private fun epochToXtreamPath(epochSeconds: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
    format.timeZone = TimeZone.getDefault()
    return format.format(java.util.Date(epochSeconds * 1000L))
}
