package com.folioscan.core.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.model.PageAdjustments
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.QualityMetrics
import com.folioscan.core.model.RedactionBox
import com.folioscan.core.model.SignatureStroke
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class NormalizedQuad(
    val topLeft: Pair<Float, Float>,
    val topRight: Pair<Float, Float>,
    val bottomRight: Pair<Float, Float>,
    val bottomLeft: Pair<Float, Float>
) {
    fun asList(): List<Pair<Float, Float>> = listOf(topLeft, topRight, bottomRight, bottomLeft)
}

/**
 * OpenCV-powered document edge detector, 4-point perspective warper, quality analyzer,
 * and bitmap enhancement renderer.
 *
 * Uses bundled OpenCV 4.10.0 (`OpenCVLoader.initLocal()`) on Android devices, with safe
 * pure-Android Bitmap/Canvas fallbacks when running under JVM unit test environments.
 */
@Singleton
class OpenCvDocumentProcessor @Inject constructor() {

    companion object {
        private const val TAG = "OpenCvDocProcessor"
    }

    private val openCvReady = AtomicBoolean(false)

    fun ensureInitialized(): Boolean {
        if (openCvReady.get()) return true
        val ok = runCatching { OpenCVLoader.initLocal() }.getOrElse {
            Log.w(TAG, "OpenCV native init unavailable in this environment: ${it.message}")
            false
        }
        openCvReady.set(ok)
        return ok
    }

    /**
     * Evaluates grayscale luma plane for blur (Laplacian variance), glare, and low-light,
     * plus detects the largest 4-corner document contour when OpenCV is initialized.
     */
    fun analyzePreviewLuma(
        lumaBytes: ByteArray,
        width: Int,
        height: Int
    ): Pair<QualityMetrics, NormalizedQuad?> {
        val quad = if (ensureInitialized()) {
            detectQuadFromLuma(lumaBytes, width, height)
        } else {
            null
        }
        val metrics = QualityHeuristics.evaluateFromLumaGrid(
            luma = lumaBytes,
            width = width,
            height = height,
            quadDetected = quad != null
        )
        return metrics to quad
    }

    private fun detectQuadFromLuma(lumaBytes: ByteArray, width: Int, height: Int): NormalizedQuad? {
        if (width <= 0 || height <= 0 || lumaBytes.size < width * height) return null
        val gray = Mat(height, width, CvType.CV_8UC1)
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        return try {
            gray.put(0, 0, lumaBytes.copyOf(width * height))
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 60.0, 180.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val imageArea = width.toDouble() * height.toDouble()
            var bestQuad: Array<Point>? = null
            var bestArea = 0.0

            for (contour in contours) {
                val c2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                val pts = approx.toArray()
                val area = Imgproc.contourArea(contour)
                c2f.release()
                approx.release()

                if (pts.size == 4 && area > imageArea * 0.14 && area > bestArea) {
                    bestArea = area
                    bestQuad = pts
                }
            }

            bestQuad?.let { orderQuadCorners(it, width, height) }
        } catch (t: Throwable) {
            Log.w(TAG, "Quad detection failed: ${t.message}")
            null
        } finally {
            contours.forEach { it.release() }
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
        }
    }

    private fun orderQuadCorners(pts: Array<Point>, width: Int, height: Int): NormalizedQuad {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()
        val remaining = pts.filter { it !== tl && it !== br }
        val tr = remaining.maxByOrNull { it.x - it.y } ?: pts[1]
        val bl = remaining.minByOrNull { it.x - it.y } ?: pts[3]
        return NormalizedQuad(
            topLeft = (tl.x / width).toFloat().coerceIn(0f, 1f) to (tl.y / height).toFloat().coerceIn(0f, 1f),
            topRight = (tr.x / width).toFloat().coerceIn(0f, 1f) to (tr.y / height).toFloat().coerceIn(0f, 1f),
            bottomRight = (br.x / width).toFloat().coerceIn(0f, 1f) to (br.y / height).toFloat().coerceIn(0f, 1f),
            bottomLeft = (bl.x / width).toFloat().coerceIn(0f, 1f) to (bl.y / height).toFloat().coerceIn(0f, 1f)
        )
    }

