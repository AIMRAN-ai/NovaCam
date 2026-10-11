# Phase 4 Plan: RIFE Video Frame Interpolation & Advanced Video Features

## Overview
Phase 4 implements **RIFE (Real-Time Intermediate Flow Estimation)** for video frame interpolation, enabling smooth slow-motion and higher frame-rate output. The Phase 3 `VideoUpscalePipeline` was designed with a `(frameIndex, totalFrames)` callback contract specifically to support this. Phase 4 also adds quality-of-life video features.

---

## Goals

| Goal | Success Criteria |
|------|------------------|
| **RIFE frame interpolation** | 2×/4× frame-rate multiplication (30→60/120 fps), <200ms/frame on Helio G85 |
| **Slow-motion export** | User-selectable 0.5×/0.25× speed with interpolated frames |
| **Quality tier extension** | Add `SMOOTH_60FPS` / `SMOOTH_120FPS` tiers combining SR + RIFE |
| **Unified video pipeline** | Single pipeline: decode → (optional SR) → (optional RIFE) → encode |
| **UI integration** | Slow-motion toggle, frame-rate selector, progress with interpolated frame count |

---

## Architecture Decisions (Resolved)

| Decision | Resolution | Rationale |
|----------|------------|-----------|
| **RIFE model** | `rife_v4.6.tflite` (IFNet, ~2.3 MB) | Small, fast, good quality; TFLite GPU delegate compatible |
| **Integration point** | Inside `VideoUpscalePipeline.run()` between decode and encode | Reuses existing frame callback contract `(index, total)` |
| **Output frame contract** | RIFE emits `N` output frames per input frame; encoder receives `(outputIndex, totalOutputFrames, buffer, info)` | Minimal encoder changes; encoder already accepts arbitrary frame rate |
| **Interpolation factor** | Configurable 2× or 4× (maps to `SMOOTH_60FPS` / `SMOOTH_120FPS` tiers) | User choice; 4× = 2 RIFE passes or single 4× model |
| **Thermal handling** | Extend `ThermalGuard` to pause RIFE when severe; skip interpolation frames if needed | Prevent throttling; graceful degradation |
| **Memory budget** | ≤350 MB during enhancement (NFR); RIFE processes 2 frames at a time | Tile-based like ESRGAN; recycle bitmaps immediately |
| **Model distribution** | Bundle in `assets/` (2.3 MB); fallback to download if missing | Offline-first; APK size impact minimal |

---

## New / Modified Files

### 1. New: `ml/RifeRunner.kt`
```kotlin
class RifeRunner(
    private val diagnostics: Diagnostics,
    private val assets: AssetManager,
) : AutoCloseable {
    // Loads rife_v4.6.tflite (IFNet) via TFLite GPU delegate
    // suspend fun interpolate(frame0: Bitmap, frame1: Bitmap, scale: Int): List<Bitmap>
    // scale = 2 → 1 intermediate frame; scale = 4 → 3 intermediate frames
    // Tile-based processing for large frames (1080p/4K) to stay in memory budget
}
```

### 2. New: `ml/FrameInterpolator.kt` (light wrapper)
```kotlin
class FrameInterpolator(
    private val rife: RifeRunner,
    private val diagnostics: Diagnostics,
) {
    // Coordinates two-frame sliding window over decode stream
    // fun interpolateStream(
    //     source: MediaCodecFrameSource,
    //     encoder: MediaCodecFrameEncoder,
    //     config: InterpolationConfig,
    //     onProgress: (Float) -> Unit,
    // ): InterpolationResult
}
```

### 3. Modified: `video/VideoQualityTier.kt`
```kotlin
enum class VideoQualityTier(...) {
    ORIGINAL("Original", ...),
    ENHANCED_1080P("Enhanced 1080p", ...),
    ENHANCED_4K("Enhanced 4K-SR", ...),
    SMOOTH_60FPS("Smooth 60fps", esrganBlocks=4, tileSize=256, targetWidth=1920, targetHeight=1080, rifeScale=2),
    SMOOTH_120FPS("Smooth 120fps", esrganBlocks=4, tileSize=256, targetWidth=1920, targetHeight=1080, rifeScale=4),
}
```

