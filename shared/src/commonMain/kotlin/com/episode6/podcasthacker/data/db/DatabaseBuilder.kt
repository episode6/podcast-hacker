package com.episode6.podcasthacker.data.db

import androidx.room.RoomDatabase
import com.episode6.podcasthacker.PlatformContext
import okio.Path

internal expect fun PlatformContext.appDatabaseBuilder(dbPath: Path): RoomDatabase.Builder<AppDatabase>
