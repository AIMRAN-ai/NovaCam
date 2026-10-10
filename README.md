# PixelForge

AI camera app for budget Android devices. On-device temporal burst super-resolution,
video upscaling, and live preview enhancement — fully on-device, no cloud, no uploads.

## Phase 1 (current)

- Camera2 burst capture (5–12 frames, >=14 fps target) with YUV_420_888 and RAW (DNG) paths
- CameraX live preview with ML enhancement (delegate decision tree: NNAPI -> GPU -> CPU/XNNPACK)
- JPEG save via MediaStore with 9:16 / 16:9 / 1:1 aspect ratios
- In-app diagnostics: delegate path, HAL capabilities, burst stats, latency, RAM

## Build

Requires JDK 17 and Android SDK 35.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Status

Phase 1 of the plan in `.kilo/plans/`. The ML inference layer uses a placeholder
enhancement model; custom-trained models (MobileNet-SR, FlowNet-Tiny, BurstFusion-Net)
land in Phase 2.
