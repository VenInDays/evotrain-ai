package com.evotrain.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.evotrain.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sliderAccuracy = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderTargetAccuracy)
        val tvAccuracy = view.findViewById<TextView>(R.id.tvTargetAccuracy)
        sliderAccuracy.addOnChangeListener { _, value, _ ->
            viewModel.updateTargetAccuracy(value)
            tvAccuracy.text = "${value.toInt()}%"
        }

        val sliderEpochs = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderEpochs)
        val tvEpochs = view.findViewById<TextView>(R.id.tvEpochs)
        sliderEpochs.addOnChangeListener { _, value, _ ->
            viewModel.updateEpochs(value.toInt())
            tvEpochs.text = value.toInt().toString()
        }

        val sliderMaxGens = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderMaxGenerations)
        val tvMaxGens = view.findViewById<TextView>(R.id.tvMaxGenerations)
        sliderMaxGens.addOnChangeListener { _, value, _ ->
            viewModel.updateMaxGenerations(value.toInt())
            tvMaxGens.text = value.toInt().toString()
        }

        val dropdownLR = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.dropdownLearningRate)
        val lrAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line,
            listOf("0.0001", "0.001", "0.01", "0.1"))
        dropdownLR.setAdapter(lrAdapter)
        dropdownLR.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateLearningRate(lrAdapter.getItem(position).toString())
        }

        val dropdownSize = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.dropdownImageSize)
        val sizeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line,
            listOf("96x96", "128x128", "224x224"))
        dropdownSize.setAdapter(sizeAdapter)
        dropdownSize.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateImageSize(sizeAdapter.getItem(position).toString())
        }

        val switchGpu = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGpu)
        switchGpu.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateUseGpu(isChecked)
        }

        val sliderBatch = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderBatchSize)
        val tvBatch = view.findViewById<TextView>(R.id.tvBatchSize)
        sliderBatch.addOnChangeListener { _, value, _ ->
            viewModel.updateBatchSize(value.toInt())
            tvBatch.text = value.toInt().toString()
        }

        val sliderMutation = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderMutation)
        val tvMutation = view.findViewById<TextView>(R.id.tvMutation)
        sliderMutation.addOnChangeListener { _, value, _ ->
            viewModel.updateMutationSigma(value)
            tvMutation.text = "%.3f".format(value)
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExportModel).setOnClickListener {
            viewModel.exportBestModel()
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearData).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear All Data")
                .setMessage(getString(R.string.confirm_clear))
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearAllData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.exportSuccess) {
                        Snackbar.make(requireView(), getString(R.string.model_exported), Snackbar.LENGTH_SHORT).show()
                        viewModel.resetFlags()
                    }
                    if (state.clearSuccess) {
                        Snackbar.make(requireView(), "Data cleared", Snackbar.LENGTH_SHORT).show()
                        viewModel.resetFlags()
                    }
                }
            }
        }
    }
}
