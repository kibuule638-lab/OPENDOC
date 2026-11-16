package com.folioscan.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.model.Folder
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.SortOrder
import com.folioscan.core.model.ViewLayoutMode
import com.folioscan.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val documents: List<ScanDocument> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedMode: CaptureMode = CaptureMode.DOCUMENT,
    val sortOrder: SortOrder = SortOrder.UPDATED_DESC,
    val viewLayout: ViewLayoutMode = ViewLayoutMode.GRID,
    val selectedIds: Set<String> = emptySet(),
    val showOnboardingBanner: Boolean = false,
    val errorMessage: String? = null
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

sealed interface HomeEffect {
    data class ShowUndoTrashSnackbar(val count: Int, val trashedIds: List<String>) : HomeEffect
    data class ShowMessage(val message: String) : HomeEffect
    data class OpenDocument(val documentId: String) : HomeEffect
    data class OpenCapture(val mode: CaptureMode) : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val selectedMode = MutableStateFlow(CaptureMode.DOCUMENT)
    private val sortOrder = MutableStateFlow(SortOrder.UPDATED_DESC)
    private val viewLayout = MutableStateFlow(ViewLayoutMode.GRID)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val errorMessage = MutableStateFlow<String?>(null)

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeActiveDocuments().catch {
            errorMessage.value = it.message ?: "Unable to load documents"
            emit(emptyList())
        },
        repository.observeAllFolders(),
        settingsRepository.settingsFlow,
        combine(selectedMode, sortOrder, viewLayout, selectedIds, errorMessage) { m, s, v, sel, err ->
            Triple(m to s, v to sel, err)
        }
    ) { docs, folders, settings, uiParams ->
        val (modeSort, viewSel, err) = uiParams
        val (mode, sort) = modeSort
        val (layout, sel) = viewSel
        val sorted = when (sort) {
            SortOrder.UPDATED_DESC -> docs.sortedByDescending { it.updatedAt }
            SortOrder.CREATED_DESC -> docs.sortedByDescending { it.createdAt }
            SortOrder.TITLE_ASC -> docs.sortedBy { it.title.lowercase() }
            SortOrder.PAGE_COUNT_DESC -> docs.sortedByDescending { it.pageCount }
        }
        HomeUiState(
            isLoading = false,
            documents = sorted,
            folders = folders,
            selectedMode = mode,
            sortOrder = sort,
            viewLayout = layout,
            selectedIds = sel.intersect(sorted.map { it.id }.toSet()),
            showOnboardingBanner = !settings.hasCompletedOnboarding,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun selectMode(mode: CaptureMode) {
        selectedMode.value = mode
    }

    fun startScan(mode: CaptureMode = selectedMode.value) {
        selectedMode.value = mode
        viewModelScope.launch { _effects.send(HomeEffect.OpenCapture(mode)) }
    }

    fun setSortOrder(order: SortOrder) {
        sortOrder.value = order
    }

    fun toggleViewLayout() {
        viewLayout.value = if (viewLayout.value == ViewLayoutMode.GRID) {
            ViewLayoutMode.LIST
        } else {
            ViewLayoutMode.GRID
        }
    }

    fun toggleSelection(docId: String) {
        val current = selectedIds.value
        selectedIds.value = if (docId in current) current - docId else current + docId
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun dismissOnboarding() {
        viewModelScope.launch { settingsRepository.markOnboardingDone() }
    }

    fun deleteSelectedToTrash() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToTrash(ids)
            selectedIds.value = emptySet()
            _effects.send(HomeEffect.ShowUndoTrashSnackbar(ids.size, ids))
        }
    }

    fun undoTrash(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { repository.restoreFromTrash(it) }
        }
    }

    fun moveSelectedToFolder(folderId: String?) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveDocumentsToFolder(ids, folderId)
            selectedIds.value = emptySet()
            _effects.send(HomeEffect.ShowMessage("Moved ${ids.size} item(s)"))
        }
    }

    fun tagSelected(tagName: String) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty() || tagName.isBlank()) return
        viewModelScope.launch {
            ids.forEach { repository.attachTagToDocument(it, tagName) }
            selectedIds.value = emptySet()
            _effects.send(HomeEffect.ShowMessage("Tagged ${ids.size} item(s)"))
        }
    }

    fun mergeSelected() {
        val ids = selectedIds.value.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            val newId = repository.mergeDocuments(ids, "Merged (${ids.size} docs)")
            selectedIds.value = emptySet()
            if (newId != null) {
                _effects.send(HomeEffect.OpenDocument(newId))
            }
        }
    }

    fun batchRenameSelected(prefix: String) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty() || prefix.isBlank()) return
        viewModelScope.launch {
            repository.batchRename(ids, prefix.trim())
            selectedIds.value = emptySet()
            _effects.send(HomeEffect.ShowMessage("Renamed ${ids.size} document(s)"))
        }
    }
}
