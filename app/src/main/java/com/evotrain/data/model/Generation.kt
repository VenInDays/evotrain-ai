package com.evotrain.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "generations")
data class Generation(
    @PrimaryKey
    val id: String,
    val generationNumber: Int,
    val startedAt: Long,
    val completedAt: Long?,
    val bestAccuracy: Float,
    val averageAccuracy: Float,
    val survivorIds: String
)
