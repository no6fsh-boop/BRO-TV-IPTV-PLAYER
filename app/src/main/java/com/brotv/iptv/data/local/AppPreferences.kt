package com.brotv.iptv.data.local

import android.content.Context
import com.brotv.iptv.data.model.IptvCategory
import com.brotv.iptv.data.model.StreamQuality

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("brotv_local_state", Context.MODE_PRIVATE)

    var seekSeconds: Int
        get() = prefs.getInt(KEY_SEEK_SECONDS, 10)
        set(value) = prefs.edit().putInt(KEY_SEEK_SECONDS, value.coerceIn(5, 60)).apply()

    fun selectedQualities(): Set<StreamQuality> {
        val raw = prefs.getStringSet(KEY_QUALITIES, null) ?: return StreamQuality.entries.toSet()
        return raw.mapNotNull { value -> StreamQuality.entries.firstOrNull { it.name == value } }.toSet()
            .ifEmpty { StreamQuality.entries.toSet() }
    }

    fun setSelectedQualities(values: Set<StreamQuality>) {
        prefs.edit().putStringSet(KEY_QUALITIES, values.map { it.name }.toSet()).apply()
    }

    fun favoriteLiveIds(): Set<Int> = intSet(KEY_FAVORITE_LIVE)
    fun favoriteMovieIds(): Set<Int> = intSet(KEY_FAVORITE_MOVIES)
    fun favoriteSeriesIds(): Set<Int> = intSet(KEY_FAVORITE_SERIES)
    fun hiddenLiveIds(): Set<Int> = intSet(KEY_HIDDEN_LIVE)
    fun hiddenCategoryIds(): Set<String> = prefs.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()).orEmpty()

    fun toggleFavoriteLive(id: Int) = toggleInt(KEY_FAVORITE_LIVE, id)
    fun toggleFavoriteMovie(id: Int) = toggleInt(KEY_FAVORITE_MOVIES, id)
    fun toggleFavoriteSeries(id: Int) = toggleInt(KEY_FAVORITE_SERIES, id)
    fun toggleHiddenLive(id: Int) = toggleInt(KEY_HIDDEN_LIVE, id)

    fun toggleHiddenCategory(id: String) {
        val set = hiddenCategoryIds().toMutableSet()
        if (!set.add(id)) set.remove(id)
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, set).apply()
    }

    fun unhideLive(id: Int) {
        val set = hiddenLiveIds().toMutableSet().apply { remove(id) }
        saveIntSet(KEY_HIDDEN_LIVE, set)
    }

    fun unhideCategory(id: String) {
        val set = hiddenCategoryIds().toMutableSet().apply { remove(id) }
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, set).apply()
    }

    fun clearHidden() {
        prefs.edit().remove(KEY_HIDDEN_LIVE).remove(KEY_HIDDEN_CATEGORIES).apply()
    }

    fun watchedEpisodeIds(): Set<Int> = intSet(KEY_WATCHED_EPISODES)
    fun markEpisodeWatched(id: Int) {
        val set = watchedEpisodeIds().toMutableSet().apply { add(id) }
        saveIntSet(KEY_WATCHED_EPISODES, set)
    }

    fun savePosition(contentKey: String, positionMs: Long) {
        if (positionMs <= 5_000L) prefs.edit().remove("position:$contentKey").apply()
        else prefs.edit().putLong("position:$contentKey", positionMs).apply()
    }

    fun loadPosition(contentKey: String): Long = prefs.getLong("position:$contentKey", 0L)

    fun saveMovieProgress(movieId: Int, positionMs: Long, durationMs: Long) {
        if (durationMs > 0 && positionMs >= durationMs * 0.90) {
            clearPosition("movie:$movieId")
        } else savePosition("movie:$movieId", positionMs)
    }

    fun movieResumeIds(): Set<Int> = prefs.all.keys.mapNotNull { key ->
        if (!key.startsWith("position:movie:")) null
        else key.removePrefix("position:movie:").toIntOrNull()?.takeIf { prefs.getLong(key, 0L) > 5_000L }
    }.toSet()

    fun saveEpisodeProgress(seriesId: Int, episodeId: Int, positionMs: Long, durationMs: Long) {
        prefs.edit().putInt("episode_series:$episodeId", seriesId).putInt("last_episode:$seriesId", episodeId).apply()
        if (durationMs > 0 && positionMs >= durationMs * 0.90) {
            clearPosition("episode:$episodeId")
            markEpisodeWatched(episodeId)
        } else savePosition("episode:$episodeId", positionMs)
    }

    fun seriesResumeIds(): Set<Int> = prefs.all.keys.mapNotNull { key ->
        if (!key.startsWith("position:episode:")) return@mapNotNull null
        val episodeId = key.removePrefix("position:episode:").toIntOrNull() ?: return@mapNotNull null
        if (prefs.getLong(key, 0L) <= 5_000L) return@mapNotNull null
        prefs.getInt("episode_series:$episodeId", -1).takeIf { it >= 0 }
    }.toSet()

    fun lastEpisodeForSeries(seriesId: Int): Int? = prefs.getInt("last_episode:$seriesId", -1).takeIf { it >= 0 }

    fun clearPosition(contentKey: String) = prefs.edit().remove("position:$contentKey").apply()

    fun preferredLiveSource(groupKey: String): Int? = prefs.getInt("preferred_source:$groupKey", -1).takeIf { it >= 0 }
    fun savePreferredLiveSource(groupKey: String, channelId: Int) {
        prefs.edit().putInt("preferred_source:$groupKey", channelId).apply()
    }

    fun sortCategories(scope: String, categories: List<IptvCategory>): List<IptvCategory> {
        val order = prefs.getString("category_order:$scope", null)?.split('|')?.filter { it.isNotBlank() }.orEmpty()
        if (order.isEmpty()) return categories
        val rank = order.withIndex().associate { it.value to it.index }
        return categories.sortedWith(compareBy<IptvCategory> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
    }

    fun moveCategory(scope: String, categories: List<IptvCategory>, id: String, delta: Int) {
        val ordered = sortCategories(scope, categories).map { it.id }.toMutableList()
        val from = ordered.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (from == to) return
        val item = ordered.removeAt(from)
        ordered.add(to, item)
        prefs.edit().putString("category_order:$scope", ordered.joinToString("|")).apply()
    }

    var parentalControlEnabled: Boolean
        get() = prefs.getBoolean("parental_enabled", false)
        set(value) = prefs.edit().putBoolean("parental_enabled", value).apply()

    var use24HourClock: Boolean
        get() = prefs.getBoolean("clock_24h", false)
        set(value) = prefs.edit().putBoolean("clock_24h", value).apply()

    var compactLibraryLayout: Boolean
        get() = prefs.getBoolean("compact_library", false)
        set(value) = prefs.edit().putBoolean("compact_library", value).apply()

    var liveLayoutMode: Int
        get() = prefs.getInt("live_layout_mode", 0)
        set(value) = prefs.edit().putInt("live_layout_mode", value.coerceIn(0, 2)).apply()

    fun saveLiveSelection(categoryId: String?, groupKey: String?) {
        prefs.edit().putString("live_last_category", categoryId).putString("live_last_group", groupKey).apply()
    }
    fun lastLiveCategoryId(): String? = prefs.getString("live_last_category", null)
    fun lastLiveGroupKey(): String? = prefs.getString("live_last_group", null)

    fun saveLiveListPositions(categoryIndex: Int, channelIndex: Int) {
        prefs.edit().putInt("live_cat_index", categoryIndex).putInt("live_channel_index", channelIndex).apply()
    }
    fun lastLiveCategoryIndex(): Int = prefs.getInt("live_cat_index", 0).coerceAtLeast(0)
    fun lastLiveChannelIndex(): Int = prefs.getInt("live_channel_index", 0).coerceAtLeast(0)

    fun clearLiveHistory() {
        prefs.edit().remove("live_last_category").remove("live_last_group").remove("live_cat_index").remove("live_channel_index").apply()
    }
    fun clearMovieHistory() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("position:movie:") }.forEach(e::remove)
        e.apply()
    }
    fun clearSeriesHistory() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("position:episode:") || it.startsWith("episode_series:") || it.startsWith("last_episode:") }.forEach(e::remove)
        e.remove(KEY_WATCHED_EPISODES).apply()
    }

    private fun intSet(key: String): Set<Int> = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    private fun toggleInt(key: String, id: Int) {
        val set = intSet(key).toMutableSet()
        if (!set.add(id)) set.remove(id)
        saveIntSet(key, set)
    }
    private fun saveIntSet(key: String, values: Set<Int>) = prefs.edit().putStringSet(key, values.map(Int::toString).toSet()).apply()

    private companion object {
        const val KEY_SEEK_SECONDS = "seek_seconds"
        const val KEY_QUALITIES = "qualities"
        const val KEY_FAVORITE_LIVE = "favorite_live"
        const val KEY_FAVORITE_MOVIES = "favorite_movies"
        const val KEY_FAVORITE_SERIES = "favorite_series"
        const val KEY_HIDDEN_LIVE = "hidden_live"
        const val KEY_HIDDEN_CATEGORIES = "hidden_categories"
        const val KEY_WATCHED_EPISODES = "watched_episodes"
    }
}
