package com.brotv.iptv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_cache")
data class CacheEntry(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val updatedAt: Long,
)
