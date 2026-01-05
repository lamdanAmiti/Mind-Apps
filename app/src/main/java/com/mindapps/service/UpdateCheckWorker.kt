package com.mindapps.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mindapps.MainActivity
import com.mindapps.R
import com.mindapps.data.MindAppsApi
import com.mindapps.data.PreferencesManager
import com.mindapps.util.PackageUtils
import kotlinx.coroutines.flow.first
import me.leolin.shortcutbadger.ShortcutBadger
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "update_check_work"
        private const val CHANNEL_ID = "mind_apps_updates"
        private const val NOTIFICATION_ID = 1001

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                6, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private val api = MindAppsApi()
    private val preferencesManager = PreferencesManager(context)

    override suspend fun doWork(): Result {
        // Check if notifications are enabled
        val notificationsEnabled = preferencesManager.isUpdateNotificationsEnabled.first()
        if (!notificationsEnabled) {
            return Result.success()
        }

        // Fetch apps and check for updates
        val result = api.fetchApps()
        result.fold(
            onSuccess = { apps ->
                var updateCount = 0
                val updatableApps = mutableListOf<String>()

                for (app in apps) {
                    val installedVersion = PackageUtils.getInstalledVersion(context, app.packageName)
                    if (installedVersion != null && PackageUtils.isUpdateAvailable(installedVersion, app.version)) {
                        updateCount++
                        updatableApps.add(app.name)
                    }
                }

                // Update launcher badge count
                setLauncherBadge(updateCount)

                if (updateCount > 0) {
                    showUpdateNotification(updateCount, updatableApps)
                }
            },
            onFailure = {
                return Result.retry()
            }
        )

        return Result.success()
    }

    private fun showUpdateNotification(count: Int, appNames: List<String>) {
        createNotificationChannel()

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.resources.getQuantityString(
            R.plurals.notification_updates_available,
            count,
            count
        )
        val text = appNames.take(3).joinToString(", ") +
                if (appNames.size > 3) " +${appNames.size - 3}" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setLauncherBadge(count: Int) {
        try {
            if (count > 0) {
                ShortcutBadger.applyCount(context, count)
            } else {
                ShortcutBadger.removeCount(context)
            }
        } catch (e: Exception) {
            // Badge not supported on this launcher
        }
    }
}
