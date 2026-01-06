package com.mindapps.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.mindapps.R
import com.mindapps.data.AppState
import com.mindapps.data.MindApp
import com.mindapps.data.MindAppWithState
import com.mindapps.ui.components.PrimaryButton
import com.mindapps.ui.theme.MindAppsTheme

private val titleFontFamily = FontFamily(
    Font(R.font.stack_sans_notch_light, FontWeight.Light)
)

private val appNameFontFamily = FontFamily(
    Font(R.font.stack_sans_notch_semibold, FontWeight.SemiBold)
)

private enum class Tab { LIBRARY, UPDATES, DISCOVER, SETTINGS }

@Composable
fun MindAppsScreen(
    viewModel: MindAppsViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    shouldEnterEditMode: Boolean = false,
    onEditModeConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    
    // PreferencesManager for persisting library apps
    val preferencesManager = remember { com.mindapps.data.PreferencesManager(context) }
    val savedLibraryAppIds by preferencesManager.libraryAppIds.collectAsState(initial = emptySet())
    var libraryAppIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    
    // Load saved library apps on first launch
    androidx.compose.runtime.LaunchedEffect(savedLibraryAppIds) {
        if (savedLibraryAppIds.isNotEmpty() && libraryAppIds.isEmpty()) {
            libraryAppIds = savedLibraryAppIds
        }
    }

    // Auto-add installed apps to library
    androidx.compose.runtime.LaunchedEffect(uiState, savedLibraryAppIds) {
        val state = uiState
        if (state is UiState.Success) {
            // Combine current and saved library IDs to check against
            val allLibraryIds = libraryAppIds + savedLibraryAppIds
            val installedApps = state.apps.filter {
                (it.state == AppState.INSTALLED || it.state == AppState.UPDATE_AVAILABLE) &&
                !it.app.packageName.startsWith("com.mindapps") &&
                it.app.packageName !in allLibraryIds
            }
            if (installedApps.isNotEmpty()) {
                val newIds = installedApps.map { it.app.packageName }.toSet()
                libraryAppIds = libraryAppIds + savedLibraryAppIds + newIds
                newIds.forEach { packageName ->
                    preferencesManager.addToLibrary(packageName)
                }
            }
        }
    }

    // Handle edit mode trigger from settings
    androidx.compose.runtime.LaunchedEffect(shouldEnterEditMode) {
        if (shouldEnterEditMode) {
            isEditMode = true
            selectedTab = Tab.LIBRARY
            onEditModeConsumed()
        }
    }

    // Refresh apps when Discover tab is selected
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        if (selectedTab == Tab.DISCOVER) {
            viewModel.loadApps()
        }
    }

    // Refresh apps when app resumes from background
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    MindAppsTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Main content area
                Box(modifier = Modifier.weight(1f)) {
                    when (val state = uiState) {
                        is UiState.Loading -> {
                            LoadingContent()
                        }
                        is UiState.Success -> {
                            // Filter out com.mindapps from Library and Discover
                            val libraryApps = state.apps.filter {
                                it.app.packageName in libraryAppIds &&
                                !it.app.packageName.startsWith("com.mindapps")
                            }
                            val discoverApps = state.apps.filter {
                                it.app.packageName !in libraryAppIds &&
                                !it.app.packageName.startsWith("com.mindapps")
                            }

                            // Updates tab: include library apps with updates AND com.mindapps if it has an update
                            val updatesApps = state.apps.filter {
                                it.state == AppState.UPDATE_AVAILABLE &&
                                (it.app.packageName in libraryAppIds || it.app.packageName.startsWith("com.mindapps"))
                            }

                            when (selectedTab) {
                                Tab.LIBRARY -> {
                                    LibraryContent(
                                        apps = libraryApps,
                                        imageLoader = imageLoader,
                                        onAppAction = viewModel::onAppAction,
                                        onUninstall = viewModel::uninstallApp,
                                        onSettingsClick = onSettingsClick,
                                        isEditMode = isEditMode,
                                        onToggleEditMode = { isEditMode = !isEditMode },
                                        onRemoveFromLibrary = { app ->
                                            libraryAppIds = libraryAppIds - app.app.packageName
                                            scope.launch {
                                                preferencesManager.removeFromLibrary(app.app.packageName)
                                            }
                                        }
                                    )
                                }
                                Tab.UPDATES -> {
                                    UpdatesContent(
                                        apps = updatesApps,
                                        imageLoader = imageLoader,
                                        onAppAction = viewModel::onAppAction,
                                        onUpdateAll = {
                                            updatesApps.forEach { viewModel.onAppAction(it) }
                                        }
                                    )
                                }
                                Tab.DISCOVER -> {
                                    DiscoverContent(
                                        apps = discoverApps,
                                        imageLoader = imageLoader,
                                        onAppAction = viewModel::onAppAction,
                                        searchQuery = searchQuery,
                                        onSearchQueryChange = { searchQuery = it },
                                        onAddToLibrary = { app ->
                                            libraryAppIds = libraryAppIds + app.app.packageName
                                            scope.launch {
                                                preferencesManager.addToLibrary(app.app.packageName)
                                            }
                                        }
                                    )
                                }
                                Tab.SETTINGS -> {
                                    SettingsTabContent(
                                        onRemoveAppsFromLibrary = {
                                            isEditMode = true
                                            selectedTab = Tab.LIBRARY
                                        }
                                    )
                                }
                            }
                        }
                        is UiState.Error -> {
                            ErrorContent(
                                message = state.message,
                                onRetry = viewModel::loadApps
                            )
                        }
                    }
                }

                // Bottom navigation
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // White gap above the line
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(MaterialTheme.colorScheme.background)
        )
        
        val borderColor = MaterialTheme.colorScheme.onBackground
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Top border line
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
        // Library tab
        Column(
            modifier = Modifier
                .clickable(
                    onClick = { onTabSelected(Tab.LIBRARY) },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (selectedTab == Tab.LIBRARY) Icons.Filled.Home else Icons.Outlined.Home,
                contentDescription = "Library",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Library",
                fontFamily = appNameFontFamily,
                fontWeight = if (selectedTab == Tab.LIBRARY) FontWeight.ExtraBold else FontWeight.Normal,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Indicator dot underneath
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (selectedTab == Tab.LIBRARY) borderColor
                        else MaterialTheme.colorScheme.background,
                        CircleShape
                    )
            )
        }

        // Discover tab
        Column(
            modifier = Modifier
                .clickable(
                    onClick = { onTabSelected(Tab.DISCOVER) },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (selectedTab == Tab.DISCOVER) Icons.Filled.Search else Icons.Outlined.Search,
                contentDescription = "Discover",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Discover",
                fontFamily = appNameFontFamily,
                fontWeight = if (selectedTab == Tab.DISCOVER) FontWeight.ExtraBold else FontWeight.Normal,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Indicator dot underneath
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (selectedTab == Tab.DISCOVER) borderColor
                        else MaterialTheme.colorScheme.background,
                        CircleShape
                    )
            )
        }

        // Updates tab
        Column(
            modifier = Modifier
                .clickable(
                    onClick = { onTabSelected(Tab.UPDATES) },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.update_24),
                contentDescription = "Updates",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Updates",
                fontFamily = appNameFontFamily,
                fontWeight = if (selectedTab == Tab.UPDATES) FontWeight.ExtraBold else FontWeight.Normal,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Indicator dot underneath
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (selectedTab == Tab.UPDATES) borderColor
                        else MaterialTheme.colorScheme.background,
                        CircleShape
                    )
            )
        }

        // Settings tab
        Column(
            modifier = Modifier
                .clickable(
                    onClick = { onTabSelected(Tab.SETTINGS) },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Settings",
                fontFamily = appNameFontFamily,
                fontWeight = if (selectedTab == Tab.SETTINGS) FontWeight.ExtraBold else FontWeight.Normal,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Indicator dot underneath
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (selectedTab == Tab.SETTINGS) borderColor
                        else MaterialTheme.colorScheme.background,
                        CircleShape
                    )
            )
        }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ThreeDotsLoading()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.error_loading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            PrimaryButton(
                text = stringResource(R.string.retry),
                onClick = onRetry
            )
        }
    }
}

