package com.evotrain.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class ImagePreprocessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun preprocessImage(
        imagePath: String,
        targetSize: Int = 224
    ): FloatArray = withContext(Dispatchers.Default) {
        val bitmap = loadAndResizeBitmap(imagePath, targetSize)
        bitmapToFloatArray(bitmap, targetSize).also {
            bitmap.recycle()
        }
    }

    suspend fun preprocessBatch(
        imagePaths: List<String>,
        targetSize: Int = 224
    ): List<FloatArray> = withContext(Dispatchers.Default) {
        imagePaths.map { path ->
            val bitmap = loadAndResizeBitmap(path, targetSize)
            bitmapToFloatArray(bitmap, targetSize).also {
                bitmap.recycle()
            }
        }
    }

    private fun loadAndResizeBitmap(path: String, size: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, size, size)
        options.inJustDecodeBounds = false

        val bitmap = BitmapFactory.decodeFile(path, options)
            ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(bitmap, size, size, true)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun bitmapToFloatArray(bitmap: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val floatArray = FloatArray(size * size * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            floatArray[i * 3] = r
            floatArray[i * 3 + 1] = g
            floatArray[i * 3 + 2] = b
        }
        return floatArray
    }

    fun getDatasetImages(datasetDir: File): Pair<List<String>, List<String>> {
        val likeDir = File(datasetDir, "like")
        val nonlikeDir = File(datasetDir, "nonlike")

        val likeImages = likeDir.listFiles()
            ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }
            ?.map { it.absolutePath }
            ?: emptyList()

        val nonlikeImages = nonlikeDir.listFiles()
            ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }
            ?.map { it.absolutePath }
            ?: emptyList()

        return Pair(likeImages, nonlikeImages)
    }

    suspend fun extractZipToDataset(
        zipInputStream: java.io.InputStream,
        datasetDir: File,
        onProgress: (Int) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val likeDir = File(datasetDir, "like").apply { mkdirs() }
        val nonlikeDir = File(datasetDir, "nonlike").apply { mkdirs() }

        var likeCount = 0
        var nonlikeCount = 0
        var totalFiles = 0

        java.util.zip.ZipInputStream(zipInputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.lowercase()
                    val isLike = name.startsWith("like/") || name.startsWith("like\\")
                    val isNonlike = name.startsWith("nonlike/") || name.startsWith("nonlike\\")

                    if ((isLike || isNonlike) &&
                        name.substringAfterLast(".") in listOf("jpg", "jpeg", "png", "webp")
                    ) {
                        val fileName = entry.name.substringAfterLast("/")
                        val targetDir = if (isLike) likeDir else nonlikeDir
                        val targetFile = File(targetDir, fileName)

                        FileOutputStream(targetFile).use { fos ->
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }

                        if (isLike) likeCount++ else nonlikeCount++
                        totalFiles++
                        onProgress(totalFiles)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        Pair(likeCount, nonlikeCount)
    }
}
