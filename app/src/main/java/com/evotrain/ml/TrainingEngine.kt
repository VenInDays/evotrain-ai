package com.evotrain.ml

import android.content.Context
import com.evotrain.data.model.AIModel
import com.evotrain.data.model.Generation
import com.evotrain.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class TrainingProgress(
    val generationNumber: Int,
    val phase: TrainingPhase,
    val modelProgress: Map<String, ModelTrainingProgress>,
    val bestAccuracy: Float,
    val averageAccuracy: Float,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val winningModelId: String? = null
)

data class ModelTrainingProgress(
    val modelId: String,
    val modelName: String,
    val currentEpoch: Int,
    val totalEpochs: Int,
    val currentLoss: Float,
    val currentAccuracy: Float,
    val isTraining: Boolean = false
)

enum class TrainingPhase {
    IDLE, EVALUATING, SELECTING, REPRODUCING, TRAINING, COMPLETED
}

data class TrainingConfig(
    val targetAccuracy: Float = 0.9f,
    val maxGenerations: Int = 50,
    val epochsPerGeneration: Int = 5,
    val learningRate: Float = 0.001f,
    val imageSize: Int = 224,
    val batchSize: Int = 16,
    val mutationSigma: Float = 0.01f,
    val useGpu: Boolean = true
)

