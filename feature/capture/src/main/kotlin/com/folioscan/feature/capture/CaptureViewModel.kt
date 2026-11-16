package com.folioscan.feature.capture

import android.app.Activity
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.QualityHintType
import com.folioscan.core.model.QualityMetrics
import com.folioscan.core.scanner.DocumentScanner
import com.folioscan.core.scanner.ScannedCapturePageInput
import com.folioscan.core.scanner.ScannerEngineAvailability
import com.folioscan.core.storage.SettingsRepository
import com.folioscan.core.vision.NormalizedQuad
import com.folioscan.core.vision.OpenCvDocumentProcessor
import com.folioscan.core.vision.QualityHeuristics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class PendingReviewCapture(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val qualityWarning: String?
)

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.DOCUMENT,
    val isVault: Boolean = false,
    val torchEnabled: Boolean = false,
    val flashAuto: Boolean = true,
    val autoCaptureEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val defaultFilter: PageFilter = PageFilter.MAGIC_COLOR,
    val pageLimit: Int = 50,
    val otherIdAspectRatio: Float = 1.586f,
    val currentStep: Int = 1,
    val totalSteps: Int = 1,
    val qualityMetrics: QualityMetrics = QualityMetrics(),
    val detectedQuad: NormalizedQuad? = null,
    val pendingReview: PendingReviewCapture? = null,
    val capturedPages: List<ByteArray> = emptyList(),
    val isProcessing: Boolean = false,
    val usingCustomFallbackBanner: String? = null
) {
    val stepLabel: String?
        get() = when {
            mode == CaptureMode.ID_CARD || mode == CaptureMode.OTHER_ID ->
                if (currentStep == 1) "Front 1/2" else "Back 2/2"
            totalSteps > 1 -> "Page $currentStep/$totalSteps"
            else -> null
        }
}

sealed interface CaptureEffect {
    data class LaunchMlKitScanner(val intentSender: IntentSender) : CaptureEffect
    data class ScanCompleted(val documentId: String) : CaptureEffect
    data class ShowError(val message: String) : CaptureEffect
    data object TriggerHaptic : CaptureEffect
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanner: DocumentScanner,
    private val processor: OpenCvDocumentProcessor,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val initialMode = savedStateHandle.get<String>("mode")
        ?.let { runCatching { CaptureMode.valueOf(it) }.getOrNull() }
        ?: CaptureMode.DOCUMENT
    private val initialVault = savedStateHandle.get<Boolean>("isVault") ?: false

