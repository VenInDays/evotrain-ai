package com.evotrain.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.evotrain.MainActivity
import com.evotrain.R
import com.evotrain.ml.TrainingEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrainingForegroundService : Service() {

    @Inject lateinit var trainingEngine: TrainingEngine

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Training in progress...")
        startForeground(1, notification)
        return START_STICKY
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "evotrain_channel")
            .setContentTitle(getString(R.string.training_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_train)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
