package com.evotrain.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evotrain.ml.TrainingConfig
import com.evotrain.ml.TrainingEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TrainingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val trainingEngine: TrainingEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val targetAccuracy = inputData.getFloat("targetAccuracy", 0.9f)
        val maxGenerations = inputData.getInt("maxGenerations", 50)
        val epochs = inputData.getInt("epochs", 5)
        val learningRate = inputData.getFloat("learningRate", 0.001f)
        val imageSize = inputData.getInt("imageSize", 224)
        val batchSize = inputData.getInt("batchSize", 16)
        val mutationSigma = inputData.getFloat("mutationSigma", 0.01f)

        val config = TrainingConfig(
            targetAccuracy = targetAccuracy,
            maxGenerations = maxGenerations,
            epochsPerGeneration = epochs,
            learningRate = learningRate,
            imageSize = imageSize,
            batchSize = batchSize,
            mutationSigma = mutationSigma
        )

        trainingEngine.startTraining(config)
        return Result.success()
    }
}
