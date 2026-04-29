package com.evotrain.data.db

import androidx.room.*
import com.evotrain.data.model.Generation
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generations ORDER BY generationNumber DESC")
    fun getAllGenerations(): Flow<List<Generation>>

    @Query("SELECT * FROM generations ORDER BY generationNumber DESC LIMIT 1")
    suspend fun getLatestGeneration(): Generation?

    @Query("SELECT * FROM generations WHERE generationNumber = :genNumber")
    suspend fun getGenerationByNumber(genNumber: Int): Generation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneration(generation: Generation)

    @Update
    suspend fun updateGeneration(generation: Generation)

    @Query("DELETE FROM generations")
    suspend fun deleteAllGenerations()

    @Query("SELECT MAX(generationNumber) FROM generations")
    suspend fun getMaxGenerationNumber(): Int?
}
