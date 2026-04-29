package com.evotrain.ui.inference

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.evotrain.R
import com.evotrain.data.model.InferenceResultEntity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class InferenceFragment : Fragment() {

    private val viewModel: InferenceViewModel by activityViewModels()

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setImageUri(it)
            val path = getPathFromUri(it)
            if (path != null) {
                viewModel.runInference(path)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_inference, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btnPickImage)?.setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(requireContext().cacheDir, "inference_temp.jpg")
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun showTestAiSheet() {
        val sheet = TestAiBottomSheet()
        sheet.show(childFragmentManager, "TestAiBottomSheet")
    }
}

@AndroidEntryPoint
class TestAiBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: InferenceViewModel by activityViewModels()

    private var currentImagePath: String? = null
    private var cameraImageUri: Uri? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setImageUri(it)
            currentImagePath = getPathFromUri(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraImageUri != null) {
            viewModel.setImageUri(cameraImageUri!!)
            currentImagePath = getPathFromUri(cameraImageUri!!)
        }
    }

    // UI references
    private lateinit var previewImage: ImageView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipScrollView: HorizontalScrollView
    private lateinit var testAllSwitch: SwitchMaterial
    private lateinit var runTestButton: MaterialButton
    private lateinit var resultsContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var historyToggleButton: MaterialButton
    private lateinit var deleteHistoryButton: MaterialButton
    private lateinit var historyActionsRow: LinearLayout
    private lateinit var noModelMessage: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return buildLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadAliveModels()
        observeState()
    }

    private fun buildLayout(): View {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
                resources.displayMetrics
            ).toInt()
        }

        val scrollContainer = ScrollView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }

        // === Title Bar ===
        val titleBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleText = TextView(requireContext()).apply {
            text = getString(R.string.test_ai_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_light))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val saveButton = ImageView(requireContext()).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_save)
            setContentDescription(getString(R.string.save_result))
            val size = dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                currentImagePath?.let { path -> viewModel.saveResult(path) }
                Snackbar.make(requireView(), getString(R.string.result_saved), Snackbar.LENGTH_SHORT).show()
            }
        }

        val historyIcon = ImageView(requireContext()).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_recent_history)
            setContentDescription(getString(R.string.history))
            val size = dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { viewModel.toggleHistory() }
        }

        titleBar.addView(titleText)
        titleBar.addView(saveButton)
        titleBar.addView(historyIcon)
        root.addView(titleBar)

        // === No Model Trained Message ===
        noModelMessage = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(32), dp(24), dp(32), dp(24))
            visibility = View.GONE
        }

        val noModelIcon = TextView(requireContext()).apply {
            text = getString(R.string.no_model_emoji)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val noModelText = TextView(requireContext()).apply {
            text = getString(R.string.train_model_first)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(12), 0, 0)
        }

        noModelMessage.addView(noModelIcon)
        noModelMessage.addView(noModelText)
        root.addView(noModelMessage)

        // === Section A: Image Input ===
        val sectionALabel = TextView(requireContext()).apply {
            text = getString(R.string.image_input)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(sectionALabel)

        val imageButtonsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val pickGalleryButton = MaterialButton(requireContext()).apply {
            text = getString(R.string.pick_from_gallery)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = dp(8) }
            setOnClickListener { galleryLauncher.launch(arrayOf("image/*")) }
        }

        val takePhotoButton = MaterialButton(requireContext()).apply {
            text = getString(R.string.take_photo)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(8) }
            setOnClickListener { launchCamera() }
        }

        imageButtonsRow.addView(pickGalleryButton)
        imageButtonsRow.addView(takePhotoButton)
        root.addView(imageButtonsRow)

        // Preview image
        val previewContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }

        previewImage = ImageView(requireContext()).apply {
            val size = dp(160)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(ContextCompat.getColor(requireContext(), R.color.outline_light))
            }
            background = bg
            clipToOutline = true
            setImageResource(android.R.drawable.ic_menu_gallery)
            setContentDescription(getString(R.string.selected_image))
        }
        previewContainer.addView(previewImage)
        root.addView(previewContainer)

        // === Section B: Model Selection ===
        val sectionBLabel = TextView(requireContext()).apply {
            text = getString(R.string.model_selection)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(sectionBLabel)

        chipScrollView = HorizontalScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        chipGroup = ChipGroup(requireContext()).apply {
            isSingleSelection = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        chipScrollView.addView(chipGroup)
        root.addView(chipScrollView)

        // Test All Models switch
        val switchRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(4), 0, dp(4))
        }

        val switchLabel = TextView(requireContext()).apply {
            text = getString(R.string.test_all_models)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_light))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        testAllSwitch = SwitchMaterial(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, isChecked ->
                viewModel.setTestAllModels(isChecked)
                chipGroup.visibility = if (isChecked) View.GONE else View.VISIBLE
                chipScrollView.visibility = if (isChecked) View.GONE else View.VISIBLE
            }
        }

        switchRow.addView(switchLabel)
        switchRow.addView(testAllSwitch)
        root.addView(switchRow)

        // === Section C: Run Test Button ===
        runTestButton = MaterialButton(requireContext()).apply {
            text = getString(R.string.run_test)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { topMargin = dp(12) }
            setOnClickListener { runTest() }
        }
        root.addView(runTestButton)

        // === Section D: Results Display ===
        resultsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        root.addView(resultsContainer)

        // === History Container ===
        historyContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        root.addView(historyContainer)

        // === History Toggle/Delete Row ===
        historyActionsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }

        historyToggleButton = MaterialButton(requireContext()).apply {
            text = getString(R.string.history)
            cornerRadius = dp(8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            visibility = View.GONE
            setOnClickListener { viewModel.toggleHistory() }
        }

        deleteHistoryButton = MaterialButton(requireContext()).apply {
            text = getString(R.string.clear_history)
            cornerRadius = dp(8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
            setOnClickListener { viewModel.deleteHistory() }
        }

        historyActionsRow.addView(historyToggleButton)
        historyActionsRow.addView(deleteHistoryButton)
        root.addView(historyActionsRow)

        scrollContainer.addView(root)
        return scrollContainer
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: InferenceUiState) {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
                resources.displayMetrics
            ).toInt()
        }

        // Update no model message
        noModelMessage.visibility = if (state.hasTrainedModel) View.GONE else View.VISIBLE

        // Update preview image
        state.imageUri?.let {
            previewImage.load(it) {
                crossfade(true)
            }
        }

        // Update chips
        chipGroup.removeAllViews()
        val bestChip = Chip(requireContext()).apply {
            text = getString(R.string.best_model)
            isCheckable = true
            isChecked = state.selectedModelId == null && !state.testAllModels
            setOnClickListener { viewModel.setSelectedModel(null) }
        }
        chipGroup.addView(bestChip)

        for (model in state.aliveModels) {
            val chip = Chip(requireContext()).apply {
                text = model.name
                isCheckable = true
                isChecked = state.selectedModelId == model.id
                setOnClickListener { viewModel.setSelectedModel(model.id) }
            }
            chipGroup.addView(chip)
        }

        // Update test all switch
        testAllSwitch.isChecked = state.testAllModels
        if (state.testAllModels) {
            chipScrollView.visibility = View.GONE
        } else {
            chipScrollView.visibility = View.VISIBLE
        }

        // Update run button
        if (state.isProcessing) {
            runTestButton.text = getString(R.string.running_test)
            runTestButton.isEnabled = false
        } else {
            runTestButton.text = getString(R.string.run_test)
            runTestButton.isEnabled = state.hasTrainedModel
        }

        // Update results
        resultsContainer.removeAllViews()
        if (state.errorMessage != null) {
            resultsContainer.visibility = View.VISIBLE
            val errorText = TextView(requireContext()).apply {
                text = state.errorMessage
                setTextColor(ContextCompat.getColor(requireContext(), R.color.rose))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(12), 0, dp(4))
            }
            resultsContainer.addView(errorText)
        } else if (state.result != null) {
            resultsContainer.visibility = View.VISIBLE
            renderSingleResult(state.result, state)
        } else if (state.multiResult != null) {
            resultsContainer.visibility = View.VISIBLE
            renderMultiResult(state.multiResult)
        } else {
            resultsContainer.visibility = View.GONE
        }

        // Update history
        if (state.showHistory) {
            historyContainer.visibility = View.VISIBLE
            renderHistory(state.history)
            historyToggleButton.visibility = View.VISIBLE
            deleteHistoryButton.visibility = View.VISIBLE
            historyActionsRow.visibility = View.VISIBLE
        } else {
            historyContainer.visibility = View.GONE
            historyContainer.removeAllViews()
            if (state.history.isNotEmpty()) {
                historyToggleButton.visibility = View.VISIBLE
                historyActionsRow.visibility = View.VISIBLE
                deleteHistoryButton.visibility = View.GONE
            } else {
                historyActionsRow.visibility = View.GONE
            }
        }
    }

    private fun renderSingleResult(result: InferenceResult, state: InferenceUiState) {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
                resources.displayMetrics
            ).toInt()
        }

        val isLike = result.predictedClass == 1
        val likeConf = result.confidence.getOrElse(1) { 0f }
        val notLikeConf = result.confidence.getOrElse(0) { 0f }
        val mainConf = if (isLike) likeConf else notLikeConf

        // Big result label
        val resultLabel = TextView(requireContext()).apply {
            text = if (isLike) getString(R.string.result_like) else getString(R.string.result_not_like)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            setTextColor(
                if (isLike) ContextCompat.getColor(requireContext(), R.color.soft_emerald)
                else ContextCompat.getColor(requireContext(), R.color.rose)
            )
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(16), 0, dp(4))
        }
        resultsContainer.addView(resultLabel)

        // Confidence percentage
        val confLabel = TextView(requireContext()).apply {
            text = getString(R.string.confidence_percent_format, (mainConf * 100).toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_light))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dp(12))
        }
        resultsContainer.addView(confLabel)

        // Like confidence bar
        val likeLabel = TextView(requireContext()).apply {
            text = getString(R.string.like_confidence_format, (likeConf * 100).toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.soft_emerald))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        resultsContainer.addView(likeLabel)

        val likeBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = (likeConf * 100).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply { bottomMargin = dp(8) }
        }
        resultsContainer.addView(likeBar)

        // Not Like confidence bar
        val notLikeLabel = TextView(requireContext()).apply {
            text = getString(R.string.not_like_confidence_format, (notLikeConf * 100).toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.rose))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        resultsContainer.addView(notLikeLabel)

        val notLikeBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = (notLikeConf * 100).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply { bottomMargin = dp(8) }
        }
        resultsContainer.addView(notLikeBar)

        // Model name + generation
        val modelName = result.modelName.ifEmpty {
            state.aliveModels.firstOrNull()?.name ?: "Best Model"
        }
        val modelGen = if (result.modelGeneration > 0) result.modelGeneration
            else state.aliveModels.firstOrNull()?.generationNumber ?: 0

        val modelInfo = TextView(requireContext()).apply {
            text = getString(R.string.model_generation_format, modelName, modelGen)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        resultsContainer.addView(modelInfo)

        // Response time
        if (result.inferenceTimeMs > 0) {
            val timeInfo = TextView(requireContext()).apply {
                text = getString(R.string.response_time_ms, result.inferenceTimeMs)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            resultsContainer.addView(timeInfo)
        }
    }

    private fun renderMultiResult(multiResult: com.evotrain.ml.MultiInferenceResult) {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
                resources.displayMetrics
            ).toInt()
        }

        // Majority vote header
        val isLike = multiResult.majorityVote == 1
        val voteText = TextView(requireContext()).apply {
            text = getString(
                R.string.majority_vote_format,
                multiResult.majorityCount,
                multiResult.totalModels,
                if (isLike) getString(R.string.result_like_label) else getString(R.string.result_not_like_label)
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(
                if (isLike) ContextCompat.getColor(requireContext(), R.color.soft_emerald)
                else ContextCompat.getColor(requireContext(), R.color.rose)
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(12), 0, dp(4))
        }
        resultsContainer.addView(voteText)

        // Consensus confidence
        val consensusText = TextView(requireContext()).apply {
            text = getString(
                R.string.consensus_confidence_format,
                (multiResult.consensusConfidence * 100).toInt()
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dp(12))
        }
        resultsContainer.addView(consensusText)

        // Find highest confidence result
        val maxConfResult = multiResult.results.maxByOrNull {
            it.confidence.getOrElse(it.predictedClass) { 0f }
        }

        // Result list header
        val listHeader = TextView(requireContext()).apply {
            text = getString(R.string.individual_results)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_light))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(8), 0, dp(4))
        }
        resultsContainer.addView(listHeader)

        // Individual results
        for (single in multiResult.results) {
            val isHighest = single == maxConfResult
            val singleIsLike = single.predictedClass == 1
            val conf = single.confidence.getOrElse(single.predictedClass) { 0f }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(8), dp(6), dp(8), dp(6))

                if (isHighest) {
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(8).toFloat()
                        setColor(0x1A10B981.toInt()) // emerald with alpha
                    }
                    background = bg
                }
            }

            val modelNameText = TextView(requireContext()).apply {
                text = single.modelName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(
                    if (isHighest) ContextCompat.getColor(requireContext(), R.color.soft_emerald)
                    else ContextCompat.getColor(requireContext(), R.color.on_surface_light)
                )
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f
                )
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val arrowText = TextView(requireContext()).apply {
                text = " → "
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val resultText = TextView(requireContext()).apply {
                text = if (singleIsLike) getString(R.string.result_like_label) else getString(R.string.result_not_like_label)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(
                    if (singleIsLike) ContextCompat.getColor(requireContext(), R.color.soft_emerald)
                    else ContextCompat.getColor(requireContext(), R.color.rose)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val confText = TextView(requireContext()).apply {
                text = " ${(conf * 100).toInt()}%"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val timeText = TextView(requireContext()).apply {
                text = " ${single.inferenceTimeMs}ms"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(modelNameText)
            row.addView(arrowText)
            row.addView(resultText)
            row.addView(confText)
            row.addView(timeText)
            resultsContainer.addView(row)
        }
    }

    private fun renderHistory(history: List<InferenceResultEntity>) {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
                resources.displayMetrics
            ).toInt()
        }

        historyContainer.removeAllViews()

        if (history.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = getString(R.string.no_history)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant_light))
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
            }
            historyContainer.addView(emptyText)
            return
        }

        val headerText = TextView(requireContext()).apply {
            text = getString(R.string.recent_results)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_light))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        }
        historyContainer.addView(headerText)

        for (item in history) {
            val isLike = item.predictedClass == 1
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(4))

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(ContextCompat.getColor(requireContext(), R.color.surface_light))
                    setStroke(1, ContextCompat.getColor(requireContext(), R.color.card_stroke_light))
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            val resultIcon = TextView(requireContext()).apply {
                text = if (isLike) "✓" else "✗"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(
                    if (isLike) ContextCompat.getColor(requireContext(), R.color.soft_emerald)
                    else ContextCompat.getColor(requireContext(), R.color.rose)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            }

            val infoText = TextView(requireContext()).apply {
                text = "${item.modelName} — ${(item.confidence * 100).toInt()}% — " +
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(item.timestamp))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_light))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            row.addView(resultIcon)
            row.addView(infoText)
            historyContainer.addView(row)
        }
    }

    private fun runTest() {
        val path = currentImagePath
        if (path == null) {
            Snackbar.make(requireView(), getString(R.string.select_image_first), Snackbar.LENGTH_SHORT).show()
            return
        }

        val state = viewModel.uiState.value
        if (!state.hasTrainedModel) {
            Snackbar.make(requireView(), getString(R.string.train_model_first), Snackbar.LENGTH_SHORT).show()
            return
        }

        if (state.testAllModels) {
            viewModel.runInferenceOnAllModels(path)
        } else {
            viewModel.runInferenceOnSelectedModel(path)
        }
    }

    private fun launchCamera() {
        val tempFile = File(requireContext().cacheDir, "camera_inference_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            tempFile
        )
        cameraImageUri?.let { cameraLauncher.launch(it) }
    }

    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(requireContext().cacheDir, "inference_temp.jpg")
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
