package com.folioscan.feature.detail

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.folioscan.core.designsystem.EmptyState
import com.folioscan.core.designsystem.FieldRow
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.OcrStatus
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onOpenEditor: (documentId: String, pageIndex: Int) -> Unit,
    onSetWindowSecure: (Boolean) -> Unit,
    onStartExportService: (Int) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflow by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    // Enforce FLAG_SECURE for Vault items, ID Cards, and Passports
    LaunchedEffect(state.shouldProtectScreen) {
        onSetWindowSecure(state.shouldProtectScreen)
    }
    DisposableEffect(Unit) {
        onDispose { onSetWindowSecure(false) }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DetailEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is DetailEffect.LaunchIntent -> runCatching { context.startActivity(effect.intent) }
                is DetailEffect.ExitDetail -> onBack()
                is DetailEffect.ShowUndoTrashSnackbar -> {
                    val res = snackbarHostState.showSnackbar(
                        message = "Moved to Trash",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.undoTrash()
                    } else {
                        onBack()
                    }
                }
            }
        }
    }

    val doc = state.document
    val tabs = remember(state.showFieldsTab) {
        if (state.showFieldsTab) {
            listOf("Pages", "Text (OCR)", "Fields", "Info")
        } else {
            listOf("Pages", "Text (OCR)", "Info")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = doc?.title ?: "Document",
                        maxLines = 1
                    )
                },
                actions = {
                    if (doc != null) {
                        IconButton(
                            onClick = viewModel::toggleFavorite,
                            modifier = Modifier.testTag("detail_fav_btn")
                        ) {
                            Icon(
                                imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Toggle favorite"
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.testTag("detail_overflow_btn")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { showOverflow = false; showRenameDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to folder") },
                                    onClick = { showOverflow = false; showMoveDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add tag") },
                                    onClick = { showOverflow = false; showTagDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    onClick = { showOverflow = false; viewModel.duplicate() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (doc.isVault) "Remove from Vault" else "Move to Vault") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    onClick = { showOverflow = false; viewModel.toggleVault() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showOverflow = false; viewModel.moveToTrash() }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (doc != null) {
                Surface(tonalElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FolioSpacing.md, vertical = FolioSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::quickSharePdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_share_btn")
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                        Button(
                            onClick = viewModel::showExportSheet,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_export_btn")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { onOpenEditor(doc.id, 0) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_edit_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (doc == null) {
            EmptyState(
                title = "Document not found",
                subtitle = "This scan may have been deleted.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab.coerceAtMost(tabs.lastIndex)) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) },
                        modifier = Modifier.testTag("detail_tab_${label.lowercase().replace(" ", "_")}")
                    )
                }
            }

            when (tabs.getOrNull(selectedTab)) {
                "Pages" -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        contentPadding = PaddingValues(FolioSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.pages, key = { _, p -> p.id }) { idx, page ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEditor(doc.id, idx) }
                                    .testTag("detail_page_card_$idx")
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    AsyncImage(
                                        model = File(page.thumbPath),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.75f)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Page ${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                "Text (OCR)" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(FolioSpacing.md)
                    ) {
                        if (doc.ocrStatus == OcrStatus.QUEUED || doc.ocrStatus == OcrStatus.RUNNING) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text("Running offline OCR…", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(FolioSpacing.sm))
                        } else if (doc.ocrStatus == OcrStatus.FAILED) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("OCR failed on a page", color = MaterialTheme.colorScheme.error)
                                Row {
                                    TextButton(onClick = viewModel::skipOcr) { Text("Skip") }
                                    Button(onClick = viewModel::retryOcr) { Text("Retry") }
                                }
                            }
                            Spacer(Modifier.height(FolioSpacing.sm))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.ocrSearchQuery,
                                onValueChange = viewModel::setOcrSearchQuery,
                                label = { Text("Find in text") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = viewModel::copyOcrText,
                                modifier = Modifier.testTag("detail_copy_ocr_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                        }
                        Spacer(Modifier.height(FolioSpacing.sm))

                        val fullText = state.pages.mapIndexedNotNull { idx, p ->
                            p.ocrText?.takeIf { it.isNotBlank() }?.let { "--- Page ${idx + 1} ---\n$it" }
                        }.joinToString("\n\n")

                        if (fullText.isBlank()) {
                            EmptyState(
                                title = "No extracted text yet",
                                subtitle = "Run offline text recognition to make this document searchable.",
                                ctaLabel = "Run OCR now",
                                onCtaClick = viewModel::retryOcr,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            SelectionContainer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val highlightColor = MaterialTheme.colorScheme.primaryContainer
                                val q = state.ocrSearchQuery.trim()
                                val annotated = buildAnnotatedString {
                                    if (q.isEmpty()) {
                                        append(fullText)
                                    } else {
                                        var start = 0
                                        while (start < fullText.length) {
                                            val idx = fullText.indexOf(q, start, ignoreCase = true)
                                            if (idx < 0) {
                                                append(fullText.substring(start))
                                                break
                                            }
                                            append(fullText.substring(start, idx))
                                            withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold)) {
                                                append(fullText.substring(idx, idx + q.length))
                                            }
                                            start = idx + q.length
                                        }
                                    }
                                }
                                Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                "Fields" -> {
                    val fields = state.identityData?.fields.orEmpty()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(FolioSpacing.md)
                    ) {
                        if (fields.isEmpty()) {
                            EmptyState(
                                title = "No structured fields yet",
                                subtitle = "OCR will extract MRZ or contact fields automatically.",
                                ctaLabel = "Re-extract fields",
                                onCtaClick = viewModel::retryOcr,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(FolioSpacing.sm)
                            ) {
                                items(fields.size) { i ->
                                    val field = fields[i]
                                    FieldRow(
                                        label = field.label,
                                        value = field.value,
                                        isValid = field.isValid,
                                        validationNote = field.validationNote,
                                        onValueChange = { viewModel.updateIdentityField(field.key, it) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(FolioSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::copyExtractedFields,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy")
                            }
                            Button(
                                onClick = viewModel::exportFieldsAsTxtOrVcf,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (doc.type == DocumentType.BUSINESS_CARD) "Export VCF" else "Export TXT")
                            }
                            if (doc.type == DocumentType.BUSINESS_CARD) {
                                Button(
                                    onClick = viewModel::saveBusinessCardToContacts,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Contacts, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Contacts")
                                }
                            }
                        }
                    }
                }

                "Info" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(FolioSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.sm)
                    ) {
                        InfoLine("Type", doc.type.name)
                        InfoLine("Created", DateFormat.getDateTimeInstance().format(Date(doc.createdAt)))
                        InfoLine("Updated", DateFormat.getDateTimeInstance().format(Date(doc.updatedAt)))
                        InfoLine("Pages", "${doc.pageCount}")
                        InfoLine("Size", Formatter.formatShortFileSize(context, doc.totalSizeBytes))
                        InfoLine("OCR Status", doc.ocrStatus.name)
                        InfoLine(
                            "Folder",
                            state.folders.firstOrNull { it.id == doc.folderId }?.name ?: "Root"
                        )
                        InfoLine(
                            "Encryption",
                            if (doc.isVault || doc.type == DocumentType.ID_CARD || doc.type == DocumentType.PASSPORT) {
                                "AES-256-GCM + Keystore"
                            } else {
                                "SQLCipher DB"
                            }
                        )

                        Text("Tags", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
                        ) {
                            doc.tags.forEach { tag ->
                                AssistChip(
                                    onClick = { viewModel.removeTag(tag.id) },
                                    label = { Text(tag.name) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "Remove ${tag.name}")
                                    }
                                )
                            }
                            AssistChip(
                                onClick = { showTagDialog = true },
                                label = { Text("Add tag") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.exportState.visible && doc != null) {
        ExportBottomSheet(
            state = state.exportState,
            documentType = doc.type,
            onDismiss = viewModel::dismissExportSheet,
            onFormatChange = viewModel::updateExportFormat,
            onQualityChange = viewModel::updateExportQuality,
            onPasswordChange = viewModel::updatePassword,
            onWatermarkChange = viewModel::updateWatermark,
            onRedactionChange = viewModel::updateApplyRedactions,
            onIdCardLayoutChange = viewModel::updateIdCardLayout,
            onStartExport = { viewModel.runExport(onStartExportService) },
            onCancelExport = viewModel::cancelExport
        )
    }

    if (showRenameDialog && doc != null) {
        var titleInput by remember { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.rename(titleInput); showRenameDialog = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
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
                        onClick = { viewModel.moveToFolder(null); showMoveDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Root", modifier = Modifier.fillMaxWidth()) }
                    state.folders.forEach { folder ->
                        TextButton(
                            onClick = { viewModel.moveToFolder(folder.id); showMoveDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(folder.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Close") }
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
                TextButton(onClick = { viewModel.addTag(tagInput); showTagDialog = false }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