@Composable
private fun SettingsTabContent(
    onRemoveAppsFromLibrary: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { com.mindapps.data.PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    val updateNotificationsEnabled by preferencesManager.isUpdateNotificationsEnabled.collectAsState(initial = true)

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrollable content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 130.dp, bottom = 24.dp)
        ) {
            // Remove apps from library
            item {
                SettingsClickItem(
                    title = "Remove apps from library",
                    description = "Select apps to remove from your library",
                    onClick = onRemoveAppsFromLibrary
                )
            }

            item { SettingsPerforatedDivider() }

            // Settings Items
            item {
                SettingsToggleItem(
                    title = stringResource(R.string.settings_update_notifications),
                    description = stringResource(R.string.settings_update_notifications_desc),
                    checked = updateNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferencesManager.setUpdateNotificationsEnabled(enabled)
                        }
                    }
                )
            }

            item { SettingsPerforatedDivider() }

            item { Spacer(modifier = Modifier.height(48.dp)) }

            // About Section
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.settings_about_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // Sticky header - title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 70.dp, bottom = 16.dp, start = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Settings",
                fontFamily = titleFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-1).sp
            )
        }

        // Sticky line at 130dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 130.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onBackground)
            )

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

@Composable
private fun SettingsClickItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { onCheckedChange(!checked) },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Custom toggle
        SettingsCustomToggle(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsCustomToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(28.dp)
            .then(
                if (checked) {
                    Modifier.background(
                        MaterialTheme.colorScheme.onBackground,
                        RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            )
            .clickable(
                onClick = { onCheckedChange(!checked) },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(22.dp)
                .background(
                    if (checked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    CircleShape
                )
        )
    }
}

@Composable
private fun SettingsPerforatedDivider() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        )
    }
}

