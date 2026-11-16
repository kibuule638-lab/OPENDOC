package com.folioscan.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface OcrEngine {
    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): String
}

/**
 * Bundled ML Kit Text Recognition v2 (Latin script) — zero network model downloads.
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText.text)
                }
                .addOnFailureListener { exc ->
                    if (cont.isActive) cont.resumeWithException(exc)
                }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine
}
