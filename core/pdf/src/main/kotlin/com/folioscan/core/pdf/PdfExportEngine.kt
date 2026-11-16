package com.folioscan.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.ExportConfig
import com.folioscan.core.model.ExportFormat
import com.folioscan.core.model.ExportResult
import com.folioscan.core.model.IdCardLayoutMode
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.model.WatermarkConfig
import com.folioscan.core.model.WatermarkPosition
import com.folioscan.core.storage.ScanFileStore
import com.folioscan.core.vision.OpenCvDocumentProcessor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

data class ExportProgress(
    val completedPages: Int,
    val totalPages: Int,
    val stageLabel: String
)

/**
 * Streaming, memory-bounded PDF & JPEG export engine.
 *
 * - Writes pages via Android PdfDocument one downsampled page at a time (peak heap < 200MB even at 50-100 pages)
 * - Uses PdfBox-Android for AES-256 PDF password protection and multi-PDF merge (no iText/AGPL)
 * - Supports ID Card 1:1 scale front+back layout on a single A4 page (85.6mm x 53.98mm -> 242.6pt x 153.0pt)
 */
@Singleton
class PdfExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: ScanFileStore,
    private val processor: OpenCvDocumentProcessor
) {
    companion object {
        // A4 @ 72 DPI (PDF points)
        const val A4_WIDTH_PT = 595
        const val A4_HEIGHT_PT = 842

        // ISO ID-1 (85.6mm x 53.98mm) converted to 72pt/inch (1mm = 2.83465pt)
        const val ID1_WIDTH_PT = 242.6f
        const val ID1_HEIGHT_PT = 153.0f

        const val FOREGROUND_SERVICE_PAGE_THRESHOLD = 10
    }

    private val pdfBoxReady = AtomicBoolean(false)

    private fun ensurePdfBoxInitialized() {
        if (pdfBoxReady.compareAndSet(false, true)) {
            runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        }
    }

    fun estimateExportSizeBytes(pages: List<ScanPage>, config: ExportConfig): Long {
        if (pages.isEmpty()) return 0L
        val qualityFactor = (config.jpegQuality / 100.0).coerceIn(0.25, 1.0)
        val rawSum = pages.sumOf { File(it.imagePath).length().coerceAtLeast(80_000L) }
        val formatMultiplier = if (config.format == ExportFormat.PDF) 1.08 else 1.0
        return (rawSum * qualityFactor * formatMultiplier).toLong().coerceAtLeast(12_000L)
    }

    suspend fun exportDocument(
        document: ScanDocument,
        pages: List<ScanPage>,
        config: ExportConfig,
        onProgress: (ExportProgress) -> Unit = {}
    ): ExportResult = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "Cannot export a document with zero pages" }

        val safeTitle = document.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifEmpty { "FolioScan" }
        val stamp = System.currentTimeMillis()

        return@withContext when (config.format) {
            ExportFormat.JPG -> exportAsJpeg(safeTitle, stamp, pages, config, onProgress)
            ExportFormat.PDF -> exportAsPdf(document, safeTitle, stamp, pages, config, onProgress)
        }
    }

    private suspend fun exportAsJpeg(
        safeTitle: String,
        stamp: Long,
        pages: List<ScanPage>,
        config: ExportConfig,
        onProgress: (ExportProgress) -> Unit
    ): ExportResult {
        var lastFile: File? = null
        var lastUriString = ""
        var totalSize = 0L

        pages.forEachIndexed { idx, page ->
            coroutineContext.ensureActive()
            onProgress(ExportProgress(idx, pages.size, "Rendering page ${idx + 1}/${pages.size}"))

            val bitmap = renderExportBitmap(page, config)
            val fileName = if (pages.size == 1) "${safeTitle}_$stamp.jpg" else "${safeTitle}_p${idx + 1}_$stamp.jpg"
            val outFile = fileStore.exportTempFile(fileName)
            try {
                outFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality.coerceIn(30, 100), out)
                }
            } finally {
                bitmap.recycle()
            }
            val publishedUri = fileStore.publishToMediaStore(outFile, fileName, "image/jpeg")
            lastFile = outFile
            lastUriString = publishedUri.toString()
            totalSize += outFile.length()
        }

        onProgress(ExportProgress(pages.size, pages.size, "Export complete"))
        val finalFile = lastFile!!
        return ExportResult(
            outputUriString = lastUriString,
            shareUriString = fileStore.getFileProviderUri(finalFile).toString(),
            displayName = finalFile.name,
            sizeBytes = totalSize,
            mimeType = "image/jpeg",
            pageCount = pages.size
        )
    }

    private suspend fun exportAsPdf(
        document: ScanDocument,
        safeTitle: String,
        stamp: Long,
        pages: List<ScanPage>,
        config: ExportConfig,
        onProgress: (ExportProgress) -> Unit
    ): ExportResult {
        val pdfDoc = PdfDocument()
        val rawPdfFile = fileStore.exportTempFile("${safeTitle}_$stamp.pdf")

        try {
            if (document.type == DocumentType.ID_CARD &&
                config.idCardLayout == IdCardLayoutMode.SINGLE_A4_TO_SCALE &&
                pages.size >= 2
            ) {
                coroutineContext.ensureActive()
                onProgress(ExportProgress(0, pages.size, "Composing ID card A4 sheet…"))
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, 1).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas
                canvas.drawColor(Color.WHITE)

                val left = (A4_WIDTH_PT - ID1_WIDTH_PT) / 2f
                val verticalGap = 42f
                val totalCardsH = ID1_HEIGHT_PT * 2f + verticalGap
                val top1 = (A4_HEIGHT_PT - totalCardsH) / 2f
                val top2 = top1 + ID1_HEIGHT_PT + verticalGap

                drawCompressedPageOnCanvas(canvas, pages[0], RectF(left, top1, left + ID1_WIDTH_PT, top1 + ID1_HEIGHT_PT), config)
                drawCompressedPageOnCanvas(canvas, pages[1], RectF(left, top2, left + ID1_WIDTH_PT, top2 + ID1_HEIGHT_PT), config)

                pdfDoc.finishPage(pdfPage)
                onProgress(ExportProgress(pages.size, pages.size, "Finalizing PDF…"))
            } else {
                pages.forEachIndexed { idx, page ->
                    coroutineContext.ensureActive()
                    onProgress(ExportProgress(idx, pages.size, "Writing page ${idx + 1}/${pages.size}…"))
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, idx + 1).create()
                    val pdfPage = pdfDoc.startPage(pageInfo)
                    val canvas = pdfPage.canvas
                    canvas.drawColor(Color.WHITE)
                    val margin = 24f
                    drawCompressedPageOnCanvas(
                        canvas = canvas,
                        page = page,
                        bounds = RectF(margin, margin, A4_WIDTH_PT - margin, A4_HEIGHT_PT - margin),
                        config = config
                    )
                    pdfDoc.finishPage(pdfPage)
                }
            }

            rawPdfFile.outputStream().use { out ->
                pdfDoc.writeTo(out)
            }
        } catch (e: IllegalStateException) {
            // Native Skia PdfDocument JNI is uninitialized in headless JVM/Robolectric tests;
            // fall back cleanly to pure-JVM PdfBox rendering.
            if (e.message?.contains("closed", ignoreCase = true) == true) {
                rawPdfFile.delete()
                writePdfWithPdfBox(document, pages, config, rawPdfFile, onProgress)
            } else {
                rawPdfFile.delete()
                throw e
            }
        } catch (t: Throwable) {
            rawPdfFile.delete()
            throw t
        } finally {
            runCatching { pdfDoc.close() }
        }

        // Optional AES-256 encryption via PdfBox-Android
        val finalPdfFile = if (!config.password.isNullOrBlank()) {
            onProgress(ExportProgress(pages.size, pages.size, "Encrypting PDF (AES-256)…"))
            encryptPdfAes256(rawPdfFile, config.password!!)
        } else {
            rawPdfFile
        }

        val publishedUri = fileStore.publishToMediaStore(finalPdfFile, finalPdfFile.name, "application/pdf")
        onProgress(ExportProgress(pages.size, pages.size, "Export complete"))
        return ExportResult(
            outputUriString = publishedUri.toString(),
            shareUriString = fileStore.getFileProviderUri(finalPdfFile).toString(),
            displayName = finalPdfFile.name,
            sizeBytes = finalPdfFile.length(),
            mimeType = "application/pdf",
            pageCount = pages.size
        )
    }

    private suspend fun writePdfWithPdfBox(
        document: ScanDocument,
        pages: List<ScanPage>,
        config: ExportConfig,
        outputFile: File,
        onProgress: (ExportProgress) -> Unit
    ) {
        ensurePdfBoxInitialized()
        PDDocument().use { pdDoc ->
            try {
                if (document.type == DocumentType.ID_CARD &&
                    config.idCardLayout == IdCardLayoutMode.SINGLE_A4_TO_SCALE &&
                    pages.size >= 2
                ) {
                    coroutineContext.ensureActive()
                    onProgress(ExportProgress(0, pages.size, "Composing ID card A4 sheet…"))
                    val page = PDPage(PDRectangle(A4_WIDTH_PT.toFloat(), A4_HEIGHT_PT.toFloat()))
                    pdDoc.addPage(page)

                    val left = (A4_WIDTH_PT - ID1_WIDTH_PT) / 2f
                    val verticalGap = 42f
                    val totalCardsH = ID1_HEIGHT_PT * 2f + verticalGap
                    // PDFBox coordinate origin is bottom-left
                    val bottom2 = (A4_HEIGHT_PT - totalCardsH) / 2f
                    val bottom1 = bottom2 + ID1_HEIGHT_PT + verticalGap

                    PDPageContentStream(pdDoc, page).use { cs ->
                        drawPageInPdfBoxStream(pdDoc, cs, pages[0], left, bottom1, ID1_WIDTH_PT, ID1_HEIGHT_PT, config)
                        drawPageInPdfBoxStream(pdDoc, cs, pages[1], left, bottom2, ID1_WIDTH_PT, ID1_HEIGHT_PT, config)
                    }
                    onProgress(ExportProgress(pages.size, pages.size, "Finalizing PDF…"))
                } else {
                    pages.forEachIndexed { idx, scanPage ->
                        coroutineContext.ensureActive()
                        onProgress(ExportProgress(idx, pages.size, "Writing page ${idx + 1}/${pages.size}…"))
                        val page = PDPage(PDRectangle(A4_WIDTH_PT.toFloat(), A4_HEIGHT_PT.toFloat()))
                        pdDoc.addPage(page)
                        val margin = 24f
                        PDPageContentStream(pdDoc, page).use { cs ->
                            drawPageInPdfBoxStream(
                                pdDoc = pdDoc,
                                cs = cs,
                                scanPage = scanPage,
                                boxLeft = margin,
                                boxBottom = margin,
                                boxW = A4_WIDTH_PT - margin * 2f,
                                boxH = A4_HEIGHT_PT - margin * 2f,
                                config = config
                            )
                        }
                    }
                }
                pdDoc.save(outputFile)
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            }
        }
    }

    private suspend fun drawPageInPdfBoxStream(
        pdDoc: PDDocument,
        cs: PDPageContentStream,
        scanPage: ScanPage,
        boxLeft: Float,
        boxBottom: Float,
        boxW: Float,
        boxH: Float,
        config: ExportConfig
    ) {
        val rendered = renderExportBitmap(scanPage, config)
        val aspect = rendered.width.toFloat() / rendered.height.toFloat().coerceAtLeast(1f)
        val jpegBytes = try {
            ByteArrayOutputStream().also { baos ->
                rendered.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality.coerceIn(30, 100), baos)
            }.toByteArray()
        } finally {
            rendered.recycle()
        }
        val pdImage = JPEGFactory.createFromByteArray(pdDoc, jpegBytes)
        val drawW: Float
        val drawH: Float
        if (boxW / boxH > aspect) {
            drawH = boxH
            drawW = drawH * aspect
        } else {
            drawW = boxW
            drawH = drawW / aspect
        }
        val x = boxLeft + (boxW - drawW) / 2f
        val y = boxBottom + (boxH - drawH) / 2f
        cs.drawImage(pdImage, x, y, drawW, drawH)
    }

    private suspend fun renderExportBitmap(page: ScanPage, config: ExportConfig): Bitmap {
        val decoded = fileStore.decodeDownsampledBitmap(page.imagePath, maxDimensionPx = 1600)
            ?: Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return try {
            val processed = processor.renderProcessedBitmap(
                source = decoded,
                rotationDegrees = page.rotation,
                filter = page.filter,
                adjustments = page.adjustments,
                redactions = page.redactions,
                signatureStrokes = page.signatureStrokes,
                applyRedactions = config.applyRedactions
            )
            if (config.watermark.enabled && config.watermark.text.isNotBlank()) {
                drawWatermark(processed, config.watermark)
            }
            processed
        } finally {
            decoded.recycle()
        }
    }

    private suspend fun drawCompressedPageOnCanvas(
        canvas: Canvas,
        page: ScanPage,
        bounds: RectF,
        config: ExportConfig
    ) {
        val rendered = renderExportBitmap(page, config)
        val compressedBitmap = try {
            val baos = ByteArrayOutputStream()
            rendered.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality.coerceIn(30, 100), baos)
            val bytes = baos.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: rendered
        } finally {
            // Only recycle rendered if a separate compressedBitmap was decoded
        }

        try {
            val aspect = compressedBitmap.width.toFloat() / compressedBitmap.height.toFloat().coerceAtLeast(1f)
            val boxW = bounds.width()
            val boxH = bounds.height()
            val drawW: Float
            val drawH: Float
            if (boxW / boxH > aspect) {
                drawH = boxH
                drawW = drawH * aspect
            } else {
                drawW = boxW
                drawH = drawW / aspect
            }
            val left = bounds.left + (boxW - drawW) / 2f
            val top = bounds.top + (boxH - drawH) / 2f
            canvas.drawBitmap(compressedBitmap, null, RectF(left, top, left + drawW, top + drawH), null)
        } finally {
            if (compressedBitmap !== rendered) compressedBitmap.recycle()
            rendered.recycle()
        }
    }

    private fun drawWatermark(bitmap: Bitmap, watermark: WatermarkConfig) {
        val canvas = Canvas(bitmap)
        val alphaInt = (watermark.opacity.coerceIn(0.05f, 0.85f) * 255).roundToInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alphaInt, 30, 41, 59)
            textSize = (bitmap.width / 14f).coerceIn(24f, 96f)
            isFakeBoldText = true
        }

        when (watermark.position) {
            WatermarkPosition.CENTER -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.save()
                canvas.rotate(-30f, bitmap.width / 2f, bitmap.height / 2f)
                canvas.drawText(watermark.text, bitmap.width / 2f, bitmap.height / 2f, paint)
                canvas.restore()
            }
            WatermarkPosition.TOP_LEFT -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(watermark.text, 36f, 36f + paint.textSize, paint)
            }
            WatermarkPosition.BOTTOM_RIGHT -> {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(watermark.text, bitmap.width - 36f, bitmap.height - 36f, paint)
            }
            WatermarkPosition.DIAGONAL_TILE -> {
                paint.textAlign = Paint.Align.CENTER
                paint.textSize *= 0.72f
                canvas.save()
                canvas.rotate(-30f, bitmap.width / 2f, bitmap.height / 2f)
                val stepX = bitmap.width / 2.2f
                val stepY = bitmap.height / 3.5f
                var y = -bitmap.height * 0.25f
                while (y < bitmap.height * 1.25f) {
                    var x = -bitmap.width * 0.25f
                    while (x < bitmap.width * 1.25f) {
                        canvas.drawText(watermark.text, x, y, paint)
                        x += stepX
                    }
                    y += stepY
                }
                canvas.restore()
            }
        }
    }

    private fun encryptPdfAes256(inputFile: File, password: String): File {
        ensurePdfBoxInitialized()
        val encryptedFile = fileStore.exportTempFile(inputFile.nameWithoutExtension + "_protected.pdf")
        PDDocument.load(inputFile).use { pdDoc ->
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(password, password, ap).apply {
                encryptionKeyLength = 256
                permissions = ap
            }
            pdDoc.protect(spp)
            pdDoc.save(encryptedFile)
        }
        inputFile.delete()
        return encryptedFile
    }

    suspend fun mergePdfFiles(inputFiles: List<File>, outputName: String): File = withContext(Dispatchers.IO) {
        ensurePdfBoxInitialized()
        val outFile = fileStore.exportTempFile(outputName)
        val merger = PDFMergerUtility()
        merger.destinationFileName = outFile.absolutePath
        inputFiles.forEach { merger.addSource(it) }
        merger.mergeDocuments(null)
        outFile
    }
}
