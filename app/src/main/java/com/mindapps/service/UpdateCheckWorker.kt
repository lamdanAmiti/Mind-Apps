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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.mindapps.MainActivity
import com.mindapps.R
import com.mindapps.data.MindAppsApi
import com.mindapps.data.PreferencesManager
import com.mindapps.util.PackageUtils
import kotlinx.coroutines.flow.first
import me.leolin.shortcutbadger.ShortcutBadger
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically checks for app updates.
 *
 * Features:
 * - Runs every 4 hours with flex interval for battery efficiency
 * - Only checks apps in user's library
 * - Persists update info for UI access
 * - Shows rich notifications with expandable details
 * - Supports immediate one-time checks
 */
class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val PERIODIC_WORK_NAME = "periodic_update_check"
        private const val ONE_TIME_WORK_NAME = "immediate_update_check"
        private const val CHANNEL_ID = "mind_apps_updates"
        private const val NOTIFICATION_ID = 1001

        // Check interval: 4 hours with 1 hour flex window
        private const val CHECK_INTERVAL_HOURS = 4L
        private const val FLEX_INTERVAL_HOURS = 1L

        // Minimum time between notifications (to avoid spam)
        private const val MIN_NOTIFICATION_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

        /**
         * Schedule periodic background update checks.
         * Uses battery-efficient scheduling with flex interval.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                CHECK_INTERVAL_HOURS, TimeUnit.HOURS,
                FLEX_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Trigger an immediate update check.
         * Useful when app is opened or user manually refreshes.
         */
        fun checkNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        /**
         * Cancel all scheduled update checks.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK_NAME)
        }

        /**
         * Clear the notification and badge.
         */
        fun clearNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            try {
                ShortcutBadger.removeCount(context)
            } catch (e: Exception) {
                // Badge not supported
            }
        }
    }

    private val api = MindAppsApi()
    private val preferencesManager = PreferencesManager(context)
    private val gson = Gson()

    override suspend fun doWork(): Result {
        return try {
            performUpdateCheck()
            Result.success()
        } catch (e: Exception) {
            // Retry on failure with exponential backoff
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun performUpdateCheck() {
        // Fetch remote apps list
        val result = api.fetchApps()
        val apps = result.getOrThrow()

        // Get library apps (apps the user has installed via this store)
        val libraryAppIds = preferencesManager.libraryAppIds.first()

        // Find updates
        val updatesAvailable = mutableListOf<UpdateInfo>()

        for (app in apps) {
            // Check if app is in library OR currently installed
            val installedVersion = PackageUtils.getInstalledVersion(context, app.packageName)
            val isInLibrary = libraryAppIds.contains(app.packageName)

            // Only check apps that are installed and either in library or installed by any means
            if (installedVersion != null) {
                if (PackageUtils.isUpdateAvailable(installedVersion, app.version)) {
                    updatesAvailable.add(
                        UpdateInfo(
                            packageName = app.packageName,
                            appName = app.name,
                            currentVersion = installedVersion,
                            newVersion = app.version
                        )
                    )
                }
            }
        }

        // Store update info
        val updatePackages = updatesAvailable.map { it.packageName }.toSet()
        preferencesManager.setAvailableUpdates(updatePackages)
        preferencesManager.setAvailableUpdatesJson(gson.toJson(updatesAvailable))

        val now = System.currentTimeMillis()
        preferencesManager.setLastUpdateCheck(now)

        // Update launcher badge
        setLauncherBadge(updatesAvailable.size)

        // Show notification if updates available and notifications enabled
        if (updatesAvailable.isNotEmpty()) {
            val notificationsEnabled = preferencesManager.isUpdateNotificationsEnabled.first()
            if (notificationsEnabled) {
                showUpdateNotification(updatesAvailable)
            }
        }
    }

    private fun showUpdateNotification(updates: List<UpdateInfo>) {
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
            putExtra("show_updates", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val count = updates.size
        val title = context.resources.getQuantityString(
            R.plurals.notification_updates_available,
            count,
            count
        )

        // Build content text showing app names
        val appNames = updates.map { it.appName }
        val shortText = when {
            appNames.size == 1 -> appNames[0]
            appNames.size == 2 -> "${appNames[0]} and ${appNames[1]}"
            appNames.size <= 4 -> appNames.dropLast(1).joinToString(", ") + " and ${appNames.last()}"
            else -> appNames.take(3).joinToString(", ") + " +${appNames.size - 3} more"
        }

        // Build expanded text with version info
        val expandedText = updates.joinToString("\n") { update ->
            "${update.appName}: ${update.currentVersion} → ${update.newVersion}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(expandedText)
                .setBigContentTitle(title)
                .setSummaryText(context.getString(R.string.notification_tap_to_update)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
                enableVibration(false)
                setSound(null, null)
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

    /**
     * Data class to hold update information
     */
    data class UpdateInfo(
        val packageName: String,
        val appName: String,
        val currentVersion: String,
        val newVersion: String
    )
}
