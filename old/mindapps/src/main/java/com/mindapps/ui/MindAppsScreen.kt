package com.mindapps.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.mindapps.R
import com.mindapps.data.AppState
import com.mindapps.data.MindApp
import com.mindapps.data.MindAppWithState
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.badge.BadgeMMD
import com.mudita.mmd.components.badge.BadgedBoxMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mindapps.ui.components.PerforatedDivider
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.progress_indicator.LinearProgressIndicatorMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindAppsScreen(
    viewModel: MindAppsViewModel = viewModel(),
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Create ImageLoader with SVG support
    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .build()

    ThemeMMD {
        Scaffold(
            topBar = {
                TopAppBarMMD(
                    title = {
                        TextMMD(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = uiState) {
                    is UiState.Loading -> {
                        LoadingContent()
                    }
                    is UiState.Success -> {
                        AppsListContent(
                            apps = state.apps,
                            imageLoader = imageLoader,
                            onAppAction = viewModel::onAppAction,
                            onUninstall = viewModel::uninstallApp
                        )
                    }
                    is UiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = viewModel::loadApps
                        )
                    }
                }
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
            CircularProgressIndicatorMMD(
                size = 48.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextMMD(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TextMMD(
                text = stringResource(R.string.error_loading),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextMMD(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            ButtonMMD(onClick = onRetry) {
                TextMMD(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun AppsListContent(
    apps: List<MindAppWithState>,
    imageLoader: ImageLoader,
    onAppAction: (MindAppWithState) -> Unit,
    onUninstall: (MindApp) -> Unit
) {
    LazyColumnMMD(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(apps, key = { it.app.packageName }) { appWithState ->
            AppListItem(
                appWithState = appWithState,
                imageLoader = imageLoader,
                onAction = { onAppAction(appWithState) },
                onUninstall = { onUninstall(appWithState.app) }
            )
            PerforatedDivider()
        }
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
    val showBadge = appWithState.state == AppState.UPDATE_AVAILABLE
    val installedLabel = stringResource(R.string.installed)

    // Build dynamic supporting text
    val supportingText = buildString {
        append(app.author)
        append(" \u2022 v${app.version}")
        if (appWithState.state == AppState.UPDATE_AVAILABLE && appWithState.installedVersion != null) {
            append(" ($installedLabel: ${appWithState.installedVersion})")
        }
    }

    // Label style: Black 21sp, line-height 23sp
    val labelStyle = TextStyle(
        fontSize = 21.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Black
    )

    // Supporting text style: Medium 18sp, line-height 18sp
    val supportingTextStyle = TextStyle(
        fontSize = 18.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = appWithState.state == AppState.INSTALLED,
                onClick = onAction
            )
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 15.5.dp,
                bottom = 15.5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon with badge - 48x48
        BadgedBoxMMD(
            badge = {
                if (showBadge) {
                    BadgeMMD()
                }
            }
        ) {
            AsyncImage(
                model = app.icon,
                contentDescription = app.name,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        // 16dp gap after icon
        Spacer(modifier = Modifier.width(16.dp))

        // Label (app name) and supporting text
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Label - dynamic app name (Black 21sp, line-height 23sp)
            TextMMD(
                text = app.name,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Supporting text - dynamic author and version info (Medium 18sp, line-height 18sp)
            TextMMD(
                text = supportingText,
                style = supportingTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (appWithState.state == AppState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicatorMMD(
                    progress = { appWithState.downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 16dp gap before action icon
        Spacer(modifier = Modifier.width(16.dp))

        // Action icon - 48x48 touch area with 28x28 icon
        AppActionIcon(
            state = appWithState.state,
            onAction = onAction,
            onUninstall = onUninstall
        )
    }
}

@Composable
private fun AppActionIcon(
    state: AppState,
    onAction: () -> Unit,
    onUninstall: () -> Unit
) {
    // 48x48 touch area with 28x28 icon
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            AppState.NOT_INSTALLED, AppState.UPDATE_AVAILABLE -> {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(
                            if (state == AppState.NOT_INSTALLED) R.string.install else R.string.update
                        ),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            AppState.INSTALLED -> {
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.uninstall),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            AppState.DOWNLOADING -> {
                CircularProgressIndicatorMMD(
                    size = 28.dp
                )
            }
        }
    }
}
