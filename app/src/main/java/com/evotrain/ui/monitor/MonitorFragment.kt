package com.evotrain.ui.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evotrain.R
import com.evotrain.ml.TrainingPhase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MonitorFragment : Fragment() {

    private val viewModel: MonitorViewModel by viewModels()
    private lateinit var leaderboardAdapter: ModelLeaderboardAdapter
    private lateinit var trainingCardsAdapter: TrainingCardsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_monitor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        leaderboardAdapter = ModelLeaderboardAdapter()
        view.findViewById<RecyclerView>(R.id.leaderboardRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = leaderboardAdapter
        }

        trainingCardsAdapter = TrainingCardsAdapter()
        view.findViewById<RecyclerView>(R.id.trainingCardsRecyclerView).apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = trainingCardsAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: MonitorUiState) {
        val view = view ?: return

        view.findViewById<android.widget.TextView>(R.id.generationNumber).text =
            state.generationNumber.toString()

        val statusChip = view.findViewById<android.widget.TextView>(R.id.statusChip)
        val (textRes, bgRes) = when (state.phase) {
            TrainingPhase.IDLE -> Pair(R.string.idle, R.drawable.bg_chip_idle)
            TrainingPhase.TRAINING -> Pair(R.string.training, R.drawable.bg_chip_training)
            TrainingPhase.EVALUATING -> Pair(R.string.evaluating, R.drawable.bg_chip_evaluating)
            TrainingPhase.REPRODUCING -> Pair(R.string.reproducing, R.drawable.bg_chip_reproducing)
            TrainingPhase.SELECTING -> Pair(R.string.evaluating, R.drawable.bg_chip_evaluating)
            TrainingPhase.COMPLETED -> Pair(R.string.completed, R.drawable.bg_chip_completed)
        }
        statusChip.setText(textRes)
        statusChip.setBackgroundResource(bgRes)

        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.accuracyProgressBar)
        progressBar.progress = (state.bestAccuracy * 100).toInt()

        view.findViewById<android.widget.TextView>(R.id.currentAccuracy).text =
            "${(state.bestAccuracy * 100).toInt()}%"

        leaderboardAdapter.submitList(state.aliveModels)
        trainingCardsAdapter.submitList(state.modelProgress.values.toList())

        updateChart(state)
    }

    private fun updateChart(state: MonitorUiState) {
        val view = view ?: return
        val chart = view.findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.accuracyChart)

        val bestEntries = state.generations.mapIndexed { index, gen ->
            Entry(gen.generationNumber.toFloat(), gen.bestAccuracy * 100)
        }
        val avgEntries = state.generations.mapIndexed { index, gen ->
            Entry(gen.generationNumber.toFloat(), gen.averageAccuracy * 100)
        }

        val bestDataSet = LineDataSet(bestEntries, getString(R.string.best_accuracy)).apply {
            color = resources.getColor(R.color.accent_indigo, null)
            lineWidth = 2f
            setDrawCircles(true)
            setCircleColor(resources.getColor(R.color.accent_indigo, null))
        }

        val avgDataSet = LineDataSet(avgEntries, getString(R.string.average_accuracy)).apply {
            color = resources.getColor(R.color.soft_emerald, null)
            lineWidth = 2f
            setDrawCircles(true)
            setCircleColor(resources.getColor(R.color.soft_emerald, null))
        }

        chart.data = LineData(bestDataSet, avgDataSet)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 100f
        chart.description.isEnabled = false
        chart.invalidate()
    }
}
