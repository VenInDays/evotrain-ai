package com.evotrain.ui.train

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evotrain.data.model.AIModel
import com.evotrain.data.repository.ModelRepository
import com.evotrain.ml.ImagePreprocessor
import com.evotrain.ml.TrainingConfig
import com.evotrain.ml.TrainingEngine
import com.evotrain.ml.TrainingPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DatasetInfo(
    val likeCount: Int = 0,
    val nonlikeCount: Int = 0,
    val likeSamples: List<String> = emptyList(),
    val nonlikeSamples: List<String> = emptyList(),
    val isValid: Boolean = false,
    val isReady: Boolean = false
)

data class TrainUiState(
    val datasetInfo: DatasetInfo = DatasetInfo(),
    val isUnzipping: Boolean = false,
    val unzipProgress: Int = 0,
    val isTraining: Boolean = false,
    val phase: TrainingPhase = TrainingPhase.IDLE,
    val errorMessage: String? = null
)

@HiltViewModel
class TrainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ModelRepository,
    private val trainingEngine: TrainingEngine,
    private val imagePreprocessor: ImagePreprocessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainUiState())
    val uiState: StateFlow<TrainUiState> = _uiState

    init {
        viewModelScope.launch {
            trainingEngine.trainingProgress.collect { progress ->
                _uiState.update { it.copy(
                    isTraining = progress.isRunning,
                    phase = progress.phase
                )}
            }
        }
        checkExistingDataset()
    }

    private fun checkExistingDataset() {
        val datasetDir = File(context.filesDir, "dataset")
        val (likePaths, nonlikePaths) = imagePreprocessor.getDatasetImages(datasetDir)
        if (likePaths.isNotEmpty() || nonlikePaths.isNotEmpty()) {
            _uiState.update { it.copy(
                datasetInfo = DatasetInfo(
                    likeCount = likePaths.size,
                    nonlikeCount = nonlikePaths.size,
                    likeSamples = likePaths.shuffled().take(6),
                    nonlikeSamples = nonlikePaths.shuffled().take(6),
                    isValid = likePaths.size >= 10 && nonlikePaths.size >= 10,
                    isReady = true
                )
            )}
        }
    }

    fun handleZipImport(uri: Uri) {
        _uiState.update { it.copy(isUnzipping = true, unzipProgress = 0) }
        viewModelScope.launch {
            try {
                val datasetDir = File(context.filesDir, "dataset").apply {
                    deleteRecursively()
                    mkdirs()
                }

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val (likeCount, nonlikeCount) = imagePreprocessor.extractZipToDataset(inputStream, datasetDir) { count ->
                    _uiState.update { it.copy(unzipProgress = count) }
                }

                val (likePaths, nonlikePaths) = imagePreprocessor.getDatasetImages(datasetDir)

                _uiState.update { it.copy(
                    isUnzipping = false,
                    datasetInfo = DatasetInfo(
                        likeCount = likeCount,
                        nonlikeCount = nonlikeCount,
                        likeSamples = likePaths.shuffled().take(6),
                        nonlikeSamples = nonlikePaths.shuffled().take(6),
                        isValid = likeCount >= 10 && nonlikeCount >= 10,
                        isReady = true
                    )
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isUnzipping = false,
                    errorMessage = e.message
                )}
            }
        }
    }

    fun startTraining(config: TrainingConfig) {
        trainingEngine.startTraining(config)
    }

    fun stopTraining() {
        trainingEngine.stopTraining()
    }
}
