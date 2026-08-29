# LeafBloom

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)]()
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Diagnose tomato-leaf diseases from a single photo using an on-device PyTorch model, ask plant-care questions in a chat, and look up info on common houseplants.

---

## 📖 Overview

Diagnose tomato-leaf diseases from a single photo using an on-device PyTorch model, ask plant-care questions in a chat, and look up info on common houseplants.

---

## ✨ Key Features

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

---

## 🛠️ Technology Stack

| Component / Layer | Technology |
|---|---|
| **Platform** | Android |
| **Primary Language** | Kotlin |
| **Architecture** | MVVM / Clean Architecture |
| **License** | Open Source (MIT) |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17 / 21
- Android SDK 34 / 35

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/shayann07/LeafBloom.git
   cd LeafBloom
   ```
2. Open the project in **Android Studio**.
3. Sync Gradle dependencies and run on an emulator or physical device.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — Copyright (c) 2026 [shayann07](https://github.com/shayann07).
