package com.evotrain.data.db

import androidx.room.*
import com.evotrain.data.model.AIModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE isAlive = 1 ORDER BY accuracyScore DESC")
    fun getAliveModels(): Flow<List<AIModel>>

    @Query("SELECT * FROM models ORDER BY accuracyScore DESC")
    fun getAllModels(): Flow<List<AIModel>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModelById(id: String): AIModel?

    @Query("SELECT * FROM models WHERE isAlive = 1 ORDER BY accuracyScore DESC")
    suspend fun getAliveModelsList(): List<AIModel>

    @Query("SELECT * FROM models WHERE isAlive = 1 ORDER BY accuracyScore DESC LIMIT 1")
    suspend fun getBestAliveModel(): AIModel?

    @Query("SELECT * FROM models WHERE isAlive = 1 ORDER BY accuracyScore DESC LIMIT :limit")
    suspend fun getTopModels(limit: Int): List<AIModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AIModel)

    @Update
    suspend fun updateModel(model: AIModel)

    @Delete
    suspend fun deleteModel(model: AIModel)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModelById(id: String)

    @Query("DELETE FROM models WHERE isAlive = 0")
    suspend fun deleteDeadModels()

    @Query("DELETE FROM models")
    suspend fun deleteAllModels()

    @Query("UPDATE models SET isAlive = 0 WHERE id NOT IN (:survivorIds)")
    suspend fun markNonSurvivorsDead(survivorIds: List<String>)

    @Query("UPDATE models SET accuracyScore = :accuracy, lossScore = :loss, epochsTrained = :epochs WHERE id = :id")
    suspend fun updateModelStats(id: String, accuracy: Float, loss: Float, epochs: Int)

    @Query("UPDATE models SET isAlive = :alive WHERE id = :id")
    suspend fun updateAliveStatus(id: String, alive: Boolean)

    @Query("SELECT COUNT(*) FROM models WHERE isAlive = 1")
    suspend fun getAliveModelCount(): Int

    @Query("UPDATE models SET precision = :precision, recall = :recall, f1Score = :f1, truePositives = :tp, trueNegatives = :tn, falsePositives = :fp, falseNegatives = :fn WHERE id = :id")
    suspend fun updateConfusionStats(id: String, precision: Float, recall: Float, f1: Float, tp: Int, tn: Int, fp: Int, fn: Int)

    @Query("UPDATE models SET stopReason = :reason WHERE id = :id")
    suspend fun updateStopReason(id: String, reason: String?)

    @Query("UPDATE models SET currentLearningRate = :lr WHERE id = :id")
    suspend fun updateLearningRate(id: String, lr: Float)

    @Query("UPDATE models SET mutationSigma = :sigma WHERE id = :id")
    suspend fun updateMutationSigma(id: String, sigma: Float)

    @Query("UPDATE models SET parentIdName = :parentName WHERE id = :id")
    suspend fun updateParentIdName(id: String, parentName: String?)

    @Query("SELECT * FROM models WHERE isAlive = 1 ORDER BY accuracyScore DESC")
    fun getAliveModelsListSync(): List<AIModel>
}
