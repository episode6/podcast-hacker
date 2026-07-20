package com.episode6.podcasthacker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DownloadLogDao {
    @Query("SELECT * FROM download_log_lines WHERE episodeGuid = :guid ORDER BY seq ASC")
    suspend fun getForEpisode(guid: String): List<DownloadLogLineEntity>

    @Insert
    suspend fun insertAll(lines: List<DownloadLogLineEntity>)

    @Query("DELETE FROM download_log_lines WHERE episodeGuid = :guid")
    suspend fun deleteForEpisode(guid: String)

    @Transaction
    suspend fun replaceForEpisode(guid: String, lines: List<DownloadLogLineEntity>) {
        deleteForEpisode(guid)
        insertAll(lines)
    }
}
