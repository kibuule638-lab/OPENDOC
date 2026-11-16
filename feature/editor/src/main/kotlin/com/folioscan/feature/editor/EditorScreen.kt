package com.folioscan.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.designsystem.ToolbarItem
import com.folioscan.core.model.PageAdjustments
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.PointFraction
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val addPageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.appendPagesFromUris(uris)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditorEffect.DoneAndExit -> onBack()
                is EditorEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is EditorEffect.ShowDeletedPageSnackbar -> {
                    val res = snackbarHostState.showSnackbar(
                        message = "Removed ${effect.pageTitle}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.undoDeletePage()
                    }
                }
            }
        }
    }

    var zoomScale by remember(state.currentPageIndex) { mutableFloatStateOf(1f) }
    var panOffset by remember(state.currentPageIndex) { mutableStateOf(Offset.Zero) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    BasicTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTitle,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input")
                    )
                },
                actions = {
                    IconButton(
                        onClick = viewModel::undo,
                        enabled = state.canUndo,
                        modifier = Modifier.testTag("editor_undo_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = viewModel::redo,
                        enabled = state.canRedo,
                        modifier = Modifier.testTag("editor_redo_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    TextButton(
                        onClick = viewModel::done,
                        modifier = Modifier.testTag("editor_done_btn")
                    ) {
                        Text("Done")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main preview with pinch-zoom + interactive Sign/Redact/Crop overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(FolioSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    val bmp = state.previewBitmap
                    if (bmp != null) {
                        var boxSize by remember { mutableStateOf(IntSize.Zero) }
                        val liveSignature = remember { mutableStateListOf<PointFraction>() }
                        var redactStart by remember { mutableStateOf<Offset?>(null) }
                        var redactEnd by remember { mutableStateOf<Offset?>(null) }
                        var cropInset by remember(state.activeTool) { mutableFloatStateOf(0.08f) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { boxSize = it }
                                .pointerInput(state.activeTool) {
                                    when (state.activeTool) {
                                        EditorTool.SIGN -> {
                                            detectDragGestures(
                                                onDragStart = { pos ->
                                                    liveSignature.clear()
                                                    if (boxSize.width > 0 && boxSize.height > 0) {
                                                        liveSignature += PointFraction(
                                                            (pos.x / boxSize.width).coerceIn(0f, 1f),
                                                            (pos.y / boxSize.height).coerceIn(0f, 1f)
                                                        )
                                                    }
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    if (boxSize.width > 0 && boxSize.height > 0) {
                                                        liveSignature += PointFraction(
                                                            (change.position.x / boxSize.width).coerceIn(0f, 1f),
                                                            (change.position.y / boxSize.height).coerceIn(0f, 1f)
                                                        )
                                                    }
                                                },
                                                onDragEnd = {
                                                    viewModel.addSignatureStroke(liveSignature.toList())
                                                    liveSignature.clear()
                                                }
                                            )
                                        }

                                        EditorTool.REDACT -> {
                                            detectDragGestures(
                                                onDragStart = { pos ->
                                                    redactStart = pos
                                                    redactEnd = pos
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    redactEnd = change.position
                                                },
                                                onDragEnd = {
                                                    val s = redactStart
                                                    val e = redactEnd
                                                    if (s != null && e != null && boxSize.width > 0 && boxSize.height > 0) {
                                                        viewModel.addRedactionBox(
                                                            s.x / boxSize.width,
                                                            s.y / boxSize.height,
                                                            e.x / boxSize.width,
                                                            e.y / boxSize.height
                                                        )
                                                    }
                                                    redactStart = null
                                                    redactEnd = null
                                                }
                                            )
                                        }

                                        else -> {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val newZoom = (zoomScale * zoom).coerceIn(1f, 4f)
                                                zoomScale = newZoom
                                                panOffset = if (newZoom > 1f) panOffset + pan else Offset.Zero
                                            }
                                        }
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Editing page ${state.currentPageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = panOffset.x
                                        translationY = panOffset.y
                                    }
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (liveSignature.size >= 2) {
                                    val path = Path()
                                    liveSignature.forEachIndexed { i, pt ->
                                        val px = pt.x * size.width
                                        val py = pt.y * size.height
                                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                    }
                                    drawPath(path, Color.Blue, style = Stroke(width = 4.dp.toPx()))
                                }
                                val rs = redactStart
                                val re = redactEnd
                                if (rs != null && re != null) {
                                    val left = minOf(rs.x, re.x)
                                    val top = minOf(rs.y, re.y)
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        topLeft = Offset(left, top),
                                        size = Size(kotlin.math.abs(re.x - rs.x), kotlin.math.abs(re.y - rs.y))
                                    )
                                }
                                if (state.activeTool == EditorTool.CROP) {
                                    val l = cropInset * size.width
                                    val t = cropInset * size.height
                                    drawRect(
                                        color = Color(0xFF10B981),
                                        topLeft = Offset(l, t),
                                        size = Size(size.width * (1f - 2 * cropInset), size.height * (1f - 2 * cropInset)),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }

                            if (state.activeTool == EditorTool.CROP) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Inset", style = MaterialTheme.typography.labelMedium)
                                        Slider(
                                            value = cropInset,
                                            onValueChange = { cropInset = it },
                                            valueRange = 0.02f..0.28f,
                                            modifier = Modifier.width(140.dp)
                                        )
                                        Button(
                                            onClick = {
                                                viewModel.applyNormalizedCrop(
                                                    cropInset,
                                                    cropInset,
                                                    1f - cropInset,
                                                    1f - cropInset
                                                )
                                            },
                                            modifier = Modifier.testTag("editor_apply_crop_btn")
                                        ) {
                                            Text("Apply")
                                        }
                                    }
                                }
                            } else if (state.activeTool == EditorTool.SIGN || state.activeTool == EditorTool.REDACT) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (state.activeTool == EditorTool.SIGN) {
                                            "Draw signature directly on page"
                                        } else {
                                            "Drag box over text to redact before export"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Page strip: horizontal reorderable thumbnails + "+" tile at the end
            LazyRow(
                contentPadding = PaddingValues(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("editor_page_strip")
            ) {
                itemsIndexed(state.pages, key = { _, p -> p.id }) { idx, page ->
                    val selected = idx == state.currentPageIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.selectPage(idx) }
                            .padding(4.dp)
                            .testTag("editor_thumb_$idx")
                    ) {
                        AsyncImage(
                            model = File(page.thumbPath),
                            contentDescription = "Page ${idx + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 48.dp, height = 64.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected && idx > 0) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Move page left",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.movePage(idx, idx - 1) }
                                )
                            }
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (selected && idx < state.pages.lastIndex) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Move page right",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.movePage(idx, idx + 1) }
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        onClick = {
                            addPageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(width = 56.dp, height = 84.dp)
                            .testTag("editor_add_page_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Add page")
                        }
                    }
                }
            }

            // Bottom scrollable tool bar: Crop | Rotate | Filter | Adjust | Sign | Redact | Delete
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = FolioSpacing.xs, vertical = FolioSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xxs)
                ) {
                    ToolbarItem(
                        icon = Icons.Default.Crop,
                        label = "Crop",
                        selected = state.activeTool == EditorTool.CROP,
                        onClick = { viewModel.setActiveTool(EditorTool.CROP) }
                    )
                    ToolbarItem(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "Rotate",
                        onClick = viewModel::rotateCurrentPage
                    )
                    ToolbarItem(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Filter",
                        selected = state.activeTool == EditorTool.FILTER,
                        onClick = { viewModel.setActiveTool(EditorTool.FILTER) }
                    )
                    ToolbarItem(
                        icon = Icons.Default.Tune,
                        label = "Adjust",
                        selected = state.activeTool == EditorTool.ADJUST,
                        onClick = { viewModel.setActiveTool(EditorTool.ADJUST) }
                    )
                    ToolbarItem(
                        icon = Icons.Default.Draw,
                        label = "Sign",
                        selected = state.activeTool == EditorTool.SIGN,
                        onClick = { viewModel.setActiveTool(EditorTool.SIGN) }
                    )
                    ToolbarItem(
                        icon = Icons.Default.HideSource,
                        label = "Redact",
                        selected = state.activeTool == EditorTool.REDACT,
                        onClick = { viewModel.setActiveTool(EditorTool.REDACT) }
                    )
                    ToolbarItem(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = viewModel::deleteCurrentPage
                    )
                }
            }
        }
    }

    // Filter picker bottom sheet over toolbar
    if (state.activeTool == EditorTool.FILTER) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setActiveTool(EditorTool.NONE) }
        ) {
            Column(modifier = Modifier.padding(FolioSpacing.md)) {
                Text("Page filter", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(FolioSpacing.sm))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)) {
                    itemsIndexed(PageFilter.entries) { _, filter ->
                        FilterChip(
                            selected = state.currentPage?.filter == filter,
                            onClick = { viewModel.applyFilter(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
                Spacer(Modifier.height(FolioSpacing.lg))
            }
        }
    }

    // Adjust sliders (brightness, contrast, sharpness) bottom sheet over toolbar
    if (state.activeTool == EditorTool.ADJUST) {
        val currentAdj = state.currentPage?.adjustments ?: PageAdjustments()
        var brightness by remember(currentAdj) { mutableFloatStateOf(currentAdj.brightness) }
        var contrast by remember(currentAdj) { mutableFloatStateOf(currentAdj.contrast) }
        var sharpness by remember(currentAdj) { mutableFloatStateOf(currentAdj.sharpness) }

        ModalBottomSheet(
            onDismissRequest = { viewModel.setActiveTool(EditorTool.NONE) }
        ) {
            Column(modifier = Modifier.padding(FolioSpacing.md)) {
                Text("Adjust image", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(FolioSpacing.sm))

                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = -0.4f..0.4f,
                    onValueChangeFinished = {
                        viewModel.updateAdjustments(PageAdjustments(brightness, contrast, sharpness))
                    }
                )
                Text("Contrast", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = contrast,
                    onValueChange = { contrast = it },
                    valueRange = 0.6f..1.8f,
                    onValueChangeFinished = {
                        viewModel.updateAdjustments(PageAdjustments(brightness, contrast, sharpness))
                    }
                )
                Text("Sharpness", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = sharpness,
                    onValueChange = { sharpness = it },
                    valueRange = 0f..1f,
                    onValueChangeFinished = {
                        viewModel.updateAdjustments(PageAdjustments(brightness, contrast, sharpness))
                    }
                )
                Spacer(Modifier.height(FolioSpacing.md))
            }
        }
    }
}
