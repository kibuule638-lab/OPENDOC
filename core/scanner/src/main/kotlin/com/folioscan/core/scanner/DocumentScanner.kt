package com.folioscan.core.scanner

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.ocr.OcrWorkScheduler
import com.folioscan.core.storage.ScanFileStore
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ScannerEngineAvailability {
    data object MlKitGmsAvailable : ScannerEngineAvailability
    data class CustomPipelineFallback(val reason: String) : ScannerEngineAvailability
}

data class ScannedCapturePageInput(
    val sourceUri: Uri?,
    val jpegBytes: ByteArray? = null,
    val filter: PageFilter = PageFilter.ORIGINAL
)

/**
 * Abstraction over capture engines (ML Kit Document Scanner on GMS devices vs.
 * custom CameraX + OpenCV pipeline for ID/Passport/Business Card and GMS-free devices).
 * Enables seamless swapping and faking in unit/UI tests.
 */
interface DocumentScanner {
    fun checkAvailability(mode: CaptureMode): ScannerEngineAvailability

    suspend fun prepareMlKitScannerIntent(
        activity: Activity,
        pageLimit: Int,
        allowGalleryImport: Boolean = true
    ): IntentSender

    suspend fun ingestCapturedPages(
        mode: CaptureMode,
        inputs: List<ScannedCapturePageInput>,
        folderId: String? = null,
        isVault: Boolean = false,
        titleOverride: String? = null
    ): String
}

@Singleton
class FolioDocumentScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: ScanFileStore,
    private val repository: DocumentRepository,
    private val ocrScheduler: OcrWorkScheduler
) : DocumentScanner {

    /**
     * Checks whether Google Play Services is available for ML Kit Document Scanner.
     *
     * Limitation: play-services-mlkit-document-scanner requires a GMS device (Play Services).
     * On de-Googled / AOSP / Huawei HMS devices or for specialized ID-1/ID-3/Business-Card
     * modes, FolioScan automatically routes to the custom CameraX + OpenCV pipeline.
     */
    override fun checkAvailability(mode: CaptureMode): ScannerEngineAvailability {
        if (mode != CaptureMode.DOCUMENT) {
            return ScannerEngineAvailability.CustomPipelineFallback(
                reason = "Specialized ${mode.displayLabel} mode uses custom CameraX + OpenCV frame guidance."
            )
        }
        val gmsStatus = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)

        return if (gmsStatus == ConnectionResult.SUCCESS) {
            ScannerEngineAvailability.MlKitGmsAvailable
        } else {
            ScannerEngineAvailability.CustomPipelineFallback(
                reason = "Google Play Services unavailable (code=$gmsStatus); using offline CameraX + OpenCV fallback."
            )
        }
    }

    override suspend fun prepareMlKitScannerIntent(
        activity: Activity,
        pageLimit: Int,
        allowGalleryImport: Boolean
    ): IntentSender = suspendCancellableCoroutine { cont ->
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(allowGalleryImport)
            .setPageLimit(pageLimit.coerceIn(1, 100))
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val client = GmsDocumentScanning.getClient(options)
        client.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                if (cont.isActive) cont.resume(sender)
            }
            .addOnFailureListener { exc ->
                if (cont.isActive) cont.resumeWithException(exc)
            }
    }

    /**
     * Immediately copies transient URIs/bytes into FolioScan's internal file store
     * (/files/scans/{docId}/{pageId}.jpg), persists Document + Pages atomically, and
     * queues offline page-by-page OCR.
     */
    override suspend fun ingestCapturedPages(
        mode: CaptureMode,
        inputs: List<ScannedCapturePageInput>,
        folderId: String?,
        isVault: Boolean,
        titleOverride: String?
    ): String {
        require(inputs.isNotEmpty()) { "Cannot persist empty scan" }
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val docType = when (mode) {
            CaptureMode.DOCUMENT -> DocumentType.DOCUMENT
            CaptureMode.ID_CARD, CaptureMode.OTHER_ID -> DocumentType.ID_CARD
            CaptureMode.PASSPORT -> DocumentType.PASSPORT
            CaptureMode.BUSINESS_CARD -> DocumentType.BUSINESS_CARD
        }
        val sensitiveAtRest = isVault || docType == DocumentType.ID_CARD || docType == DocumentType.PASSPORT

        val savedPages = inputs.mapIndexed { idx, item ->
            val pageId = UUID.randomUUID().toString()
            val saved = when {
                item.jpegBytes != null -> fileStore.savePageBytes(
                    jpegBytes = item.jpegBytes,
                    docId = docId,
                    pageId = pageId,
                    encryptAtRest = sensitiveAtRest
                )
                item.sourceUri != null -> fileStore.savePageFromUri(
                    sourceUri = item.sourceUri,
                    docId = docId,
                    pageId = pageId,
                    encryptAtRest = sensitiveAtRest
                )
                else -> throw IllegalArgumentException("Page input $idx has neither URI nor bytes")
            }
            ScanPage(
                id = pageId,
                documentId = docId,
                index = idx,
                imagePath = saved.imagePath,
                thumbPath = saved.thumbPath,
                width = saved.width,
                height = saved.height,
                filter = item.filter
            )
        }

        val doc = ScanDocument(
            id = docId,
            title = titleOverride ?: repository.generateSmartTitle(docType),
            type = docType,
            folderId = folderId,
            createdAt = now,
            updatedAt = now,
            pageCount = savedPages.size,
            ocrStatus = OcrStatus.QUEUED,
            isVault = isVault,
            coverThumbPath = savedPages.first().thumbPath,
            totalSizeBytes = savedPages.sumOf { java.io.File(it.imagePath).length() }
        )

        repository.persistCapturedDocument(doc, savedPages)
        ocrScheduler.enqueueDocumentOcr(docId)
        return docId
    }

    fun extractUrisFromMlKitResult(result: GmsDocumentScanningResult?): List<Uri> {
        return result?.pages?.map { it.imageUri }.orEmpty()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {
    @Binds
    abstract fun bindDocumentScanner(impl: FolioDocumentScanner): DocumentScanner
}
