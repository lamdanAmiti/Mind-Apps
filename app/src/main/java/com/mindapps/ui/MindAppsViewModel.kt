package com.mindapps.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindapps.data.AppState
import com.mindapps.data.MindApp
import com.mindapps.data.MindAppWithState
import com.mindapps.data.MindAppsApi
import com.mindapps.util.BadgeUtils
import com.mindapps.util.PackageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class UiState {
    data object Loading : UiState()
    data class Success(val apps: List<MindAppWithState>) : UiState()
    data class Error(val message: String) : UiState()
}

class MindAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val api = MindAppsApi()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _downloadingApps = MutableStateFlow<Map<String, Float>>(emptyMap())

    private var cachedApps: List<MindApp> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_REMOVED -> {
                    refreshAppStates()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        application.registerReceiver(packageReceiver, filter)

        loadApps()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            // Only show loading state if we don't have cached data
            if (cachedApps.isEmpty()) {
                _uiState.value = UiState.Loading
            }

            val result = api.fetchApps()
            result.fold(
                onSuccess = { apps ->
                    cachedApps = apps
                    updateAppStates(apps)
                },
                onFailure = { error ->
                    // Only show error if we don't have cached data
                    if (cachedApps.isEmpty()) {
                        _uiState.value = UiState.Error(error.message ?: "Unknown error")
                    }
                    // If we have cached data, silently keep showing it
                }
            )
        }
    }

    private fun refreshAppStates() {
        if (cachedApps.isNotEmpty()) {
            updateAppStates(cachedApps)
        }
    }

    private fun updateAppStates(apps: List<MindApp>) {
        val context = getApplication<Application>()
        val downloadingMap = _downloadingApps.value

        val appsWithState = apps.map { app ->
            val isInstalled = PackageUtils.isPackageInstalled(context, app.packageName)
            val installedVersion = PackageUtils.getInstalledVersion(context, app.packageName)
            val isDownloading = downloadingMap.containsKey(app.packageName)

            val state = when {
                isDownloading -> AppState.DOWNLOADING
                !isInstalled -> AppState.NOT_INSTALLED
                PackageUtils.isUpdateAvailable(installedVersion, app.version) -> AppState.UPDATE_AVAILABLE
                else -> AppState.INSTALLED
            }

            MindAppWithState(
                app = app,
                state = state,
                installedVersion = installedVersion,
                downloadProgress = downloadingMap[app.packageName] ?: 0f
            )
        }

        // Update launcher badge with update count
        val updateCount = appsWithState.count { it.state == AppState.UPDATE_AVAILABLE }
        BadgeUtils.setLauncherBadge(context, updateCount)

        _uiState.value = UiState.Success(appsWithState)
    }

    fun onAppAction(appWithState: MindAppWithState) {
        when (appWithState.state) {
            AppState.INSTALLED -> openApp(appWithState.app)
            AppState.NOT_INSTALLED, AppState.UPDATE_AVAILABLE -> downloadAndInstall(appWithState.app)
            AppState.DOWNLOADING -> { /* Already downloading */ }
        }
    }

    private fun openApp(app: MindApp) {
        val context = getApplication<Application>()
        PackageUtils.openApp(context, app.packageName)
    }

    private fun downloadAndInstall(app: MindApp) {
        viewModelScope.launch {
            val context = getApplication<Application>()

            // Mark as downloading
            _downloadingApps.value = _downloadingApps.value + (app.packageName to 0f)
            refreshAppStates()

            // Create cache directory for APKs
            val cacheDir = File(context.cacheDir, "apks")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val apkFile = File(cacheDir, "${app.packageName}.apk")

            // Download APK
            val downloadResult = api.downloadApk(
                apkUrl = app.apkLink,
                destinationFile = apkFile,
                onProgress = { progress ->
                    _downloadingApps.value = _downloadingApps.value + (app.packageName to progress)
                    refreshAppStates()
                }
            )

            downloadResult.fold(
                onSuccess = { file ->
                    // Send analytics
                    api.sendDownloadAnalytics(app.packageName, app.name)

                    // Remove from downloading
                    _downloadingApps.value = _downloadingApps.value - app.packageName
                    refreshAppStates()

                    // Install APK
                    PackageUtils.installApk(context, file)
                },
                onFailure = {
                    // Remove from downloading on failure
                    _downloadingApps.value = _downloadingApps.value - app.packageName
                    refreshAppStates()
                }
            )
        }
    }

    fun uninstallApp(app: MindApp) {
        val context = getApplication<Application>()
        PackageUtils.uninstallApp(context, app.packageName)
    }
}
