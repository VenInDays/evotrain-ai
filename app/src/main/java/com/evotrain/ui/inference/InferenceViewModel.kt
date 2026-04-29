package com.evotrain.ui.inference

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evotrain.data.model.AIModel
import com.evotrain.data.model.InferenceResultEntity
import com.evotrain.data.repository.ModelRepository
import com.evotrain.ml.MultiInferenceResult
import com.evotrain.ml.TrainingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class InferenceResult(
    val predictedClass: Int = -1,
    val confidence: FloatArray = floatArrayOf(),
    val modelName: String = "",
    val modelGeneration: Int = 0,
    val inferenceTimeMs: Long = 0L
)

data class InferenceUiState(
    val imageUri: Uri? = null,
    val isProcessing: Boolean = false,
    val result: InferenceResult? = null,
    val errorMessage: String? = null,
    val aliveModels: List<AIModel> = emptyList(),
    val selectedModelId: String? = null,
    val testAllModels: Boolean = false,
    val multiResult: MultiInferenceResult? = null,
    val history: List<InferenceResultEntity> = emptyList(),
    val showHistory: Boolean = false,
    val hasTrainedModel: Boolean = false
)

@HiltViewModel
class InferenceViewModel @Inject constructor(
    private val trainingEngine: TrainingEngine,
    private val repository: ModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InferenceUiState())
    val uiState: StateFlow<InferenceUiState> = _uiState.asStateFlow()

    init {
        loadAliveModels()
    }

    fun setImageUri(uri: Uri) {
        _uiState.update { it.copy(
            imageUri = uri,
            result = null,
            multiResult = null,
            errorMessage = null
        ) }
    }

    fun loadAliveModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val models = repository.getAliveModelsList()
                _uiState.update { it.copy(
                    aliveModels = models,
                    hasTrainedModel = models.isNotEmpty()
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    aliveModels = emptyList(),
                    hasTrainedModel = false
                )}
            }
        }
    }

    fun setSelectedModel(modelId: String?) {
        _uiState.update { it.copy(
            selectedModelId = modelId,
            testAllModels = false
        )}
    }

    fun setTestAllModels(enabled: Boolean) {
        _uiState.update { it.copy(
            testAllModels = enabled,
            selectedModelId = if (enabled) null else it.selectedModelId
        )}
    }

    fun runInference(imagePath: String) {
        _uiState.update { it.copy(isProcessing = true, result = null, multiResult = null, errorMessage = null) }
        try {
            val result = trainingEngine.runInference(imagePath, 224)
            if (result != null) {
                _uiState.update { it.copy(
                    isProcessing = false,
                    result = InferenceResult(
                        predictedClass = result.first,
                        confidence = result.second
                    )
                )}
            } else {
                _uiState.update { it.copy(
                    isProcessing = false,
                    errorMessage = "No trained model available"
                )}
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(
                isProcessing = false,
                errorMessage = e.message
            )}
        }
    }

    fun runInferenceOnSelectedModel(imagePath: String) {
        val state = _uiState.value
        if (state.selectedModelId == null) {
            // No specific model selected, use best model
            runInference(imagePath)
            return
        }

        _uiState.update { it.copy(isProcessing = true, result = null, multiResult = null, errorMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val multiResult = trainingEngine.runMultiInference(
                    imagePath, 224, listOf(state.selectedModelId)
                )
                if (multiResult != null && multiResult.results.isNotEmpty()) {
                    val single = multiResult.results.first()
                    _uiState.update { it.copy(
                        isProcessing = false,
                        result = InferenceResult(
                            predictedClass = single.predictedClass,
                            confidence = single.confidence,
                            modelName = single.modelName,
                            modelGeneration = single.generation,
                            inferenceTimeMs = single.inferenceTimeMs
                        ),
                        multiResult = null
                    )}
                } else {
                    _uiState.update { it.copy(
                        isProcessing = false,
                        errorMessage = "Selected model not available"
                    )}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isProcessing = false,
                    errorMessage = e.message
                )}
            }
        }
    }

    fun runInferenceOnAllModels(imagePath: String) {
        _uiState.update { it.copy(isProcessing = true, result = null, multiResult = null, errorMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val multiResult = trainingEngine.runMultiInference(imagePath, 224)
                if (multiResult != null && multiResult.results.isNotEmpty()) {
                    _uiState.update { it.copy(
                        isProcessing = false,
                        multiResult = multiResult,
                        result = null
                    )}
                } else {
                    _uiState.update { it.copy(
                        isProcessing = false,
                        errorMessage = "No trained models available"
                    )}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isProcessing = false,
                    errorMessage = e.message
                )}
            }
        }
    }

    fun saveResult(imagePath: String) {
        val state = _uiState.value
        val result = state.result
        val multiResult = state.multiResult

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (result != null) {
                    val likeConf = result.confidence.getOrElse(1) { 0f }
                    val notLikeConf = result.confidence.getOrElse(0) { 0f }
                    val entity = InferenceResultEntity(
                        id = UUID.randomUUID().toString(),
                        imagePath = imagePath,
                        predictedClass = result.predictedClass,
                        confidence = if (result.predictedClass == 1) likeConf else notLikeConf,
                        modelName = result.modelName.ifEmpty {
                            state.aliveModels.firstOrNull()?.name ?: "Best Model"
                        },
                        modelGeneration = if (result.modelGeneration > 0) result.modelGeneration
                            else state.aliveModels.firstOrNull()?.generationNumber ?: 0,
                        timestamp = System.currentTimeMillis(),
                        likeConfidence = likeConf,
                        notLikeConfidence = notLikeConf
                    )
                    repository.saveInferenceResult(entity)
                } else if (multiResult != null) {
                    // Save the majority vote result
                    val likeConf = multiResult.consensusConfidence
                    val entity = InferenceResultEntity(
                        id = UUID.randomUUID().toString(),
                        imagePath = imagePath,
                        predictedClass = multiResult.majorityVote,
                        confidence = multiResult.consensusConfidence,
                        modelName = "Ensemble (${multiResult.totalModels} models)",
                        modelGeneration = 0,
                        timestamp = System.currentTimeMillis(),
                        likeConfidence = if (multiResult.majorityVote == 1) likeConf else 1f - likeConf,
                        notLikeConfidence = if (multiResult.majorityVote == 0) likeConf else 1f - likeConf
                    )
                    repository.saveInferenceResult(entity)
                }
            } catch (_: Exception) {
                // Silently fail save
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = repository.getRecentInferenceResults(10)
                _uiState.update { it.copy(history = results) }
            } catch (_: Exception) {
                _uiState.update { it.copy(history = emptyList()) }
            }
        }
    }

    fun toggleHistory() {
        val show = !_uiState.value.showHistory
        _uiState.update { it.copy(showHistory = show) }
        if (show) {
            loadHistory()
        }
    }

    fun deleteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteAllInferenceResults()
                _uiState.update { it.copy(history = emptyList(), showHistory = false) }
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(
            result = null,
            multiResult = null,
            errorMessage = null,
            isProcessing = false
        )}
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
