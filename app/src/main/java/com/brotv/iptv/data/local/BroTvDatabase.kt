package com.brotv.iptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class BroTvDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile private var instance: BroTvDatabase? = null
        fun get(context: Context): BroTvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BroTvDatabase::class.java, "brotvplus.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
