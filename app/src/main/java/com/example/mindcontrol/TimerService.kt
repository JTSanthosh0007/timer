package com.example.mindcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    private var countdownTimer: CountDownTimer? = null
    private val CHANNEL_ID = "MindControlChannel"
    private val NOTIFICATION_ID = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Utils.isLocked(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Timer Active"))

        val endTime = Utils.getEndTime(this)
        val duration = endTime - System.currentTimeMillis()

        if (duration <= 0) {
            finishTimer()
        } else {
            startTimer(duration)
        }

        return START_STICKY
    }

    private fun startTimer(duration: Long) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateNotification(Utils.formatTime(millisUntilFinished))
                val intent = Intent(Utils.ACTION_TIMER_TICK)
                intent.putExtra(Utils.EXTRA_TIME_REMAINING, millisUntilFinished)
                sendBroadcast(intent)
            }

            override fun onFinish() {
                finishTimer()
            }
        }.start()
    }

    private fun finishTimer() {
        Utils.unlock(this)
        
        // Broadcast for any other listeners
        val intent = Intent(Utils.ACTION_TIMER_FINISHED)
        sendBroadcast(intent)
        
        // Launch Activity to show Ended Screen and Play Alarm
        val activityIntent = Intent(this, MainActivity::class.java)
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        activityIntent.putExtra("show_ended_screen", true)
        startActivity(activityIntent)
        
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Focus Timer Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MindControl Focus Mode")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification("Time Remaining: $text")
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        super.onDestroy()
    }
}
