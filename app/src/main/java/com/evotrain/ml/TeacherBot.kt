package com.evotrain.ml

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class TeacherBot {

    private val random = java.util.Random()

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

    fun createStratifiedSplit(
        inputs: List<FloatArray>,
        labels: List<Int>,
        validationRatio: Float = 0.2f
    ): Pair<Pair<List<FloatArray>, List<Int>>, Pair<List<FloatArray>, List<Int>>> {
        val classIndices = labels.indices.groupBy { labels[it] }

        val trainInputs = mutableListOf<FloatArray>()
        val trainLabels = mutableListOf<Int>()
        val valInputs = mutableListOf<FloatArray>()
        val valLabels = mutableListOf<Int>()

        for ((_, indices) in classIndices) {
            val shuffled = indices.shuffled(random)
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
