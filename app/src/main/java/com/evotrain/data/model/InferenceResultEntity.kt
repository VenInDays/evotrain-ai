package com.evotrain.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inference_results")
data class InferenceResultEntity(
    @PrimaryKey
    val id: String,
    val imagePath: String,
    val predictedClass: Int,
    val confidence: Float,
    val modelName: String,
    val modelGeneration: Int,
    val timestamp: Long,
    val likeConfidence: Float,
    val notLikeConfidence: Float
)
