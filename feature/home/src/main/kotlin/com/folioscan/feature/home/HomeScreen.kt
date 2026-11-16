package com.folioscan.feature.home

import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioscan.core.designsystem.DocCard
import com.folioscan.core.designsystem.EmptyState
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.designsystem.ModeChip
import com.folioscan.core.designsystem.R
import com.folioscan.core.designsystem.SelectionTopBar
import com.folioscan.core.designsystem.SkeletonBox
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.model.SortOrder
import com.folioscan.core.model.ViewLayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenCapture: (CaptureMode) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenExportForDocument: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOverflow by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.OpenCapture -> onOpenCapture(effect.mode)
                is HomeEffect.OpenDocument -> onOpenDocument(effect.documentId)
                is HomeEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is HomeEffect.ShowUndoTrashSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Moved ${effect.count} document(s) to Trash",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoTrash(effect.trashedIds)
                    }
                }
            }
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = when {
        state.viewLayout == ViewLayoutMode.LIST -> 1
        isLandscape -> 3
        else -> 2
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    onClearSelection = viewModel::clearSelection,
                    onMove = { showMoveDialog = true },
                    onTag = { showTagDialog = true },
                    onMerge = viewModel::mergeSelected,
                    onExport = {
                        state.selectedIds.firstOrNull()?.let { onOpenExportForDocument(it) }
                    },
                    onDelete = viewModel::deleteSelectedToTrash
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "FolioScan",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.testTag("home_search_btn")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search documents")
                        }
                        IconButton(
                            onClick = onOpenVault,
                            modifier = Modifier.testTag("home_vault_btn")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Private Vault")
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.testTag("home_overflow_btn")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort (${state.sortOrder.label})") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        showSortDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.viewLayout == ViewLayoutMode.GRID) "List view" else "Grid view"
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (state.viewLayout == ViewLayoutMode.GRID) {
                                                Icons.AutoMirrored.Filled.ViewList
                                            } else {
                                                Icons.Default.GridView
                                            },
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.toggleViewLayout()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!state.inSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startScan() },
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                    text = { Text("Scan") },
                    modifier = Modifier.testTag("home_scan_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.showOnboardingBanner) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs)
                ) {
                    Row(
                        modifier = Modifier.padding(FolioSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "100% Offline & Private",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "OCR, MRZ verification, and AES-256 PDF export run locally on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = viewModel::dismissOnboarding) {
                            Text("Got it")
                        }
                    }
                }
            }

            // Mode carousel: Document | ID Card | Passport | Business Card | Other ID
            LazyRow(
                contentPadding = PaddingValues(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
            ) {
                items(CaptureMode.entries) { mode ->
                    ModeChip(
                        mode = mode,
                        selected = state.selectedMode == mode,
                        onClick = {
                            viewModel.selectMode(mode)
                            viewModel.startScan(mode)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent scans",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (state.inSelectionMode) {
                    TextButton(
                        onClick = { showBatchRenameDialog = true },
                        modifier = Modifier.testTag("home_batch_rename_btn")
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Batch rename")
                    }
                }
            }

            when {
                state.isLoading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(FolioSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(6) {
                            SkeletonBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }

                state.errorMessage != null -> {
                    EmptyState(
                        title = "Couldn't load scans",
                        subtitle = state.errorMessage ?: "Please try again.",
                        ctaLabel = "Scan now",
                        onCtaClick = { viewModel.startScan() },
                        modifier = Modifier.weight(1f)
                    )
                }

                state.documents.isEmpty() -> {
                    EmptyState(
                        title = "No scans yet",
                        subtitle = "Capture paper documents, two-sided IDs, passports with MRZ check, or business cards offline.",
                        illustrationRes = R.drawable.img_empty_scans,
                        ctaLabel = "Scan your first document",
                        onCtaClick = { viewModel.startScan() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(
                            start = FolioSpacing.md,
                            end = FolioSpacing.md,
                            top = FolioSpacing.xs,
                            bottom = 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_recent_grid")
                    ) {
                        items(state.documents, key = { it.id }) { doc ->
                            DocCard(
                                document = doc,
                                isSelected = doc.id in state.selectedIds,
                                selectionMode = state.inSelectionMode,
                                onClick = {
                                    if (state.inSelectionMode) {
                                        viewModel.toggleSelection(doc.id)
                                    } else {
                                        onOpenDocument(doc.id)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(doc.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort scans") },
            text = {
                Column {
                    SortOrder.entries.forEach { order ->
                        TextButton(
                            onClick = {
                                viewModel.setSortOrder(order)
                                showSortDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (order == state.sortOrder) "✓ ${order.label}" else order.label,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) { Text("Close") }
            }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to folder") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.moveSelectedToFolder(null)
                            showMoveDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Root (No folder)", modifier = Modifier.fillMaxWidth()) }
                    state.folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                viewModel.moveSelectedToFolder(folder.id)
                                showMoveDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(folder.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTagDialog) {
        var tagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add tag") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Tag name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.tagSelected(tagInput)
                        showTagDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBatchRenameDialog) {
        var prefix by remember { mutableStateOf("Document") }
        AlertDialog(
            onDismissRequest = { showBatchRenameDialog = false },
            title = { Text("Batch rename") },
            text = {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Title prefix") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchRenameSelected(prefix)
                        showBatchRenameDialog = false
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}
