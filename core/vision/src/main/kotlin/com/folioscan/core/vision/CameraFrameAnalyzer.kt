package com.folioscan.core.vision

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.folioscan.core.model.QualityMetrics

/**
 * CameraX ImageAnalysis.Analyzer extracting the Y (luminance) plane without allocating
 * full RGB bitmaps, running OpenCV edge detection + Laplacian blur/glare/light checks.
 */
class CameraFrameAnalyzer(
    private val processor: OpenCvDocumentProcessor,
    private val autoCaptureEnabled: Boolean,
    private val onAnalysisResult: (metrics: QualityMetrics, quad: NormalizedQuad?, shouldAutoCapture: Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val stabilityTracker = AutoCaptureStabilityTracker()
    @Volatile
    private var isPaused = false

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) stabilityTracker.reset()
    }

    override fun analyze(image: ImageProxy) {
        if (isPaused) {
            image.close()
            return
        }
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = image.width
            val height = image.height

            val luma = ByteArray(width * height)
            if (rowStride == width) {
                buffer.get(luma, 0, luma.size)
            } else {
                val rowBuffer = ByteArray(rowStride)
                for (row in 0 until height) {
                    val toRead = minOf(rowStride, buffer.remaining())
                    buffer.get(rowBuffer, 0, toRead)
                    System.arraycopy(rowBuffer, 0, luma, row * width, width)
                }
            }

            val (metrics, quad) = processor.analyzePreviewLuma(luma, width, height)
            val triggerAuto = autoCaptureEnabled && stabilityTracker.update(
                metrics = metrics,
                corners = quad?.asList(),
                nowMs = System.currentTimeMillis()
            )
            if (triggerAuto) {
                stabilityTracker.reset()
            }
            onAnalysisResult(metrics, quad, triggerAuto)
        } finally {
            image.close()
        }
    }
}
