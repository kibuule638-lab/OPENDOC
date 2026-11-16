package com.folioscan.core.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

class InsufficientStorageException(message: String) : IOException(message)

/**
 * Manages scoped internal file storage for scanned pages, 256px thumbnails,
 * AES-256-GCM encryption for sensitive identity documents, MediaStore exports,
 * FileProvider URIs, and local ZIP backup/restore.
 */
@Singleton
class ScanFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    companion object {
        const val THUMBNAIL_MAX_PX = 256
        private const val MIN_FREE_BYTES = 20L * 1024L * 1024L // 20 MB safety floor
        private const val ENCRYPTED_MAGIC_HEADER = "FSENC1"
    }

    private val scansRoot: File
        get() = File(context.filesDir, "scans").apply { mkdirs() }

    private val thumbsRoot: File
        get() = File(context.filesDir, "thumbs").apply { mkdirs() }

    private val exportsRoot: File
        get() = File(context.filesDir, "exports").apply { mkdirs() }

    fun ensureStorageAvailable(requiredBytes: Long = MIN_FREE_BYTES) {
        val availableBytes = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            if (stat.blockCountLong > 0L) stat.availableBytes else context.filesDir.usableSpace
        }.getOrElse { context.filesDir.usableSpace }
        if (availableBytes < requiredBytes) {
            throw InsufficientStorageException(
                "Low device storage (${availableBytes / (1024 * 1024)} MB free). Free up space to save scans."
            )
        }
    }

    fun pageImageFile(docId: String, pageId: String): File {
        val dir = File(scansRoot, docId).apply { mkdirs() }
        return File(dir, "$pageId.jpg")
    }

    fun pageThumbFile(docId: String, pageId: String): File {
        val dir = File(thumbsRoot, docId).apply { mkdirs() }
        return File(dir, "${pageId}_thumb.jpg")
    }

    fun exportTempFile(fileName: String): File {
        return File(exportsRoot, fileName)
    }

    /**
     * Copies a transient content:// or file:// URI immediately into internal app storage,
     * optionally encrypting at rest for sensitive ID/passport/vault documents, and generates
     * a 256px disk-cached thumbnail off the main thread.
     */
    suspend fun savePageFromUri(
        sourceUri: Uri,
        docId: String,
        pageId: String,
        encryptAtRest: Boolean,
        jpegQuality: Int = 90
    ): SavedPageFile = withContext(Dispatchers.IO) {
        ensureStorageAvailable()
        val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw IOException("Unable to read scanned image URI: $sourceUri")
        savePageBytes(
            jpegBytes = rawBytes,
            docId = docId,
            pageId = pageId,
            encryptAtRest = encryptAtRest,
            jpegQuality = jpegQuality
        )
    }

    suspend fun savePageBitmap(
        bitmap: Bitmap,
        docId: String,
        pageId: String,
        encryptAtRest: Boolean,
        jpegQuality: Int = 90
    ): SavedPageFile = withContext(Dispatchers.IO) {
        ensureStorageAvailable()
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
        savePageBytes(
            jpegBytes = baos.toByteArray(),
            docId = docId,
            pageId = pageId,
            encryptAtRest = encryptAtRest,
            jpegQuality = jpegQuality
        )
    }

    suspend fun savePageBytes(
        jpegBytes: ByteArray,
        docId: String,
        pageId: String,
        encryptAtRest: Boolean,
        jpegQuality: Int = 90
    ): SavedPageFile = withContext(Dispatchers.IO) {
        ensureStorageAvailable()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)

        val imageFile = pageImageFile(docId, pageId)
        val payloadToWrite = if (encryptAtRest) {
            val encrypted = cryptoManager.encryptBytes(jpegBytes)
            ENCRYPTED_MAGIC_HEADER.toByteArray(Charsets.US_ASCII) + encrypted
        } else {
            jpegBytes
        }
        imageFile.writeBytes(payloadToWrite)

        val thumbFile = pageThumbFile(docId, pageId)
        generateThumbnailFromBytes(jpegBytes, thumbFile, jpegQuality)

        SavedPageFile(
            imagePath = imageFile.absolutePath,
            thumbPath = thumbFile.absolutePath,
            width = max(1, bounds.outWidth),
            height = max(1, bounds.outHeight),
            sizeBytes = imageFile.length()
        )
    }

    private fun generateThumbnailFromBytes(
        jpegBytes: ByteArray,
        thumbFile: File,
        quality: Int = 82
    ) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
        val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sampleSize = 1
        while (maxDim / sampleSize > THUMBNAIL_MAX_PX * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts) ?: return
        try {
            val scale = THUMBNAIL_MAX_PX.toFloat() / max(decoded.width, decoded.height).coerceAtLeast(1)
            val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }
            try {
                thumbFile.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(70, 88), out)
                }
            } finally {
                if (scaled !== decoded) {
                    scaled.recycle()
                }
            }
        } finally {
            decoded.recycle()
        }
    }

    /**
     * Reads raw JPEG bytes for a stored page, transparently decrypting if encrypted at rest.
     */
    suspend fun readPageBytes(imagePath: String): ByteArray = withContext(Dispatchers.IO) {
        val file = File(imagePath)
        if (!file.exists()) throw IOException("Scan file missing: $imagePath")
        val raw = file.readBytes()
        val header = ENCRYPTED_MAGIC_HEADER.toByteArray(Charsets.US_ASCII)
        if (raw.size > header.size && raw.copyOfRange(0, header.size).contentEquals(header)) {
            cryptoManager.decryptBytes(raw.copyOfRange(header.size, raw.size))
        } else {
            raw
        }
    }

    /**
     * Decodes a memory-bounded downsampled Bitmap for previewing or PDF streaming without holding
     * full-resolution bitmaps in heap.
     */
    suspend fun decodeDownsampledBitmap(imagePath: String, maxDimensionPx: Int = 1600): Bitmap? =
        withContext(Dispatchers.IO) {
            val bytes = runCatching { readPageBytes(imagePath) }.getOrNull() ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val largest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (largest / sampleSize > maxDimensionPx) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }

    suspend fun deleteDocumentFiles(docId: String) = withContext(Dispatchers.IO) {
        File(scansRoot, docId).deleteRecursively()
        File(thumbsRoot, docId).deleteRecursively()
    }

    suspend fun deletePageFiles(docId: String, pageId: String) = withContext(Dispatchers.IO) {
        pageImageFile(docId, pageId).delete()
        pageThumbFile(docId, pageId).delete()
    }

    fun getFileProviderUri(file: File): Uri {
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrElse { Uri.fromFile(file) }
    }

    /**
     * Publishes an exported PDF or JPEG into user-visible MediaStore (Downloads / Pictures).
     */
    suspend fun publishToMediaStore(
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = if (mimeType.startsWith("image/")) {
                    "${Environment.DIRECTORY_PICTURES}/FolioScan"
                } else {
                    "${Environment.DIRECTORY_DOWNLOADS}/FolioScan"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (mimeType.startsWith("image/")) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = context.contentResolver.insert(collection, values)
            ?: return@withContext getFileProviderUri(sourceFile)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, doneValues, null, null)
        }
        uri
    }

    /**
     * Creates a local offline backup ZIP archive containing the database and scans directory.
     */
    suspend fun exportLocalBackupZip(destinationUri: Uri): Long = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                val dirsToBackup = listOf(scansRoot, thumbsRoot)
                dirsToBackup.forEach { root ->
                    root.walkTopDown().filter { it.isFile }.forEach { file ->
                        val rel = file.relativeTo(context.filesDir).path
                        zip.putNextEntry(ZipEntry(rel))
                        totalBytes += file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                val dbFile = context.getDatabasePath("folioscan.db")
                if (dbFile.exists()) {
                    zip.putNextEntry(ZipEntry("database/folioscan.db"))
                    totalBytes += dbFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IOException("Unable to open backup destination")
        totalBytes
    }

    suspend fun restoreLocalBackupZip(sourceUri: Uri): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val safeName = entry.name.replace("..", "")
                        val target = if (safeName.startsWith("database/")) {
                            context.getDatabasePath("folioscan.db")
                        } else {
                            File(context.filesDir, safeName)
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> zip.copyTo(out) }
                        restoredCount++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IOException("Unable to read backup archive")
        restoredCount
    }

    suspend fun clearAllScanFiles() = withContext(Dispatchers.IO) {
        scansRoot.deleteRecursively()
        thumbsRoot.deleteRecursively()
        exportsRoot.deleteRecursively()
        scansRoot.mkdirs()
        thumbsRoot.mkdirs()
        exportsRoot.mkdirs()
    }

    fun calculateTotalStorageBytes(): Long {
        val scanBytes = scansRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val thumbBytes = thumbsRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return scanBytes + thumbBytes
    }
}

data class SavedPageFile(
    val imagePath: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)
