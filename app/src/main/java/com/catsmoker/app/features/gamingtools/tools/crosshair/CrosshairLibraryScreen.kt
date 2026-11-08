package com.catsmoker.app.features.gamingtools.tools.crosshair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.SectionCard

@Composable
fun CrosshairLibraryRoute(
    selectedAsset: String,
    onBack: () -> Unit,
    onUse: (String) -> Unit,
    viewModel: CrosshairLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CrosshairLibraryScreen(
        state = state,
        selectedAsset = selectedAsset,
        onBack = onBack,
        onDownload = viewModel::download,
        onRetry = viewModel::refresh,
        onUse = onUse
    )
}

@Composable
private fun CrosshairLibraryScreen(
    state: CrosshairLibraryViewModel.UiState,
    selectedAsset: String,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onRetry: () -> Unit,
    onUse: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.core_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.gt_crosshair_library),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.gt_crosshair_library_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            }
            state.catalogLoadFailed -> {
                SectionCard {
                    Text(stringResource(R.string.gt_crosshair_catalog_error), color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.gt_crosshair_retry))
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.entries, key = { it.filename }) { entry ->
                        val localName = CrosshairAssets.downloadedAssetName(entry.filename)
                        val isDownloaded = localName in state.downloadedAssets
                        val isSelected = selectedAsset == localName && isDownloaded
                        SectionCard(contentPadding = 12.dp) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(entry.downloadUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.gt_crosshair_number, entry.number),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(88.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.gt_crosshair_number, entry.number),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            when {
                                isSelected -> {
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            stringResource(R.string.gt_crosshair_selected),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                isDownloaded -> Button(
                                    onClick = { onUse(localName) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) {
                                    Text(stringResource(R.string.gt_crosshair_use))
                                }
                                else -> {
                                    val isDownloading = entry.filename in state.downloading
                                    Button(
                                        onClick = { onDownload(entry.filename) },
                                        enabled = !isDownloading,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(stringResource(R.string.gt_crosshair_download))
                                        }
                                    }
                                    if (entry.filename in state.failedDownloads) {
                                        Text(
                                            stringResource(R.string.gt_crosshair_download_error),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
