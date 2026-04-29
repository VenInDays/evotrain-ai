package com.evotrain.ui.train

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.evotrain.R
import com.evotrain.ml.TrainingConfig
import com.evotrain.ml.TrainingPhase
import com.evotrain.ui.inference.TestAiBottomSheet
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class TrainFragment : Fragment() {

    private val viewModel: TrainViewModel by viewModels()
    private lateinit var previewAdapter: ImagePreviewAdapter

    private val zipPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleZipImport(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_train, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Test AI FAB
        view.findViewById<FloatingActionButton>(R.id.fabTestAi)?.setOnClickListener {
            val sheet = TestAiBottomSheet()
            sheet.show(childFragmentManager, "TestAiBottomSheet")
        }

        previewAdapter = ImagePreviewAdapter()
        view.findViewById<RecyclerView>(R.id.previewRecyclerView).apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = previewAdapter
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectZip).setOnClickListener {
            zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartTraining).setOnClickListener {
            val config = TrainingConfig()
            viewModel.startTraining(config)
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStopTraining).setOnClickListener {
            viewModel.stopTraining()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: TrainUiState) {
        val view = view ?: return

        view.findViewById<ProgressBar>(R.id.unzipProgress).visibility =
            if (state.isUnzipping) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.unzipStatus).visibility =
            if (state.isUnzipping) View.VISIBLE else View.GONE

        if (state.isUnzipping) {
            view.findViewById<TextView>(R.id.unzipStatus).text =
                "Extracted ${state.unzipProgress} files..."
        }

        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.datasetSummaryCard).visibility =
            if (state.datasetInfo.isReady) View.VISIBLE else View.GONE

        if (state.datasetInfo.isReady) {
            view.findViewById<TextView>(R.id.likeCount).text = state.datasetInfo.likeCount.toString()
            view.findViewById<TextView>(R.id.nonlikeCount).text = state.datasetInfo.nonlikeCount.toString()

            view.findViewById<TextView>(R.id.warningText).visibility =
                if (!state.datasetInfo.isValid) View.VISIBLE else View.GONE

            val samples = state.datasetInfo.likeSamples + state.datasetInfo.nonlikeSamples
            previewAdapter.submitList(samples)
        }

        val startBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartTraining)
        val stopBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStopTraining)

        when (state.phase) {
            TrainingPhase.IDLE, TrainingPhase.COMPLETED -> {
                startBtn.visibility = if (state.datasetInfo.isReady) View.VISIBLE else View.GONE
                stopBtn.visibility = View.GONE
                startBtn.isEnabled = state.datasetInfo.isValid
            }
            else -> {
                startBtn.visibility = View.GONE
                stopBtn.visibility = View.VISIBLE
            }
        }

        state.errorMessage?.let {
            Snackbar.make(requireView(), it, Snackbar.LENGTH_LONG).show()
        }
    }
}

class ImagePreviewAdapter : RecyclerView.Adapter<ImagePreviewAdapter.ViewHolder>() {

    private var items = listOf<String>()

    fun submitList(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(path: String) {
            val imageView = itemView.findViewById<android.widget.ImageView>(R.id.previewImage)
            val file = File(path)
            if (file.exists()) {
                imageView.load(file)
            }
        }
    }
}
