package com.evotrain.ui.monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.evotrain.R
import com.evotrain.data.model.AIModel
import com.evotrain.ml.ModelTrainingProgress

enum class ModelStatus {
    IDLE, TRAINING, EVALUATING, SURVIVED, ELIMINATED, CLONED_FROM
}

class ModelLeaderboardAdapter : ListAdapter<AIModel, ModelLeaderboardAdapter.ViewHolder>(ModelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val rankBadge: TextView = view.findViewById(R.id.rankBadge)
        private val modelName: TextView = view.findViewById(R.id.modelName)
        private val generationBadge: TextView = view.findViewById(R.id.generationBadge)
        private val statusDot: View = view.findViewById(R.id.statusDot)
        private val miniProgress: ProgressBar = view.findViewById(R.id.miniProgressBar)
        private val accuracyText: TextView = view.findViewById(R.id.accuracyText)
        private val epochIndicator: TextView = view.findViewById(R.id.epochIndicator)
        private val statusLabel: TextView = view.findViewById(R.id.statusLabel)
        private val parentName: TextView = view.findViewById(R.id.parentName)
        private val card: View = view.findViewById(R.id.leaderboardCard)

        fun bind(model: AIModel, position: Int, totalItems: Int) {
            modelName.text = model.name
            generationBadge.text = itemView.context.getString(R.string.generation_abbrev_format, model.generationNumber)
            accuracyText.text = "${(model.accuracyScore * 100).toInt()}%"
            miniProgress.progress = (model.accuracyScore * 100).toInt()
            epochIndicator.text = itemView.context.getString(R.string.epoch_format, model.epochsTrained)
            rankBadge.text = "${position + 1}"

            // Determine model status
            val status = when {
                !model.isAlive -> ModelStatus.ELIMINATED
                model.parentId != null && model.parentIdName != null -> ModelStatus.CLONED_FROM
                model.isAlive && model.accuracyScore > 0f -> ModelStatus.SURVIVED
                else -> ModelStatus.IDLE
            }

            // Status dot color and animation
            val dotColor = when (status) {
                ModelStatus.TRAINING -> R.color.accent_indigo
                ModelStatus.EVALUATING -> R.color.muted_amber
                ModelStatus.SURVIVED -> R.color.soft_emerald
                ModelStatus.ELIMINATED -> R.color.rose
                ModelStatus.CLONED_FROM -> R.color.accent_indigo
                ModelStatus.IDLE -> R.color.on_surface_variant_light
            }
            statusDot.setBackgroundResource(R.drawable.ic_status_dot)
            val dotDrawable = statusDot.background?.mutate()
            dotDrawable?.setTint(itemView.context.getColor(dotColor))
            statusDot.background = dotDrawable

            // Pulsing animation for training/evaluating
            statusDot.clearAnimation()
            if (status == ModelStatus.TRAINING || status == ModelStatus.EVALUATING) {
                statusDot.startAnimation(
                    AnimationUtils.loadAnimation(itemView.context, R.anim.pulse_status_dot)
                )
            }

            // Status label
            val (statusText, statusBg) = when (status) {
                ModelStatus.IDLE -> Pair(R.string.status_idle, R.drawable.bg_chip_idle)
                ModelStatus.TRAINING -> Pair(R.string.status_training, R.drawable.bg_chip_training)
                ModelStatus.EVALUATING -> Pair(R.string.status_evaluating, R.drawable.bg_chip_evaluating)
                ModelStatus.SURVIVED -> Pair(R.string.status_survived, R.drawable.bg_chip_completed)
                ModelStatus.ELIMINATED -> Pair(R.string.status_eliminated, R.drawable.bg_card_eliminated)
                ModelStatus.CLONED_FROM -> Pair(R.string.status_cloned, R.drawable.bg_chip_reproducing)
            }
            statusLabel.setText(statusText)
            statusLabel.setBackgroundResource(statusBg)

            // Parent name for clones
            if (model.parentIdName != null && model.parentId != null) {
                parentName.text = itemView.context.getString(R.string.cloned_from_format, model.parentIdName)
                parentName.visibility = View.VISIBLE
            } else {
                parentName.visibility = View.GONE
            }

            // Eliminated cards get 40% alpha
            if (status == ModelStatus.ELIMINATED) {
                itemView.alpha = 0.4f
            } else {
                itemView.alpha = 1.0f
            }

            // Card stroke color by rank
            val strokeRes = when (position) {
                0 -> R.color.gold
                1 -> R.color.silver
                2 -> R.color.bronze
                else -> R.color.card_stroke_light
            }
            if (card is com.google.android.material.card.MaterialCardView) {
                card.strokeColor = itemView.context.getColor(strokeRes)
            }

            // Accuracy text color coding
            val accColor = when {
                model.accuracyScore >= 0.8f -> R.color.soft_emerald
                model.accuracyScore >= 0.5f -> R.color.muted_amber
                model.accuracyScore > 0f -> R.color.rose
                else -> R.color.on_surface_variant_light
            }
            accuracyText.setTextColor(itemView.context.getColor(accColor))
        }
    }

    class ModelDiffCallback : DiffUtil.ItemCallback<AIModel>() {
        override fun areItemsTheSame(oldItem: AIModel, newItem: AIModel): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AIModel, newItem: AIModel): Boolean =
            oldItem == newItem
    }
}

