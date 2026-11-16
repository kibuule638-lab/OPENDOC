package com.folioscan.core.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.database.FolioScanDatabase
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.storage.KeystoreCryptoManager
import com.folioscan.core.storage.ScanFileStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OcrWorkerTest {

    private lateinit var context: Context
    private lateinit var db: FolioScanDatabase
    private lateinit var fileStore: ScanFileStore
    private lateinit var repository: DocumentRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = FolioScanDatabase.createInMemory(context)
        val crypto = KeystoreCryptoManager(context)
        fileStore = ScanFileStore(context, crypto)
        repository = DocumentRepository(
            db = db,
            documentDao = db.documentDao(),
            pageDao = db.pageDao(),
            folderAndTagDao = db.folderAndTagDao(),
            searchDao = db.searchDao(),
            identityDataDao = db.identityDataDao(),
            cryptoManager = crypto,
            fileStore = fileStore
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `worker is resumable after process death and skips already OCRd pages`() = runTest {
        val docId = "doc-resume"
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        bmp.recycle()

        val s1 = fileStore.savePageBytes(bytes, docId, "p1", encryptAtRest = false)
        val s2 = fileStore.savePageBytes(bytes, docId, "p2", encryptAtRest = false)

        repository.persistCapturedDocument(
            ScanDocument(
                id = docId,
                title = "Scan Test",
                type = DocumentType.DOCUMENT,
                createdAt = 1L,
                updatedAt = 1L,
                pageCount = 2,
                ocrStatus = OcrStatus.RUNNING
            ),
            listOf(
                ScanPage("p1", docId, 0, s1.imagePath, s1.thumbPath, 100, 100),
                ScanPage("p2", docId, 1, s2.imagePath, s2.thumbPath, 100, 100)
            )
        )
        // Simulate page 1 finished before process death
        repository.savePageOcrText("p1", docId, "Already recognized page 1")

        val fakeOcrEngine = mockk<OcrEngine>()
        coEvery { fakeOcrEngine.recognizeText(any(), any()) } returns "Recognized page 2"

        val worker = TestListenableWorkerBuilder<OcrWorker>(context)
            .setInputData(androidx.work.workDataOf(OcrWorker.KEY_DOCUMENT_ID to docId))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = OcrWorker(appContext, workerParameters, repository, fileStore, fakeOcrEngine)
            })
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { fakeOcrEngine.recognizeText(any(), any()) }
        val pages = repository.getPages(docId)
        assertEquals("Already recognized page 1", pages[0].ocrText)
        assertEquals("Recognized page 2", pages[1].ocrText)
        assertEquals(OcrStatus.DONE, repository.getDocument(docId)?.ocrStatus)
    }
}
