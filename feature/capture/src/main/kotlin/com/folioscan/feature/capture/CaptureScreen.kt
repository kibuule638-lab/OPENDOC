package com.folioscan.feature.capture

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.designsystem.LocalFolioSemanticColors
import com.folioscan.core.designsystem.LocalReduceMotion
import com.folioscan.core.designsystem.QualityHint
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.vision.CameraFrameAnalyzer
import com.folioscan.core.vision.OpenCvDocumentProcessor
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.util.concurrent.Executors

@Composable
fun CaptureScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onScanCompleted: (String) -> Unit,
    processor: OpenCvDocumentProcessor = remember { OpenCvDocumentProcessor() },
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val mlKitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanResult?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) {
                viewModel.onMlKitUrisReturned(uris)
            } else {
                onClose()
            }
        } else {
            onClose()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.handleGalleryImportUris(uris)
        }
    }

    LaunchedEffect(activity) {
        if (activity != null) {
            viewModel.tryLaunchMlKitIfSupported(activity)
        }
        if (!hasCameraPermission && state.mode != CaptureMode.DOCUMENT) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CaptureEffect.LaunchMlKitScanner -> {
                    mlKitLauncher.launch(IntentSenderRequest.Builder(effect.intentSender).build())
                }
                is CaptureEffect.ScanCompleted -> onScanCompleted(effect.documentId)
                is CaptureEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                CaptureEffect.TriggerHaptic -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val triggerStillCapture: () -> Unit = remember(imageCapture) {
        {
            val capture = imageCapture
            if (capture != null) {
                capture.flashMode = if (state.flashAuto) ImageCapture.FLASH_MODE_AUTO else ImageCapture.FLASH_MODE_OFF
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = imageProxyToBitmap(image)
                            image.close()
                            if (bmp != null) {
                                viewModel.processCapturedStillBitmap(bmp)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            // Handled gracefully
                        }
                    }
                )
            }
        }
    }

    val analyzer = remember(state.autoCaptureEnabled) {
        CameraFrameAnalyzer(
            processor = processor,
            autoCaptureEnabled = state.autoCaptureEnabled,
            onAnalysisResult = { metrics, quad, autoCapture ->
                viewModel.onFrameAnalyzed(metrics, quad, autoCapture)
                if (autoCapture) {
                    ContextCompat.getMainExecutor(context).execute {
                        triggerStillCapture()
                    }
                }
            }
        )
    }

    LaunchedEffect(state.torchEnabled, cameraControl) {
        cameraControl?.cameraControl?.enableTorch(state.torchEnabled)
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val pending = state.pendingReview
        if (pending != null) {
            // Review Step: image preview with Retake (left) and Confirm/Use (right)
            CaptureReviewOverlay(
                pending = pending,
                stepLabel = state.stepLabel,
                onRetake = viewModel::retakePending,
                onConfirm = viewModel::confirmPending
            )
        } else {
            // Full-bleed CameraX preview (or permission prompt)
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            setOnTouchListener { view, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    val factory = meteringPointFactory
                                    val point = factory.createPoint(event.x, event.y)
                                    val action = FocusMeteringAction.Builder(point).build()
                                    cameraControl?.cameraControl?.startFocusAndMetering(action)
                                    view.performClick()
                                }
                                true
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val provider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = surfaceProvider
                                }
                                val cap = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                                imageCapture = cap
                                runCatching {
                                    provider.unbindAll()
                                    cameraControl = provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        cap,
                                        analysis
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(FolioSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(FolioSpacing.md))
                    Text(
                        text = "Camera access is needed for live ${state.mode.displayLabel} guidance.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Spacer(Modifier.height(FolioSpacing.md))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.testTag("grant_camera_permission_btn")
                    ) {
                        Text("Grant Camera Access")
                    }
                    Spacer(Modifier.height(FolioSpacing.sm))
                    TextButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text("Import from Gallery instead", color = Color.White)
                    }
                }
            }

            // Center frame overlay matching the mode (ID-1, ID-3, A4, landscape Business Card)
            val aspectRatio = when (state.mode) {
                CaptureMode.OTHER_ID -> state.otherIdAspectRatio
                else -> state.mode.aspectWidth / state.mode.aspectHeight
            }
            ModeFrameOverlay(
                aspectRatio = aspectRatio,
                locked = state.qualityMetrics.frameLocked,
                modifier = Modifier.fillMaxSize()
            )

            // Top row + hint banner + bottom bar inside safeDrawing insets
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Top row: close (X), torch toggle, flash/auto, settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("capture_close_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close capture", tint = Color.White)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            state.stepLabel?.let { step ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = viewModel::toggleTorch,
                                modifier = Modifier.testTag("capture_torch_btn")
                            ) {
                                Icon(
                                    imageVector = if (state.torchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                    contentDescription = "Toggle torch",
                                    tint = if (state.torchEnabled) Color.Yellow else Color.White
                                )
                            }
                            IconButton(
                                onClick = viewModel::toggleFlashAuto,
                                modifier = Modifier.testTag("capture_flash_btn")
                            ) {
                                Icon(
                                    imageVector = if (state.flashAuto) Icons.Default.FlashAuto else Icons.Default.FlashOff,
                                    contentDescription = "Toggle auto flash",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("capture_settings_btn")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Capture settings", tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(FolioSpacing.xs))
                    QualityHint(hintType = state.qualityMetrics.hint)

                    state.usingCustomFallbackBanner?.let { note ->
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Bottom bar: gallery import (left), shutter (center), auto/manual toggle (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = FolioSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("capture_gallery_btn")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import from gallery", tint = Color.White)
                    }

                    Surface(
                        onClick = triggerStillCapture,
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("capture_shutter_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            ) {}
                        }
                    }

                    Surface(
                        onClick = viewModel::toggleAutoCapture,
                        shape = RoundedCornerShape(20.dp),
                        color = if (state.autoCaptureEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.White.copy(alpha = 0.2f)
                        },
                        modifier = Modifier.testTag("capture_auto_toggle_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Auto capture toggle",
                                tint = if (state.autoCaptureEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    Color.White
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (state.autoCaptureEnabled) "Auto" else "Manual",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (state.autoCaptureEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    Color.White
                                }
                            )
                        }
                    }
                }
            }
        }

        if (state.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ModeFrameOverlay(
    aspectRatio: Float,
    locked: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFolioSemanticColors.current
    val reduceMotion = LocalReduceMotion.current
    val targetColor = if (locked) semantic.scanLockGreen else Color.White
    val guideColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 220),
        label = "frame_guide_color"
    )

    Canvas(modifier = modifier) {
        val maxW = size.width * 0.84f
        val maxH = size.height * 0.62f
        val frameW: Float
        val frameH: Float
        if (maxW / maxH > aspectRatio) {
            frameH = maxH
            frameW = frameH * aspectRatio
        } else {
            frameW = maxW
            frameH = frameW / aspectRatio
        }

        val left = (size.width - frameW) / 2f
        val top = (size.height - frameH) / 2f

        // Subtle frame outline
        drawRoundRect(
            color = guideColor.copy(alpha = 0.35f),
            topLeft = Offset(left, top),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Corner brackets
        val cornerLen = 28.dp.toPx()
        val stroke = 4.dp.toPx()
        // Top-left
        drawLine(guideColor, Offset(left, top), Offset(left + cornerLen, top), stroke, StrokeCap.Round)
        drawLine(guideColor, Offset(left, top), Offset(left, top + cornerLen), stroke, StrokeCap.Round)
        // Top-right
        drawLine(guideColor, Offset(left + frameW, top), Offset(left + frameW - cornerLen, top), stroke, StrokeCap.Round)
        drawLine(guideColor, Offset(left + frameW, top), Offset(left + frameW, top + cornerLen), stroke, StrokeCap.Round)
        // Bottom-left
        drawLine(guideColor, Offset(left, top + frameH), Offset(left + cornerLen, top + frameH), stroke, StrokeCap.Round)
        drawLine(guideColor, Offset(left, top + frameH), Offset(left, top + frameH - cornerLen), stroke, StrokeCap.Round)
        // Bottom-right
        drawLine(guideColor, Offset(left + frameW, top + frameH), Offset(left + frameW - cornerLen, top + frameH), stroke, StrokeCap.Round)
        drawLine(guideColor, Offset(left + frameW, top + frameH), Offset(left + frameW, top + frameH - cornerLen), stroke, StrokeCap.Round)
    }
}

@Composable
private fun CaptureReviewOverlay(
    pending: PendingReviewCapture,
    stepLabel: String?,
    onRetake: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(FolioSpacing.md),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Review capture${stepLabel?.let { " ($it)" } ?: ""}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            pending.qualityWarning?.let { warning ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LocalFolioSemanticColors.current.qualityWarningAmber
                ) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Image(
            bitmap = pending.bitmap.asImageBitmap(),
            contentDescription = "Captured page review",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = FolioSpacing.md)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.md)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("capture_retake_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Retake", color = Color.White)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("capture_confirm_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Use")
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    raw.recycle()
    return rotated
}
