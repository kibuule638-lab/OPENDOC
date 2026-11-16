package com.folioscan.core.vision

import com.folioscan.core.model.QualityHintType
import com.folioscan.core.model.QualityMetrics
import kotlin.math.abs

/**
 * Pure pixel-level & OpenCV-backed quality heuristics for real-time camera preview frames
 * and captured still verification.
 *
 * Metrics:
 * - Blur: Variance of Laplacian (low variance => blurry)
 * - Glare: Fraction of pixels with luma >= 248 (saturated highlights)
 * - Low Light: Mean luminance < 65 triggers torch suggestion
 * - Stability: Tracks consecutive sharp, aligned frames for >= 600ms auto-capture lock
 */
object QualityHeuristics {
    const val BLUR_VARIANCE_THRESHOLD = 95.0
    const val GLARE_RATIO_THRESHOLD = 0.06
    const val LOW_LIGHT_LUMINANCE_THRESHOLD = 65.0
    const val STABILITY_HOLD_MS = 600L

    fun evaluateFromLumaGrid(
        luma: ByteArray,
        width: Int,
        height: Int,
        quadDetected: Boolean
    ): QualityMetrics {
        if (width < 8 || height < 8 || luma.size < width * height) {
            return QualityMetrics(hint = QualityHintType.MOVE_CLOSER)
        }

        var sumLuma = 0L
        var glareCount = 0L
        var lapSum = 0.0
        var lapSqSum = 0.0
        var lapSamples = 0L

        // Sample interior pixels with stride 2 for 60fps performance on Galaxy A14 class CPUs
        val stride = 2
        for (y in 2 until height - 2 step stride) {
            val rowOffset = y * width
            for (x in 2 until width - 2 step stride) {
                val center = luma[rowOffset + x].toInt() and 0xFF
                sumLuma += center
                if (center >= 248) glareCount++

                val top = luma[(y - 1) * width + x].toInt() and 0xFF
                val bottom = luma[(y + 1) * width + x].toInt() and 0xFF
                val left = luma[rowOffset + (x - 1)].toInt() and 0xFF
                val right = luma[rowOffset + (x + 1)].toInt() and 0xFF
                val lap = (top + bottom + left + right - 4 * center).toDouble()
                lapSum += lap
                lapSqSum += lap * lap
                lapSamples++
            }
        }

        val totalSamples = lapSamples.coerceAtLeast(1)
        val meanLum = sumLuma.toDouble() / totalSamples
        val glareRatio = glareCount.toDouble() / totalSamples
        val meanLap = lapSum / totalSamples
        val variance = (lapSqSum / totalSamples) - (meanLap * meanLap)

        val isBlurry = variance < BLUR_VARIANCE_THRESHOLD
        val hasGlare = glareRatio > GLARE_RATIO_THRESHOLD
        val isLowLight = meanLum < LOW_LIGHT_LUMINANCE_THRESHOLD

        val hint = when {
            isLowLight -> QualityHintType.LOW_LIGHT_TORCH
            hasGlare -> QualityHintType.TOO_MUCH_GLARE
            !quadDetected -> QualityHintType.MOVE_CLOSER
            isBlurry -> QualityHintType.HOLD_STEADY
            else -> QualityHintType.GOOD
        }

        return QualityMetrics(
            laplacianVariance = variance,
            glareRatio = glareRatio,
            meanLuminance = meanLum,
            isBlurry = isBlurry,
            hasGlare = hasGlare,
            isLowLight = isLowLight,
            frameLocked = quadDetected && !isBlurry && !hasGlare && !isLowLight,
            hint = hint
        )
    }
}

/**
 * Tracks 600ms continuous frame lock + quad corner stability before auto-capturing.
 */
class AutoCaptureStabilityTracker(
    private val holdDurationMs: Long = QualityHeuristics.STABILITY_HOLD_MS
) {
    private var lockStartMs: Long? = null
    private var previousCorners: List<Pair<Float, Float>>? = null

    fun update(
        metrics: QualityMetrics,
        corners: List<Pair<Float, Float>>?,
        nowMs: Long
    ): Boolean {
        if (!metrics.frameLocked || corners == null || corners.size != 4) {
            reset()
            return false
        }
        val prev = previousCorners
        if (prev != null && prev.size == 4) {
            val maxDrift = corners.zip(prev).maxOf { (a, b) ->
                maxOf(abs(a.first - b.first), abs(a.second - b.second))
            }
            if (maxDrift > 0.035f) {
                lockStartMs = nowMs
                previousCorners = corners
                return false
            }
        }
        previousCorners = corners
        val start = lockStartMs ?: nowMs.also { lockStartMs = it }
        return (nowMs - start) >= holdDurationMs
    }

    fun reset() {
        lockStartMs = null
        previousCorners = null
    }
}
