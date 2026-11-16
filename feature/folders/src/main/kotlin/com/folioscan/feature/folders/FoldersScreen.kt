package com.folioscan.feature.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioscan.core.designsystem.DocCard
import com.folioscan.core.designsystem.EmptyState
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.designsystem.SelectionTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    onOpenDocument: (String) -> Unit,
    onOpenExportForDocument: (String) -> Unit,
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var permanentDeleteDocId by remember { mutableStateOf<String?>(null) }

    if (state.viewingTrash) {
        BackHandler { viewModel.closeTrash() }
    } else if (state.currentFolderId != null) {
        BackHandler {
            val parent = state.breadcrumb.dropLast(1).lastOrNull()?.id
            viewModel.openFolder(parent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FoldersEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is FoldersEffect.ShowUndoTrashSnackbar -> {
                    val res = snackbarHostState.showSnackbar(
                        message = "Moved ${effect.ids.size} item(s) to Trash",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.undoTrash(effect.ids)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedDocumentIds.size,
                    onClearSelection = viewModel::clearSelection,
                    onExport = { state.selectedDocumentIds.firstOrNull()?.let(onOpenExportForDocument) },
                    onDelete = viewModel::trashSelected
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (state.viewingTrash || state.currentFolderId != null) {
                            IconButton(
                                onClick = {
                                    if (state.viewingTrash) {
                                        viewModel.closeTrash()
                                    } else {
                                        val parent = state.breadcrumb.dropLast(1).lastOrNull()?.id
                                        viewModel.openFolder(parent)
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                            }
                        }
                    },
                    title = {
                        Text(
                            when {
                                state.viewingTrash -> "Trash"
                                state.breadcrumb.isNotEmpty() -> state.breadcrumb.last().name
                                else -> "Folders"
                            }
                        )
                    },
                    actions = {
                        if (!state.viewingTrash) {
                            TextButton(
                                onClick = { showNewFolderDialog = true },
                                modifier = Modifier.testTag("folders_new_btn")
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("+ New folder")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.viewingTrash) {
                if (state.trashedDocuments.isEmpty()) {
                    EmptyState(
                        title = "Trash is empty",
                        subtitle = "Deleted scans appear here so you can restore them anytime.",
                        icon = Icons.Default.DeleteOutline,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(FolioSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
                    ) {
                        items(state.trashedDocuments, key = { it.id }) { doc ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(doc.title) },
                                    supportingContent = { Text("${doc.pageCount} page(s)") },
                                    trailingContent = {
                                        Row {
                                            IconButton(
                                                onClick = { viewModel.restoreFromTrash(doc.id) },
                                                modifier = Modifier.testTag("trash_restore_${doc.id}")
                                            ) {
                                                Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore")
                                            }
                                            IconButton(
                                                onClick = { permanentDeleteDocId = doc.id },
                                                modifier = Modifier.testTag("trash_delete_forever_${doc.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteForever,
                                                    contentDescription = "Delete permanently",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Breadcrumb for nested folders
                if (state.breadcrumb.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Root",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.openFolder(null) }
                        )
                        state.breadcrumb.forEach { crumb ->
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                            Text(
                                text = crumb.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.openFolder(crumb.id) }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(FolioSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
                ) {
                    if (state.folders.isEmpty() && state.documents.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No folders yet",
                                subtitle = "Create folders to organize receipts, contracts, and travel scans offline.",
                                icon = Icons.Default.Folder,
                                ctaLabel = "Create folder",
                                onCtaClick = { showNewFolderDialog = true }
                            )
                        }
                    }

                    items(state.folders, key = { it.id }) { folder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { viewModel.openFolder(folder.id) },
                                    onLongClick = {}
                                )
                                .testTag("folder_row_${folder.id}")
                        ) {
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                headlineContent = { Text(folder.name) },
                                trailingContent = {
                                    Text(
                                        text = "${folder.itemCount}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }

                    items(state.documents, key = { it.id }) { doc ->
                        DocCard(
                            document = doc,
                            isSelected = doc.id in state.selectedDocumentIds,
                            selectionMode = state.inSelectionMode,
                            onClick = {
                                if (state.inSelectionMode) {
                                    viewModel.toggleDocumentSelection(doc.id)
                                } else {
                                    onOpenDocument(doc.id)
                                }
                            },
                            onLongClick = { viewModel.toggleDocumentSelection(doc.id) }
                        )
                    }
                }

                // Trash entry pinned at the bottom of the list
                HorizontalDivider()
                Surface(
                    onClick = viewModel::openTrash,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folders_trash_entry")
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        headlineContent = { Text("Trash") },
                        trailingContent = {
                            Text(
                                text = "${state.trashedDocuments.size}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
    }

    if (showNewFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.testTag("new_folder_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolder(name)
                        showNewFolderDialog = false
                    },
                    modifier = Modifier.testTag("new_folder_confirm_btn")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    permanentDeleteDocId?.let { docId ->
        AlertDialog(
            onDismissRequest = { permanentDeleteDocId = null },
            title = { Text("Delete permanently?") },
            text = { Text("This scan and its encrypted files will be erased permanently and cannot be restored.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePermanently(docId)
                        permanentDeleteDocId = null
                    }
                ) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteDocId = null }) { Text("Cancel") }
            }
        )
    }
}
