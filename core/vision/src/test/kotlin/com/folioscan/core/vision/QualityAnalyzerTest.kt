package com.folioscan.core.vision

import com.folioscan.core.model.QualityHintType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QualityAnalyzerTest {

    @Test
    fun `detects sharp checkerboard frame as non-blurry and locked when quad detected`() {
        val w = 64
        val h = 64
        val luma = ByteArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x / 2 + y / 2) % 2 == 0) 200.toByte() else 60.toByte()
        }
        val metrics = QualityHeuristics.evaluateFromLumaGrid(luma, w, h, quadDetected = true)
        assertFalse(metrics.isBlurry)
        assertFalse(metrics.hasGlare)
        assertFalse(metrics.isLowLight)
        assertTrue(metrics.frameLocked)
        assertEquals(QualityHintType.GOOD, metrics.hint)
    }

    @Test
    fun `detects blurred flat frame via low Laplacian variance`() {
        val w = 64
        val h = 64
        val luma = ByteArray(w * h) { 128.toByte() }
        val metrics = QualityHeuristics.evaluateFromLumaGrid(luma, w, h, quadDetected = true)
        assertTrue(metrics.isBlurry)
        assertFalse(metrics.frameLocked)
        assertEquals(QualityHintType.HOLD_STEADY, metrics.hint)
    }

    @Test
    fun `detects glare when saturated pixels exceed threshold`() {
        val w = 64
        val h = 64
        val luma = ByteArray(w * h) { idx ->
            if (idx < (w * h) / 4) 254.toByte() else 120.toByte()
        }
        val metrics = QualityHeuristics.evaluateFromLumaGrid(luma, w, h, quadDetected = true)
        assertTrue(metrics.hasGlare)
        assertEquals(QualityHintType.TOO_MUCH_GLARE, metrics.hint)
    }

    @Test
    fun `detects low light and suggests torch`() {
        val w = 64
        val h = 64
        val luma = ByteArray(w * h) { 28.toByte() }
        val metrics = QualityHeuristics.evaluateFromLumaGrid(luma, w, h, quadDetected = true)
        assertTrue(metrics.isLowLight)
        assertEquals(QualityHintType.LOW_LIGHT_TORCH, metrics.hint)
    }

    @Test
    fun `stability tracker requires 600ms continuous steady hold`() {
        val tracker = AutoCaptureStabilityTracker(holdDurationMs = 600L)
        val sharpMetrics = com.folioscan.core.model.QualityMetrics(frameLocked = true)
        val corners = listOf(0.1f to 0.1f, 0.9f to 0.1f, 0.9f to 0.9f, 0.1f to 0.9f)

        assertFalse(tracker.update(sharpMetrics, corners, 1000L))
        assertFalse(tracker.update(sharpMetrics, corners, 1400L))
        assertTrue(tracker.update(sharpMetrics, corners, 1650L))

        // Large drift resets timer
        val drifted = listOf(0.2f to 0.2f, 0.9f to 0.1f, 0.9f to 0.9f, 0.1f to 0.9f)
        assertFalse(tracker.update(sharpMetrics, drifted, 1700L))
    }
}
