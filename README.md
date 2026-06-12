# LeafBloom

LeafBloom is a Kotlin Android app that performs **on-device tomato leaf disease diagnosis** using PyTorch Lite models. It also covers pest identification, fruit-ripeness classification, a Pl@ntNet-backed remote plant identification fallback, an in-app Gemini chat, weather-aware context, and local scan history. Single Activity + Navigation Component, ViewBinding, Room, CameraX, and a small Firebase Cloud Functions backend for the plant-identify proxy.

## Status

Functional Android app under active development. Core scanner, diagnosis, pest, ripeness, history, disease library, chat, and walkthrough modules are wired up against bundled PyTorch Lite assets and a deployed Firebase function. Only the default Android Studio test scaffold is present.

## Features Verified In Code

- **Single-Activity Navigation.** `ui/main/MainActivity` inflates `R.navigation.nav_graph` programmatically and chooses between the walkthrough and home destinations based on `prefs/UserPrefs.isFirstRun`. BottomNav is shown only on the home destination; FAB drives scanner entry.
- **On-device classifiers** (`data/source/local/`) — all PyTorch Lite (`org.pytorch:pytorch_android_lite`):
  - `DiseaseClassifier` (`tomato_disease_robust.ptl`) — 4-class tomato output remapped into 5 slots: `UNKNOWN, EARLY_BLIGHT, HEALTHY, LATE_BLIGHT, SEPTORIA`.
  - `LeafValidator` (`leaf_nonleaf_model.ptl`) — gates the disease and identify flows; non-leaf inputs surface `Error("NOT_A_PLANT")`.
  - `PestClassifier` (`pest_id_model.ptl`).
  - `RipenessClassifier` (`ripeness_model.ptl`).
  - All models expect 224×224 ImageNet-normalized RGB tensors.
- **Repositories** (`data/repository/`) wrap each classifier with `initialize()` + `predict()`. `IdentifyRepository` is a Retrofit/OkHttp singleton calling the deployed Firebase function. `ChatRepository` wraps Google Gemini via `google-ai-generativeai`. `WeatherRepository` adds weather context (`data/model/WeatherResponse`). `ScanHistoryRepository` writes scans to Room.
- **Shared scanner state.** `ui/scanner/ScannerViewModel` exposes a `sealed class ScannerUiState` (`Idle / Loading / SuccessDiagnosis / SuccessIdentify / SuccessPest / SuccessRipeness / Error`) via `StateFlow`. Inference runs on `Dispatchers.IO`; models are preloaded asynchronously on init.
- **CameraX-based scanner** (`ui/scanner/`): `ScannerFragment` for capture, `CropFragment` for image cropping (Android Image Cropper), with location permission for weather context.
- **Result + history flows.** Scan results are persisted via Room (`data/source/local/db/`), surfaced in the History screen, and the Disease Library shows curated info per class (`data/model/DiseaseInfo`, `DiseaseCareInfo`, `PestInfo`, `RipenessInfo`).
- **Chat module** (`ui/chat/`) backed by Gemini, with the API key loaded from `local.properties` into `BuildConfig.GEMINI_API_KEY`.
- **Auth, profile, walkthrough, model-download, dialogs, motion, components.** Each lives under its own `ui/<module>` package and uses `BaseFragment` for edge-to-edge insets and an adaptive curved header.
- **Bilingual UI.** `supportsRtl="true"`, with English in `res/values/` and Urdu in the localized values folder.

## Cloud Function (`functions/v2/identify.js`)

