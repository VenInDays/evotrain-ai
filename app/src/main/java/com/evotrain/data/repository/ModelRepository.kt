package com.evotrain.data.repository

import com.evotrain.data.db.GenerationDao
import com.evotrain.data.db.ModelDao
import com.evotrain.data.model.AIModel
import com.evotrain.data.model.Generation
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    private val generationDao: GenerationDao
) {
    fun getAliveModels(): Flow<List<AIModel>> = modelDao.getAliveModels()
    fun getAllModels(): Flow<List<AIModel>> = modelDao.getAllModels()
    fun getAllGenerations(): Flow<List<Generation>> = generationDao.getAllGenerations()

    suspend fun getModelById(id: String): AIModel? = modelDao.getModelById(id)
    suspend fun getAliveModelsList(): List<AIModel> = modelDao.getAliveModelsList()
    suspend fun getBestAliveModel(): AIModel? = modelDao.getBestAliveModel()
    suspend fun getTopModels(limit: Int): List<AIModel> = modelDao.getTopModels(limit)
    suspend fun getLatestGeneration(): Generation? = generationDao.getLatestGeneration()

    suspend fun insertModel(model: AIModel) = modelDao.insertModel(model)
    suspend fun updateModel(model: AIModel) = modelDao.updateModel(model)
    suspend fun deleteModel(model: AIModel) = modelDao.deleteModel(model)
    suspend fun deleteModelById(id: String) = modelDao.deleteModelById(id)
    suspend fun markNonSurvivorsDead(survivorIds: List<String>) = modelDao.markNonSurvivorsDead(survivorIds)
    suspend fun updateModelStats(id: String, accuracy: Float, loss: Float, epochs: Int) =
        modelDao.updateModelStats(id, accuracy, loss, epochs)
    suspend fun updateAliveStatus(id: String, alive: Boolean) = modelDao.updateAliveStatus(id, alive)

    suspend fun insertGeneration(generation: Generation) = generationDao.insertGeneration(generation)
    suspend fun updateGeneration(generation: Generation) = generationDao.updateGeneration(generation)
    suspend fun deleteAllGenerations() = generationDao.deleteAllGenerations()

    suspend fun deleteAllModels() = modelDao.deleteAllModels()
    suspend fun getAliveModelCount(): Int = modelDao.getAliveModelCount()

    fun createModel(
        name: String,
        generationNumber: Int,
        parentId: String?,
        accuracyScore: Float = 0f,
        lossScore: Float = 1f,
        epochsTrained: Int = 0,
        tflitePath: String,
        cloneIndex: Int = 0
    ): AIModel {
        return AIModel(
            id = UUID.randomUUID().toString(),
            name = name,
            generationNumber = generationNumber,
            parentId = parentId,
            accuracyScore = accuracyScore,
            lossScore = lossScore,
            epochsTrained = epochsTrained,
            createdAt = System.currentTimeMillis(),
            isAlive = true,
            tflitePath = tflitePath,
            cloneIndex = cloneIndex
        )
    }

    fun createGeneration(
        generationNumber: Int,
        bestAccuracy: Float = 0f,
        averageAccuracy: Float = 0f,
        survivorIds: String = ""
    ): Generation {
        return Generation(
            id = UUID.randomUUID().toString(),
            generationNumber = generationNumber,
            startedAt = System.currentTimeMillis(),
            completedAt = null,
            bestAccuracy = bestAccuracy,
            averageAccuracy = averageAccuracy,
            survivorIds = survivorIds
        )
    }
}
