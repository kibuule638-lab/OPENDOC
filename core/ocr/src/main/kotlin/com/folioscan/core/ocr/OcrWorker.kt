package com.folioscan.core.ocr

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.identity.BusinessCardParser
import com.folioscan.core.identity.MrzParser
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.IdentityData
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.storage.ScanFileStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Idempotent, resumable page-by-page OCR background worker.
 *
 * State machine: Captured -> Persisted -> OcrQueued -> OcrRunning -> OcrDone | OcrFailed.
 * If process death occurs mid-run, already-OCR'd pages keep their persisted PageTextEntity
 * and are skipped on retry.
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentRepository,
    private val fileStore: ScanFileStore,
    private val ocrEngine: OcrEngine
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_COMPLETED_PAGES = "completed_pages"
        const val KEY_TOTAL_PAGES = "total_pages"
        private const val MAX_RUN_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return@withContext Result.failure()
        val document = repository.getDocument(documentId) ?: return@withContext Result.failure()
        val pages = repository.getPages(documentId)
        if (pages.isEmpty()) {
            repository.updateOcrStatus(documentId, OcrStatus.DONE)
            return@withContext Result.success()
        }

        repository.updateOcrStatus(documentId, OcrStatus.RUNNING)
        val total = pages.size
        val combinedTextBuilder = StringBuilder()

        try {
            pages.forEachIndexed { idx, page ->
                val existingText = page.ocrText
                val text = if (!existingText.isNullOrEmpty()) {
                    existingText
                } else {
                    val bitmap = fileStore.decodeDownsampledBitmap(page.imagePath, maxDimensionPx = 1800)
                    if (bitmap != null) {
                        try {
                            ocrEngine.recognizeText(bitmap, page.rotation)
                        } finally {
                            bitmap.recycle()
                        }
                    } else {
                        ""
                    }
                }
                repository.savePageOcrText(page.id, documentId, text)
                if (text.isNotBlank()) {
                    if (combinedTextBuilder.isNotEmpty()) combinedTextBuilder.append('\n')
                    combinedTextBuilder.append(text)
                }
                setProgress(
                    workDataOf(
                        KEY_DOCUMENT_ID to documentId,
                        KEY_COMPLETED_PAGES to (idx + 1),
                        KEY_TOTAL_PAGES to total
                    )
                )
            }

            val fullText = combinedTextBuilder.toString()
            when (document.type) {
                DocumentType.PASSPORT -> {
                    val mrz = MrzParser.parseFromOcrText(fullText)
                    if (mrz != null) {
                        repository.saveIdentityData(
                            IdentityData(
                                documentId = documentId,
                                fields = mrz.toIdentityFields(),
                                mrzRaw = "${mrz.rawLine1}\n${mrz.rawLine2}",
                                mrzValid = mrz.allCheckDigitsValid
                            )
                        )
                    }
                }
                DocumentType.BUSINESS_CARD -> {
                    val card = BusinessCardParser.parse(fullText)
                    repository.saveIdentityData(
                        IdentityData(
                            documentId = documentId,
                            fields = card.toIdentityFields(),
                            mrzRaw = null,
                            mrzValid = card.emails.isNotEmpty() || card.phones.isNotEmpty()
                        )
                    )
                }
                else -> Unit
            }

            // Smart auto-naming if document still has a generic default prefix
            if (document.title.startsWith("Scan ") || document.title.startsWith("Untitled")) {
                val smartTitle = repository.generateSmartTitle(document.type, fullText)
                if (smartTitle != document.title) {
                    repository.renameDocument(documentId, smartTitle)
                }
            }

            repository.updateOcrStatus(documentId, OcrStatus.DONE)
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                Result.retry()
            } else {
                repository.updateOcrStatus(documentId, OcrStatus.FAILED)
                Result.failure(workDataOf("error" to (t.message ?: "OCR failed")))
            }
        }
    }
}

@Singleton
class OcrWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val repository: DocumentRepository
) {
    suspend fun enqueueDocumentOcr(documentId: String, replaceExisting: Boolean = false) {
        repository.updateOcrStatus(documentId, OcrStatus.QUEUED)
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(OcrWorker.KEY_DOCUMENT_ID to documentId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("ocr_$documentId")
            .build()

        workManager.enqueueUniqueWork(
            "ocr_$documentId",
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }
}
