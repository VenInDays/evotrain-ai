package com.evotrain.ui.monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.evotrain.R
import com.evotrain.data.model.AIModel
import com.evotrain.ml.ModelTrainingProgress

class ModelLeaderboardAdapter : ListAdapter<AIModel, ModelLeaderboardAdapter.ViewHolder>(ModelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val rankIcon: ImageView = view.findViewById(R.id.rankIcon)
        private val modelName: TextView = view.findViewById(R.id.modelName)
        private val generationBadge: TextView = view.findViewById(R.id.generationBadge)
        private val miniProgress: ProgressBar = view.findViewById(R.id.miniProgressBar)
        private val accuracyText: TextView = view.findViewById(R.id.accuracyText)
        private val crownIcon: ImageView = view.findViewById(R.id.crownIcon)
        private val skullIcon: ImageView = view.findViewById(R.id.skullIcon)
        private val lineageIcon: ImageView = view.findViewById(R.id.lineageIcon)

        fun bind(model: AIModel, position: Int) {
            modelName.text = model.name
            generationBadge.text = "Gen ${model.generationNumber}"
            accuracyText.text = "${(model.accuracyScore * 100).toInt()}%"
            miniProgress.progress = (model.accuracyScore * 100).toInt()

            crownIcon.visibility = if (position == 0) View.VISIBLE else View.GONE
            skullIcon.visibility = if (position >= itemCount - 3) View.VISIBLE else View.GONE
            lineageIcon.visibility = if (model.parentId != null) View.VISIBLE else View.GONE

            when (position) {
                0 -> itemView.setBackgroundResource(R.drawable.bg_card_rank_gold)
                1 -> itemView.setBackgroundResource(R.drawable.bg_card_rank_silver)
                2 -> itemView.setBackgroundResource(R.drawable.bg_card_rank_bronze)
                else -> itemView.setBackgroundResource(R.drawable.bg_card)
            }
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
