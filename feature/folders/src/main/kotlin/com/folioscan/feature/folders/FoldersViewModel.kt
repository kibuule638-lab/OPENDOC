package com.folioscan.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.model.Folder
import com.folioscan.core.model.ScanDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoldersUiState(
    val isLoading: Boolean = true,
    val currentFolderId: String? = null,
    val breadcrumb: List<Folder> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val documents: List<ScanDocument> = emptyList(),
    val trashedDocuments: List<ScanDocument> = emptyList(),
    val viewingTrash: Boolean = false,
    val selectedDocumentIds: Set<String> = emptySet()
) {
    val inSelectionMode: Boolean get() = selectedDocumentIds.isNotEmpty()
}

sealed interface FoldersEffect {
    data class ShowUndoTrashSnackbar(val ids: List<String>) : FoldersEffect
    data class ShowMessage(val message: String) : FoldersEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val currentFolderId = MutableStateFlow<String?>(null)
    private val viewingTrash = MutableStateFlow(false)
    private val breadcrumb = MutableStateFlow<List<Folder>>(emptyList())
    private val selectedDocIds = MutableStateFlow<Set<String>>(emptySet())

    private val _effects = Channel<FoldersEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val subFoldersFlow = currentFolderId.flatMapLatest { fid ->
        repository.observeFolders(fid)
    }

    private val folderDocsFlow = currentFolderId.flatMapLatest { fid ->
        if (fid == null) flowOf(emptyList()) else repository.observeDocumentsInFolder(fid)
    }

    val uiState: StateFlow<FoldersUiState> = combine(
        currentFolderId,
        breadcrumb,
        subFoldersFlow,
        folderDocsFlow,
        combine(
            repository.observeTrashedDocuments(),
            viewingTrash,
            selectedDocIds
        ) { trash, inTrash, sel -> Triple(trash, inTrash, sel) }
    ) { fid, crumbs, folders, docs, extras ->
        val (trash, inTrash, sel) = extras
        FoldersUiState(
            isLoading = false,
            currentFolderId = fid,
            breadcrumb = crumbs,
            folders = folders,
            documents = docs,
            trashedDocuments = trash,
            viewingTrash = inTrash,
            selectedDocumentIds = sel
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FoldersUiState(isLoading = true)
    )

    fun openFolder(folderId: String?) {
        viewingTrash.value = false
        selectedDocIds.value = emptySet()
        currentFolderId.value = folderId
        viewModelScope.launch {
            breadcrumb.value = repository.getFolderBreadcrumb(folderId)
        }
    }

    fun openTrash() {
        viewingTrash.value = true
        selectedDocIds.value = emptySet()
    }

    fun closeTrash() {
        viewingTrash.value = false
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createFolder(name, currentFolderId.value)
        }
    }

    fun toggleDocumentSelection(docId: String) {
        val cur = selectedDocIds.value
        selectedDocIds.value = if (docId in cur) cur - docId else cur + docId
    }

    fun clearSelection() {
        selectedDocIds.value = emptySet()
    }

    fun trashSelected() {
        val ids = selectedDocIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToTrash(ids)
            selectedDocIds.value = emptySet()
            _effects.send(FoldersEffect.ShowUndoTrashSnackbar(ids))
        }
    }

    fun undoTrash(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { repository.restoreFromTrash(it) }
        }
    }

    fun restoreFromTrash(docId: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(docId)
            _effects.send(FoldersEffect.ShowMessage("Restored document"))
        }
    }

    fun deletePermanently(docId: String) {
        viewModelScope.launch {
            repository.deletePermanently(docId)
            _effects.send(FoldersEffect.ShowMessage("Permanently deleted"))
        }
    }
}
