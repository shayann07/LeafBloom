# 🌿 **LeafBloom** — *Smart Offline Tomato Leaf Diagnosis*

A beautifully crafted Android app for farmers, growers, and researchers — delivering **fast, offline, on-device ML disease detection** with a clean, modern UI and upcoming soil-analysis capabilities.

---

<div align="center">
  <img src="https://github.com/shayann07/LeafBloom/blob/main/assets/app_logo.png" width="140" style="opacity:0.8" />
  <br><br>
  <b>📱 Offline. 🎯 Accurate. 🌱 Farm-Ready.</b>
  <br>
  <sub>Designed with modern UX, glassy surfaces, and a leafy-green identity.</sub>
</div>

---

## 🔥 **Why LeafBloom?**

Leaf diseases destroy thousands of hectares of tomato crops every year. LeafBloom brings ML-powered diagnosis directly to the device:

* ⚡ **Instant, offline inference** (1–3 seconds)
* 🎯 **High-accuracy predictions for 4 major diseases**
* 🔒 **Privacy-first** — photos never leave your device
* 🪶 **Smooth, polished UI** with modern gradients and glass elements
* 🌍 **Bilingual** (English + Urdu with full RTL support)

---

## ✨ **Core Features**

### 📷 **Smart Leaf Scanning**

* Guided camera overlay to frame leaves perfectly
* Auto-crop + pre-filter (leaf / non-leaf)
* Low-confidence alerts & retake suggestions

### 🧠 **On-Device Machine Learning**

* Optimized PyTorch/TFLite inference
* ImageNet-normalized preprocessing
* Local model versioning + update manager

### 📊 **Crystal-Clear Results**

* Predicted disease + confidence
* Dynamic probability bars
* Quick actionable tips
* Severity indicators & color coding

### 📁 **Scan History & Notes**

* Each scan stored with metadata
* Add notes for tracking
* Compare scans side-by-side
* Export PDF / CSV report

### ☁️ **Upcoming Features**

* Soil nutrient analysis
* Weather-based recommendations
* Cloud sync + account system
* Community feedback loop

---

## 🎨 **Design & UI/UX**

LeafBloom uses a **milky-white base**, **glassy cards**, and **leafy-green accents** for a fresh agricultural aesthetic.

### Visual Identity

* 🍃 *Deep Leaf Green*: #2F9E44
* 🌤️ *Milky Gradient Surfaces*: #FFFFFF → #F7FBFF
* 🧊 *Glass Cards*: blur + soft highlight
* ✨ Micro-animations: pulses, bars, shutter feedback

### Full UI/UX Source (Figma)

📁 **Design System + Screens:**
[https://www.figma.com/design/ZO7MQ8XkGcQJyHemEgWD7E/Minor-X-Global?node-id=465-53&p=f&t=QIiV5bjmQiTI67rw-0](https://www.figma.com/design/ZO7MQ8XkGcQJyHemEgWD7E/Minor-X-Global?node-id=465-53&p=f&t=QIiV5bjmQiTI67rw-0)

---

## 🧠 **Tech Stack**

* **Kotlin** (100%)
* **Jetpack:** ViewModel, LiveData, Navigation, Room
* **Material 3 UI**
* **ML Runtime:** PyTorch Mobile / TFLite
* **WorkManager** for sync tasks
* **Firebase (optional)** for backup & accounts

---

## 🧬 **ML Model Pipeline**

* Resize → Center-crop → Normalize
* Channels-first tensor → Run model
* Softmax applied on-device
* Returns label, confidence, probability vector

---

## 📦 **Project Structure**

```
LeafBloom/
 ├── app/
 │    ├── java/                       # Kotlin code
 │    ├── res/                        # UI layouts + drawables
 │    └── assets/models/              # Local ML models
 ├── gradle/                          # Gradle wrapper
 ├── build.gradle.kts
 ├── settings.gradle.kts
 ├── LICENSE
 └── README.md
```

---

## 🛠️ **Setup for Developers**

Clone the repo:

```bash
git clone https://github.com/shayann07/LeafBloom.git
```

Open in Android Studio and build:

```bash
./gradlew assembleDebug
```

Run on device. ML models load automatically.

---

## 🗺️ **Roadmap**

* [ ] Soil analysis module
* [ ] Disease encyclopedia
* [ ] AR-based scan helper
* [ ] Model update manager
* [ ] Cloud sync
* [ ] Farmer tip feed

---

## 📜 **License**

MIT License — permissive and developer-friendly.

---

## 📩 **Contact**

For collaboration or inquiries, reach out to the project owner.

**Figma Design File:**
[https://www.figma.com/design/ZO7MQ8XkGcQJyHemEgWD7E/Minor-X-Global?node-id=465-53&p=f&t=QIiV5bjmQiTI67rw-0](https://www.figma.com/design/ZO7MQ8XkGcQJyHemEgWD7E/Minor-X-Global?node-id=465-53&p=f&t=QIiV5bjmQiTI67rw-0)

<!-- gitpulse:contribution index="1" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="2" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="3" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="4" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="5" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="6" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="7" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="8" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="9" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="10" timestamp="2026-04-24" -->
<!-- gitpulse:contribution index="11" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="12" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="13" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="14" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="15" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="16" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="17" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="18" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="19" timestamp="2026-04-26" -->
<!-- gitpulse:contribution index="20" timestamp="2026-04-26" -->