# PixelForge

AI camera app for budget Android devices. On-device temporal burst super-resolution,
video upscaling, and live preview enhancement — fully on-device, no cloud, no uploads.

## Phase 2 (current)

- Temporal Burst Super-Resolution pipeline:
  - FlowNet-Tiny optical-flow alignment (phase-correlation fallback, 8px motion budget)
  - BurstFusion-Net multi-frame fusion (median-rejection fallback)
  - ESRGAN-Lite tiled upscaling (256px tiles, 16px overlap, 2x/4x)
  - S-curve tone-map before upscale
- Shutter flow: original saved synchronously (<=1.5s), enhanced JPEG async
- Quality tiers: Fast / Balanced / Quality
- SR pipeline stage timings in diagnostics

## Phase 1

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

Phase 2 of the plan. The ML inference layer uses quantized TFLite models
when the assets are present (esrgan_lite.tflite, flownet_tiny.tflite,
burstfusion_net.tflite) and deterministic CPU fallbacks otherwise, so the
full pipeline is exercisable on any device. Custom-trained models
(MobileNet-SR, FlowNet-Tiny, BurstFusion-Net) land in Phase 2 tuning.
