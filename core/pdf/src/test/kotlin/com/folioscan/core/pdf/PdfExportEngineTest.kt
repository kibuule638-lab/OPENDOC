package com.folioscan.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.folioscan.core.model.CompressionPreset
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.ExportConfig
import com.folioscan.core.model.ExportFormat
import com.folioscan.core.model.IdCardLayoutMode
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.storage.KeystoreCryptoManager
import com.folioscan.core.storage.ScanFileStore
import com.folioscan.core.vision.OpenCvDocumentProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfExportEngineTest {

    private lateinit var fileStore: ScanFileStore
    private lateinit var exportEngine: PdfExportEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fileStore = ScanFileStore(context, KeystoreCryptoManager(context))
        exportEngine = PdfExportEngine(context, fileStore, OpenCvDocumentProcessor())
    }

    @Test
    fun `exports 50 page stress PDF within memory budget`() = runTest {
        val docId = "stress-50"
        val bmp = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        bmp.recycle()

        val saved = fileStore.savePageBytes(bytes, docId, "p0", encryptAtRest = false)
        val pages = (0 until 50).map { idx ->
            ScanPage("p$idx", docId, idx, saved.imagePath, saved.thumbPath, 240, 320)
        }
        val doc = ScanDocument(
            id = docId,
            title = "Stress50",
            type = DocumentType.DOCUMENT,
            createdAt = 1L,
            updatedAt = 1L,
            pageCount = 50
        )

        val result = exportEngine.exportDocument(
            document = doc,
            pages = pages,
            config = ExportConfig(format = ExportFormat.PDF, compressionPreset = CompressionPreset.MEDIUM)
        )
        assertTrue(result.sizeBytes > 1000L)
        assertTrue(File(fileStore.exportTempFile(result.displayName).absolutePath).exists())
    }

    @Test
    fun `exports front and back ID card on single A4 PDF`() = runTest {
        val docId = "id-a4"
        val bmp = Bitmap.createBitmap(400, 252, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        bmp.recycle()

        val f = fileStore.savePageBytes(bytes, docId, "front", encryptAtRest = true)
        val b = fileStore.savePageBytes(bytes, docId, "back", encryptAtRest = true)

        val doc = ScanDocument(
            id = docId,
            title = "DriverLicense",
            type = DocumentType.ID_CARD,
            createdAt = 1L,
            updatedAt = 1L,
            pageCount = 2
        )
        val result = exportEngine.exportDocument(
            document = doc,
            pages = listOf(
                ScanPage("front", docId, 0, f.imagePath, f.thumbPath, 400, 252),
                ScanPage("back", docId, 1, b.imagePath, b.thumbPath, 400, 252)
            ),
            config = ExportConfig(
                format = ExportFormat.PDF,
                idCardLayout = IdCardLayoutMode.SINGLE_A4_TO_SCALE
            )
        )
        assertTrue(result.sizeBytes > 500L)
        assertTrue(result.displayName.endsWith(".pdf"))
    }

    @Test
    fun `exports 100 page stress PDF within memory budget`() = runTest {
        val docId = "stress-100"
        val bmp = Bitmap.createBitmap(180, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) }.toByteArray()
        bmp.recycle()

        val saved = fileStore.savePageBytes(bytes, docId, "p0", encryptAtRest = false)
        val pages = (0 until 100).map { idx ->
            ScanPage("p$idx", docId, idx, saved.imagePath, saved.thumbPath, 180, 240)
        }
        val doc = ScanDocument(
            id = docId,
            title = "Stress100",
            type = DocumentType.DOCUMENT,
            createdAt = 1L,
            updatedAt = 1L,
            pageCount = 100
        )

        val result = exportEngine.exportDocument(
            document = doc,
            pages = pages,
            config = ExportConfig(format = ExportFormat.PDF, compressionPreset = CompressionPreset.LOW)
        )
        assertTrue(result.sizeBytes > 2000L)
        assertTrue(File(fileStore.exportTempFile(result.displayName).absolutePath).exists())
    }

    @Test
    fun `cancelling mid-export cleans up partial PDF file`() = runTest {
        val docId = "cancel-test"
        val bmp = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        bmp.recycle()

        val saved = fileStore.savePageBytes(bytes, docId, "p0", encryptAtRest = false)
        val pages = (0 until 20).map { idx ->
            ScanPage("p$idx", docId, idx, saved.imagePath, saved.thumbPath, 160, 200)
        }
        val doc = ScanDocument(
            id = docId,
            title = "CancelMidExport",
            type = DocumentType.DOCUMENT,
            createdAt = 1L,
            updatedAt = 1L,
            pageCount = 20
        )

        var cancelled = false
        try {
            coroutineScope {
                lateinit var exportJob: Job
                exportJob = this@coroutineScope.launch {
                    exportEngine.exportDocument(
                        document = doc,
                        pages = pages,
                        config = ExportConfig(format = ExportFormat.PDF)
                    ) { progress ->
                        if (progress.completedPages >= 2) {
                            exportJob.cancel()
                        }
                    }
                }
                exportJob.join()
                cancelled = exportJob.isCancelled
            }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        val partialFiles = fileStore.exportTempFile("dummy").parentFile
            ?.listFiles { f -> f.name.startsWith("CancelMidExport") && f.name.endsWith(".pdf") }
            .orEmpty()
        assertTrue("Partial PDF files must be cleaned up on cancel", partialFiles.isEmpty())
    }
}
