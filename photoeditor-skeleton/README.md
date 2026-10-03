# Photo Editor — Project Skeleton

Kotlin + Jetpack Compose, built around the design we worked through:
personalized, per-photo AI editing (not copy-pasted presets), auto trip
clustering, preview-first calibration, correction loop, and silent dataset
logging for a future trained model.

## What's here and working-level

- **Gradle project** set up for Compose, Room, Coil, OkHttp — should sync in
  Android Studio / Antigravity as-is.
- **`data/PhotoRepository.kt`** — reads your actual local gallery via
  MediaStore (not Google Photos' API, which can't see your whole library).
- **`data/TripClusterer.kt`** — groups photos into trips by date gap +
  location drift, falling back to date-only when GPS is missing.
- **`data/model/EditModels.kt`** — the core data shapes: `StyleDescription`
  (semantic style, not raw numbers), `PhotoEditParams` (concrete per-photo
  output), `EditLogEntry` (the training-data row).
- **`vlm/VLMClient.kt`** — the two-stage brain: `analyzeStyle()` turns your
  reference edit into a semantic description; `planEdit()` reasons about one
  new photo against that style; `replanWithCorrection()` handles the "tell it
  what went wrong" loop.
- **`editing/ImageProcessor.kt`** — executes params on real pixels. Exposure,
  white balance (approximate), saturation, contrast, and crop all work via
  `ColorMatrix`. Blur, chromatic aberration, and lens correction are stubbed
  with notes on the recommended approach for each.
- **`data/EditLogDatabase.kt`** — Room DB quietly logging every edit (initial
  attempt + correction if any) — this is the dataset for the eventual trained
  model (Path B), without building any training code yet.
- **UI screens** — `GalleryScreen` (trip grid), `TripScreen` (pick reference
  photos), `EditScreen` (manual calibration with live preview).

## What's intentionally NOT done yet (next steps in Antigravity)

1. **Wire the actual "apply to rest of trip" flow.** All the pieces exist
   (`VLMClient`, `ImageProcessor`, `EditLogRepository`) but the orchestrating
   logic — loop over the trip's photos, call `planEdit`, run `ImageProcessor`,
   show a review grid, handle corrections — isn't connected yet. That's the
   natural first thing to build once this compiles and runs.
2. **Make `Trip` Parcelable** (`@Parcelize`) so it can pass through Compose
   Navigation cleanly, or switch to passing an id and looking it up from a
   shared ViewModel.
3. **Real crop UI** — sliders stand in for crop right now. A gesture-based
   crop view (drag handles) is the real version.
4. **GPS from EXIF** — `PhotoRepository` currently leaves lat/lng null; the
   MediaStore columns for it are deprecated on newer Android. Read EXIF
   directly instead (`androidx.exifinterface`).
5. **Blur / chromatic aberration / lens correction** — each has a TODO in
   `ImageProcessor.kt` with the recommended library/approach (ML Kit
   segmentation for blur, OpenCV for the lens-geometry stuff).
6. **API key handling** — a hardcoded key in `MainActivity` is fine for your
   own testing only. Before this goes anywhere near a published app, route
   Claude API calls through a small backend you control instead of shipping
   the key in the app binary.

## Why Kotlin + Compose (not Flutter)

No bridge layer needed — direct MediaStore/EXIF access, and Compose builds a
clean Material UI fast. Since you don't have a stake in either language, this
avoids the extra complexity of a cross-platform + native-bridge setup for a
first build.

## Import into Antigravity

Unzip this, open the folder as an existing Android project. It should Gradle
-sync directly. From there: fix any version-mismatch errors Gradle flags (SDK
versions drift fast), get it running on an emulator or your phone, then start
on next-step #1 above.