@Singleton
class TrainingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ModelRepository,
    private val evolutionManager: EvolutionManager,
    private val teacherBot: TeacherBot,
    private val imagePreprocessor: ImagePreprocessor,
    private val weightMutator: WeightMutator
) {
    private val _trainingProgress = MutableStateFlow(TrainingProgress(
        generationNumber = 0,
        phase = TrainingPhase.IDLE,
        modelProgress = emptyMap(),
        bestAccuracy = 0f,
        averageAccuracy = 0f
    ))
    val trainingProgress: StateFlow<TrainingProgress> = _trainingProgress

    private var trainingJob: Job? = null
    private var isTraining = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startTraining(config: TrainingConfig) {
        if (isTraining) return
        isTraining = true

        trainingJob = scope.launch {
            try {
                runEvolutionaryLoop(config)
            } catch (e: CancellationException) {
                _trainingProgress.value = _trainingProgress.value.copy(
                    phase = TrainingPhase.IDLE,
                    isRunning = false
                )
            } catch (e: Exception) {
                _trainingProgress.value = _trainingProgress.value.copy(
                    phase = TrainingPhase.IDLE,
                    isRunning = false
                )
            } finally {
                isTraining = false
            }
        }
    }

    fun stopTraining() {
        trainingJob?.cancel()
        isTraining = false
        _trainingProgress.value = _trainingProgress.value.copy(
            phase = TrainingPhase.IDLE,
            isRunning = false
        )
    }

    private suspend fun runEvolutionaryLoop(config: TrainingConfig) {
        val datasetDir = File(context.filesDir, "dataset")
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val (likePaths, nonlikePaths) = imagePreprocessor.getDatasetImages(datasetDir)

        if (likePaths.isEmpty() || nonlikePaths.isEmpty()) return

        val allPaths = likePaths + nonlikePaths
        val allLabels = List(likePaths.size) { 1 } + List(nonlikePaths.size) { 0 }

        val preprocessedInputs = imagePreprocessor.preprocessBatch(allPaths, config.imageSize)
        val (trainData, valData) = teacherBot.createStratifiedSplit(
            preprocessedInputs, allLabels, 0.2f
        )
        val (trainInputs, trainLabels) = trainData
        val (valInputs, valLabels) = valData

        var currentGeneration = repository.getLatestGeneration()?.generationNumber?.plus(1) ?: 1

        _trainingProgress.value = _trainingProgress.value.copy(
            isRunning = true,
            generationNumber = currentGeneration
        )

        while (currentGeneration <= config.maxGenerations && isActive()) {
            _trainingProgress.value = _trainingProgress.value.copy(
                generationNumber = currentGeneration,
                phase = TrainingPhase.EVALUATING
            )

            val generation = repository.createGeneration(currentGeneration)
            repository.insertGeneration(generation)

            var aliveModels = repository.getAliveModelsList()
            if (aliveModels.isEmpty()) {
                aliveModels = evolutionManager.initializePopulation(currentGeneration, modelsDir)
            }

            _trainingProgress.value = _trainingProgress.value.copy(
                phase = TrainingPhase.TRAINING
            )

            val cnnModels = mutableMapOf<String, CNNModel>()
            val modelProgressMap = mutableMapOf<String, ModelTrainingProgress>()

            for (model in aliveModels) {
                modelProgressMap[model.id] = ModelTrainingProgress(
                    modelId = model.id,
                    modelName = model.name,
                    currentEpoch = 0,
                    totalEpochs = config.epochsPerGeneration,
                    currentLoss = 1f,
                    currentAccuracy = 0f,
                    isTraining = true
                )
            }
            _trainingProgress.value = _trainingProgress.value.copy(
                modelProgress = modelProgressMap
            )

            val parallelism = minOf(4, Runtime.getRuntime().availableProcessors())
            aliveModels.chunked(parallelism).forEach { chunk ->
                coroutineScope {
                    chunk.map { model ->
                        async(Dispatchers.Default) {
                        val cnn = CNNModel(
                            inputSize = config.imageSize,
                            learningRate = config.learningRate
                        )
                        cnn.buildSimpleCNN()

                        val modelFile = File(model.tflitePath)
                        if (modelFile.exists()) {
                            try {
                                cnn.loadModel(model.tflitePath)
                            } catch (e: Exception) {
                                cnn.buildSimpleCNN()
                            }
                        }

                        cnnModels[model.id] = cnn

                        cnn.trainBatch(trainInputs, trainLabels, config.epochsPerGeneration) { epoch, loss, acc ->
                            modelProgressMap[model.id] = ModelTrainingProgress(
                                modelId = model.id,
                                modelName = model.name,
                                currentEpoch = epoch + 1,
                                totalEpochs = config.epochsPerGeneration,
                                currentLoss = loss,
                                currentAccuracy = acc,
                                isTraining = true
                            )
                            _trainingProgress.value = _trainingProgress.value.copy(
                                modelProgress = modelProgressMap.toMap()
                            )
                        }

                        cnn.saveModel(model.tflitePath)

                        val (accuracy, loss) = cnn.evaluate(valInputs, valLabels)
                        repository.updateModelStats(model.id, accuracy, loss, config.epochsPerGeneration)
                        }
                    }.awaitAll()
                }
            }

            val trainedModels = repository.getAliveModelsList()

            _trainingProgress.value = _trainingProgress.value.copy(
                phase = TrainingPhase.EVALUATING
            )

            val bestAccuracy = trainedModels.maxOfOrNull { it.accuracyScore } ?: 0f
            val avgAccuracy = if (trainedModels.isNotEmpty()) trainedModels.map { it.accuracyScore }.average().toFloat() else 0f

            _trainingProgress.value = _trainingProgress.value.copy(
                phase = TrainingPhase.SELECTING,
                bestAccuracy = bestAccuracy,
                averageAccuracy = avgAccuracy
            )

            val survivors = evolutionManager.selectSurvivors(trainedModels)
            evolutionManager.eliminateNonSurvivors(survivors.map { it.id })

            _trainingProgress.value = _trainingProgress.value.copy(
                phase = TrainingPhase.REPRODUCING
            )

            evolutionManager.reproduce(survivors, currentGeneration + 1, modelsDir, config.mutationSigma)

            repository.updateGeneration(generation.copy(
                completedAt = System.currentTimeMillis(),
                bestAccuracy = bestAccuracy,
                averageAccuracy = avgAccuracy,
                survivorIds = survivors.joinToString(",") { it.id }
            ))

            if (bestAccuracy >= config.targetAccuracy) {
                _trainingProgress.value = _trainingProgress.value.copy(
                    phase = TrainingPhase.COMPLETED,
                    isComplete = true,
                    isRunning = false,
                    winningModelId = survivors.firstOrNull()?.id
                )
                return
            }

            currentGeneration++
        }

        _trainingProgress.value = _trainingProgress.value.copy(
            phase = TrainingPhase.COMPLETED,
            isComplete = true,
            isRunning = false
        )
    }

    private fun isActive(): Boolean = isTraining && scope.coroutineContext[Job]?.isActive == true

    fun isInTraining(): Boolean = isTraining

    fun runInference(imagePath: String, imageSize: Int): Pair<Int, FloatArray>? {
        val bestModel = kotlinx.coroutines.runBlocking {
            repository.getBestAliveModel()
        } ?: return null

        val modelFile = File(bestModel.tflitePath)
        if (!modelFile.exists()) return null

        val cnn = CNNModel(inputSize = imageSize)
        cnn.buildSimpleCNN()
        try {
            cnn.loadModel(bestModel.tflitePath)
        } catch (e: Exception) {
            return null
        }

        val input = kotlinx.coroutines.runBlocking {
            imagePreprocessor.preprocessImage(imagePath, imageSize)
        }
        return cnn.predict(input)
    }
}
