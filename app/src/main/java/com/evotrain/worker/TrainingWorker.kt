package com.evotrain.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evotrain.ml.TrainingConfig
import com.evotrain.ml.TrainingEngine

class TrainingWorker(
    appContext: Context,
    workerParams: WorkerParameters
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

        // Training is handled by TrainingEngine directly via the ForegroundService
        // This worker serves as a backup mechanism for long-running training
        return Result.success()
    }
}
