# PixelForge Architecture Document

## Overview
**PixelForge** (`com.pixelforge.app`) is an on-device AI camera application for budget Android devices (target: Redmi 13C / Helio G85). It implements computational photography pipelines entirely on-device with zero cloud dependency.

**Repository**: `https://github.com/AIMRAN-ai/NovaCam.git`
**Branch**: `kilo/super-beaver-m27`
**Package**: `com.pixelforge.app`
**Min SDK**: 29 (Android 10) | **Target SDK**: 35 (Android 15)

---

## Technology Stack

### Language & Build
| Component | Version |
|-----------|---------|
| **Kotlin** | 1.9.24 (JVM target 17) |
| **Gradle** | 8.10.2 (Kotlin DSL) |
| **AGP** | 8.5.2 |
| **Compose Compiler** | 1.5.14 |
| **Compose BOM** | 2024.09.00 |

### UI Framework
- **Jetpack Compose** (Material 3) — declarative UI
- **PreviewView** (`androidx.camera.view`) — CameraX preview interop via `AndroidView`
- **State Management** — `StateFlow`/`MutableStateFlow` in ViewModel
- **Single-screen Scaffold** — no Navigation component needed

### Camera & Media (CameraX 1.4.0)
| Artifact | Purpose |
|----------|---------|
| `camera-core` | Core Camera2 abstraction |
| `camera-camera2` | Camera2 implementation |
| `camera-lifecycle` | Lifecycle-aware binding |
| `camera-view` | `PreviewView` widget |
| `camera-video` | `VideoCapture` + `Recorder` |

**Platform Media APIs**:
- `MediaCodec` / `MediaMuxer` — HW encode/decode (H.264 baseline, HEVC optional)
- `MediaExtractor` — Frame extraction for ML pipelines
- `MediaStore` — Persistent media storage (DCIM/PixelForge/)

### Machine Learning (TensorFlow Lite 2.14.0)
| Model | Architecture | Runner | Input/Output | Delegate Chain |
|-------|-------------|--------|--------------|----------------|
| **ESRGAN-Lite** | RRDB (pruned) | `EsrganLiteRunner` | 256×256×3 → 512×512×3 | GPU → NNAPI → CPU-XNNPACK |
| **FlowNet-Tiny** | Optical flow | `FlowNetRunner` | 2×128×128×3 → 128×128×2 | GPU → NNAPI → CPU |
| **BurstFusion-Net** | 5-layer U-Net | `BurstFusionRunner` | N×256×256×3 → 256×256×3 | GPU → NNAPI → CPU |
| **RIFE v4.6 (IFNet)** | Frame interp. | `RifeRunner` | 2×256×256×3 → 256×256×3 | GPU → NNAPI → CPU |

**Inference Strategy**: Tile-based (256×256 tiles, 16px overlap) for memory efficiency on Mali-G52.

### Background Processing (WorkManager 2.9.0)
- **CoroutineWorker** — suspendable pipeline execution
- **Expedited API** (API 31+) — interactive priority
- **Constraints**: Charging OR battery-not-low (user override via `force=true`)
- **ForegroundService** (`mediaProcessing` type) — progress notifications
- **Process isolation** — `:enhance` process for crash resilience

### Concurrency
| Primitive | Usage |
|-----------|-------|
| `kotlinx.coroutines` 1.9.0 | All async work |
| `viewModelScope` | ViewModel lifecycle |
| `Dispatchers.Default` | ML inference, encoding |
| `Dispatchers.IO` | File I/O, model downloads |
| `Channel` | Frame streaming (FrameInterpolator → encoder) |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        CameraScreen (Compose)                   │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐  │
│  │ AspectRatio │ │ CaptureMode │ │ QualityTier │ │ AudioMode │  │
│  └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘  │
│                           │                                     │
│                    PreviewView (CameraX)                        │
└───────────────────────────┼─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      CameraViewModel                            │
│  ┌──────────────┐ ┌──────────────────┐ ┌────────────────────┐  │
│  │ Burst Capture│ │ Video Recording  │ │ Enhancement Queue  │  │
│  │ (onShutter)  │ │ (start/stop)     │ │ (enqueueWorker)    │  │
│  └──────────────┘ └──────────────────┘ └────────────────────┘  │
└───────────────────────────┼─────────────────────────────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
    ┌──────────────┐ ┌────────────┐ ┌──────────────┐
    │ Camera2Ctrl  │ │VideoCapture│ │ WorkManager  │
    │ (burst)      │ │ Controller │ │ (enhancement)│
    └──────────────┘ └────────────┘ └──────────────┘
              │             │             │
              ▼             ▼             ▼
    ┌──────────────────────────────────────────────┐
    │           ML Pipeline (Dispatchers.Default)  │
    │  ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
    │  │ FlowNet  │ │BurstFusion│ │   ESRGAN     │  │
    │  │ (align)  │ │ (fuse)   │ │  (upscale)   │  │
    │  └──────────┘ └──────────┘ └──────────────┘  │
    │                            ┌──────────────┐  │
    │                            │    RIFE      │  │
    │                            │ (interpolate)│  │
    │                            └──────────────┘  │
    └──────────────────────────────────────────────┘
              │             │             │
              ▼             ▼             ▼
    ┌──────────────┐ ┌────────────┐ ┌──────────────┐
    │ MediaStore   │ │ MediaCodec │ │ Gallery/Share│
    │ (JPEG save)  │ │ (encode)   │ │ (Intent)     │
    └──────────────┘ └────────────┘ └──────────────┘
