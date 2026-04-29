package com.evotrain.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evotrain.data.model.AIModel
import com.evotrain.data.model.Generation
import com.evotrain.data.repository.ModelRepository
import com.evotrain.ml.ModelTrainingProgress
import com.evotrain.ml.TrainingEngine
import com.evotrain.ml.TrainingLogger
import com.evotrain.ml.TrainingPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonitorUiState(
    val generationNumber: Int = 0,
    val phase: TrainingPhase = TrainingPhase.IDLE,
    val bestAccuracy: Float = 0f,
    val averageAccuracy: Float = 0f,
    val targetAccuracy: Float = 0.9f,
    val eta: String = "",
    val aliveModels: List<AIModel> = emptyList(),
    val generations: List<Generation> = emptyList(),
    val modelProgress: Map<String, ModelTrainingProgress> = emptyMap(),
    val isRunning: Boolean = false
)

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val trainingEngine: TrainingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState

    private val _logEntries = MutableStateFlow<List<TrainingLogger.LogEntry>>(emptyList())
    val logEntries: StateFlow<List<TrainingLogger.LogEntry>> = _logEntries

    init {
        viewModelScope.launch {
            combine(
                trainingEngine.trainingProgress,
                repository.getAliveModels().flowOn(Dispatchers.IO).conflate(),
                repository.getAllGenerations().flowOn(Dispatchers.IO).conflate()
            ) { progress, models, generations ->
                _uiState.update { it.copy(
                    generationNumber = progress.generationNumber,
                    phase = progress.phase,
                    bestAccuracy = progress.bestAccuracy,
                    averageAccuracy = progress.averageAccuracy,
                    aliveModels = models,
                    generations = generations,
                    modelProgress = progress.modelProgress,
                    isRunning = progress.isRunning
                )}
            }.flowOn(Dispatchers.IO).collect {}
        }
    }

    init {
        viewModelScope.launch {
            TrainingLogger.logs.collect { entry ->
                _logEntries.update { (it + entry).takeLast(50) }
            }
        }
    }
}
