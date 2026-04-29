package com.evotrain.ml

import java.io.*
import java.util.Random
import kotlin.math.abs

class WeightMutator {

    private val random = Random()

    fun mutateWeights(weights: FloatArray, sigma: Float): FloatArray {
        return FloatArray(weights.size) { i ->
            weights[i] + (random.nextGaussian() * sigma).toFloat()
        }
    }

    fun loadWeights(path: String): FloatArray {
        return File(path).readBytes().let { bytes ->
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            FloatArray(bytes.size / 4) { buffer.float }
        }
    }

    fun saveWeights(weights: FloatArray, path: String) {
        val buffer = java.nio.ByteBuffer.allocate(weights.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        weights.forEach { buffer.putFloat(it) }
        File(path).writeBytes(buffer.array())
    }

    fun cloneAndMutate(sourcePath: String, destPath: String, sigma: Float): Boolean {
        return try {
            val weights = loadWeights(sourcePath)
            val mutated = mutateWeights(weights, sigma)
            saveWeights(mutated, destPath)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun copyFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
}
