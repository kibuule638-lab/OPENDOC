package com.folioscan.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentType {
    DOCUMENT,
    ID_CARD,
    PASSPORT,
    BUSINESS_CARD
}

@Serializable
enum class CaptureMode(
    val displayLabel: String,
    val aspectWidth: Float,
    val aspectHeight: Float
) {
    DOCUMENT("Document", 210f, 297f),           // ISO A4 portrait
    ID_CARD("ID Card", 85.6f, 53.98f),          // ISO/IEC 7810 ID-1
    PASSPORT("Passport", 125f, 88f),            // ISO/IEC 7810 ID-3
    BUSINESS_CARD("Business Card", 85f, 55f),   // Standard landscape card
    OTHER_ID("Other ID", 85.6f, 53.98f)         // Configurable ratio via settings
}

@Serializable
enum class OcrStatus {
    NONE,
    QUEUED,
    RUNNING,
    DONE,
    FAILED
}

sealed interface DocumentWorkflowState {
    data object Captured : DocumentWorkflowState
    data object Persisted : DocumentWorkflowState
    data object OcrQueued : DocumentWorkflowState
    data class OcrRunning(val completedPages: Int, val totalPages: Int) : DocumentWorkflowState
    data object OcrDone : DocumentWorkflowState
    data class OcrFailed(val message: String) : DocumentWorkflowState
}

@Serializable
enum class PageFilter(val label: String) {
    ORIGINAL("Original"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W Doc"),
    MAGIC_COLOR("Magic Color"),
    SHARPEN("Crisp")
}

@Serializable
data class PageAdjustments(
    val brightness: Float = 0f,   // -0.5f..0.5f
    val contrast: Float = 1f,     // 0.5f..2.0f
    val sharpness: Float = 0f     // 0f..1f
)

@Serializable
data class RedactionBox(
    val id: String,
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float
)

@Serializable
data class PointFraction(
    val x: Float,
    val y: Float
)

@Serializable
data class SignatureStroke(
    val points: List<PointFraction>,
    val strokeWidthDp: Float = 3f
)

@Serializable
data class ScanPage(
    val id: String,
    val documentId: String,
    val index: Int,
    val imagePath: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val filter: PageFilter = PageFilter.ORIGINAL,
    val adjustments: PageAdjustments = PageAdjustments(),
    val redactions: List<RedactionBox> = emptyList(),
    val signatureStrokes: List<SignatureStroke> = emptyList(),
    val ocrText: String? = null
)

@Serializable
data class Tag(
    val id: String,
    val name: String
)

@Serializable
data class Folder(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val itemCount: Int = 0,
    val createdAt: Long = 0L
)

@Serializable
data class ScanDocument(
    val id: String,
    val title: String,
    val type: DocumentType,
    val folderId: String? = null,
    val folderName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val pdfPath: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.NONE,
    val isVault: Boolean = false,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val coverThumbPath: String? = null,
    val totalSizeBytes: Long = 0L,
    val tags: List<Tag> = emptyList()
)