private const val APPS_PER_PAGE = 4
private const val SWIPE_THRESHOLD = 50f

@Composable
private fun LibraryContent(
    apps: List<MindAppWithState>,
    imageLoader: ImageLoader,
    onAppAction: (MindAppWithState) -> Unit,
    onUninstall: (MindApp) -> Unit,
    onSettingsClick: () -> Unit,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onRemoveFromLibrary: (MindAppWithState) -> Unit
) {
    // Group apps into pages
    val pages = remember(apps) {
        apps.chunked(APPS_PER_PAGE)
    }
    val pageCount = maxOf(1, pages.size)
    var currentPage by rememberSaveable { mutableStateOf(0) }

    // Reset to first page if pages change and current page is out of bounds
    androidx.compose.runtime.LaunchedEffect(pageCount) {
        if (currentPage >= pageCount) {
            currentPage = maxOf(0, pageCount - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (apps.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 130.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add apps to your library",
                    fontFamily = titleFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            // Paged content with instant page switching (no animation)
            val pageApps = pages.getOrElse(currentPage) { emptyList() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 140.dp)
                    .pointerInput(pageCount) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (totalDrag < -SWIPE_THRESHOLD && currentPage < pageCount - 1) {
                                    currentPage++
                                } else if (totalDrag > SWIPE_THRESHOLD && currentPage > 0) {
                                    currentPage--
                                }
                                totalDrag = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                            }
                        )
                    }
            ) {
                pageApps.forEachIndexed { index, appWithState ->
                    LibraryAppItem(
                        appWithState = appWithState,
                        imageLoader = imageLoader,
                        onAction = { onAppAction(appWithState) },
                        onUninstall = { onUninstall(appWithState.app) },
                        isEditMode = isEditMode,
                        onRemoveFromLibrary = { onRemoveFromLibrary(appWithState) }
                    )
                    // Add perforated divider between items (not after the last one)
                    if (index < pageApps.size - 1) {
                        PerforatedDivider()
                    }
                }
            }
        }

        // Sticky header - title and page dots
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 70.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = titleFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1).sp
                )

                // Page indicator dots (only show if more than 1 page)
                if (pageCount > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pageCount) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentPage) 8.dp else 6.dp)
                                    .background(
                                        if (index == currentPage)
                                            MaterialTheme.colorScheme.onBackground
                                        else
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Sticky line at 130dp (where apps start) - content scrolls under this
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 130.dp)
        ) {
            // Solid black line (stays fixed at the top of the app list)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onBackground)
            )

            // White gap below the line
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // Top right - Done button in edit mode only
        if (isEditMode) {
            IconButton(
                onClick = onToggleEditMode,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 70.dp, end = 24.dp)
                    .size(48.dp)
            ) {
                Text(
                    text = "Done",
                    fontFamily = appNameFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun LibraryAppItem(
    appWithState: MindAppWithState,
    imageLoader: ImageLoader,
    onAction: () -> Unit,
    onUninstall: () -> Unit,
    isEditMode: Boolean,
    onRemoveFromLibrary: () -> Unit
) {
    val app = appWithState.app
    val showUpdateBadge = appWithState.state == AppState.UPDATE_AVAILABLE
    val isInstalled = appWithState.state == AppState.INSTALLED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = appWithState.state == AppState.INSTALLED && !isEditMode,
                onClick = onAction,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minus button in edit mode
        if (isEditMode) {
            IconButton(
                onClick = onRemoveFromLibrary,
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "−",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // App icon
        Box {
            AsyncImage(
                model = app.icon,
                contentDescription = app.name,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(MaterialTheme.colorScheme.background),
                contentScale = ContentScale.Crop
            )

            // Update badge
            if (showUpdateBadge) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = 2.dp, y = (-2).dp)
                        .background(
                            MaterialTheme.colorScheme.onBackground,
                            CircleShape
                        )
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // App info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = app.name,
                fontFamily = appNameFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = buildString {
                    append(app.author)
                    append(" / v${app.version}")
                    if (appWithState.state == AppState.UPDATE_AVAILABLE && appWithState.installedVersion != null) {
                        append(" (${appWithState.installedVersion})")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Download progress
            if (appWithState.state == AppState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { appWithState.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.background,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Action button (only show if not in edit mode)
        if (!isEditMode) {
            LibraryActionButton(
                state = appWithState.state,
                onAction = onAction,
                onUninstall = onUninstall
            )
        }
    }
}

@Composable
private fun LibraryActionButton(
    state: AppState,
    onAction: () -> Unit,
    onUninstall: () -> Unit
) {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            AppState.NOT_INSTALLED -> {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.install),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            AppState.UPDATE_AVAILABLE -> {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.update),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            AppState.INSTALLED -> {
                // Trash icon to uninstall
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.uninstall),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            AppState.DOWNLOADING -> {
                ThreeDotsLoading(dotSize = 6.dp)
            }
        }
    }
}

@Composable
private fun UpdatesContent(
    apps: List<MindAppWithState>,
    imageLoader: ImageLoader,
    onAppAction: (MindAppWithState) -> Unit,
    onUpdateAll: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (apps.isEmpty()) {
            // No updates available
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "All apps are up to date",
                        fontFamily = appNameFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Update All button
                Spacer(modifier = Modifier.height(48.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${apps.size} update${if (apps.size > 1) "s" else ""} available",
                        fontFamily = appNameFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Update All button
                    Box(
                        modifier = Modifier
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                onClick = onUpdateAll,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Update All",
                            fontFamily = appNameFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Solid line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onBackground)
                )

                // Apps list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(apps, key = { index, it -> "${it.app.packageName}_update_$index" }) { index, appWithState ->
                        UpdateAppItem(
                            appWithState = appWithState,
                            imageLoader = imageLoader,
                            onUpdate = { onAppAction(appWithState) }
                        )
                        if (index < apps.size - 1) {
                            UpdatesDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesDivider() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )
    }
}

@Composable
private fun UpdateAppItem(
    appWithState: MindAppWithState,
    imageLoader: ImageLoader,
    onUpdate: () -> Unit
) {
    val app = appWithState.app

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        AsyncImage(
            model = app.icon,
            contentDescription = app.name,
            imageLoader = imageLoader,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(MaterialTheme.colorScheme.background),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // App info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = app.name,
                fontFamily = appNameFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "v${appWithState.installedVersion ?: "?"} → v${app.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Update button
        if (appWithState.state == AppState.DOWNLOADING) {
            ThreeDotsLoading(dotSize = 6.dp)
        } else {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.onBackground,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        onClick = onUpdate,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Update",
                    fontFamily = appNameFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.background
                )
            }
        }
    }
}

@Composable
private fun DiscoverContent(
    apps: List<MindAppWithState>,
    imageLoader: ImageLoader,
    onAppAction: (MindAppWithState) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddToLibrary: (MindAppWithState) -> Unit
) {
    val sortedApps = remember(apps) { apps.sortedBy { it.app.name.lowercase() } }
    val filteredApps = remember(sortedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedApps
        } else {
            sortedApps.filter {
                it.app.name.contains(searchQuery, ignoreCase = true) ||
                it.app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val borderColor = MaterialTheme.colorScheme.onBackground
    var selectedApp by remember { mutableStateOf<MindAppWithState?>(null) }

    // Paging for discover
    val pages = remember(filteredApps) {
        filteredApps.chunked(APPS_PER_PAGE)
    }
    val pageCount = maxOf(1, pages.size)
    var currentPage by rememberSaveable { mutableStateOf(0) }

    // Reset to first page if filtered results change
    androidx.compose.runtime.LaunchedEffect(filteredApps.size) {
        currentPage = 0
    }

    // Reset if current page is out of bounds
    androidx.compose.runtime.LaunchedEffect(pageCount) {
        if (currentPage >= pageCount) {
            currentPage = maxOf(0, pageCount - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Apps list with paging
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 130.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (apps.isEmpty()) "You've added all the apps" else "No apps found",
                    fontFamily = appNameFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            val pageApps = pages.getOrElse(currentPage) { emptyList() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 140.dp)
                    .pointerInput(pageCount) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (totalDrag < -SWIPE_THRESHOLD && currentPage < pageCount - 1) {
                                    currentPage++
                                } else if (totalDrag > SWIPE_THRESHOLD && currentPage > 0) {
                                    currentPage--
                                }
                                totalDrag = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                            }
                        )
                    }
            ) {
                pageApps.forEachIndexed { index, appWithState ->
                    DiscoverAppItem(
                        appWithState = appWithState,
                        imageLoader = imageLoader,
                        onAddToLibrary = { onAddToLibrary(appWithState) },
                        onView = { selectedApp = appWithState }
                    )
                    // Perforated divider between items (edge to edge)
                    if (index < pageApps.size - 1) {
                        DiscoverDivider()
                    }
                }
            }
        }

        // Sticky header with search bar and page dots
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Spacer(modifier = Modifier.height(70.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = titleFontFamily,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                // Page indicator dots (only show if more than 1 page)
                if (pageCount > 1) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pageCount) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentPage) 8.dp else 6.dp)
                                    .background(
                                        if (index == currentPage)
                                            MaterialTheme.colorScheme.onBackground
                                        else
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Sticky line at 130dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 130.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onBackground)
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // App detail overlay
        selectedApp?.let { app ->
            AppDetailOverlay(
                appWithState = app,
                imageLoader = imageLoader,
                onBack = { selectedApp = null },
                onAddToLibrary = {
                    onAddToLibrary(app)
                    selectedApp = null
                }
            )
        }
    }
}

@Composable
private fun DiscoverDivider() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )
    }
}

