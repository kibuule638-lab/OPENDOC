package com.folioscan.feature.detail

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.identity.BusinessCardParser
import com.folioscan.core.model.CompressionPreset
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.ExportConfig
import com.folioscan.core.model.ExportFormat
import com.folioscan.core.model.ExportResult
import com.folioscan.core.model.Folder
import com.folioscan.core.model.IdCardLayoutMode
import com.folioscan.core.model.IdentityData
import com.folioscan.core.model.IdentityField
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.model.WatermarkConfig
import com.folioscan.core.model.WatermarkPosition
import com.folioscan.core.ocr.OcrWorkScheduler
import com.folioscan.core.pdf.ExportProgress
import com.folioscan.core.pdf.PdfExportEngine
import com.folioscan.core.storage.AutoClearingClipboardManager
import com.folioscan.core.storage.ScanFileStore
import com.folioscan.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportSheetState(
    val visible: Boolean = false,
    val config: ExportConfig = ExportConfig(),
    val estimatedBytes: Long = 0L,
    val isExporting: Boolean = false,
    val progress: ExportProgress? = null,
    val result: ExportResult? = null,
    val errorMessage: String? = null
)

data class DetailUiState(
    val isLoading: Boolean = true,
    val document: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
    val identityData: IdentityData? = null,
    val folders: List<Folder> = emptyList(),
    val ocrSearchQuery: String = "",
    val exportState: ExportSheetState = ExportSheetState(),
    val clipboardClearSeconds: Int = 30
) {
    val showFieldsTab: Boolean
        get() = document?.type == DocumentType.PASSPORT || document?.type == DocumentType.BUSINESS_CARD

    val shouldProtectScreen: Boolean
        get() = document?.let {
            it.isVault || it.type == DocumentType.ID_CARD || it.type == DocumentType.PASSPORT
        } ?: false
}

