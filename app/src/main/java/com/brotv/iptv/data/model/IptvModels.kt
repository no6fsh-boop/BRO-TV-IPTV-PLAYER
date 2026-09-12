package com.brotv.iptv.data.model

data class IptvCategory(
    val id: String,
    val name: String,
    val count: Int? = null,
)

data class LiveChannel(
    val id: Int,
    val name: String,
    val categoryId: String,
    val icon: String? = null,
    val epgChannelId: String? = null,
    val streamUrl: String,
    val tvArchive: Boolean = false,
    val archiveDurationDays: Int = 0,
    val catchupSource: String? = null,
    val quality: StreamQuality = StreamQuality.UNKNOWN,
)

data class VodItem(
    val id: Int,
    val name: String,
    val categoryId: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val duration: String? = null,
    val extension: String = "mp4",
    val addedEpoch: Long = 0L,
    val streamUrl: String,
)

data class SeriesItem(
    val id: Int,
    val name: String,
    val categoryId: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val addedEpoch: Long = 0L,
)

data class SeriesEpisode(
    val id: Int,
    val episodeNum: Int,
    val title: String,
    val season: Int,
    val extension: String = "mp4",
    val duration: String? = null,
    val plot: String? = null,
    val image: String? = null,
    val streamUrl: String,
)

data class SeriesDetails(
    val item: SeriesItem,
    val episodesBySeason: Map<Int, List<SeriesEpisode>>,
)

data class EpgEntry(
    val title: String,
    val start: String? = null,
    val end: String? = null,
    val description: String? = null,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
)

enum class StreamQuality { Q4K, FHD, HD, SD, UNKNOWN }

enum class MediaSort { DEFAULT, NEWEST, OLDEST, AZ, ZA }
