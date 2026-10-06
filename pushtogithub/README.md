# Tasveer (तस्वीर) 📸

[![Android CI](https://github.com/your-username/tasveer/actions/workflows/android.yml/badge.svg)](https://github.com/your-username/tasveer/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Tasveer** is a privacy-first, professional batch photo editor and intelligent gallery app for Android powered by Jetpack Compose and Gemini Vision VLM. Edit once, refine with AI, and batch-apply consistent cinematic styles across entire photo sets with hardware-accelerated 60/120 FPS rendering.

---

## ⚡ Highlights & Key Features

- 📁 **Studio Gallery & Folder Navigation**: Browse photos organized either by local device directories (Camera, Pictures, DCIM, Downloads) or timeline stream, with smooth cover thumbnails and fast photo counting.
- 🎨 **Real-Time Color Grading Engine**: Hardware-accelerated GPU `ColorMatrix` rendering with zero-lag slider manipulation for Brightness, Contrast, Saturation, Temperature (Warmth), and Tint.
- 🪄 **Intelligent Auto-Enhancement**: Single-buffer pixel luminance and RGB statistics calculation (`getPixels` buffer) with zero-crossing tone curves that balance shadows, exposure, and white points without blowing highlights.
- 🤖 **Batch VLM Style Transfer (Gemini Vision)**: Analyze reference edits with Gemini Vision and batch transfer adjustments to entire trips or photo clusters.
- 🧹 **Intelligent Cleanup Assistant**: Find blurry photos, low-contrast shots, and duplicate captures to reclaim storage space.
- 🔒 **Privacy & Security Hardened**: API keys encrypted via Android Keystore / EncryptedSharedPreferences; API communication authenticated via direct `x-goog-api-key` header; zero cleartext traffic.
- ✨ **Apple & Emil Kowalski Standard Motion**: Fluid transitions powered by Jetpack Compose `AnimatedContent` with sub-200ms `FastOutSlowInEasing`.

---

## 📱 Pre-Built APK

Download and install the ready-to-use APK directly on your Android device (Android 8.0+ / API 26+):

- **[Tasveer.apk](Tasveer.apk)** (~19.9 MB)

---

## 🏗️ Architecture & Tech Stack

```
tasveer/
├── app/
│   ├── src/main/java/com/photoeditor/skeleton/
│   │   ├── data/             # Room Database, PhotoRepository, MediaStore Scanner
│   │   ├── editing/          # ColorMatrix Shaders, AutoToneEngine, BitmapUtils
│   │   ├── vlm/              # Gemini Vision VLM Client, Multi-turn Refinement
│   │   ├── ui/               # Jetpack Compose Screens (Gallery, Edit, Review, Cleanup)
│   │   └── widget/           # Android Glance Home Screen Widget
│   └── src/test/java/        # 32 Automated Unit Tests (100% Passing)
├── docs/                     # Full Architectural & API Documentation
└── .github/workflows/        # Automated CI Build & Test Workflow
```

- **Language**: Kotlin 2.0 (Coroutines & StateFlow)
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Local Storage**: Room Database + SQLite
- **Image Pipeline**: Coil 2.6 + Android Graphics Hardware Acceleration
- **Networking**: OkHttp 4.12
- **Testing**: JUnit 4, Kotlin Test, MockK, Robolectric

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Koala / Ladybug or newer
- JDK 17
- Android SDK Platform 34 (API 34)
- Minimum SDK: API 26 (Android 8.0 Oreo)

### Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/tasveer.git
   cd tasveer
   ```

2. Open the project in **Android Studio** (select the root folder `tasveer`).

3. Or build via command line:
   ```bash
   ./gradlew assembleDebug
   ```
   The compiled APK will be located at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Running Unit Tests

Run the full suite of 32 automated unit tests:
```bash
./gradlew testDebugUnitTest
```

---

## 📖 Detailed Documentation

For a comprehensive breakdown of the application architecture, VLM algorithms, shader implementations, security models, and verification logs, check out [docs/Tasveer_Documentation.md](docs/Tasveer_Documentation.md).

---

## 📄 License

This project is open-source under the terms of the [MIT License](LICENSE).
