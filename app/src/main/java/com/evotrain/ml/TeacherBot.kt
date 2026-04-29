package com.evotrain.ml

import java.util.Random
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ModelScore(
    val modelId: String,
    val score: Float,
    val accuracy: Float,
    val precision: Float,
    val recall: Float,
    val f1Score: Float,
    val tp: Int, val tn: Int, val fp: Int, val fn: Int
)

class TeacherBot @Inject constructor() {

    private val random = Random()

    fun evaluate(
        model: CNNModel,
        validationInputs: List<FloatArray>,
        validationLabels: List<Int>
    ): Float {
        if (validationInputs.isEmpty()) return 0f
        val (accuracy, _) = model.evaluate(validationInputs, validationLabels)
        return accuracy
    }

    fun evaluateAll(
        models: List<CNNModel>,
        validationInputs: List<FloatArray>,
        validationLabels: List<Int>
    ): List<Float> {
        return models.map { model ->
            evaluate(model, validationInputs, validationLabels)
        }
    }

    fun scoreModel(
        modelId: String,
        model: CNNModel,
        validationInputs: List<FloatArray>,
        validationLabels: List<Int>
    ): ModelScore {
        if (validationInputs.isEmpty()) {
            return ModelScore(modelId, 0f, 0f, 0f, 0f, 0f, 0, 0, 0, 0)
        }
        val (accuracy, _) = model.evaluate(validationInputs, validationLabels)
        val cm = model.computeConfusionMatrix(validationInputs, validationLabels)
        val score = 0.6f * accuracy + 0.2f * cm.precision + 0.2f * cm.recall
        return ModelScore(modelId, score, accuracy, cm.precision, cm.recall, cm.f1Score,
            cm.tp, cm.tn, cm.fp, cm.fn)
    }

    fun createStratifiedSplit(
        inputs: List<FloatArray>,
        labels: List<Int>,
        validationRatio: Float = 0.2f,
        seed: Long = System.currentTimeMillis()
    ): Pair<Pair<List<FloatArray>, List<Int>>, Pair<List<FloatArray>, List<Int>>> {
        val seededRandom = Random(seed)
        val classIndices = labels.indices.groupBy { labels[it] }

        val trainInputs = mutableListOf<FloatArray>()
        val trainLabels = mutableListOf<Int>()
        val valInputs = mutableListOf<FloatArray>()
        val valLabels = mutableListOf<Int>()

        for ((_, indices) in classIndices) {
            val shuffled = indices.shuffled(seededRandom)
            val splitPoint = (shuffled.size * validationRatio).toInt()

            for (i in shuffled.indices) {
                if (i < splitPoint) {
                    valInputs.add(inputs[shuffled[i]])
                    valLabels.add(labels[shuffled[i]])
                } else {
                    trainInputs.add(inputs[shuffled[i]])
                    trainLabels.add(labels[shuffled[i]])
                }
            }
        }

        return Pair(
            Pair(trainInputs, trainLabels),
            Pair(valInputs, valLabels)
        )
    }
}
