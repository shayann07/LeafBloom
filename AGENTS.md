# AGENTS.md — LeafBloom

Offline-first Android app (Kotlin, minSdk 28, target/compile 36) for tomato leaf diagnosis with PyTorch Lite on-device models + a Firebase Cloud Function fallback for generic plant ID (Pl@ntNet).

## Architecture (read these to orient)

- **Single activity + Nav Graph**: `ui/main/MainActivity.kt` programmatically inflates `R.navigation.nav_graph` and chooses start destination based on `UserPrefs.isFirstRun` (walkthrough vs home). BottomNav + FAB drive navigation; `homeFragment` is the only destination that shows the bottom nav.
- **Package layout** (`com.devsphere.leafbloom`):
  - `data/source/local/` — PyTorch Lite classifiers (`DiseaseClassifier`, `LeafValidator`, `PestClassifier`, `RipenessClassifier`). Each loads a `.ptl` from `app/src/main/assets/` by copying to `filesDir` via the `assetFilePath(...)` helper (this pattern is duplicated per class — keep it).
  - `data/repository/` — Thin wrappers exposing `initialize()` + `predict(bitmap): PredictionResult` (or `Result<IdentifyResponse>` for the remote `IdentifyRepository` singleton `object`).
  - `data/remote/LeafBloomApiService.kt` — Retrofit interface; base URL is hardcoded in `IdentifyRepository` (`https://asia-south1-devsphere-leafbloom.cloudfunctions.net/`).
  - `ui/scanner/ScannerViewModel.kt` — Shared (`activityViewModels`) VM owning a `sealed class ScannerUiState` (`Idle / Loading / SuccessDiagnosis / SuccessIdentify / SuccessPest / SuccessRipeness / Error`) emitted via `StateFlow`. `init {}` preloads all four models off the main thread. Instantiated via inner `Factory(application)`.
  - `ui/common/BaseFragment.kt` — All fragments extend this for `applySystemBarInsets()` and `setupAdaptiveHeader()` (edge-to-edge curved header math). Use it instead of raw `Fragment`.
  - `prefs/UserPrefs.kt` — `SharedPreferences` singleton (`getInstance(context)`); flags `isFirstRun`, `isDevMode`.
- **Cloud function** (`functions/v2/identify.js`): `onRequest` in region `asia-south1`, proxies Pl@ntNet, expects multipart field name **`images`** and optional `organ` query (`leaf|flower|fruit|bark|auto`). Returns `{ ok, traceId, data: { bestMatch, results[], meta } }`. Secret `PLANTNET_API_KEY` is stored in Google Secret Manager. Node runtime in `functions/package.json`.

## Critical conventions

- **Model I/O contract**: All on-device models expect 224×224, ImageNet-normalized RGB via `TensorImageUtils.bitmapToFloat32Tensor(..., TORCHVISION_NORM_MEAN_RGB, TORCHVISION_NORM_STD_RGB)`. `DiseaseClassifier.predict` remaps the 4-class model output into a 5-slot `FloatArray` where **index 0 = UNKNOWN** (returned `[1,0,0,0,0]` when `LeafValidator` rejects the image). Preserve this index mapping (`INDEX_UNKNOWN/EARLY_BLIGHT/HEALTHY/LATE_BLIGHT/SEPTORIA`) — `DiseaseRepository` and result fragments depend on it.
- **Leaf gate**: `DiseaseClassifier` always runs `LeafValidator.isValidLeaf(bitmap)` first; the remote `identifyPlant(uri)` flow in `ScannerViewModel` also gates with `LeafValidator` and emits `Error("NOT_A_PLANT")` as a sentinel string — UI matches on this exact value.
- **Threading**: Repositories suspend; ViewModel launches on `viewModelScope` and wraps inference in `withContext(Dispatchers.IO)`. Don't block the main thread with model loads — follow the async `init {}` pattern in `ScannerViewModel`.
- **Scanner modes**: `ScannerFragment.scanMode` is a `String` ("DIAGNOSE" | "IDENTIFY" | pest/ripeness variants) dispatched to `analyzeImage / analyzePest / analyzeRipeness / identifyPlant`. New scan types should add a new `ScannerUiState.SuccessX` + repository + classifier pair, mirroring existing ones.
- **Fragments**: ViewBinding only (`buildFeatures { viewBinding = true }`) — no DataBinding/Compose. Null out `_binding` in `onDestroyView`. Navigation via `findNavController()` + nav graph actions; no manual fragment transactions.
- **Dependencies**: Declared in `gradle/libs.versions.toml` (version catalog) and referenced as `libs.xxx` in `app/build.gradle.kts`. Add new libs there, not inline.
- **Edge-to-edge UI**: `enableEdgeToEdge()` is on; apply insets only where needed via `BaseFragment.applySystemBarInsets(view)`. Bottom nav visibility is toggled per destination in `MainActivity`.
- **i18n / RTL**: `supportsRtl="true"`; Urdu strings live under `res/values-*`. Don't hardcode user-facing text.
- **kotlin-parcelize** is enabled — use `@Parcelize` for nav args, not manual `Parcelable`.

## Developer workflows

- Build debug APK: `./gradlew assembleDebug` (Java 11, AGP 9.2.1, Kotlin 2.3.21 — Android Studio Iguana+/Hedgehog may be too old).
- Run unit tests: `./gradlew test`; instrumented: `./gradlew connectedDebugAndroidTest`.
- Models ship in `app/src/main/assets/*.ptl` (`tomato_disease_robust.ptl`, `leaf_nonleaf_model.ptl`, `pest_id_model.ptl`, `ripeness_model.ptl`). To replace, retrain via scripts in `ml/<task>/train_*.py` then export with the corresponding `convert_to_mobile.py` / `convert_*.py`, drop the `.ptl` into assets, and keep filenames stable (hardcoded in classifier `loadModel()`).
- ML side: `pip install -r ml/requirements.txt`; per-task datasets under `ml/<task>/dataset/` (not committed in full). Evaluation scripts: `ml/<task>/evaluate_*.py`, `test_*.py`.
- Cloud function: `cd functions && npm install`; deploy `firebase deploy --only functions:identifyPlantV2`. Secret: `firebase functions:secrets:set PLANTNET_API_KEY`. `firebase.json` lives at repo root.
- Reset onboarding while developing: toggle `UserPrefs.getInstance(ctx).resetOnboarding()` (used when `isDevMode` is on).

## When adding code

- New classifier → mirror `DiseaseClassifier` (suspend `loadModel`, `assetFilePath` copy, `LiteModuleLoader.load`, 224×224 + ImageNet norm). Wrap in a `Repository` exposing `initialize()` + `predict()`. Wire into `ScannerViewModel.Factory` and add a `ScannerUiState.SuccessX` case.
- New remote endpoint → extend `LeafBloomApiService`; reuse the singleton Retrofit/OkHttp in `IdentifyRepository` (60s timeouts for cold starts, `HttpLoggingInterceptor` at `HEADERS` level).
- New screen → create fragment extending `BaseFragment`, add to `res/navigation/nav_graph.xml`, register any bottom-nav visibility exception in `MainActivity`'s destination listener.

