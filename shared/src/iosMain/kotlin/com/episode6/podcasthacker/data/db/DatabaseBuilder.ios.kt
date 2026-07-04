package com.episode6.podcasthacker.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.episode6.podcasthacker.PlatformContext
import okio.Path

internal actual fun PlatformContext.appDatabaseBuilder(dbPath: Path): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = dbPath.toString())
