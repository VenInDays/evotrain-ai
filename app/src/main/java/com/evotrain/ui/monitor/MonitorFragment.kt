package com.evotrain.ui.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.evotrain.R
import com.evotrain.ml.TrainingPhase
import com.evotrain.ui.inference.TestAiBottomSheet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MonitorFragment : Fragment() {

    private val viewModel: MonitorViewModel by viewModels()
    private lateinit var leaderboardAdapter: ModelLeaderboardAdapter
    private lateinit var trainingCardsAdapter: TrainingCardsAdapter
    private lateinit var logAdapter: TrainingLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_monitor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Test AI FAB
        view.findViewById<FloatingActionButton>(R.id.fabTestAi)?.setOnClickListener {
            val sheet = TestAiBottomSheet()
            sheet.show(childFragmentManager, "TestAiBottomSheet")
        }

        // Set layoutManager BEFORE adapter
        leaderboardAdapter = ModelLeaderboardAdapter()
        view.findViewById<RecyclerView>(R.id.leaderboardRecyclerView).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = leaderboardAdapter
        }

        trainingCardsAdapter = TrainingCardsAdapter()
        view.findViewById<RecyclerView>(R.id.trainingCardsRecyclerView).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = trainingCardsAdapter
        }

        logAdapter = TrainingLogAdapter()
        view.findViewById<RecyclerView>(R.id.logRecyclerView)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
        }

        // Initialize chart with empty data to prevent null crash
        val chart = view.findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.accuracyChart)
        chart.doOnLayout {
            setupChart(chart)
        }
        // Fallback if doOnLayout doesn't fire
        setupChart(chart)

        // Collapsible sections
        val historyToggle = view.findViewById<View>(R.id.generationHistoryToggle)
        val historyContent = view.findViewById<View>(R.id.generationHistoryContent)
        historyToggle?.setOnClickListener {
            historyContent?.visibility = if (historyContent?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            val arrow = view.findViewById<View>(R.id.historyArrow)
            arrow?.rotation = if (historyContent?.visibility == View.VISIBLE) 180f else 0f
        }

        val logToggle = view.findViewById<View>(R.id.trainingLogToggle)
        val logContent = view.findViewById<View>(R.id.trainingLogContent)
        logToggle?.setOnClickListener {
            logContent?.visibility = if (logContent?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            val arrow = view.findViewById<View>(R.id.logArrow)
            arrow?.rotation = if (logContent?.visibility == View.VISIBLE) 180f else 0f
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logEntries.collect { entries ->
                    logAdapter.submitList(entries.map { entry ->
                        TrainingLogEntry(
                            level = entry.level,
                            message = entry.message,
                            timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(entry.timestamp))
                        )
                    })
                }
            }
        }
    }

    private fun updateUi(state: MonitorUiState) {
        val view = view ?: return

        // Sticky header updates
        view.findViewById<TextView>(R.id.generationTitle)?.text =
            getString(R.string.generation_number_format, state.generationNumber)
        view.findViewById<TextView>(R.id.bestAccuracyHeader)?.text =
            getString(R.string.best_accuracy_format, (state.bestAccuracy * 100).toInt())
        view.findViewById<TextView>(R.id.targetAccuracyHeader)?.text =
            getString(R.string.target_accuracy_format, (state.targetAccuracy * 100).toInt())
        view.findViewById<android.widget.ProgressBar>(R.id.overallProgress)?.progress =
            (state.bestAccuracy * 100).toInt()

        // Legacy status card (keep for compatibility)
        view.findViewById<TextView>(R.id.generationNumber)?.text =
            state.generationNumber.toString()

        val statusChip = view.findViewById<TextView>(R.id.statusChip)
        val (textRes, bgRes) = when (state.phase) {
            TrainingPhase.IDLE -> Pair(R.string.idle, R.drawable.bg_chip_idle)
            TrainingPhase.TRAINING -> Pair(R.string.training, R.drawable.bg_chip_training)
            TrainingPhase.EVALUATING -> Pair(R.string.evaluating, R.drawable.bg_chip_evaluating)
            TrainingPhase.REPRODUCING -> Pair(R.string.reproducing, R.drawable.bg_chip_reproducing)
            TrainingPhase.SELECTING -> Pair(R.string.evaluating, R.drawable.bg_chip_evaluating)
            TrainingPhase.COMPLETED -> Pair(R.string.completed, R.drawable.bg_chip_completed)
        }
        statusChip?.setText(textRes)
        statusChip?.setBackgroundResource(bgRes)

        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.accuracyProgressBar)
        progressBar?.progress = (state.bestAccuracy * 100).toInt()

        view.findViewById<TextView>(R.id.currentAccuracy)?.text =
            "${(state.bestAccuracy * 100).toInt()}%"

        leaderboardAdapter.submitList(state.aliveModels)
        trainingCardsAdapter.submitList(state.modelProgress.values.toList())

        // Update generation history text
        val historyText = view.findViewById<TextView>(R.id.generationHistoryText)
        historyText?.text = if (state.generations.isEmpty()) {
            getString(R.string.no_generation_history)
        } else {
            state.generations.joinToString("\n") { gen ->
                "Gen ${gen.generationNumber}: Best ${(gen.bestAccuracy * 100).toInt()}% | Avg ${(gen.averageAccuracy * 100).toInt()}%"
            }
        }

        updateChart(state)
    }

    private fun setupChart(chart: com.github.mikephil.charting.charts.LineChart) {
        try {
            val emptyBest = LineDataSet(ArrayList<Entry>(), getString(R.string.best_accuracy)).apply {
                color = resources.getColor(R.color.accent_indigo, null)
                lineWidth = 2f
                setDrawCircles(true)
                setCircleColor(resources.getColor(R.color.accent_indigo, null))
                setDrawFilled(true)
                fillColor = resources.getColor(R.color.accent_indigo, null)
                fillAlpha = 30
            }
            val emptyAvg = LineDataSet(ArrayList<Entry>(), getString(R.string.average_accuracy)).apply {
                color = resources.getColor(R.color.soft_emerald, null)
                lineWidth = 2f
                setDrawCircles(true)
                setCircleColor(resources.getColor(R.color.soft_emerald, null))
            }
            chart.data = LineData(emptyBest, emptyAvg)
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.granularity = 1f
            chart.axisLeft.axisMinimum = 0f
            chart.axisLeft.axisMaximum = 100f
            chart.description.isEnabled = false
            chart.setTouchEnabled(true)
            chart.invalidate()
        } catch (e: Exception) {
            // Chart may not be ready yet
        }
    }

    private fun updateChart(state: MonitorUiState) {
        val view = view ?: return
        val chart = view.findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.accuracyChart)
            ?: return

        try {
            val bestEntries = if (state.generations.isEmpty()) {
                ArrayList()
            } else {
                state.generations.mapIndexed { index, gen ->
                    Entry(gen.generationNumber.toFloat(), gen.bestAccuracy * 100)
                }
            }
            val avgEntries = if (state.generations.isEmpty()) {
                ArrayList()
            } else {
                state.generations.mapIndexed { index, gen ->
                    Entry(gen.generationNumber.toFloat(), gen.averageAccuracy * 100)
                }
            }

            // Loss entries for right Y-axis
            val lossEntries = if (state.generations.isEmpty()) {
                ArrayList()
            } else {
                state.generations.mapIndexed { index, gen ->
                    Entry(gen.generationNumber.toFloat(), gen.avgLoss)
                }
            }

            val bestDataSet = LineDataSet(bestEntries, getString(R.string.best_accuracy)).apply {
                color = resources.getColor(R.color.accent_indigo, null)
                lineWidth = 2f
                setDrawCircles(true)
                setCircleColor(resources.getColor(R.color.accent_indigo, null))
                setDrawFilled(true)
                fillColor = resources.getColor(R.color.accent_indigo, null)
                fillAlpha = 30
            }

            val avgDataSet = LineDataSet(avgEntries, getString(R.string.average_accuracy)).apply {
                color = resources.getColor(R.color.soft_emerald, null)
                lineWidth = 2f
                setDrawCircles(true)
                setCircleColor(resources.getColor(R.color.soft_emerald, null))
            }

            val lossDataSet = LineDataSet(lossEntries, getString(R.string.loss)).apply {
                color = resources.getColor(R.color.rose, null)
                lineWidth = 1.5f
                setDrawCircles(false)
                setDrawDashedLine(floatArrayOf(5f, 3f), 0f)
                axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
            }

            chart.data = LineData(bestDataSet, avgDataSet, lossDataSet)
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.granularity = 1f
            chart.axisLeft.axisMinimum = 0f
            chart.axisLeft.axisMaximum = 100f

            // Right Y-axis for loss
            chart.axisRight.isEnabled = true
            chart.axisRight.axisMinimum = 0f
            chart.axisRight.granularity = 0.1f

            chart.description.isEnabled = false
            chart.setTouchEnabled(true)
            chart.invalidate()
        } catch (e: Exception) {
            // Chart may not be ready, ignore
        }
    }
}