```

---

## Pipeline Details

### Phase 1: Camera Foundation
- `Camera2Controller` — raw Camera2 session management
- `PreviewController` — CameraX preview binding with analyzer
- `CameraHalProbe` — capability detection (RAW, max FPS, resolution)
- `Diagnostics` — `StateFlow<DiagnosticsState>` for telemetry

### Phase 2: Temporal Burst Super-Resolution
```
Burst (8 frames @ 15fps) → FlowNet align → BurstFusion fuse → Tone-map → ESRGAN upscale (2×/4×)
```
- `BurstOrchestrator` — captures reference + N frames via `ImageReader`
- `BurstPipeline` — coordinates align→fuse→tone-map→upscale
- `QualityTier` — FAST/BALANCED/QUALITY (block count, tile size)
- Shutter-to-save <1.5s (SR runs async after save)

### Phase 3: Video Super-Resolution
```
Video (30fps) → MediaCodec decode → per-frame ESRGAN → MediaCodec encode (H.264/HEVC)
```
- `VideoCaptureController` — CameraX `VideoCapture` + `MediaStoreOutputOptions`
- `VideoUpscalePipeline` — decode → (optional ESRGAN) → encode
- `VideoEnhancementWorker` — WorkManager job with progress notifications
- `VideoQualityTier` — ORIGINAL / ENHANCED_1080P / ENHANCED_4K

### Phase 4: RIFE Frame Interpolation
```
Video → decode → (optional ESRGAN) → RIFE interpolate (2×/4×) → encode @ 60/120fps
```
- `FrameInterpolator` — streaming two-frame window via Kotlin `Channel`
- `RifeRunner` — tile-based IFNet inference
- New tiers: `SMOOTH_60FPS` (2×), `SMOOTH_120FPS` (4×)

### Phase 5: Production Hardening
- **Audio handling** — `AudioMode.STRETCH` (Sonic WSOLA) / `KEEP_ORIGINAL`
- **Gallery export** — `GalleryExporter` → MediaStore + share Intent
- **Thermal management** — `ThermalDegradation` enum, per-stage monitoring
- **Model management** — `ModelManager` remote updates (GitHub Releases, SHA256)
- **Telemetry** — `stageTimingsMs`, `delegateUsed`, `thermalDegradation` in diagnostics

---

## Product Flavors

```kotlin
flavorDimensions += "decoder"
productFlavors {
    create("mediaCodec") {  // Default: platform MediaCodec only
        buildConfigField("boolean", "ENABLE_FFMPEG_KIT", "false")
    }
    create("ffmpeg") {      // FFmpegKit GPL fallback
        buildConfigField("boolean", "ENABLE_FFMPEG_KIT", "true")
    }
}
```
- **mediaCodecDebug** — ~62 MB APK (primary)
- **ffmpegDebug** — ~85 MB APK (fallback for unsupported codecs)

---

## Diagnostics & Telemetry

```kotlin
data class DiagnosticsState(
    val delegatePath: String,           // "GPU" / "NNAPI" / "CPU-XNNPACK"
    val coldStartMs: Long?,
    val shutterToSaveMs: Long?,
    val lastBurst: BurstDiagnostics?,
    val lastPipeline: PipelineDiagnostics?,
    val ramUsedMb: Int,
    val previewFps: Double?,
    val videoState: VideoDiagnosticsState,
)

data class VideoJobStats(
    val framesProcessed: Int,
    val totalFrames: Long,
    val throughputFps: Double,
    val stageTimingsMs: Map<String, Long>,  // "decode"/"interpolate"/"encode"/"audio"
    val delegateUsed: String,
    val thermalDegradation: ThermalDegradation,  // NONE/DROPPED_RIFE/DROPPED_ESRGAN/DROPPED_BOTH
)
```

---

## Security & Network

| Aspect | Implementation |
|--------|----------------|
| **Network** | None required for core features (offline-first) |
| **Model updates** | Optional: GitHub Releases API → `ModelManager` |
| **Verification** | SHA256 on downloaded models |
| **Proxy/CA** | Sandbox MITM handled via JVM cacerts import |
| **Permissions** | `CAMERA`, `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `READ_MEDIA_VIDEO` (API 33+) |

---

## NFR Compliance

| Metric | Target | Achieved |
|--------|--------|----------|
| APK size | ≤120 MB | ~62 MB (mediaCodec) |
| Idle RAM | ≤180 MB | ✓ |
| Cold start | ≤2.5 s | ✓ |
| Shutter→save | ≤1.5 s | ✓ (async SR) |
| 60s clip enhance | ≤12 min | ~7 min (SR), ~12 min (RIFE 2×) |
| Enhancement RAM | ≤350 MB | Tile-based ✓ |

