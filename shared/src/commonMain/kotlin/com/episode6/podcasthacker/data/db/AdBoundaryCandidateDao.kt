package com.episode6.podcasthacker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AdBoundaryCandidateDao {
    @Query("SELECT * FROM ad_boundary_candidates WHERE episodeGuid = :guid ORDER BY timeMs ASC")
    suspend fun getForEpisode(guid: String): List<AdBoundaryCandidateEntity>

    @Insert
    suspend fun insertAll(candidates: List<AdBoundaryCandidateEntity>)

    @Query("DELETE FROM ad_boundary_candidates WHERE episodeGuid = :guid")
    suspend fun deleteForEpisode(guid: String)

    @Transaction
    suspend fun replaceForEpisode(guid: String, candidates: List<AdBoundaryCandidateEntity>) {
        deleteForEpisode(guid)
        insertAll(candidates)
    }
}
