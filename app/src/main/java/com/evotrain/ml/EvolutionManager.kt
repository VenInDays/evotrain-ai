package com.evotrain.ml

import com.evotrain.data.model.AIModel
import com.evotrain.data.repository.ModelRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvolutionManager @Inject constructor(
    private val repository: ModelRepository,
    private val weightMutator: WeightMutator
) {
    companion object {
        const val POPULATION_SIZE = 10
        const val SURVIVORS_PER_GENERATION = 2
    }

    suspend fun selectSurvivors(models: List<AIModel>): List<AIModel> {
        val sorted = models.sortedByDescending { it.accuracyScore }
        return sorted.take(SURVIVORS_PER_GENERATION)
    }

    suspend fun eliminateNonSurvivors(survivorIds: List<String>) {
        repository.markNonSurvivorsDead(survivorIds)
    }

    suspend fun reproduce(
        survivors: List<AIModel>,
        generationNumber: Int,
        modelsDir: File,
        mutationSigma: Float
    ): List<AIModel> {
        val clonesPerSurvivor = (POPULATION_SIZE - SURVIVORS_PER_GENERATION) / survivors.size
        val newModels = mutableListOf<AIModel>()

        for ((survivorIndex, survivor) in survivors.withIndex()) {
            for (cloneIndex in 1..clonesPerSurvivor) {
                val cloneId = UUID.randomUUID().toString()
                val clonePath = File(modelsDir, "$cloneId.bin").absolutePath

                val sourcePath = File(survivor.tflitePath)
                if (sourcePath.exists()) {
                    val destPath = File(clonePath)
                    weightMutator.cloneAndMutate(sourcePath.absolutePath, destPath.absolutePath, mutationSigma)
                }

                val cloneName = "${survivor.name} Clone $cloneIndex"
                val clone = repository.createModel(
                    name = cloneName,
                    generationNumber = generationNumber,
                    parentId = survivor.id,
                    tflitePath = clonePath,
                    cloneIndex = cloneIndex
                )
                repository.insertModel(clone)
                newModels.add(clone)
            }
        }

        while (survivors.size + newModels.size < POPULATION_SIZE) {
            val survivor = survivors[newModels.size % survivors.size]
            val cloneId = UUID.randomUUID().toString()
            val clonePath = File(modelsDir, "$cloneId.bin").absolutePath

            val sourcePath = File(survivor.tflitePath)
            if (sourcePath.exists()) {
                weightMutator.cloneAndMutate(sourcePath.absolutePath, clonePath, mutationSigma)
            }

            val cloneName = "${survivor.name} Clone ${newModels.size + 1}"
            val clone = repository.createModel(
                name = cloneName,
                generationNumber = generationNumber,
                parentId = survivor.id,
                tflitePath = clonePath,
                cloneIndex = newModels.size + 1
            )
            repository.insertModel(clone)
            newModels.add(clone)
        }

        return newModels
    }

    suspend fun initializePopulation(
        generationNumber: Int,
        modelsDir: File
    ): List<AIModel> {
        modelsDir.mkdirs()
        val models = mutableListOf<AIModel>()

        for (i in 1..POPULATION_SIZE) {
            val modelId = UUID.randomUUID().toString()
            val modelPath = File(modelsDir, "$modelId.bin").absolutePath

            val cnn = CNNModel()
            cnn.buildSimpleCNN()
            cnn.saveModel(modelPath)

            val model = repository.createModel(
                name = "Model $i Gen $generationNumber",
                generationNumber = generationNumber,
                parentId = null,
                tflitePath = modelPath
            )
            repository.insertModel(model)
            models.add(model)
        }

        return models
    }
}
