package com.folioscan.feature.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.model.PageAdjustments
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.PointFraction
import com.folioscan.core.model.RedactionBox
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.model.SignatureStroke
import com.folioscan.core.ocr.OcrWorkScheduler
import com.folioscan.core.storage.ScanFileStore
import com.folioscan.core.vision.OpenCvDocumentProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

enum class EditorTool {
    NONE,
    CROP,
    FILTER,
    ADJUST,
    SIGN,
    REDACT
}

data class EditorUiState(
    val isLoading: Boolean = true,
    val document: ScanDocument? = null,
    val title: String = "",
    val pages: List<ScanPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val activeTool: EditorTool = EditorTool.NONE,
    val previewBitmap: Bitmap? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val currentPage: ScanPage?
        get() = pages.getOrNull(currentPageIndex)
}

sealed interface EditorEffect {
    data object DoneAndExit : EditorEffect
    data class ShowDeletedPageSnackbar(val pageTitle: String) : EditorEffect
    data class ShowMessage(val message: String) : EditorEffect
}

private data class EditorSnapshot(
    val title: String,
    val pages: List<ScanPage>,
    val currentIndex: Int
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val fileStore: ScanFileStore,
    private val processor: OpenCvDocumentProcessor,
    private val ocrScheduler: OcrWorkScheduler
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])
    private val initialPageIndex: Int = savedStateHandle.get<Int>("pageIndex") ?: 0

    private val _uiState = MutableStateFlow(EditorUiState(currentPageIndex = initialPageIndex))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var lastDeletedSnapshot: EditorSnapshot? = null

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            val doc = repository.getDocument(documentId)
            val pages = repository.getPages(documentId)
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Document not found") }
                return@launch
            }
            val safeIdx = initialPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            _uiState.update {
                it.copy(
                    isLoading = false,
                    document = doc,
                    title = doc.title,
                    pages = pages,
                    currentPageIndex = safeIdx
                )
            }
            renderCurrentPagePreview()
        }
    }

    private fun pushUndoSnapshot() {
        val st = _uiState.value
        undoStack.addLast(EditorSnapshot(st.title, st.pages, st.currentPageIndex))
        if (undoStack.size > 25) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
    }

    private fun updateUndoFlags() {
        _uiState.update {
            it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
        }
    }

    fun undo() {
        val snap = undoStack.removeLastOrNull() ?: return
        val cur = _uiState.value
        redoStack.addLast(EditorSnapshot(cur.title, cur.pages, cur.currentPageIndex))
        _uiState.update {
            it.copy(
                title = snap.title,
                pages = snap.pages,
                currentPageIndex = snap.currentIndex.coerceIn(0, (snap.pages.size - 1).coerceAtLeast(0)),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        autosavePages()
        renderCurrentPagePreview()
    }

    fun redo() {
        val snap = redoStack.removeLastOrNull() ?: return
        val cur = _uiState.value
        undoStack.addLast(EditorSnapshot(cur.title, cur.pages, cur.currentPageIndex))
        _uiState.update {
            it.copy(
                title = snap.title,
                pages = snap.pages,
                currentPageIndex = snap.currentIndex.coerceIn(0, (snap.pages.size - 1).coerceAtLeast(0)),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        autosavePages()
        renderCurrentPagePreview()
    }

    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
        viewModelScope.launch {
            repository.renameDocument(documentId, newTitle)
        }
    }

    fun selectPage(index: Int) {
        val maxIdx = (_uiState.value.pages.size - 1).coerceAtLeast(0)
        val clamped = index.coerceIn(0, maxIdx)
        if (clamped == _uiState.value.currentPageIndex) return
        _uiState.update { it.copy(currentPageIndex = clamped) }
        renderCurrentPagePreview()
    }

    fun setActiveTool(tool: EditorTool) {
        _uiState.update {
            it.copy(activeTool = if (it.activeTool == tool) EditorTool.NONE else tool)
        }
    }

    fun rotateCurrentPage() {
        val page = _uiState.value.currentPage ?: return
        pushUndoSnapshot()
        mutateCurrentPage { page.copy(rotation = (page.rotation + 90) % 360) }
    }

    fun applyFilter(filter: PageFilter) {
        val page = _uiState.value.currentPage ?: return
        if (page.filter == filter) return
        pushUndoSnapshot()
        mutateCurrentPage { page.copy(filter = filter) }
    }

    fun updateAdjustments(adjustments: PageAdjustments) {
        val page = _uiState.value.currentPage ?: return
        pushUndoSnapshot()
        mutateCurrentPage { page.copy(adjustments = adjustments) }
    }

    fun addSignatureStroke(points: List<PointFraction>) {
        if (points.size < 2) return
        val page = _uiState.value.currentPage ?: return
        pushUndoSnapshot()
        mutateCurrentPage {
            page.copy(signatureStrokes = page.signatureStrokes + SignatureStroke(points))
        }
    }

    fun addRedactionBox(left: Float, top: Float, right: Float, bottom: Float) {
        val page = _uiState.value.currentPage ?: return
        val l = minOf(left, right).coerceIn(0f, 1f)
        val r = maxOf(left, right).coerceIn(0f, 1f)
        val t = minOf(top, bottom).coerceIn(0f, 1f)
        val b = maxOf(top, bottom).coerceIn(0f, 1f)
        if ((r - l) < 0.02f || (b - t) < 0.02f) return
        pushUndoSnapshot()
        val box = RedactionBox(UUID.randomUUID().toString(), l, t, r, b)
        mutateCurrentPage { page.copy(redactions = page.redactions + box) }
    }

    /**
     * Applies normalized crop margins (inset fractions from edges) permanently onto the page file
     * so subsequent OCR and exports operate on the cropped region.
     */
    fun applyNormalizedCrop(leftFrac: Float, topFrac: Float, rightFrac: Float, bottomFrac: Float) {
        val page = _uiState.value.currentPage ?: return
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val croppedSaved = withContext(Dispatchers.IO) {
                val decoded = fileStore.decodeDownsampledBitmap(page.imagePath, maxDimensionPx = 2200)
                    ?: return@withContext null
                try {
                    val x = (leftFrac.coerceIn(0f, 0.45f) * decoded.width).toInt()
                    val y = (topFrac.coerceIn(0f, 0.45f) * decoded.height).toInt()
                    val w = ((rightFrac - leftFrac).coerceIn(0.2f, 1f) * decoded.width).toInt()
                        .coerceAtMost(decoded.width - x)
                    val h = ((bottomFrac - topFrac).coerceIn(0.2f, 1f) * decoded.height).toInt()
                        .coerceAtMost(decoded.height - y)
                    val cropped = Bitmap.createBitmap(decoded, x, y, w.coerceAtLeast(10), h.coerceAtLeast(10))
                    try {
                        val baos = ByteArrayOutputStream()
                        cropped.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                        fileStore.savePageBytes(
                            jpegBytes = baos.toByteArray(),
                            docId = documentId,
                            pageId = page.id,
                            encryptAtRest = doc.isVault
                        )
                    } finally {
                        cropped.recycle()
                    }
                } finally {
                    decoded.recycle()
                }
            }
            if (croppedSaved != null) {
                pushUndoSnapshot()
                mutateCurrentPage {
                    page.copy(
                        imagePath = croppedSaved.imagePath,
                        thumbPath = croppedSaved.thumbPath,
                        width = croppedSaved.width,
                        height = croppedSaved.height
                    )
                }
            }
            _uiState.update { it.copy(isSaving = false, activeTool = EditorTool.NONE) }
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.pages.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        pushUndoSnapshot()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        val reindexed = list.mapIndexed { idx, p -> p.copy(index = idx) }
        _uiState.update { it.copy(pages = reindexed, currentPageIndex = toIndex) }
        autosavePages()
    }

    fun deleteCurrentPage() {
        val state = _uiState.value
        if (state.pages.size <= 1) {
            viewModelScope.launch {
                _effects.send(EditorEffect.ShowMessage("A document must keep at least 1 page"))
            }
            return
        }
        val snap = EditorSnapshot(state.title, state.pages, state.currentPageIndex)
        lastDeletedSnapshot = snap
        pushUndoSnapshot()
        val mutable = state.pages.toMutableList()
        mutable.removeAt(state.currentPageIndex)
        val reindexed = mutable.mapIndexed { idx, p -> p.copy(index = idx) }
        val nextIdx = state.currentPageIndex.coerceAtMost(reindexed.lastIndex)
        _uiState.update { it.copy(pages = reindexed, currentPageIndex = nextIdx) }
        autosavePages()
        renderCurrentPagePreview()
        viewModelScope.launch {
            _effects.send(EditorEffect.ShowDeletedPageSnackbar("Page ${state.currentPageIndex + 1}"))
        }
    }

    fun undoDeletePage() {
        val snap = lastDeletedSnapshot ?: return
        lastDeletedSnapshot = null
        _uiState.update {
            it.copy(
                pages = snap.pages,
                currentPageIndex = snap.currentIndex
            )
        }
        autosavePages()
        renderCurrentPagePreview()
    }

    fun appendPagesFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            pushUndoSnapshot()
            val startIndex = _uiState.value.pages.size
            val newPages = uris.mapIndexed { offset, uri ->
                val pageId = UUID.randomUUID().toString()
                val saved = fileStore.savePageFromUri(uri, documentId, pageId, encryptAtRest = doc.isVault)
                ScanPage(
                    id = pageId,
                    documentId = documentId,
                    index = startIndex + offset,
                    imagePath = saved.imagePath,
                    thumbPath = saved.thumbPath,
                    width = saved.width,
                    height = saved.height
                )
            }
            val combined = _uiState.value.pages + newPages
            _uiState.update {
                it.copy(
                    isSaving = false,
                    pages = combined,
                    currentPageIndex = combined.lastIndex
                )
            }
            autosavePages()
            ocrScheduler.enqueueDocumentOcr(documentId, replaceExisting = true)
            renderCurrentPagePreview()
        }
    }

    private fun mutateCurrentPage(transform: (ScanPage) -> ScanPage) {
        val idx = _uiState.value.currentPageIndex
        val list = _uiState.value.pages.toMutableList()
        if (idx !in list.indices) return
        list[idx] = transform(list[idx])
        _uiState.update { it.copy(pages = list) }
        autosavePages()
        renderCurrentPagePreview()
    }

    private fun autosavePages() {
        val pages = _uiState.value.pages
        viewModelScope.launch {
            repository.updatePagesAndStats(documentId, pages)
        }
    }

    private fun renderCurrentPagePreview() {
        val page = _uiState.value.currentPage ?: return
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                val base = fileStore.decodeDownsampledBitmap(page.imagePath, maxDimensionPx = 1280)
                    ?: return@withContext null
                try {
                    processor.renderProcessedBitmap(
                        source = base,
                        rotationDegrees = page.rotation,
                        filter = page.filter,
                        adjustments = page.adjustments,
                        redactions = page.redactions,
                        signatureStrokes = page.signatureStrokes,
                        applyRedactions = true
                    )
                } finally {
                    base.recycle()
                }
            }
            _uiState.update { state ->
                state.previewBitmap?.recycle()
                state.copy(previewBitmap = rendered)
            }
        }
    }

    fun done() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            repository.renameDocument(documentId, _uiState.value.title)
            repository.updatePagesAndStats(documentId, _uiState.value.pages)
            _uiState.update { it.copy(isSaving = false) }
            _effects.send(EditorEffect.DoneAndExit)
        }
    }

    override fun onCleared() {
        _uiState.value.previewBitmap?.recycle()
        super.onCleared()
    }
}
