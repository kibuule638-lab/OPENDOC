package com.folioscan.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat {
    PDF,
    JPG
}

@Serializable
enum class CompressionPreset(val label: String, val jpegQuality: Int) {
    LOW("Low (Compact)", 55),
    MEDIUM("Medium (Balanced)", 78),
    HIGH("High (Crisp)", 92)
}

@Serializable
enum class WatermarkPosition(val label: String) {
    CENTER("Center"),
    TOP_LEFT("Top Left"),
    BOTTOM_RIGHT("Bottom Right"),
    DIAGONAL_TILE("Diagonal")
}

@Serializable
data class WatermarkConfig(
    val enabled: Boolean = false,
    val text: String = "CONFIDENTIAL",
    val opacity: Float = 0.25f,
    val position: WatermarkPosition = WatermarkPosition.CENTER
)

@Serializable
enum class IdCardLayoutMode(val label: String) {
    SINGLE_A4_TO_SCALE("Single A4 (To Scale)"),
    TWO_SEPARATE_PAGES("Two Separate Pages")
}

@Serializable
data class ExportConfig(
    val format: ExportFormat = ExportFormat.PDF,
    val compressionPreset: CompressionPreset = CompressionPreset.MEDIUM,
    val jpegQuality: Int = CompressionPreset.MEDIUM.jpegQuality,
    val password: String? = null,
    val watermark: WatermarkConfig = WatermarkConfig(),
    val applyRedactions: Boolean = true,
    val idCardLayout: IdCardLayoutMode = IdCardLayoutMode.SINGLE_A4_TO_SCALE
)

@Serializable
data class ExportResult(
    val outputUriString: String,
    val shareUriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val pageCount: Int
)

enum class QualityHintType(val message: String) {
    GOOD("Ready"),
    HOLD_STEADY("Hold steady…"),
    TOO_MUCH_GLARE("Too much glare"),
    LOW_LIGHT_TORCH("Low light — tap torch"),
    MOVE_CLOSER("Align inside frame"),
    LOCKED("Locked! Capturing…")
}

data class QualityMetrics(
    val laplacianVariance: Double = 0.0,
    val glareRatio: Double = 0.0,
    val meanLuminance: Double = 128.0,
    val isBlurry: Boolean = false,
    val hasGlare: Boolean = false,
    val isLowLight: Boolean = false,
    val frameLocked: Boolean = false,
    val hint: QualityHintType = QualityHintType.MOVE_CLOSER
)

enum class SortOrder(val label: String) {
    UPDATED_DESC("Recently updated"),
    CREATED_DESC("Newest created"),
    TITLE_ASC("Title A–Z"),
    PAGE_COUNT_DESC("Most pages")
}

enum class ViewLayoutMode {
    GRID,
    LIST
}

enum class ThemePreference(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val defaultPageLimit: Int = 50,
    val defaultScanFilter: PageFilter = PageFilter.MAGIC_COLOR,
    val autoCaptureEnabled: Boolean = true,
    val hapticOnCapture: Boolean = true,
    val defaultExportFormat: ExportFormat = ExportFormat.PDF,
    val defaultCompressionPreset: CompressionPreset = CompressionPreset.MEDIUM,
    val appLockEnabled: Boolean = false,
    val vaultBiometricEnabled: Boolean = true,
    val vaultPinConfigured: Boolean = false,
    val clipboardAutoClearSeconds: Int = 30,
    val otherIdAspectRatio: Float = 1.586f,
    val hasCompletedOnboarding: Boolean = false
)

data class SearchFilter(
    val type: DocumentType? = null,
    val folderId: String? = null,
    val tagId: String? = null,
    val fromTimestamp: Long? = null
)

data class SearchResultItem(
    val document: ScanDocument,
    val snippet: String,
    val matchedPageIndex: Int = 0
)
