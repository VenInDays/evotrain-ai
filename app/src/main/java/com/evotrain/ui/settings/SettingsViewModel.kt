package com.evotrain.ui.settings

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evotrain.data.repository.ModelRepository
import com.evotrain.ml.TrainingConfig
import com.evotrain.ml.TrainingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val targetAccuracy: Float = 90f,
    val epochsPerGeneration: Int = 5,
    val maxGenerations: Int = 50,
    val learningRate: String = "0.001",
    val imageSize: String = "224x224",
    val useGpu: Boolean = true,
    val batchSize: Int = 16,
    val mutationSigma: Float = 0.01f,
    val exportSuccess: Boolean = false,
    val clearSuccess: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ModelRepository,
    private val trainingEngine: TrainingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateTargetAccuracy(value: Float) = _uiState.update { it.copy(targetAccuracy = value) }
    fun updateEpochs(value: Int) = _uiState.update { it.copy(epochsPerGeneration = value) }
    fun updateMaxGenerations(value: Int) = _uiState.update { it.copy(maxGenerations = value) }
    fun updateLearningRate(value: String) = _uiState.update { it.copy(learningRate = value) }
    fun updateImageSize(value: String) = _uiState.update { it.copy(imageSize = value) }
    fun updateUseGpu(value: Boolean) = _uiState.update { it.copy(useGpu = value) }
    fun updateBatchSize(value: Int) = _uiState.update { it.copy(batchSize = value) }
    fun updateMutationSigma(value: Float) = _uiState.update { it.copy(mutationSigma = value) }

    fun getTrainingConfig(): TrainingConfig {
        val imgSize = _uiState.value.imageSize.substringBefore("x").toIntOrNull() ?: 224
        return TrainingConfig(
            targetAccuracy = _uiState.value.targetAccuracy / 100f,
            maxGenerations = _uiState.value.maxGenerations,
            epochsPerGeneration = _uiState.value.epochsPerGeneration,
            learningRate = _uiState.value.learningRate.toFloatOrNull() ?: 0.001f,
            imageSize = imgSize,
            batchSize = _uiState.value.batchSize,
            mutationSigma = _uiState.value.mutationSigma,
            useGpu = _uiState.value.useGpu
        )
    }

    fun exportBestModel() {
        viewModelScope.launch {
            val bestModel = repository.getBestAliveModel()
            if (bestModel != null) {
                val sourceFile = File(bestModel.tflitePath)
                if (sourceFile.exists()) {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    )
                    val destFile = File(downloadsDir, "evotrain_best_model_${System.currentTimeMillis()}.bin")
                    sourceFile.copyTo(destFile, overwrite = true)
                    _uiState.update { it.copy(exportSuccess = true) }
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            trainingEngine.stopTraining()
            repository.deleteAllModels()
            repository.deleteAllGenerations()

            val datasetDir = File(context.filesDir, "dataset")
            datasetDir.deleteRecursively()

            val modelsDir = File(context.filesDir, "models")
            modelsDir.deleteRecursively()

            _uiState.update { it.copy(clearSuccess = true) }
        }
    }

    fun resetFlags() {
        _uiState.update { it.copy(exportSuccess = false, clearSuccess = false) }
    }
}
