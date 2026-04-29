package com.evotrain.ui.inference

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.evotrain.ml.TrainingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class InferenceResult(
    val predictedClass: Int = -1,
    val confidence: FloatArray = floatArrayOf(),
    val modelName: String = "",
    val modelGeneration: Int = 0
)

data class InferenceUiState(
    val imageUri: Uri? = null,
    val isProcessing: Boolean = false,
    val result: InferenceResult? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class InferenceViewModel @Inject constructor(
    private val trainingEngine: TrainingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(InferenceUiState())
    val uiState: StateFlow<InferenceUiState> = _uiState.asStateFlow()

    fun setImageUri(uri: Uri) {
        _uiState.update { it.copy(imageUri = uri, result = null, errorMessage = null) }
    }

    fun runInference(imagePath: String) {
        _uiState.update { it.copy(isProcessing = true) }
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
}