    /**
     * Applies 4-point perspective warp to a fixed output size matching the CaptureMode
     * (ID-1: 1200x756, ID-3: 1400x986, Business Card: 1200x776, A4 Document: 1480x2094).
     */
    fun warpToModeOutput(
        source: Bitmap,
        mode: CaptureMode,
        quad: NormalizedQuad? = null,
        customAspectRatio: Float? = null
    ): Bitmap {
        val (targetW, targetH) = when (mode) {
            CaptureMode.ID_CARD -> 1200 to 756
            CaptureMode.PASSPORT -> 1400 to 986
            CaptureMode.BUSINESS_CARD -> 1200 to 776
            CaptureMode.OTHER_ID -> {
                val ratio = (customAspectRatio ?: 1.586f).coerceIn(1.1f, 2.2f)
                1200 to (1200f / ratio).roundToInt()
            }
            CaptureMode.DOCUMENT -> 1480 to 2094
        }

        if (!ensureInitialized() || quad == null) {
            return centerCropToAspect(source, targetW, targetH)
        }

        val srcMat = Mat()
        val dstMat = Mat(targetH, targetW, CvType.CV_8UC4)
        return try {
            Utils.bitmapToMat(source, srcMat)
            val w = source.width.toDouble()
            val h = source.height.toDouble()
            val srcPts = MatOfPoint2f(
                Point(quad.topLeft.first * w, quad.topLeft.second * h),
                Point(quad.topRight.first * w, quad.topRight.second * h),
                Point(quad.bottomRight.first * w, quad.bottomRight.second * h),
                Point(quad.bottomLeft.first * w, quad.bottomLeft.second * h)
            )
            val dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(targetW.toDouble(), 0.0),
                Point(targetW.toDouble(), targetH.toDouble()),
                Point(0.0, targetH.toDouble())
            )
            val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(srcMat, dstMat, transform, Size(targetW.toDouble(), targetH.toDouble()))
            srcPts.release()
            dstPts.release()
            transform.release()

            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also { out ->
                Utils.matToBitmap(dstMat, out)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Perspective warp fallback: ${t.message}")
            centerCropToAspect(source, targetW, targetH)
        } finally {
            srcMat.release()
            dstMat.release()
        }
    }

    private fun centerCropToAspect(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val targetRatio = targetW.toFloat() / targetH.toFloat()
        val srcRatio = source.width.toFloat() / source.height.toFloat()
        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = source.height
            cropW = (cropH * targetRatio).roundToInt().coerceAtMost(source.width)
        } else {
            cropW = source.width
            cropH = (cropW / targetRatio).roundToInt().coerceAtMost(source.height)
        }
        val left = ((source.width - cropW) / 2).coerceAtLeast(0)
        val top = ((source.height - cropH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(source, left, top, max(1, cropW), max(1, cropH))
        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        if (cropped !== scaled) cropped.recycle()
        return scaled
    }

    /**
     * Applies rotation, filter, brightness/contrast adjustments, signature strokes, and black-out
     * redaction boxes into a fresh Bitmap for preview or export. Caller must recycle returned Bitmap.
     */
    fun renderProcessedBitmap(
        source: Bitmap,
        rotationDegrees: Int,
        filter: PageFilter,
        adjustments: PageAdjustments,
        redactions: List<RedactionBox>,
        signatureStrokes: List<SignatureStroke>,
        applyRedactions: Boolean = true
    ): Bitmap {
        val rotated = if (rotationDegrees % 360 != 0) {
            val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()
        when (filter) {
            PageFilter.ORIGINAL -> Unit
            PageFilter.GRAYSCALE -> cm.setSaturation(0f)
            PageFilter.BLACK_WHITE -> {
                cm.setSaturation(0f)
                val bwContrast = ColorMatrix(
                    floatArrayOf(
                        2.2f, 0f, 0f, 0f, -140f,
                        0f, 2.2f, 0f, 0f, -140f,
                        0f, 0f, 2.2f, 0f, -140f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(bwContrast)
            }
            PageFilter.MAGIC_COLOR -> {
                val boost = ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -18f,
                        0f, 1.25f, 0f, 0f, -18f,
                        0f, 0f, 1.25f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(boost)
            }
            PageFilter.SHARPEN -> {
                val crisp = ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0f, 0f, -10f,
                        0f, 1.15f, 0f, 0f, -10f,
                        0f, 0f, 1.15f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(crisp)
            }
        }

        val c = adjustments.contrast.coerceIn(0.5f, 2.0f)
        val b = (adjustments.brightness.coerceIn(-0.5f, 0.5f)) * 255f
        val translate = (1f - c) * 128f + b
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, translate,
                    0f, c, 0f, 0f, translate,
                    0f, 0f, c, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        if (rotated !== source) rotated.recycle()

        // Signature strokes
        if (signatureStrokes.isNotEmpty()) {
            val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (stroke in signatureStrokes) {
                if (stroke.points.size < 2) continue
                sigPaint.strokeWidth = max(2f, stroke.strokeWidthDp * (output.width / 360f))
                val path = Path()
                stroke.points.forEachIndexed { idx, pt ->
                    val px = pt.x * output.width
                    val py = pt.y * output.height
                    if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                canvas.drawPath(path, sigPaint)
            }
        }

        // Redaction black-out boxes
        if (applyRedactions && redactions.isNotEmpty()) {
            val redactPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            for (box in redactions) {
                canvas.drawRect(
                    box.leftFraction * output.width,
                    box.topFraction * output.height,
                    box.rightFraction * output.width,
                    box.bottomFraction * output.height,
                    redactPaint
                )
            }
        }

        return output
    }
}
