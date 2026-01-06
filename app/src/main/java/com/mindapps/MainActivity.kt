package com.mindapps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mindapps.data.PreferencesManager
import com.mindapps.service.UpdateCheckWorker
import com.mindapps.ui.MindAppsScreen
import com.mindapps.ui.SettingsScreen
import com.mindapps.ui.SetupScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Schedule periodic update check worker
        UpdateCheckWorker.schedule(this)

        // Also trigger an immediate check when app opens
        UpdateCheckWorker.checkNow(this)

        setContent {
            MindAppsNavigation()
        }
    }
}

@Composable
fun MindAppsNavigation() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    // Shared state for edit mode
    var shouldEnterEditMode by remember { mutableStateOf(false) }

    val isSetupCompleted by preferencesManager.isSetupCompleted.collectAsState(initial = null)

    // Wait for initial value to load
    if (isSetupCompleted == null) {
        return
    }

    val startDestination = if (isSetupCompleted == true) "main" else "setup"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable("setup") {
            SetupScreen(
                onSetupComplete = {
                    scope.launch {
                        preferencesManager.setSetupCompleted(true)
                        navController.navigate("main") {
                            popUpTo("setup") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("main") {
            MindAppsScreen(
                onSettingsClick = {
                    navController.navigate("settings")
                },
                shouldEnterEditMode = shouldEnterEditMode,
                onEditModeConsumed = { shouldEnterEditMode = false }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onRemoveAppsFromLibrary = {
                    shouldEnterEditMode = true
                    navController.popBackStack()
                }
            )
        }
    }
}
