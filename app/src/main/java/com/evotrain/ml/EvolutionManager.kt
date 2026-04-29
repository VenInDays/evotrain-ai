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
        // Simple accuracy-based selection (TeacherBot scoring is done in TrainingEngine)
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
        val newModels = mutableListOf<AIModel>()
        val decayedSigma = (mutationSigma * 0.9f).coerceAtLeast(0.001f)

        // Weighted cloning: winner gets 6 clones, runner-up gets 2
        val clonesDistribution = if (survivors.size >= 2) {
            mapOf(survivors[0] to 6, survivors[1] to 2)
        } else if (survivors.size == 1) {
            mapOf(survivors[0] to 8)
        } else {
            emptyMap()
        }

        for ((survivor, numClones) in clonesDistribution) {
            for (cloneIndex in 1..numClones) {
                val cloneId = UUID.randomUUID().toString()
                val clonePath = File(modelsDir, "$cloneId.bin").absolutePath

                val sourcePath = File(survivor.tflitePath)
                if (sourcePath.exists()) {
                    weightMutator.cloneAndMutate(sourcePath.absolutePath, clonePath, decayedSigma)
                }

                val baseName = survivor.name.replace(Regex(" Clone \\d+$"), "")
                val cloneName = "$baseName Clone $cloneIndex"
                val clone = repository.createModel(
                    name = cloneName,
                    generationNumber = generationNumber,
                    parentId = survivor.id,
                    tflitePath = clonePath,
                    cloneIndex = cloneIndex,
                    mutationSigma = decayedSigma
                )
                repository.insertModel(clone)
                repository.updateParentIdName(clone.id, survivor.name)
                repository.updateMutationSigma(clone.id, decayedSigma)
                newModels.add(clone)
            }
        }

        // Fill remaining if needed (shouldn't normally happen with 6+2+2=10)
        while (survivors.size + newModels.size < POPULATION_SIZE) {
            val survivor = survivors[newModels.size % survivors.size]
            val cloneId = UUID.randomUUID().toString()
            val clonePath = File(modelsDir, "$cloneId.bin").absolutePath
            if (File(survivor.tflitePath).exists()) {
                weightMutator.cloneAndMutate(survivor.tflitePath, clonePath, decayedSigma)
            }
            val cloneName = "${survivor.name} Clone ${newModels.size + 1}"
            val clone = repository.createModel(
                name = cloneName,
                generationNumber = generationNumber,
                parentId = survivor.id,
                tflitePath = clonePath,
                cloneIndex = newModels.size + 1,
                mutationSigma = decayedSigma
            )
            repository.insertModel(clone)
            repository.updateParentIdName(clone.id, survivor.name)
            repository.updateMutationSigma(clone.id, decayedSigma)
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