### 4. Modified: `video/VideoUpscalePipeline.kt`
- Add `rifeRunner: RifeRunner?` constructor param (nullable for ORIGINAL/ENHANCED tiers)
- In `run()`: after decoding each frame, if `config.tier.rifeScale > 1`:
  - Buffer previous frame
  - Call `rifeRunner.interpolate(prev, current, scale)` → list of intermediate frames
  - Encode original + intermediates in temporal order
  - Update `totalFrames` for progress = `probe.frameCount * rifeScale`
- Encoder `frameRate` = `config.frameRate * rifeScale`

### 5. Modified: `video/VideoEnhancementWorker.kt`
- Pass `RifeRunner` instance from `PixelForgeApp` to `VideoUpscalePipeline`
- Worker input data includes `rifeScale` (from tier)

### 6. Modified: `PixelForgeApp.kt`
```kotlin
val rife: RifeRunner by lazy {
    RifeRunner(diagnostics, assets)
}
```
- Add to `onCleared()` / shutdown sequence

### 7. Modified: `ui/CameraViewModel.kt`
- Add `setVideoSlowMotion(scale: Float)` → maps to `SMOOTH_60FPS` / `SMOOTH_120FPS`
- Expose `videoQualityTier` options including new tiers
- Update `enqueueVideoEnhancement()` to pass tier with RIFE scale

### 8. Modified: `ui/CameraScreen.kt`
- Add slow-motion chip row: `Normal` / `0.5× (60fps)` / `0.25× (120fps)`
- Show interpolated frame count in progress: "Processing frame 120/360 (RIFE 2×)"

### 9. New: `video/VideoPipelineException.kt` (extract from VideoUpscalePipeline)
- Shared exception hierarchy for pipeline errors

### 10. Tests
- `RifeRunnerTest.kt` — model loads, interpolates 2 frames → 1 intermediate
- `FrameInterpolatorTest.kt` — streaming interpolation produces correct frame count
- `VideoUpscalePipelineTest.kt` — integration test with mock RIFE (verifies frame count multiplication)

---

## Data Flow

```
Input Video (30 fps)
       │
       ▼
MediaCodecFrameSource.decode()
       │
       ├─► [ORIGINAL] ──────────────────► MediaCodecFrameEncoder (30 fps)
       │
       ├─► [ENHANCED_1080P/4K] ──► ESRGAN upscale ──► MediaCodecFrameEncoder (30 fps)
       │
       └─► [SMOOTH_60FPS/120FPS]
                 │
                 ▼
         ESRGAN upscale (optional)
                 │
                 ▼
         RifeRunner.interpolate(prev, curr, scale)
                 │
                 ├─► Frame 0 (original)
                 ├─► Frame 0.5 (interpolated)
                 ├─► Frame 1 (original)
                 ├─► Frame 1.5 (interpolated)
                 └─► ...
                 │
                 ▼
         MediaCodecFrameEncoder (60/120 fps)
```

---

## RIFE Model Details

| Property | Value |
|----------|-------|
| Model | `rife_v4.6.tflite` (IFNet architecture) |
| Input | 2× RGB frames (6 channels), normalized to [-1, 1] |
| Output | 1× RGB intermediate frame |
| Input resolution | Divisible by 32 (tile internally) |
| TFLite ops | Conv2D, DepthwiseConv2D, Add, Mul, Concat, ResizeBilinear |
| GPU delegate | Supported (G71 MP2 on Helio G85) |
| Inference time (est.) | ~120ms/frame @ 1080p on Helio G85 GPU |
| APK size impact | +2.3 MB (model) + ~5 KB (code) |

**Tile strategy for 1080p/4K:**
- Split frames into 256×256 tiles with 16px overlap (like ESRGAN)
- Process tile pairs through RIFE
- Stitch with weighted blending at overlaps
- Recycle tile bitmaps immediately

---

## NFR Compliance

