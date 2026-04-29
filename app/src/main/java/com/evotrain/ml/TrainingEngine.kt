package com.evotrain.ml

import android.content.Context
import com.evotrain.data.model.AIModel
import com.evotrain.data.model.Generation
import com.evotrain.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

object TrainingLogger {
    private val _logs = MutableSharedFlow<LogEntry>(extraBufferCapacity = 50)
    val logs: SharedFlow<LogEntry> = _logs.asSharedFlow()

    data class LogEntry(val level: String, val message: String, val timestamp: Long = System.currentTimeMillis())

    fun info(msg: String) { _logs.tryEmit(LogEntry("INFO", msg)) }
    fun success(msg: String) { _logs.tryEmit(LogEntry("SUCCESS", msg)) }
    fun warning(msg: String) { _logs.tryEmit(LogEntry("WARNING", msg)) }
    fun error(msg: String) { _logs.tryEmit(LogEntry("ERROR", msg)) }
}

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
    val isTraining: Boolean = false,
    val currentLr: Float = 0.001f,
    val stopReason: String? = null
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

data class SingleInferenceResult(
    val modelId: String,
    val modelName: String,
    val generation: Int,
    val predictedClass: Int,
    val confidence: FloatArray,
    val inferenceTimeMs: Long
)

data class MultiInferenceResult(
    val results: List<SingleInferenceResult>,
    val majorityVote: Int,
    val majorityCount: Int,
    val totalModels: Int,
    val consensusConfidence: Float
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
                TrainingLogger.error("Training failed: ${e.message}")
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

    /**
     * Create a stratified split on indices so we can map back to original paths.
     * Returns (trainIndices, valIndices).
     */
    private fun createStratifiedIndexSplit(
        labels: List<Int>,
        validationRatio: Float = 0.2f,
        seed: Long = System.currentTimeMillis()
    ): Pair<List<Int>, List<Int>> {
        val seededRandom = Random(seed)
        val classIndices = labels.indices.groupBy { labels[it] }

        val trainIndices = mutableListOf<Int>()
        val valIndices = mutableListOf<Int>()

        for ((_, indices) in classIndices) {
            val shuffled = indices.shuffled(seededRandom)
            val splitPoint = (shuffled.size * validationRatio).toInt()

            for (i in shuffled.indices) {
                if (i < splitPoint) {
                    valIndices.add(shuffled[i])
                } else {
                    trainIndices.add(shuffled[i])
                }
            }
        }

        return Pair(trainIndices, valIndices)
    }

    private suspend fun runEvolutionaryLoop(config: TrainingConfig) {
        val datasetDir = File(context.filesDir, "dataset")
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val (likePaths, nonlikePaths) = imagePreprocessor.getDatasetImages(datasetDir)

        if (likePaths.isEmpty() || nonlikePaths.isEmpty()) {
            TrainingLogger.error("No dataset images found")
            return
        }

        TrainingLogger.info("Starting evolutionary training with ${likePaths.size} like + ${nonlikePaths.size} nonlike images")

        val allPaths = likePaths + nonlikePaths
        val allLabels = List(likePaths.size) { 1 } + List(nonlikePaths.size) { 0 }

        val preprocessedInputs = imagePreprocessor.preprocessBatch(allPaths, config.imageSize)

        var currentGeneration = repository.getLatestGeneration()?.generationNumber?.plus(1) ?: 1
        var currentMutationSigma = config.mutationSigma

        _trainingProgress.value = _trainingProgress.value.copy(
            isRunning = true,
            generationNumber = currentGeneration
        )

        while (currentGeneration <= config.maxGenerations && isActive()) {
            TrainingLogger.info("=== Generation $currentGeneration ===")

            _trainingProgress.value = _trainingProgress.value.copy(
                generationNumber = currentGeneration,
                phase = TrainingPhase.EVALUATING
            )

            val generation = repository.createGeneration(currentGeneration)
            repository.insertGeneration(generation)

            var aliveModels = repository.getAliveModelsList()
            if (aliveModels.isEmpty()) {
                TrainingLogger.info("Initializing new population")
                aliveModels = evolutionManager.initializePopulation(currentGeneration, modelsDir)
            }

            // Re-shuffle validation split each generation with a new seed
            val (trainIndices, valIndices) = createStratifiedIndexSplit(
                allLabels, 0.2f, seed = System.currentTimeMillis()
            )

            val trainPaths = trainIndices.map { allPaths[it] }
            val trainLabels = trainIndices.map { allLabels[it] }
            val valInputs = valIndices.map { preprocessedInputs[it] }
            val valLabels = valIndices.map { allLabels[it] }

            // Apply augmentation to training data (re-load from paths with augmentation)
            val augmentedTrainInputs = imagePreprocessor.preprocessAndAugmentBatch(
                trainPaths,
                config.imageSize,
                isTraining = true
            )

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
                    isTraining = true,
                    currentLr = config.learningRate,
                    stopReason = null
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

                            // Use trainBatchAdvanced instead of trainBatch
                            val stopReason = cnn.trainBatchAdvanced(
                                augmentedTrainInputs,
                                trainLabels,
                                config.epochsPerGeneration,
                                isTraining = true
                            ) { epoch, loss, acc, lr ->
                                modelProgressMap[model.id] = ModelTrainingProgress(
                                    modelId = model.id,
                                    modelName = model.name,
                                    currentEpoch = epoch + 1,
                                    totalEpochs = config.epochsPerGeneration,
                                    currentLoss = loss,
                                    currentAccuracy = acc,
                                    isTraining = true,
                                    currentLr = lr,
                                    stopReason = null
                                )
                                _trainingProgress.value = _trainingProgress.value.copy(
                                    modelProgress = modelProgressMap.toMap()
                                )
                            }

                            // Store stop reason
                            modelProgressMap[model.id] = modelProgressMap[model.id]!!.copy(
                                stopReason = stopReason,
                                isTraining = false
                            )

                            cnn.saveModel(model.tflitePath)

                            // Score model using TeacherBot
                            val modelScore = teacherBot.scoreModel(
                                model.id, cnn, valInputs, valLabels
                            )

                            // Save all stats including confusion matrix
                            repository.updateModelStats(model.id, modelScore.accuracy, modelProgressMap[model.id]!!.currentLoss, config.epochsPerGeneration)
                            repository.updateConfusionStats(
                                model.id,
                                modelScore.precision,
                                modelScore.recall,
                                modelScore.f1Score,
                                modelScore.tp,
                                modelScore.tn,
                                modelScore.fp,
                                modelScore.fn
                            )
                            repository.updateStopReason(model.id, stopReason)
                            repository.updateLearningRate(model.id, modelProgressMap[model.id]!!.currentLr)

                            TrainingLogger.info("Model ${model.name}: acc=${String.format("%.3f", modelScore.accuracy)}, " +
                                "prec=${String.format("%.3f", modelScore.precision)}, " +
                                "rec=${String.format("%.3f", modelScore.recall)}, " +
                                "stop=$stopReason")
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

            TrainingLogger.success("Generation $currentGeneration complete: best=${String.format("%.3f", bestAccuracy)}, avg=${String.format("%.3f", avgAccuracy)}")

            _trainingProgress.value = _trainingProgress.value.copy(
                phase = TrainingPhase.SELECTING,
                bestAccuracy = bestAccuracy,
                averageAccuracy = avgAccuracy
            )

            val survivors = evolutionManager.selectSurvivors(trainedModels)
            evolutionManager.eliminateNonSurvivors(survivors.map { it.id })

            TrainingLogger.info("Survivors: ${survivors.map { it.name }}")

            _trainingProgress.value = _trainingProgress.value.copy(
                phase = TrainingPhase.REPRODUCING
            )

            currentMutationSigma = (currentMutationSigma * 0.9f).coerceAtLeast(0.001f)
            evolutionManager.reproduce(survivors, currentGeneration + 1, modelsDir, currentMutationSigma)

            repository.updateGeneration(generation.copy(
                completedAt = System.currentTimeMillis(),
                bestAccuracy = bestAccuracy,
                averageAccuracy = avgAccuracy,
                survivorIds = survivors.joinToString(",") { it.id }
            ))

            if (bestAccuracy >= config.targetAccuracy) {
                TrainingLogger.success("Target accuracy ${config.targetAccuracy} reached!")
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

    suspend fun runMultiInference(imagePath: String, imageSize: Int, modelIds: List<String>? = null): MultiInferenceResult? {
        val models = if (modelIds != null) {
            modelIds.mapNotNull { repository.getModelById(it) }
        } else {
            repository.getAliveModelsList()
        }
        if (models.isEmpty()) return null

        val input = imagePreprocessor.preprocessImage(imagePath, imageSize) ?: return null
        val results = mutableListOf<SingleInferenceResult>()

        for (model in models) {
            val modelFile = File(model.tflitePath)
            if (!modelFile.exists()) continue
            val cnn = CNNModel(inputSize = imageSize)
            cnn.buildSimpleCNN()
            try { cnn.loadModel(model.tflitePath) } catch (_: Exception) { continue }

            val startTime = System.currentTimeMillis()
            val (predicted, confidence) = cnn.predict(input)
            val elapsed = System.currentTimeMillis() - startTime
            results.add(SingleInferenceResult(model.id, model.name, model.generationNumber, predicted, confidence, elapsed))
        }

        if (results.isEmpty()) return null

        val likeCount = results.count { it.predictedClass == 1 }
        val notLikeCount = results.count { it.predictedClass == 0 }
        val majorityVote = if (likeCount >= notLikeCount) 1 else 0
        val majorityCount = maxOf(likeCount, notLikeCount)

        val consensusConfidence = if (majorityVote == 1) {
            results.filter { it.predictedClass == 1 }.map { it.confidence.getOrElse(1) { 0f } }.average().toFloat()
        } else {
            results.filter { it.predictedClass == 0 }.map { it.confidence.getOrElse(0) { 0f } }.average().toFloat()
        }

        return MultiInferenceResult(results, majorityVote, majorityCount, results.size, consensusConfidence)
    }
}
