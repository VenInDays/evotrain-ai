package com.evotrain.ml

import java.io.*
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class CNNModel(
    val inputSize: Int = 96,
    val numClasses: Int = 2,
    val learningRate: Float = 0.001f
) {
    private val random = Random()

    data class ConvLayer(
        val inputChannels: Int,
        val outputChannels: Int,
        val kernelSize: Int,
        val stride: Int = 1,
        val padding: Int = 0,
        var weights: FloatArray,
        var biases: FloatArray,
        var gradWeights: FloatArray,
        var gradBiases: FloatArray,
        var lastInput: FloatArray? = null,
        var lastOutput: FloatArray? = null
    )

    data class DenseLayer(
        val inputSize: Int,
        val outputSize: Int,
        var weights: FloatArray,
        var biases: FloatArray,
        var gradWeights: FloatArray,
        var gradBiases: FloatArray,
        var lastInput: FloatArray? = null,
        var lastOutput: FloatArray? = null,
        val activation: String = "relu"
    )

    private var convLayers = mutableListOf<ConvLayer>()
    private var denseLayers = mutableListOf<DenseLayer>()
    private var poolSizes = mutableListOf<Int>()

    var weights: FloatArray = floatArrayOf()
        get() {
            val allWeights = mutableListOf<Float>()
            convLayers.forEach { layer ->
                allWeights.addAll(layer.weights.toList())
                allWeights.addAll(layer.biases.toList())
            }
            denseLayers.forEach { layer ->
                allWeights.addAll(layer.weights.toList())
                allWeights.addAll(layer.biases.toList())
            }
            return allWeights.toFloatArray()
        }
        set(value) {
            field = value
            var offset = 0
            convLayers.forEach { layer ->
                val wSize = layer.weights.size
                val bSize = layer.biases.size
                System.arraycopy(value, offset, layer.weights, 0, wSize)
                offset += wSize
                System.arraycopy(value, offset, layer.biases, 0, bSize)
                offset += bSize
            }
            denseLayers.forEach { layer ->
                val wSize = layer.weights.size
                val bSize = layer.biases.size
                System.arraycopy(value, offset, layer.weights, 0, wSize)
                offset += wSize
                System.arraycopy(value, offset, layer.biases, 0, bSize)
                offset += bSize
            }
        }

    fun buildSimpleCNN() {
        convLayers.clear()
        denseLayers.clear()
        poolSizes.clear()

        val conv1 = createConvLayer(3, 16, 3, 1, 1)
        convLayers.add(conv1)
        poolSizes.add(2)

        val conv2 = createConvLayer(16, 32, 3, 1, 1)
        convLayers.add(conv2)
        poolSizes.add(2)

        val afterConvSize = inputSize / (poolSizes.fold(1) { acc, _ -> acc * 2 })
        val flattenedSize = 32 * afterConvSize * afterConvSize

        denseLayers.add(createDenseLayer(flattenedSize, 128, "relu"))
        denseLayers.add(createDenseLayer(128, numClasses, "softmax"))
    }

    private fun createConvLayer(inChannels: Int, outChannels: Int, kernelSize: Int, stride: Int, padding: Int): ConvLayer {
        val fanIn = inChannels * kernelSize * kernelSize
        val std = (2.0f / fanIn).let { sqrt -> kotlin.math.sqrt(sqrt.toDouble()).toFloat() }
        val wSize = outChannels * inChannels * kernelSize * kernelSize

        return ConvLayer(
            inputChannels = inChannels,
            outputChannels = outChannels,
            kernelSize = kernelSize,
            stride = stride,
            padding = padding,
            weights = FloatArray(wSize) { (random.nextGaussian() * std).toFloat() },
            biases = FloatArray(outChannels) { 0f },
            gradWeights = FloatArray(wSize) { 0f },
            gradBiases = FloatArray(outChannels) { 0f }
        )
    }

    private fun createDenseLayer(inSize: Int, outSize: Int, activation: String): DenseLayer {
        val std = (2.0f / inSize).let { sqrt -> kotlin.math.sqrt(sqrt.toDouble()).toFloat() }
        val wSize = inSize * outSize

        return DenseLayer(
            inputSize = inSize,
            outputSize = outSize,
            weights = FloatArray(wSize) { (random.nextGaussian() * std).toFloat() },
            biases = FloatArray(outSize) { 0f },
            gradWeights = FloatArray(wSize) { 0f },
            gradBiases = FloatArray(outSize) { 0f },
            activation = activation
        )
    }

    fun forward(input: FloatArray): FloatArray {
        var current = input

        for (i in convLayers.indices) {
            current = convForward(current, convLayers[i])
            current = relu(current)
            current = maxPool2d(current, poolSizes[i],
                calcOutputSize(inputSize, convLayers[i], poolSizes[i]))
        }

        current = flatten(current)

        for (layer in denseLayers) {
            layer.lastInput = current
            current = denseForward(current, layer)
            layer.lastOutput = current
            current = when (layer.activation) {
                "relu" -> relu(current)
                "softmax" -> softmax(current)
                else -> current
            }
        }

        return current
    }

    private fun convForward(input: FloatArray, layer: ConvLayer): FloatArray {
        layer.lastInput = input
        val h = inputSize
        val w = inputSize
        val outH = (h + 2 * layer.padding - layer.kernelSize) / layer.stride + 1
        val outW = (w + 2 * layer.padding - layer.kernelSize) / layer.stride + 1
        val output = FloatArray(layer.outputChannels * outH * outW)

        for (oc in 0 until layer.outputChannels) {
            for (oh in 0 until outH) {
                for (ow in 0 until outW) {
                    var sum = layer.biases[oc]
                    for (ic in 0 until layer.inputChannels) {
                        for (kh in 0 until layer.kernelSize) {
                            for (kw in 0 until layer.kernelSize) {
                                val ih = oh * layer.stride - layer.padding + kh
                                val iw = ow * layer.stride - layer.padding + kw
                                if (ih in 0 until h && iw in 0 until w) {
                                    val inputIdx = ic * h * w + ih * w + iw
                                    val weightIdx = oc * layer.inputChannels * layer.kernelSize * layer.kernelSize +
                                            ic * layer.kernelSize * layer.kernelSize + kh * layer.kernelSize + kw
                                    sum += input[inputIdx] * layer.weights[weightIdx]
                                }
                            }
                        }
                    }
                    output[oc * outH * outW + oh * outW + ow] = sum
                }
            }
        }

        layer.lastOutput = output
        return output
    }

    private fun maxPool2d(input: FloatArray, poolSize: Int, spatialSize: Int): FloatArray {
        val channels = input.size / (spatialSize * spatialSize)
        val outSize = spatialSize / poolSize
        val output = FloatArray(channels * outSize * outSize)

        for (c in 0 until channels) {
            for (oh in 0 until outSize) {
                for (ow in 0 until outSize) {
                    var maxVal = Float.NEGATIVE_INFINITY
                    for (ph in 0 until poolSize) {
                        for (pw in 0 until poolSize) {
                            val ih = oh * poolSize + ph
                            val iw = ow * poolSize + pw
                            val idx = c * spatialSize * spatialSize + ih * spatialSize + iw
                            if (idx < input.size) maxVal = max(maxVal, input[idx])
                        }
                    }
                    output[c * outSize * outSize + oh * outSize + ow] = maxVal
                }
            }
        }
        return output
    }

    private fun denseForward(input: FloatArray, layer: DenseLayer): FloatArray {
        val output = FloatArray(layer.outputSize)
        for (o in 0 until layer.outputSize) {
            var sum = layer.biases[o]
            for (i in 0 until layer.inputSize) {
                sum += input[i] * layer.weights[o * layer.inputSize + i]
            }
            output[o] = sum
        }
        return output
    }

    private fun flatten(input: FloatArray): FloatArray = input

    private fun relu(input: FloatArray): FloatArray = FloatArray(input.size) { i -> max(0f, input[i]) }

    private fun softmax(input: FloatArray): FloatArray {
        val maxVal = input.maxOrNull() ?: 0f
        val exps = FloatArray(input.size) { i -> exp((input[i] - maxVal).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(input.size) { i -> exps[i] / sum }
    }

    private fun reluDerivative(input: FloatArray): FloatArray = FloatArray(input.size) { i -> if (input[i] > 0) 1f else 0f }

    private fun calcOutputSize(inputSize: Int, conv: ConvLayer, poolSize: Int): Int {
        val convOut = (inputSize + 2 * conv.padding - conv.kernelSize) / conv.stride + 1
        return convOut / poolSize
    }

    fun trainBatch(
        inputs: List<FloatArray>,
        labels: List<Int>,
        epochs: Int = 1,
        onEpochComplete: (epoch: Int, loss: Float, accuracy: Float) -> Unit
    ) {
        for (epoch in 0 until epochs) {
            var totalLoss = 0f
            var correct = 0

            for (i in inputs.indices) {
                val output = forward(inputs[i])
                val predicted = output.indices.maxByOrNull { output[it] } ?: 0
                if (predicted == labels[i]) correct++

                val target = FloatArray(numClasses) { 0f }
                target[labels[i]] = 1f
                totalLoss += crossEntropyLoss(output, target)

                val gradOutput = FloatArray(numClasses) { j -> output[j] - target[j] }
                backpropDense(gradOutput)
            }

            val avgLoss = totalLoss / inputs.size
            val accuracy = correct.toFloat() / inputs.size
            onEpochComplete(epoch, avgLoss, accuracy)
        }
    }

    private fun backpropDense(gradOutput: FloatArray) {
        var gradient = gradOutput

        for (i in denseLayers.size - 1 downTo 0) {
            val layer = denseLayers[i]
            val input = layer.lastInput ?: return

            if (layer.activation == "relu") {
                gradient = FloatArray(gradient.size) { j -> gradient[j] * reluDerivative(layer.lastOutput!!)[j] }
            }

            for (o in 0 until layer.outputSize) {
                for (inp in 0 until layer.inputSize) {
                    layer.gradWeights[o * layer.inputSize + inp] += gradient[o] * input[inp]
                }
                layer.gradBiases[o] += gradient[o]
            }

            val newGradient = FloatArray(layer.inputSize)
            for (inp in 0 until layer.inputSize) {
                var sum = 0f
                for (o in 0 until layer.outputSize) {
                    sum += gradient[o] * layer.weights[o * layer.inputSize + inp]
                }
                newGradient[inp] = sum
            }
            gradient = newGradient

            val lr = learningRate
            for (j in layer.weights.indices) {
                layer.weights[j] -= lr * layer.gradWeights[j]
                layer.gradWeights[j] = 0f
            }
            for (j in layer.biases.indices) {
                layer.biases[j] -= lr * layer.gradBiases[j]
                layer.gradBiases[j] = 0f
            }
        }
    }

    private fun crossEntropyLoss(output: FloatArray, target: FloatArray): Float {
        var loss = 0f
        for (i in output.indices) {
            val clipped = max(1e-7f, min(1f - 1e-7f, output[i]))
            loss -= target[i] * ln(clipped.toDouble()).toFloat()
        }
        return loss
    }

    fun evaluate(inputs: List<FloatArray>, labels: List<Int>): Pair<Float, Float> {
        var correct = 0
        var totalLoss = 0f

        for (i in inputs.indices) {
            val output = forward(inputs[i])
            val predicted = output.indices.maxByOrNull { output[it] } ?: 0
            if (predicted == labels[i]) correct++

            val target = FloatArray(numClasses) { 0f }
            target[labels[i]] = 1f
            totalLoss += crossEntropyLoss(output, target)
        }

        val accuracy = correct.toFloat() / inputs.size
        val avgLoss = totalLoss / inputs.size
        return Pair(accuracy, avgLoss)
    }

    fun predict(input: FloatArray): Pair<Int, FloatArray> {
        val output = forward(input)
        val predicted = output.indices.maxByOrNull { output[it] } ?: 0
        return Pair(predicted, output)
    }

    fun saveModel(path: String) {
        val allWeights = weights
        val buffer = java.nio.ByteBuffer.allocate(allWeights.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        allWeights.forEach { buffer.putFloat(it) }
        File(path).writeBytes(buffer.array())
    }

    fun loadModel(path: String) {
        val bytes = File(path).readBytes()
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val loaded = FloatArray(bytes.size / 4) { buffer.float }
        weights = loaded
    }

    fun getWeightsSnapshot(): FloatArray = weights.copyOf()
}