@Composable
private fun DiscoverAppItem(
    appWithState: MindAppWithState,
    imageLoader: ImageLoader,
    onAddToLibrary: () -> Unit,
    onView: () -> Unit
) {
    val app = appWithState.app

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onView,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        AsyncImage(
            model = app.icon,
            contentDescription = app.name,
            imageLoader = imageLoader,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(MaterialTheme.colorScheme.background),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // App info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = app.name,
                fontFamily = appNameFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = app.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // View text
        Text(
            text = "View",
            fontFamily = appNameFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Plus button to add to library
        IconButton(
            onClick = onAddToLibrary,
            modifier = Modifier.size(32.dp)
        ) {
            val color = MaterialTheme.colorScheme.onBackground
            Canvas(modifier = Modifier.size(16.dp)) {
                val strokeWidth = 2.dp.toPx()
                val padding = 1.dp.toPx()

                // Horizontal line
                drawLine(
                    color = color,
                    start = Offset(padding, size.height / 2),
                    end = Offset(size.width - padding, size.height / 2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Vertical line
                drawLine(
                    color = color,
                    start = Offset(size.width / 2, padding),
                    end = Offset(size.width / 2, size.height - padding),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun AppDetailOverlay(
    appWithState: MindAppWithState,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onAddToLibrary: () -> Unit
) {
    val app = appWithState.app

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp)
        ) {
            // Back button
            item {
                Text(
                    text = "← Back",
                    fontFamily = appNameFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(
                        onClick = onBack,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Icon, name, and tags row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = app.icon,
                        contentDescription = app.name,
                        imageLoader = imageLoader,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .background(MaterialTheme.colorScheme.background),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = app.name,
                        fontFamily = appNameFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (app.appTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        app.appTags.forEachIndexed { index, tag ->
                            Text(
                                text = tag,
                                fontFamily = appNameFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (index < app.appTags.size - 1) {
                                Text(
                                    text = " · ",
                                    fontFamily = appNameFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Author
            item {
                Text(
                    text = app.author,
                    fontFamily = titleFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Divider
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onBackground)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // App info/description
            if (app.appInfo.isNotEmpty()) {
                item {
                    Text(
                        text = app.appInfo,
                        fontFamily = titleFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Release date
            if (app.appRelease.isNotEmpty()) {
                item {
                    Text(
                        text = "Released: ${app.appRelease}",
                        fontFamily = titleFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Fixed bottom button - white background with black plus
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(
                        onClick = onAddToLibrary,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontFamily = appNameFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun SolidBorder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val cornerRadius = 16.dp.toPx()

                // Draw solid rounded rectangle border
                drawRoundRect(
                    color = color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke
                    )
                )
            }
    ) {
        content()
    }
}

@Composable
private fun SolidBorderWide(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val cornerRadius = 16.dp.toPx()

                // Draw solid rounded rectangle border
                drawRoundRect(
                    color = color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke
                    )
                )
            }
    ) {
        content()
    }
}

@Composable
private fun PerforatedBorder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                // Draw perforated rectangle border
                drawRect(
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke,
                        pathEffect = dashEffect
                    )
                )
            }
    ) {
        content()
    }
}

@Composable
private fun PerforatedDivider() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )
    }
}

@Composable
private fun AppListItem(
    appWithState: MindAppWithState,
    imageLoader: ImageLoader,
    onAction: () -> Unit,
    onUninstall: () -> Unit
) {
    val app = appWithState.app
    val showUpdateBadge = appWithState.state == AppState.UPDATE_AVAILABLE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = appWithState.state == AppState.INSTALLED,
                onClick = onAction,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        Box {
            AsyncImage(
                model = app.icon,
                contentDescription = app.name,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(MaterialTheme.colorScheme.background),
                contentScale = ContentScale.Crop
            )

            // Update badge
            if (showUpdateBadge) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = 2.dp, y = (-2).dp)
                        .background(
                            MaterialTheme.colorScheme.onBackground,
                            CircleShape
                        )
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // App info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = app.name,
                fontFamily = appNameFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = buildString {
                    append(app.author)
                    append(" / v${app.version}")
                    if (appWithState.state == AppState.UPDATE_AVAILABLE && appWithState.installedVersion != null) {
                        append(" (${appWithState.installedVersion})")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Download progress
            if (appWithState.state == AppState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { appWithState.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.background,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Action button
        AppActionButton(
            state = appWithState.state,
            onAction = onAction,
            onUninstall = onUninstall
        )
    }
}

@Composable
private fun AppActionButton(
    state: AppState,
    onAction: () -> Unit,
    onUninstall: () -> Unit
) {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            AppState.NOT_INSTALLED -> {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.install),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            AppState.UPDATE_AVAILABLE -> {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.onBackground,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.update),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.background
                    )
                }
            }
            AppState.INSTALLED -> {
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.uninstall),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            AppState.DOWNLOADING -> {
                ThreeDotsLoading(dotSize = 6.dp)
            }
        }
    }
}

@Composable
private fun ThreeDotsLoading(
    dotSize: androidx.compose.ui.unit.Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    // E-ink optimized: snap between positions instead of smooth transition
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 133, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 266, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    // Snap positions: either 0 (bottom) or -12 (top)
    val snap1 = if (dot1Offset < -6f) -12f else 0f
    val snap2 = if (dot2Offset < -6f) -12f else 0f
    val snap3 = if (dot3Offset < -6f) -12f else 0f

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    translationY = snap1
                }
                .background(MaterialTheme.colorScheme.onBackground, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    translationY = snap2
                }
                .background(MaterialTheme.colorScheme.onBackground, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    translationY = snap3
                }
                .background(MaterialTheme.colorScheme.onBackground, CircleShape)
        )
    }
}