- Firebase Functions Gen 2, deployed to region `asia-south1`. Endpoint base: `https://asia-south1-devsphere-leafbloom.cloudfunctions.net/`.
- Accepts a multipart POST with field name `images` (1–5 images, ≤6MB each) and an optional `organ` query param (`leaf|flower|fruit|bark|auto`, default `leaf`).
- Proxies the request to the [Pl@ntNet API](https://my.plantnet.org/) using a `PLANTNET_API_KEY` stored in Google Secret Manager.
- Returns `{ ok, traceId, data: { bestMatch, results[], meta } }`.

## ML Pipeline (`ml/`)

Python tooling for retraining and evaluating the four shipped models:

```
ml/
  tomato_leaf_disease/
  nutrient_deficiency/
  pests/
  ripeness/
  eval_assets_models.py
  requirements.txt
```

Each task directory holds training, conversion, and evaluation scripts. Datasets are not committed in full. To replace a model, retrain via `train_*.py`, export with the corresponding `convert_to_mobile.py` / `convert_*.py`, drop the `.ptl` into `app/src/main/assets/`, and keep the file name stable (the classifier `loadModel()` paths are hardcoded).

## Tech Stack

- **Kotlin** with `kotlin-parcelize`, JVM target 11.
- **Android Gradle Plugin 9.2.1**, **Kotlin 2.3.21**, **KSP 2.3.7** (per `gradle/libs.versions.toml`).
- `compileSdk 36`, `minSdk 28`, `targetSdk 36`.
- **Jetpack:** Navigation Component (fragment + ui), ViewBinding, Lifecycle (`viewModelScope`, `repeatOnLifecycle`), Room (with KSP and `schemas/` exported), Core Splash Screen.
- **CameraX** (core, camera2, lifecycle, view, extensions).
- **Networking:** Retrofit + Gson converter, OkHttp + logging interceptor.
- **Imaging:** Glide, Android Image Cropper, AndroidX ExifInterface, Animated VectorDrawable, Dots Indicator, GridLayout.
- **ML:** PyTorch Android Lite + Torchvision Lite ops.
- **AI chat:** `com.google.ai.client.generativeai`.
- **Location:** Play Services Location, Guava ListenableFuture support.
- **Build inputs:** `local.properties` provides `GEMINI_API_KEY` for `BuildConfig`.

## Project Structure

```
LeafBloom/
  app/
    src/main/
      AndroidManifest.xml
      assets/
        leaf_nonleaf_model.ptl
        pest_id_model.ptl
        ripeness_model.ptl
        tomato_disease_robust.ptl
      java/com/devsphere/leafbloom/
        data/
          model/                 # ChatMessage, DiseaseInfo, DiseaseCareInfo,
                                 # HistoryItem, IdentifyResponse, PestInfo,
                                 # PredictionResult, RipenessInfo, WeatherResponse
          remote/                # Retrofit API service for the identify function
          repository/            # Disease, Identify, Pest, Ripeness, ScanHistory,
                                 # Chat, Weather repositories
          source/local/          # PyTorch Lite classifiers + Room DB sources
        prefs/                   # UserPrefs (SharedPreferences singleton)
        ui/
          adapter/  auth/  chat/  common/  components/  dialog/
          disease/  history/  home/  main/  modelDownload/
          motion/  profile/  result/  scanner/  walkthrough/
        util/
      res/                       # layouts, drawables, navigation, values, values-night, etc.
    schemas/                     # Room schema exports
  functions/
    index.js
    package.json
    package-lock.json
    v2/identify.js               # Pl@ntNet proxy (asia-south1)
  ml/
    tomato_leaf_disease/  nutrient_deficiency/  pests/  ripeness/
    eval_assets_models.py  requirements.txt
  assets/app_logo.png            # README/logo asset only (not the app icon)
  firebase.json
  build.gradle.kts  settings.gradle.kts
  AGENTS.md  LICENSE  README.md
```

## Build / Run

### Requirements

- Android Studio recent enough for **AGP 9.2.1** and **Kotlin 2.3.21** (older Iguana/Hedgehog will not work).
- JDK 11.
- A device or emulator running Android 9 (`minSdk 28`) or later.

### Steps

1. Clone the repository.
2. Add a `local.properties` at the repo root with at least:
   ```
   sdk.dir=...absolute path to your Android SDK...
   GEMINI_API_KEY=your_gemini_key   # optional; the chat module won't work without it
   ```
3. Open in Android Studio and let it sync.
4. Build/install the debug variant with `./gradlew assembleDebug` or run from the IDE.

The four `.ptl` models are bundled under `app/src/main/assets/` and are copied into `filesDir` on first use.

### Running the cloud function locally (optional)

```
cd functions
npm install
firebase functions:secrets:set PLANTNET_API_KEY
firebase deploy --only functions:identifyPlantV2
```

## Tests

Only the default Android Studio scaffold is present:

- `app/src/test/java/com/devsphere/leafbloom/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/devsphere/leafbloom/ExampleInstrumentedTest.kt`

There is no automated coverage of the classifiers, repositories, or UI flows.

## Honest Limitations

- The disease classifier is trained for **tomato leaves only**, with four real classes (`EARLY_BLIGHT`, `HEALTHY`, `LATE_BLIGHT`, `SEPTORIA`) plus an `UNKNOWN` slot used when `LeafValidator` rejects an image. Other plants and tomato diseases outside this set will not be classified accurately.
- The `nutrient_deficiency/` ML directory exists, but **no nutrient-deficiency `.ptl` is bundled in `app/src/main/assets/`**; the on-device pipeline does not yet ship soil/nutrient analysis. Treat soil analysis as a roadmap item, not a current feature.
- The chat module depends on the user supplying a `GEMINI_API_KEY` via `local.properties`. Without it, the in-app chat will not function.
- Side-by-side scan comparison and PDF/CSV export were claimed in older marketing copy but are not present in the current codebase.
- The remote identify path requires the `devsphere-leafbloom` Firebase project; redeploying for another account requires updating the Retrofit base URL in `IdentifyRepository`.
- Tests are stubs only; treat any behavioral guarantees as manual.

## License

MIT — see [LICENSE](LICENSE).
