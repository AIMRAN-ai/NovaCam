# Phase 5 Plan: Real Model Assets, Optimization & Production-Ready App Functionality

## Context
Phases 1-4 implemented the full ML pipeline architecture with **real model interfaces** (not dummies):
- **ESRGAN-Lite** (`esrgan_lite.tflite`) - 2×/4× super-resolution, tiled inference
- **FlowNet-Tiny** (`flownet_tiny.tflite`) - Optical flow for burst alignment (≤8px)
- **BurstFusion-Net** (`burstfusion_net.tflite`) - 5-layer U-Net multi-frame fusion
- **RIFE v4.6 / IFNet** (`rife_v4.6.tflite`) - Frame interpolation (2×/4×)

**Current state**: All 4 model assets are **missing or empty placeholders** (0 bytes). Runners fall back to CPU implementations when assets absent. Phase 5 makes this production-ready with real models.

---

## Goals

| Goal | Success Criteria |
|------|------------------|
| **Source & bundle real models** | 4 quantized .tflite models in `assets/`, total ≤15 MB |
| **Helio G85 optimization** | GPU delegate works on Mali-G52; NNAPI fallback; <200ms/frame ESRGAN, <120ms RIFE |
| **Audio for slow-motion** | Time-stretch audio to match interpolated video fps; user choice: stretch vs keep original |
| **Export & sharing** | Save to MediaStore, share via Intent, gallery integration |
| **Thermal/performance profiling** | Per-stage timings in Diagnostics; auto-degrade on thermal throttle |
| **Model versioning** | Remote model check/update; local asset priority |

---

## Model Assets Required