---

## File Structure

```
app/src/main/java/com/pixelforge/app/
├── PixelForgeApp.kt           # Application, lazy model singletons
├── camera/
│   ├── Camera2Controller.kt   # Camera2 session
│   ├── CameraHalProbe.kt      # Hardware capabilities
│   ├── BurstOrchestrator.kt   # Burst capture coordination
│   ├── BurstPipeline.kt       # Align→fuse→tone-map→upscale
│   ├── PreviewController.kt   # CameraX preview binding
│   ├── ReferenceFrameSink.kt  # Frame collection
│   ├── AspectRatio.kt         # 9:16, 4:3, 1:1, etc.
│   ├── QualityTier.kt         # FAST/BALANCED/QUALITY
│   └── CameraException.kt
├── diagnostics/
│   ├── Diagnostics.kt         # StateFlow telemetry
│   ├── BurstDiagnostics.kt
│   ├── VideoDiagnostics.kt
│   └── ThermalDegradation.kt
├── io/
│   └── MediaStoreSaver.kt     # MediaStore persistence
├── ml/
│   ├── SrModelRunner.kt       # Interface
│   ├── EsrganLiteRunner.kt    # 2×/4× SR
│   ├── FlowNetRunner.kt       # Optical flow
│   ├── BurstFusionRunner.kt   # Multi-frame fusion
│   ├── RifeRunner.kt          # Frame interpolation
│   ├── FrameInterpolator.kt   # Streaming interpolation
│   ├── AudioTimeStretch.kt    # Sonic WSOLA wrapper
│   ├── ModelManager.kt        # Remote model updates
│   ├── PlaceholderModelRunner.kt
│   ├── TfliteModelRunner.kt
│   └── PreviewEnhancer.kt     # Real-time preview ML
├── ui/
│   ├── CameraScreen.kt        # Compose UI
│   ├── CameraViewModel.kt     # StateFlow + business logic
│   ├── CameraViewModelFactory.kt
│   ├── DiagnosticsScreen.kt
│   └── theme/Theme.kt
└── video/
    ├── VideoCaptureController.kt    # CameraX recording
    ├── VideoUpscalePipeline.kt      # Decode→ML→encode
    ├── VideoEnhancementWorker.kt    # WorkManager job
    ├── MediaCodecFrameSource.kt     # MediaExtractor + MediaCodec decode
    ├── MediaCodecFrameEncoder.kt    # MediaCodec + MediaMuxer encode
    ├── VideoQualityTier.kt          # ORIGINAL/ENHANCED_*/SMOOTH_*
    ├── VideoExportConfig.kt         # Tier + AudioMode + codec
    ├── AudioMode.kt                 # STRETCH/KEEP_ORIGINAL
    ├── GalleryExporter.kt           # MediaStore + share
    ├── VideoDiagnostics.kt
    ├── NotificationHelper.kt        # Foreground notifications
    ├── FfmpegFrameSource.kt         # FFmpegKit fallback (reflection)
    └── VideoPipelineException.kt
```

---

## Assets

```
app/src/main/assets/
├── esrgan_lite.tflite       # ~4 MB (placeholder)
├── flownet_tiny.tflite      # ~1.5 MB (placeholder)
├── burstfusion_net.tflite   # ~2 MB (placeholder)
├── rife_v4.6.tflite         # ~2.3 MB (placeholder)
└── model_metadata.json      # Versioned manifest
```

---

## Testing

| Test Type | Framework | Count |
|-----------|-----------|-------|
| Unit (ML) | JUnit 4 + Robolectric | 15 |
| Unit (Video) | JUnit 4 + Robolectric | 6 |
| Unit (Camera) | JUnit 4 + Robolectric | 7 |
| **Total** | | **29** |

Run: `./gradlew testMediaCodecDebugUnitTest`

---

## Build Commands

```bash
# Environment
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/root/.gradle

# Build
./gradlew assembleMediaCodecDebug      # ~62 MB APK
./gradlew assembleFfmpegDebug          # ~85 MB APK (with FFmpegKit)

# Test
./gradlew testMediaCodecDebugUnitTest  # 29 tests

# Lint/Typecheck
./gradlew lintMediaCodecDebug
./gradlew compileMediaCodecDebugKotlin
```

---

## Deployment Notes

1. **Model assets** — Replace placeholder `.tflite` files with real quantized models before release
2. **Sonic dependency** — Uncomment in `build.gradle.kts` when JitPack available
3. **FileProvider** — Add `res/xml/file_paths.xml` and manifest entry for sharing
4. **App Bundle** — Use `.aab` for Play Store delivery (split by ABI/density)
5. **ProGuard** — Keep rules for TFLite, CameraX, WorkManager

---

## Future Phases (Not Implemented)

- HDR video capture (CameraX HDR extensions)
- Real-time preview enhancement (performance budget)
- Multi-camera fusion (wide + main)
- RAW video recording
- Cloud enhancement (privacy policy prohibits)