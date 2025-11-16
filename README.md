# 🌿 LeafBloom — Offline Tomato Leaf Disease Diagnosis (Android)

A field-ready, offline-first Android app for fast, accurate tomato-leaf disease detection using on-device machine learning — with upcoming soil analysis, farmer-focused UX, bilingual support, and a polished modern UI.

---

## 🚀 What LeafBloom Does

LeafBloom classifies tomato leaf photos into four key categories:

* **Early Blight**
* **Healthy**
* **Late Blight**
* **Septoria Leaf Spot**

Everything runs *locally on the device*—no internet required. That means:

* ⚡ **Instant analysis**
* 🔒 **True privacy** (your leaf images never leave your phone)
* 🏞️ **Perfect for farms with poor connectivity**

Upcoming modules include **soil nutrient analysis**, **weather-aware crop guidance**, and a comprehensive **disease knowledgebase**.

---

## ✨ Feature Highlights

### 🌱 **On-Device Machine Learning**

* Runs a lightweight, optimized PyTorch/TFLite model locally
* Works offline at full accuracy
* Fast inference: typically 1–3 seconds

### 📷 **Smart Scan Flow**

* Guided camera overlay for perfect leaf framing
* Automatic crop suggestion
* Leaf vs. non-leaf pre-filter
* Low-confidence warnings

### 📊 **Clean, Expert-Grade Results**

* Primary diagnosis + confidence score
* Probability breakdown for all four classes
* Quick actionable tips
* Visual severity indicator

### 📁 **History & Notes**

* Each scan stored with image, label, confidence, and notes
* Optional comparison mode (side-by-side)
* Export as PDF or CSV

### ☁️ **Cloud Sync (Upcoming)**

* Optional sync via Firebase
* Offline-first with Wi-Fi-only upload policy

### 🌍 **Bilingual UI (English + Urdu)**

* Full RTL layout for Urdu
* All screens localized

---

## 🎨 UI & Experience

LeafBloom uses a **milky-white gradient UI** with soft glassy layers and leafy-green accents.

### Key Design Elements:

* Glass cards with subtle shadow & blur
* Rounded-line Material icons
* Accessible large-text mode
* Clean animations (pulse meter, result reveal, camera shutter)

---

## 🧠 Tech Stack

* **Language:** Kotlin
* **Architecture:** MVVM + Jetpack
* **Navigation:** Jetpack Navigation Component
* **UI Toolkit:** Material 3
* **ML Runtime:** PyTorch Mobile / TensorFlow Lite
* **Local DB:** Room
* **Async Work:** WorkManager
* **(Optional)** Firebase Auth + Firestore + Cloud Storage

---

## 🔧 Project Structure

```
LeafBloom/
 ├── app/                        # Main Android module
 │    ├── src/main/java/...      # Kotlin source
 │    ├── res/                   # Layouts, drawables, themes
 │    └── assets/models/         # Local ML models
 ├── gradle/                     # Gradle wrapper
 ├── build.gradle.kts            # Root Gradle config
 ├── settings.gradle.kts
 ├── .gitignore
 ├── LICENSE
 └── README.md
```

---

## 🧪 Machine Learning Models

### Disease Model

* Input: 224×224 RGB center-crop
* Output: 4-class logits → softmax applied on device
* Normalization: ImageNet mean/std
* Model versioning built into Settings

### Leaf/Non-Leaf Pre-Filter

* Lightweight classifier to reject invalid scans
* Threshold-based gating (default: 0.6)

---

## 🗺️ Roadmap

* [ ] Soil nutrient analysis module
* [ ] Advanced disease encyclopedia
* [ ] Model update manager with changelogs
* [ ] AR-assisted leaf capture mode
* [ ] Cloud sync with history backup
* [ ] Farmer community feedback loop

---

## 🛠️ Getting Started (Developers)

Clone and open in Android Studio:

```
git clone https://github.com/shayann07/LeafBloom.git
```

Build using Gradle wrapper:

```
./gradlew assembleDebug
```

Run on device or emulator.

---

## 📜 License

MIT License — free to modify, extend, and integrate.

---

## 🤝 Contributing

This repository is currently private. Contributions are accepted from invited collaborators only.

---

## 📩 Contact

For collaboration or inquiries, contact the project owner.
