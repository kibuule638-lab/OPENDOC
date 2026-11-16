# FolioScan — Offline Document & Identity Scanner

FolioScan is a 100% offline Android document, ID card, passport MRZ, and business card scanner built with Kotlin, Jetpack Compose (Material 3), CameraX, bundled OpenCV, bundled ML Kit Text Recognition v2, PdfBox-Android, SQLCipher + Room FTS4, Hilt, and WorkManager.

## Build & Run Instructions

- **Requirements**: Android Studio Ladybug+ / JDK 17+, Android SDK 36 (`minSdk 26`, `targetSdk 36`).
- **Build Debug APK**:
  ```bash
  gradle :app:assembleDebug
  ```
- **Run All Unit & Robolectric Tests**:
  ```bash
  gradle testDebugUnitTest
  ```
- **Build Release AAB (with R8 + Resource Shrinking + ABI Splits)**:
  ```bash
  gradle :app:bundleRelease
  ```

## Google Play Services Limitation & Fallback

- **Standard Document Mode**: Uses `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0`, which requires Google Play Services (GMS) on the device.
- **GMS-Free Devices**: `DocumentScanner.checkAvailability(mode)` checks `GoogleApiAvailability`. On AOSP/de-Googled/HMS devices (and for all ID Card, Passport, Business Card, and Other ID modes), FolioScan automatically routes to its bundled **CameraX + OpenCV** pipeline (`OpenCvDocumentProcessor` + `CameraFrameAnalyzer`) with live quad detection, blur/glare/low-light checks, and 600ms auto-capture stability lock.
- **OCR**: Uses `com.google.mlkit:text-recognition:16.0.1` (bundled Latin v2 model), requiring **zero** model downloads or network access.

## Estimated APK Size Delta (Per-ABI Split via AAB)

| Component | Approx. Size Delta (arm64-v8a split) |
|---|---|
| Base Compose + Material 3 + Hilt + Room + WorkManager | ~6.5 MB |
| ML Kit Text Recognition v2 (Bundled Latin model) | ~12.5 MB |
| OpenCV Android 4.10.0 (`libopencv_java4.so`) | ~11.0 MB |
| SQLCipher Android (`libsqlcipher.so`) | ~2.4 MB |
| PdfBox-Android + Font/Glyph Assets | ~3.2 MB |
| **Total AAB per-ABI install size** | **~35.6 MB** |

*(With `bundle { abi { enableSplit = true } }`, R8 full mode, and `isShrinkResources = true` enabled in `release`.)*

## Itemized Delivery Timeline & Engineering Estimate (by Phase)

| Phase | Scope | Duration | Estimated Cost (USD) |
|---|---|---|---|
| **Phase 1** | Multi-module architecture, Room + FTS4 + SQLCipher, ML Kit Document mode, Page Editor (crop/rotate/filters/adjust/sign/redact), bundled OCR worker, Folders/Tags/Trash, Search, PDF/JPG export, Settings | 4 weeks | $18,000 |
| **Phase 2** | Custom CameraX + OpenCV pipeline (`OpenCVLoader.initLocal()`), live quad overlay, Laplacian blur/glare/low-light checks, 600ms auto-capture, two-sided ID Card (1:1 A4 export), Business Card parser + Contacts intent | 3 weeks | $14,500 |
| **Phase 3** | Passport ICAO 9303 TD3 MRZ parser & check-digit validator, Private Vault (Biometric + PIN), AES-256-GCM file/field encryption, `FLAG_SECURE`, PdfBox AES-256 PDF protection, watermarks, redaction, local ZIP backup/restore, CI & performance tuning | 3 weeks | $15,000 |
| **Total** | Full production delivery across all 3 phases | **10 weeks** | **$47,500** |
