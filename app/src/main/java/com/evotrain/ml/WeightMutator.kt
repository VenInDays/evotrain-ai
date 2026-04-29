package com.evotrain.ml

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import javax.inject.Inject
import kotlin.math.abs

class WeightMutator @Inject constructor() {

    private val random = Random()

    fun mutateWeights(weights: FloatArray, sigma: Float): FloatArray {
        return FloatArray(weights.size) { i ->
            weights[i] + (random.nextGaussian() * sigma).toFloat()
        }
    }

    fun loadWeights(path: String): FloatArray {
        return File(path).readBytes().let { bytes ->
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(bytes.size / 4) { buffer.float }
        }
    }

    fun saveWeights(weights: FloatArray, path: String) {
        val buffer = ByteBuffer.allocate(weights.size * 4).order(ByteOrder.LITTLE_ENDIAN)
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
