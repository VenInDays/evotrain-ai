package com.evotrain.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "models")
data class AIModel(
    @PrimaryKey
    val id: String,
    val name: String,
    val generationNumber: Int,
    val parentId: String?,
    val accuracyScore: Float,
    val lossScore: Float,
    val epochsTrained: Int,
    val createdAt: Long,
    val isAlive: Boolean,
    val tflitePath: String,
    val cloneIndex: Int = 0,
    val precision: Float = 0f,
    val recall: Float = 0f,
    val f1Score: Float = 0f,
    val truePositives: Int = 0,
    val trueNegatives: Int = 0,
    val falsePositives: Int = 0,
    val falseNegatives: Int = 0,
    val stopReason: String? = null,
    val currentLearningRate: Float = 0.001f,
    val mutationSigma: Float = 0.01f,
    val parentIdName: String? = null
)