sealed interface DetailEffect {
    data class ShowMessage(val message: String) : DetailEffect
    data class LaunchIntent(val intent: Intent) : DetailEffect
    data class ShowUndoTrashSnackbar(val documentId: String) : DetailEffect
    data object ExitDetail : DetailEffect
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val ocrScheduler: OcrWorkScheduler,
    private val exportEngine: PdfExportEngine,
    private val fileStore: ScanFileStore,
    private val clipboardManager: AutoClearingClipboardManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])
    private val openExportImmediately: Boolean = savedStateHandle.get<Boolean>("openExport") ?: false

    private val ocrSearchQuery = MutableStateFlow("")
    private val exportSheetState = MutableStateFlow(ExportSheetState(visible = openExportImmediately))
    private val _effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            exportSheetState.update {
                it.copy(
                    config = it.config.copy(
                        format = settings.defaultExportFormat,
                        compressionPreset = settings.defaultCompressionPreset,
                        jpegQuality = settings.defaultCompressionPreset.jpegQuality
                    )
                )
            }
        }
    }

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeDocument(documentId),
        repository.observePages(documentId),
        repository.observeIdentityData(documentId),
        repository.observeAllFolders(),
        combine(ocrSearchQuery, exportSheetState, settingsRepository.settingsFlow) { q, exp, settings ->
            val estimated = exp.copy(
                estimatedBytes = exp.estimatedBytes
            )
            Triple(q, estimated, settings.clipboardAutoClearSeconds)
        }
    ) { doc, pages, identity, folders, uiExtras ->
        val (q, exp, clearSec) = uiExtras
        val updatedExp = exp.copy(
            estimatedBytes = exportEngine.estimateExportSizeBytes(pages, exp.config)
        )
        DetailUiState(
            isLoading = false,
            document = doc,
            pages = pages,
            identityData = identity,
            folders = folders,
            ocrSearchQuery = q,
            exportState = updatedExp,
            clipboardClearSeconds = clearSec
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(isLoading = true)
    )

    fun setOcrSearchQuery(q: String) {
        ocrSearchQuery.value = q
    }

    fun copyOcrText() {
        val text = uiState.value.pages.mapNotNull { it.ocrText }.joinToString("\n\n")
        if (text.isBlank()) return
        clipboardManager.copySensitive(
            label = "FolioScan OCR Text",
            text = text,
            autoClearSeconds = uiState.value.clipboardClearSeconds
        )
        viewModelScope.launch {
            _effects.send(
                DetailEffect.ShowMessage(
                    "Copied OCR text (auto-clears in ${uiState.value.clipboardClearSeconds}s)"
                )
            )
        }
    }

    fun copyExtractedFields() {
        val fields = uiState.value.identityData?.fields.orEmpty()
        if (fields.isEmpty()) return
        val formatted = fields.joinToString("\n") { "${it.label}: ${it.value}" }
        clipboardManager.copySensitive(
            label = "FolioScan Extracted Fields",
            text = formatted,
            autoClearSeconds = uiState.value.clipboardClearSeconds
        )
        viewModelScope.launch {
            _effects.send(
                DetailEffect.ShowMessage(
                    "Copied fields (auto-clears in ${uiState.value.clipboardClearSeconds}s)"
                )
            )
        }
    }

    fun updateIdentityField(key: String, newValue: String) {
        val current = uiState.value.identityData ?: return
        val updatedFields = current.fields.map { f ->
            if (f.key == key) f.copy(value = newValue, isValid = newValue.isNotBlank(), validationNote = null) else f
        }
        viewModelScope.launch {
            repository.saveIdentityData(current.copy(fields = updatedFields))
        }
    }

    fun exportFieldsAsTxtOrVcf() {
        val doc = uiState.value.document ?: return
        val fields = uiState.value.identityData?.fields.orEmpty()
        if (fields.isEmpty()) return
        viewModelScope.launch {
            val isVcf = doc.type == DocumentType.BUSINESS_CARD
            val ext = if (isVcf) "vcf" else "txt"
            val mime = if (isVcf) "text/vcard" else "text/plain"
            val payload = if (isVcf) {
                buildVCard(fields)
            } else {
                fields.joinToString("\n") { "${it.label}: ${it.value}" }
            }
            val safeName = doc.title.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".$ext"
            val file = fileStore.exportTempFile(safeName)
            file.writeText(payload)
            fileStore.publishToMediaStore(file, safeName, mime)
            val shareUri = fileStore.getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _effects.send(DetailEffect.LaunchIntent(Intent.createChooser(intent, "Export $ext")))
        }
    }

    fun saveBusinessCardToContacts() {
        val fields = uiState.value.identityData?.fields.orEmpty()
        fun field(key: String) = fields.firstOrNull { it.key == key }?.value.orEmpty()
        val intent = BusinessCardParser.buildAddToContactsIntent(
            name = field("name"),
            company = field("company"),
            jobTitle = field("job_title"),
            phone = field("phone").substringBefore(",").trim(),
            email = field("email").substringBefore(",").trim()
        )
        viewModelScope.launch {
            _effects.send(DetailEffect.LaunchIntent(intent))
        }
    }

    private fun buildVCard(fields: List<IdentityField>): String {
        fun f(k: String) = fields.firstOrNull { it.key == k }?.value.orEmpty()
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:${f("name")}")
            if (f("company").isNotBlank()) appendLine("ORG:${f("company")}")
            if (f("job_title").isNotBlank()) appendLine("TITLE:${f("job_title")}")
            if (f("phone").isNotBlank()) appendLine("TEL;TYPE=WORK,VOICE:${f("phone").substringBefore(",").trim()}")
            if (f("email").isNotBlank()) appendLine("EMAIL;TYPE=PREF,INTERNET:${f("email").substringBefore(",").trim()}")
            if (f("website").isNotBlank()) appendLine("URL:${f("website")}")
            appendLine("END:VCARD")
        }
    }

    fun retryOcr() {
        viewModelScope.launch {
            ocrScheduler.enqueueDocumentOcr(documentId, replaceExisting = true)
            _effects.send(DetailEffect.ShowMessage("Queued offline OCR…"))
        }
    }

    fun skipOcr() {
        viewModelScope.launch {
            repository.updateOcrStatus(documentId, OcrStatus.DONE)
        }
    }

    fun rename(newTitle: String) {
        viewModelScope.launch { repository.renameDocument(documentId, newTitle) }
    }

    fun moveToFolder(folderId: String?) {
        viewModelScope.launch { repository.moveDocumentsToFolder(listOf(documentId), folderId) }
    }

    fun addTag(tagName: String) {
        viewModelScope.launch { repository.attachTagToDocument(documentId, tagName) }
    }

    fun removeTag(tagId: String) {
        viewModelScope.launch { repository.detachTagFromDocument(documentId, tagId) }
    }

    fun toggleFavorite() {
        val doc = uiState.value.document ?: return
        viewModelScope.launch { repository.toggleFavorite(documentId, !doc.isFavorite) }
    }

    fun toggleVault() {
        val doc = uiState.value.document ?: return
        viewModelScope.launch {
            val next = !doc.isVault
            repository.setVaultStatus(documentId, next)
            _effects.send(
                DetailEffect.ShowMessage(
                    if (next) "Moved to Private Vault" else "Removed from Private Vault"
                )
            )
        }
    }

    fun duplicate() {
        viewModelScope.launch {
            val copyId = repository.duplicateDocument(documentId)
            if (copyId != null) {
                _effects.send(DetailEffect.ShowMessage("Document duplicated"))
            }
        }
    }

    fun moveToTrash() {
        viewModelScope.launch {
            repository.moveToTrash(listOf(documentId))
            _effects.send(DetailEffect.ShowUndoTrashSnackbar(documentId))
        }
    }

    fun undoTrash() {
        viewModelScope.launch {
            repository.restoreFromTrash(documentId)
        }
    }

    fun showExportSheet() {
        exportSheetState.update { it.copy(visible = true, result = null, errorMessage = null) }
    }

    fun dismissExportSheet() {
        exportJob?.cancel()
        exportSheetState.update { it.copy(visible = false, isExporting = false) }
    }

    fun updateExportFormat(format: ExportFormat) {
        exportSheetState.update { it.copy(config = it.config.copy(format = format), result = null) }
    }

    fun updateExportQuality(quality: Int) {
        val preset = when {
            quality < 65 -> CompressionPreset.LOW
            quality < 86 -> CompressionPreset.MEDIUM
            else -> CompressionPreset.HIGH
        }
        exportSheetState.update {
            it.copy(
                config = it.config.copy(compressionPreset = preset, jpegQuality = quality),
                result = null
            )
        }
    }

    fun updatePassword(password: String?) {
        exportSheetState.update {
            it.copy(config = it.config.copy(password = password?.takeIf { p -> p.isNotBlank() }), result = null)
        }
    }

    fun updateWatermark(enabled: Boolean, text: String, opacity: Float, position: WatermarkPosition) {
        exportSheetState.update {
            it.copy(
                config = it.config.copy(
                    watermark = WatermarkConfig(enabled, text, opacity, position)
                ),
                result = null
            )
        }
    }

    fun updateApplyRedactions(apply: Boolean) {
        exportSheetState.update {
            it.copy(config = it.config.copy(applyRedactions = apply), result = null)
        }
    }

    fun updateIdCardLayout(mode: IdCardLayoutMode) {
        exportSheetState.update {
            it.copy(config = it.config.copy(idCardLayout = mode), result = null)
        }
    }

    fun runExport(onNeedForegroundExport: ((Int) -> Unit)? = null) {
        val doc = uiState.value.document ?: return
        val pages = uiState.value.pages
        if (pages.isEmpty()) return

        if (pages.size > PdfExportEngine.FOREGROUND_SERVICE_PAGE_THRESHOLD) {
            onNeedForegroundExport?.invoke(pages.size)
        }

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            exportSheetState.update { it.copy(isExporting = true, errorMessage = null, result = null) }
            try {
                val res = exportEngine.exportDocument(
                    document = doc,
                    pages = pages,
                    config = exportSheetState.value.config
                ) { progress ->
                    exportSheetState.update { it.copy(progress = progress) }
                }
                exportSheetState.update { it.copy(isExporting = false, result = res) }
            } catch (_: CancellationException) {
                exportSheetState.update { it.copy(isExporting = false, errorMessage = "Export cancelled") }
            } catch (t: Throwable) {
                exportSheetState.update {
                    it.copy(isExporting = false, errorMessage = t.message ?: "Export failed")
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    fun quickSharePdf() {
        val doc = uiState.value.document ?: return
        val pages = uiState.value.pages
        if (pages.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                exportEngine.exportDocument(doc, pages, exportSheetState.value.config)
            }.onSuccess { res ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = res.mimeType
                    putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(res.shareUriString))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _effects.send(DetailEffect.LaunchIntent(Intent.createChooser(intent, "Share ${doc.title}")))
            }.onFailure { err ->
                _effects.send(DetailEffect.ShowMessage(err.message ?: "Share failed"))
            }
        }
    }
}
