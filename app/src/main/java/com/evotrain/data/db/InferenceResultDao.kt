package com.evotrain.data.db

import androidx.room.*
import com.evotrain.data.model.InferenceResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InferenceResultDao {
    @Query("SELECT * FROM inference_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<InferenceResultEntity>>

    @Query("SELECT * FROM inference_results ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentResults(limit: Int): List<InferenceResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: InferenceResultEntity)

    @Query("DELETE FROM inference_results")
    suspend fun deleteAllResults()
}