| NFR | Target | Phase 4 Impact |
|-----|--------|----------------|
| Idle RAM | ≤180 MB | +2.3 MB model; lazy-loaded |
| Install size | ≤120 MB | +2.3 MB model; total ~62 MB |
| Cold start | ≤2.5 s | Lazy init (no impact) |
| Shutter→save | ≤1.5 s | N/A (video async) |
| 60s clip enhance | ≤12 min | 60s × 30fps × 2× RIFE = 3600 frames × 120ms = 7.2 min (OK) |
| Enhancement RAM | ≤350 MB | Tile-based; peak ~200 MB |

---

## Implementation Order

1. **Add RIFE model asset** → `app/src/main/assets/rife_v4.6.tflite`
2. **Implement `RifeRunner.kt`** — model load, GPU delegate, tile-based inference
3. **Implement `FrameInterpolator.kt`** — streaming two-frame buffer logic
4. **Extend `VideoQualityTier`** — add `SMOOTH_60FPS`, `SMOOTH_120FPS`
5. **Integrate into `VideoUpscalePipeline`** — conditional RIFE stage
6. **Wire `RifeRunner` in `PixelForgeApp`** + `VideoEnhancementWorker`
7. **UI: `CameraViewModel` + `CameraScreen`** — slow-motion tier selector
8. **Tests** — unit + integration
9. **Build & verify** — `assembleMediaCodecDebug`, `testMediaCodecDebugUnitTest`

---

## Open Questions (Need User Input)

1. **RIFE model source**: Use `rife_v4.6.tflite` from [huggingface.co/styler00dollar/RIFE](https://huggingface.co/styler00dollar/RIFE/tree/main) (IFNet, 2.3 MB)? Or prefer a smaller distilled variant?
2. **4× interpolation**: Single 4× model pass, or two 2× passes? (Two passes = higher quality, 2× latency)
3. **Slow-motion UI**: Separate "Slow Motion" toggle + speed selector (0.5×/0.25×), or merge into quality tier chips?
4. **Audio handling**: RIFE changes video frame rate; audio must be time-stretched or kept at original speed? (Recommendation: keep audio at original speed, only video interpolated — user can choose in UI)
5. **HEVC + RIFE**: Allow `useHevc=true` with RIFE tiers? (HEVC encoder may not support 120fps on all devices; need capability probe)

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| RIFE too slow on Helio G85 GPU | Medium | Tile processing; fallback to 2× only; disable if thermal severe |
| TFLite GPU delegate unsupported ops | Low | Test model ops against G71 MP2; CPU fallback via NNAPI |
| Memory OOM at 4K + 4× RIFE | Medium | Enforce 1080p max for RIFE tiers; 4K only for SR-only tiers |
| Audio/video sync drift | Medium | Keep audio at original rate; document limitation |
| Model license | Low | RIFE v4.6 is MIT; verify before bundling |

---

## Validation Plan

1. **Unit tests** (Robolectric):
   - `RifeRunnerTest`: loads model, runs inference on 256×256 tiles
   - `FrameInterpolatorTest`: 10 input frames × 2× = 20 output frames
   - `VideoUpscalePipelineTest`: mock RIFE, verify frame count multiplication

2. **Integration test** (device/emulator):
   - Record 10s 1080p30 → enhance to `SMOOTH_60FPS` → verify 600 output frames
   - Thermal stress: run while `adb shell dumpsys batterystats` shows severe

3. **NFR checks**:
   - APK size (`aapt dump badging`)
   - RAM during enhancement (`adb shell dumpsys meminfo`)
   - 60s clip time (stopwatch)

4. **UI smoke test**:
   - Select `SMOOTH_120FPS` → record → enhance → playback smooth

---

## Rollback Plan
- Feature flag: `BuildConfig.ENABLE_RIFE` (default `false`)
- If issues: disable flag, ship without RIFE; re-enable in patch

---

## Dependencies
- No new Gradle deps (uses existing `tensorflow-lite-gpu:2.14.0`)
- Model asset: `rife_v4.6.tflite` in `assets/`
- Requires Phase 3 `VideoUpscalePipeline` (done)

---

## Estimated Scope
- ~8 new/modified Kotlin files
- ~600 lines of new code
- 1 model asset (2.3 MB)
- 3 new test files