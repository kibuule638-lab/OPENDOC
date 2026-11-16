# FolioScan Architecture

## 1. Multi-Module Dependency Graph

```
                    +-------+
                    | :app  |
                    +---+---+
                        |
   +--------+--------+--+------+--------+--------+--------+--------+
   |        |        |         |        |        |        |        |
   v        v        v         v        v        v        v        v
:home   :capture  :editor   :detail  :search  :folders  :vault  :settings
   |        |        |         |        |        |        |        |
   +--------+--------+---------+--------+--------+--------+--------+
                        |  (feature -> core ONLY)
                        v
  +--------------+--------------+--------------+--------------+
  | :core:scanner|  :core:pdf   |  :core:ocr   |:core:designsystem
  +------+-------+------+-------+------+-------+--------------+
         |              |              |
         v              v              v
  +------+-------+------+-------+------+-------+
  | :core:vision |:core:identity|:core:database|
  +------+-------+------+-------+------+-------+
         |              |              |
         +--------------+------+-------+
                        |      |
                        v      v
                  :core:storage
                        |
                        v
                   :core:model  (Pure Kotlin JVM)
```

Strict enforcement:
- Feature modules depend **only** on `:core:*` modules (never on another feature).
- `:core:*` modules never depend on `:feature:*`.
- `:core:model` is pure Kotlin JVM (`kotlin.jvm`) with zero Android SDK dependencies.

---

## 2. Unidirectional Data Flow & Concurrency

```
UI (Jetpack Compose)
  │  collectAsStateWithLifecycle()
  ▼
ViewModel (@HiltViewModel)
  │  StateFlow<UiState> + Channel<Effect>
  ▼
Repositories / Engines (:core:database, :core:scanner, :core:pdf, :core:ocr)
  │  Flow<T> reads / suspend @Transaction writes
  ▼
Data Sources (SQLCipher Room + FTS4, Keystore AES-256-GCM FileStore, DataStore, CameraX+OpenCV, ML Kit)
```

---

## 3. Document Processing State Machine

```
[Captured] ──(Copy transient URIs/bytes to /files/scans/{docId}/{pageId}.jpg + AES-256-GCM if sensitive)
     │
     ▼
[Persisted] ──(Single Room @Transaction: DocumentEntity + PageEntity list + optional IdentityDataEntity)
     │
     ▼
[OcrQueued] ──(WorkManager ExistingWorkPolicy.KEEP unique work "ocr_{docId}")
     │
     ▼
[OcrRunning] ──(Idempotent page-by-page loop; skips pages with existing PageTextEntity; emits setProgress)
     │
     ├──(All pages done + Passport MRZ / Business Card extraction)──▶ [OcrDone]
     │
     └──(Exhausted 3 exponential-backoff retries)──────────────────▶ [OcrFailed] (Retry / Skip in Detail)
```

---

## 4. Type-Safe Navigation Graph

```
[HomeRoute] ──▶ [CaptureRoute(mode, isVault)] ──▶ [DetailRoute(documentId, openExport)]
     │                                                    │
     ├──▶ [SearchRoute] ──────────────────────────────────┼──▶ [EditorRoute(documentId, pageIndex)]
     ├──▶ [FoldersRoute] ─────────────────────────────────┤
     ├──▶ [SettingsRoute] ──▶ [VaultRoute] ───────────────┘
     └──▶ [VaultRoute] ──────▶ [CaptureRoute(..., isVault = true)]
```

---

## 5. Offline & Privacy Guarantees
- `AndroidManifest.xml` explicitly removes `INTERNET` and `ACCESS_NETWORK_STATE` (`tools:node="remove"`).
- `android:allowBackup="false"`.
- Room database encrypted via SQLCipher (`net.zetetic:sqlcipher-android`) with Keystore-wrapped passphrase.
- `IdentityDataEntity` (`fieldsJson`, `mrzRaw`) encrypted at rest with AES-256-GCM (`KeystoreCryptoManager`).
- ID Card, Passport, and Vault page image files encrypted at rest with AES-256-GCM.
- `FLAG_SECURE` enabled automatically on Vault, ID Card, and Passport screens.
- Clipboard auto-clears copied sensitive fields after configurable timeout (default 30s).