| Model | Source | Target Size | Input | Output | Ops |
|-------|--------|-------------|-------|--------|-----|
| ESRGAN-Lite | [TFHub ESRGAN](https://tfhub.dev/captain-pool/esrgan-tf2/1) → convert to TFLite | ~4 MB | 256×256×3 | 512×512×3 | Conv2D, LeakyReLU, PixelShuffle |
| FlowNet-Tiny | [FlowNet2](https://github.com/NVIDIA/flownet2-pytorch) → prune/quantize | ~1.5 MB | 2×128×128×3 | 128×128×2 (dx,dy) | Conv2D, Correlation, UpConv |
| BurstFusion-Net | Custom 5-layer U-Net (train on synthetic bursts) | ~2 MB | N×256×256×3 | 256×256×3 | Conv2D, Concat, UpConv |
| RIFE v4.6 | [RIFE](https://github.com/hzwer/RIFE) → `rife_v4.6.tflite` (IFNet) | ~2.3 MB | 2×256×256×3 | 256×256×3 | Conv2D, DepthwiseConv2D, ResizeBilinear |

**Total**: ~10 MB (well within 120 MB APK budget)

---

## Implementation Tasks

### 1. Model Acquisition & Conversion
- [ ] **ESRGAN-Lite**: Export from TFHub RRDB model → quantize (int8) → validate PSNR on Set5/Set14
- [ ] **FlowNet-Tiny**: Prune FlowNet2-S to 128×128 → quantize → validate EPE on MPI-Sintel
- [ ] **BurstFusion-Net**: Train 5-layer U-Net on synthetic bursts (add noise, misalign) → quantize
- [ ] **RIFE v4.6**: Use pre-converted `rife_v4.6.tflite` from HuggingFace (MIT license)
- [ ] **Validation script**: Python script to run each model on test images, compare CPU vs GPU delegate output

### 2. Asset Integration
- [ ] Place 4 `.tflite` files in `app/src/main/assets/`
- [ ] Update `PixelForgeApp.kt` model asset constants to match actual filenames
- [ ] Verify `assets.list("")?.contains(modelAsset)` works for all 4
- [ ] Add model metadata JSON: `model_metadata.json` with version, SHA256, input/output specs

### 3. GPU Delegate Tuning (Helio G85 / Mali-G52)
- [ ] Test GPU delegate compatibility for each model (check unsupported ops)
- [ ] Add delegate fallback chain: **GPU → NNAPI → CPU-XNNPACK**
- [ ] Profile each model on device: latency, memory, thermal
- [ ] Tune tile sizes: ESRGAN 256→128 if OOM; RIFE 256→128 if thermal
- [ ] Document delegate support matrix in `MODEL_DELEGATES.md`

### 4. Audio Handling for Slow-Motion (Critical UX)
- [ ] **Time-stretch audio** using `MediaCodec` + `Sonic` library or `RubberBand` (JNI)
- [ ] **Two modes**:
  - `STRETCH`: Audio slows down with video (natural slow-mo)
  - `KEEP_ORIGINAL`: Audio plays at normal speed, video interpolated (music videos)
- [ ] Add `AudioMode` enum to `VideoQualityTier` or separate `VideoExportConfig`
- [ ] Implement in `VideoUpscalePipeline` → `MediaMuxer` with adjusted audio PTS

### 5. Export, Gallery & Sharing
- [ ] **MediaStore integration**: Save enhanced video with proper mime type, date, location
- [ ] **Share Intent**: `ACTION_SEND` with `video/mp4` or `video/hevc`
- [ ] **Gallery thumbnail**: Generate poster frame (first frame) for MediaStore
- [ ] **Progress notification**: Show "Exporting to gallery..." with percentage
- [ ] **Completion toast**: "Saved to PixelForge album" with open action

### 6. Thermal-Aware Adaptive Pipeline
- [ ] Extend `ThermalGuard` to **per-stage** checks (decode/ESRGAN/RIFE/encode)
- [ ] **Degradation policy**:
  - Severe thermal → drop RIFE (fallback to SR-only)
  - Critical thermal → drop ESRGAN (fallback to original resolution)
  - Log degradation in Diagnostics
- [ ] Add `ThermalDegradation` enum to `VideoJobStats`

### 7. Remote Model Updates (Optional but Recommended)
- [ ] Add `ModelManager` class: checks GitHub Releases / Firebase for newer models
- [ ] Download to app-specific storage (not assets), verify SHA256
- [ ] Fallback to bundled assets if download fails
- [ ] User setting: "Auto-update models" (default off, saves bandwidth)

### 8. End-to-End Integration Tests
- [ ] **Burst SR test**: 8-frame burst → `BALANCED` tier → verify output dimensions, PSNR
- [ ] **Video SR test**: 10s 1080p30 → `ENHANCED_1080P` → verify frame count, size
- [ ] **Slow-mo test**: 5s 1080p30 → `SMOOTH_60FPS` → verify 2× frames, audio sync
- [ ] **Thermal test**: Run 60s clip enhancement while `adb shell dumpsys batterystats` shows severe

### 9. Diagnostics & Telemetry Enhancements
- [ ] Per-stage timing breakdown in `Diagnostics` (align/fuse/upscale/interpolate/encode)
- [ ] Delegate used per model (GPU/NNAPI/CPU)
- [ ] Memory peak during enhancement (via `Debug.getRuntimeStat`)
- [ ] Export to CSV for analysis

---

## Architecture Decisions (Pre-Resolved)

| Decision | Resolution | Rationale |
|----------|------------|-----------|
| **Model storage** | Bundled in APK assets (primary) + optional remote update | Offline-first; no network permission needed for core features |
| **Audio library** | `Sonic` (pure Java, Apache 2.0) for time-stretch | No JNI, small (~50 KB), good quality for speech/music |
| **GPU delegate** | Enable for all models; blacklist unsupported ops per model | Mali-G52 supports most TFLite ops; fallback is automatic |
| **Tile size** | ESRGAN: 256 (128 fallback); RIFE: 256 (128 fallback) | Balance memory vs seam quality |
| **Audio default** | `STRETCH` for ≤60fps, `KEEP_ORIGINAL` for 120fps | 120fps audio stretch sounds unnatural |
| **Model versioning** | Semantic version in metadata JSON; bundle 1.0.0 | Allows future updates without breaking changes |

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| GPU delegate crashes on Mali-G52 | Medium | Test each model; maintain op blacklist; force CPU for problematic ops |
| Model conversion loses quality | Medium | Validate PSNR/SSIM against FP32 reference; keep quantization-aware training |
| Audio stretch introduces artifacts | Medium | Use high-quality Sonic; offer `KEEP_ORIGINAL` mode |
| Thermal throttle kills 60s clip | Medium | Per-stage thermal check; progressive degradation |
| APK size exceeds 120 MB | Low | Models total ~10 MB; use App Bundle for split delivery |
| NNAPI slower than GPU on G85 | Low | Benchmark; prefer GPU delegate when available |

---

## Validation Checklist

### Functional
- [ ] All 4 models load from assets without fallback
- [ ] Burst SR: 8 frames → 2× output, no artifacts
- [ ] Video SR: 1080p30 → 1080p30 enhanced, visible detail improvement
- [ ] Slow-mo 2×: 30→60fps, smooth motion, audio in sync (STRETCH mode)
- [ ] Slow-mo 4×: 30→120fps, audio in sync (KEEP_ORIGINAL default)
- [ ] Save to gallery → appears in Photos app
- [ ] Share via WhatsApp/Telegram → plays correctly

### Performance (Helio G85)
- [ ] ESRGAN 1080p tile: <200ms (GPU) / <500ms (CPU)
- [ ] RIFE 1080p frame pair: <120ms (GPU) / <300ms (CPU)
- [ ] 60s clip ENHANCED_1080P: <8 min
- [ ] 60s clip SMOOTH_60FPS: <12 min
- [ ] Peak RAM: <350 MB
- [ ] Thermal: No critical throttle during 60s clip

### Quality
- [ ] ESRGAN PSNR ≥28 dB on Set5 (vs bicubic)
- [ ] FlowNet EPE ≤2.0 px on Sintel clean
- [ ] BurstFusion: noise reduction visible at ISO 800+
- [ ] RIFE: no ghosting on moderate motion (≤16px/frame)

---

## File Changes Summary

### New Files
```
app/src/main/assets/
  esrgan_lite.tflite           (~4 MB)
  flownet_tiny.tflite          (~1.5 MB)
  burstfusion_net.tflite       (~2 MB)
  rife_v4.6.tflite             (~2.3 MB)
  model_metadata.json          (version, SHA256, specs)

app/src/main/java/com/pixelforge/app/ml/
  ModelManager.kt              (remote update check)
  AudioTimeStretch.kt          (Sonic wrapper)

app/src/main/java/com/pixelforge/app/video/
  AudioMode.kt                 (STRETCH / KEEP_ORIGINAL)
  VideoExportConfig.kt         (tier + audioMode + useHevc)
  GalleryExporter.kt           (MediaStore save + share)

app/src/test/java/com/pixelforge/app/ml/
  EsrganModelTest.kt
  FlowNetModelTest.kt
  BurstFusionModelTest.kt
  RifeModelTest.kt

app/src/test/java/com/pixelforge/app/video/
  AudioTimeStretchTest.kt
  GalleryExporterTest.kt
  ThermalDegradationTest.kt
```

### Modified Files
```
PixelForgeApp.kt              (ModelManager, updated asset names)
VideoUpscalePipeline.kt       (AudioMode, thermal degradation, GalleryExporter)
VideoEnhancementWorker.kt     (pass AudioMode, export to gallery)
CameraViewModel.kt            (audioMode selector, export flow)
CameraScreen.kt               (audio mode UI, share button)
Diagnostics.kt                (per-stage timings, delegate tracking, thermal degradation)
```

---

## Dependencies to Add

```kotlin
// build.gradle.kts
implementation("com.github.besherh:sonic-android:1.0.0")  // Audio time-stretch
// Or local JNI build of RubberBand if higher quality needed
```

---

## Out of Scope (Future Phases)
- HDR video capture (requires CameraX HDR extensions)
- Real-time preview of enhancement (too heavy for preview)
- Cloud enhancement fallback (privacy policy prohibits)
- Multi-camera fusion (wide + main)
- RAW video recording (storage/bandwidth)

---

## Rollout Plan
1. **Internal alpha**: Team devices, all 4 models bundled
2. **Beta**: TestFlight/Play Internal, monitor crashlytics for delegate crashes
3. **Staged rollout**: 10% → 50% → 100% with remote config for model versions
4. **Post-launch**: Collect anonymous performance metrics (opt-in)

---

## Open Questions (Need Decision)

1. **Audio library**: Sonic (pure Java) vs RubberBand (JNI, better quality)? → **Recommendation: Sonic first, RubberBand if quality complaints**
2. **Remote model hosting**: GitHub Releases vs Firebase Hosting vs S3? → **Recommendation: GitHub Releases (free, versioned)**
3. **Default audio mode for 120fps**: STRETCH or KEEP_ORIGINAL? → **Recommendation: KEEP_ORIGINAL**
4. **Model quantization**: int8 vs fp16? → **Recommendation: int8 for GPU, fp16 for NNAPI/CPU**
5. **Telemetry**: Anonymous performance upload? → **Recommendation: Opt-in only, GDPR-compliant**