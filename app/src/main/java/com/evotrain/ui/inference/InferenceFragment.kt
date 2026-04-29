package com.evotrain.ui.inference

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.evotrain.R
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class InferenceFragment : Fragment() {

    private val viewModel: InferenceViewModel by viewModels()

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

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPickImage)?.setOnClickListener {
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
}
