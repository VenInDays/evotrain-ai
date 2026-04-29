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
    val cloneIndex: Int = 0
)