    private val _uiState = MutableStateFlow(
        CaptureUiState(
            mode = initialMode,
            isVault = initialVault,
            totalSteps = if (initialMode == CaptureMode.ID_CARD || initialMode == CaptureMode.OTHER_ID) 2 else 1
        )
    )
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CaptureEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var mlKitLaunchedOnce = false

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val availability = scanner.checkAvailability(initialMode)
            val fallbackMsg = if (initialMode == CaptureMode.DOCUMENT &&
                availability is ScannerEngineAvailability.CustomPipelineFallback
            ) {
                availability.reason
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    autoCaptureEnabled = settings.autoCaptureEnabled,
                    hapticEnabled = settings.hapticOnCapture,
                    defaultFilter = settings.defaultScanFilter,
                    pageLimit = settings.defaultPageLimit,
                    otherIdAspectRatio = settings.otherIdAspectRatio,
                    usingCustomFallbackBanner = fallbackMsg
                )
            }
        }
    }

    fun tryLaunchMlKitIfSupported(activity: Activity) {
        if (mlKitLaunchedOnce) return
        val state = _uiState.value
        if (state.mode != CaptureMode.DOCUMENT) return
        val availability = scanner.checkAvailability(state.mode)
        if (availability !is ScannerEngineAvailability.MlKitGmsAvailable) return

        mlKitLaunchedOnce = true
        viewModelScope.launch {
            runCatching {
                scanner.prepareMlKitScannerIntent(
                    activity = activity,
                    pageLimit = state.pageLimit,
                    allowGalleryImport = true
                )
            }.onSuccess { sender ->
                _effects.send(CaptureEffect.LaunchMlKitScanner(sender))
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        usingCustomFallbackBanner = "ML Kit Scanner unavailable (${err.localizedMessage}); using offline CameraX fallback."
                    )
                }
            }
        }
    }

    fun onMlKitUrisReturned(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching {
                val filter = _uiState.value.defaultFilter
                scanner.ingestCapturedPages(
                    mode = _uiState.value.mode,
                    inputs = uris.map { ScannedCapturePageInput(sourceUri = it, filter = filter) },
                    isVault = _uiState.value.isVault
                )
            }.onSuccess { docId ->
                _uiState.update { it.copy(isProcessing = false) }
                _effects.send(CaptureEffect.ScanCompleted(docId))
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false) }
                _effects.send(CaptureEffect.ShowError(err.message ?: "Failed to save scanned pages"))
            }
        }
    }

    fun onFrameAnalyzed(metrics: QualityMetrics, quad: NormalizedQuad?, shouldAutoCapture: Boolean) {
        if (_uiState.value.pendingReview != null || _uiState.value.isProcessing) return
        _uiState.update {
            it.copy(
                qualityMetrics = if (shouldAutoCapture) {
                    metrics.copy(hint = QualityHintType.LOCKED, frameLocked = true)
                } else {
                    metrics
                },
                detectedQuad = quad
            )
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun toggleFlashAuto() {
        _uiState.update { it.copy(flashAuto = !it.flashAuto) }
    }

    fun toggleAutoCapture() {
        _uiState.update { it.copy(autoCaptureEnabled = !it.autoCaptureEnabled) }
    }

    fun processCapturedStillBitmap(rawBitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            if (_uiState.value.hapticEnabled) {
                _effects.send(CaptureEffect.TriggerHaptic)
            }
            val state = _uiState.value
            val review = withContext(Dispatchers.Default) {
                val warped = processor.warpToModeOutput(
                    source = rawBitmap,
                    mode = state.mode,
                    quad = state.detectedQuad,
                    customAspectRatio = state.otherIdAspectRatio
                )
                rawBitmap.recycle()

                val warning = if (state.qualityMetrics.isBlurry) {
                    "Image appears slightly blurry — consider retaking."
                } else if (state.qualityMetrics.hasGlare) {
                    "Strong glare detected — tilt slightly and retake if unreadable."
                } else {
                    null
                }
                val baos = ByteArrayOutputStream()
                warped.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                PendingReviewCapture(
                    bitmap = warped,
                    jpegBytes = baos.toByteArray(),
                    qualityWarning = warning
                )
            }
            _uiState.update { it.copy(pendingReview = review, isProcessing = false) }
        }
    }

    fun retakePending() {
        _uiState.value.pendingReview?.bitmap?.recycle()
        _uiState.update { it.copy(pendingReview = null) }
    }

    fun confirmPending() {
        val state = _uiState.value
        val pending = state.pendingReview ?: return
        val updatedPages = state.capturedPages + pending.jpegBytes
        pending.bitmap.recycle()

        val needsSecondSide = (state.mode == CaptureMode.ID_CARD || state.mode == CaptureMode.OTHER_ID) &&
            state.currentStep == 1

        if (needsSecondSide) {
            _uiState.update {
                it.copy(
                    capturedPages = updatedPages,
                    pendingReview = null,
                    currentStep = 2
                )
            }
        } else {
            finalizeCustomCapture(updatedPages)
        }
    }

    fun finishDocumentCapture() {
        val pending = _uiState.value.pendingReview
        val allPages = if (pending != null) {
            pending.bitmap.recycle()
            _uiState.value.capturedPages + pending.jpegBytes
        } else {
            _uiState.value.capturedPages
        }
        if (allPages.isNotEmpty()) {
            finalizeCustomCapture(allPages)
        }
    }

    private fun finalizeCustomCapture(pagesBytes: List<ByteArray>) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, pendingReview = null) }
            runCatching {
                scanner.ingestCapturedPages(
                    mode = state.mode,
                    inputs = pagesBytes.map { ScannedCapturePageInput(sourceUri = null, jpegBytes = it, filter = state.defaultFilter) },
                    isVault = state.isVault
                )
            }.onSuccess { docId ->
                _uiState.update { it.copy(isProcessing = false, capturedPages = emptyList()) }
                _effects.send(CaptureEffect.ScanCompleted(docId))
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false) }
                _effects.send(CaptureEffect.ShowError(err.message ?: "Unable to save scan"))
            }
        }
    }

    fun handleGalleryImportUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        onMlKitUrisReturned(uris)
    }

    override fun onCleared() {
        _uiState.value.pendingReview?.bitmap?.recycle()
        super.onCleared()
    }
}