class TrainingCardsAdapter : ListAdapter<ModelTrainingProgress, TrainingCardsAdapter.ViewHolder>(TrainingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_training_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val modelName: TextView = view.findViewById(R.id.trainingModelName)
        private val epochProgress: ProgressBar = view.findViewById(R.id.epochProgress)
        private val epochText: TextView = view.findViewById(R.id.epochText)
        private val lossText: TextView = view.findViewById(R.id.lossText)

        fun bind(progress: ModelTrainingProgress) {
            modelName.text = progress.modelName
            epochProgress.max = progress.totalEpochs * 100
            epochProgress.progress = progress.currentEpoch * 100
            epochText.text = "Epoch ${progress.currentEpoch}/${progress.totalEpochs}"
            lossText.text = "Loss: ${"%.4f".format(progress.currentLoss)}"
        }
    }

    class TrainingDiffCallback : DiffUtil.ItemCallback<ModelTrainingProgress>() {
        override fun areItemsTheSame(oldItem: ModelTrainingProgress, newItem: ModelTrainingProgress): Boolean =
            oldItem.modelId == newItem.modelId
        override fun areContentsTheSame(oldItem: ModelTrainingProgress, newItem: ModelTrainingProgress): Boolean =
            oldItem == newItem
    }
}

class TrainingLogAdapter : ListAdapter<TrainingLogEntry, TrainingLogAdapter.ViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val levelText: TextView = view.findViewById(R.id.logLevel)
        private val messageText: TextView = view.findViewById(R.id.logMessage)
        private val timestampText: TextView = view.findViewById(R.id.logTimestamp)

        fun bind(entry: TrainingLogEntry) {
            levelText.text = entry.level
            messageText.text = entry.message
            timestampText.text = entry.timestamp

            val colorRes = when (entry.level) {
                "INFO" -> R.color.on_surface_variant_light
                "SUCCESS" -> R.color.soft_emerald
                "WARNING" -> R.color.muted_amber
                "ERROR" -> R.color.rose
                else -> R.color.on_surface_variant_light
            }
            levelText.setTextColor(itemView.context.getColor(colorRes))
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<TrainingLogEntry>() {
        override fun areItemsTheSame(oldItem: TrainingLogEntry, newItem: TrainingLogEntry): Boolean =
            oldItem.timestamp == newItem.timestamp && oldItem.message == newItem.message
        override fun areContentsTheSame(oldItem: TrainingLogEntry, newItem: TrainingLogEntry): Boolean =
            oldItem == newItem
    }
}

data class TrainingLogEntry(
    val level: String,
    val message: String,
    val timestamp: String
)
