# Photo & Video Editor Plan - PixelForge

## Overview
Add a comprehensive photo/video editor to PixelForge that opens after capture. Phased approach:
- **Phase 1**: Photo editor with 15 adjustments, presets, crop, GPU shader preview
- **Phase 2**: Multi-track video timeline editor with keyframes

---

## Phase 1: Photo Editor

### 1.1 Data Models

**File**: `app/src/main/java/com/pixelforge/app/edit/PhotoEditState.kt`

```kotlin
// Adjustment parameters (-100 to +100)
data class PhotoAdjustments(
    val exposure: Float = 0f,      // EV compensation
    val ev: Float = 0f,            // Exposure value (synced with exposure)
    val brilliance: Float = 0f,    // Local contrast/clarity
    val vignette: Float = 0f,      // Corner darkening
    val noiseReduction: Float = 0f, // Luminance NR
    val definition: Float = 0f,    // Mid-tone contrast
    val sharpness: Float = 0f,     // Edge sharpening
    val tint: Float = 0f,          // Green-Magenta
    val warmth: Float = 0f,        // Blue-Orange (temperature)
    val vibrance: Float = 0f,      // Smart saturation
    val blackPoint: Float = 0f,    // Lift blacks
    val brightness: Float = 0f,    // Overall brightness
    val contrast: Float = 0f,      // Global contrast
    val shadows: Float = 0f,       // Shadow lift
    val highlights: Float = 0f,    // Highlight recovery
)

// Crop state
data class CropState(
    val rect: RectF = RectF(0f, 0f, 1f, 1f),  // Normalized crop rect
    val rotationDegrees: Float = 0f,
    val aspectRatio: AspectRatio? = null,      // Locked ratio
    val perspectiveTransform: Matrix? = null,  // For perspective correction
)

// Preset definition
data class PhotoPreset(
    val id: String,
    val name: String,
    val thumbnailLutPath: String?,  // Optional LUT for preview
    val adjustments: PhotoAdjustments,
    val isIPhonePreset: Boolean = false,
)

enum class AspectRatio(val label: String, val ratio: Float) {
    FREE("Free", 0f),
    SQUARE("1:1", 1f),
    RATIO_4_5("4:5", 0.8f),
    RATIO_9_16("9:16", 0.5625f),
    RATIO_16_9("16:9", 1.777f),
    RATIO_3_4("3:4", 0.75f),
}
```

### 1.2 GPU Shader Pipeline

**Files**:
- `app/src/main/java/com/pixelforge/app/edit/gpu/ShaderProgram.kt` - Shader compilation/linking
- `app/src/main/java/com/pixelforge/app/edit/gpu/AdjustmentShader.kt` - All 15 adjustments in one fragment shader
- `app/src/main/java/com/pixelforge/app/edit/gpu/LutShader.kt` - 3D LUT application
- `app/src/main/java/com/pixelforge/app/edit/gpu/CropShader.kt` - Crop/rotate/perspective
- `app/src/main/java/com/pixelforge/app/edit/gpu/ShaderPipeline.kt` - Composes shaders
- `app/src/main/assets/shaders/adjustment.frag` - GLSL fragment shader
- `app/src/main/assets/shaders/lut.frag` - LUT shader
- `app/src/main/assets/shaders/crop.frag` - Crop shader
- `app/src/main/assets/shaders/vertex.vert` - Shared vertex shader

**Shader Architecture**:
- Single-pass fragment shader combining all 15 adjustments
- Uniforms for each parameter (-1 to 1 normalized)
- 3D texture for LUTs (16³ or 32³)
- Crop/transform via vertex shader + fragment sampling

### 1.3 PhotoEditorViewModel

**File**: `app/src/main/java/com/pixelforge/app/edit/PhotoEditorViewModel.kt`

Responsibilities:
- Load source image (Bitmap or URI)
- Manage `PhotoAdjustments`, `CropState`, selected `PhotoPreset`
- GPU preview via `ShaderPipeline` + `SurfaceTexture`/`TextureView`
- Auto-enhance (analyze histogram, compute optimal adjustments)
- Export: render final bitmap via GPU compute or CPU fallback
- Undo/redo stack for adjustments
- Save to MediaStore

### 1.4 Editor UI (Compose)

**File**: `app/src/main/java/com/pixelforge/app/ui/PhotoEditorScreen.kt`

Layout:
```
┌─────────────────────────────────────┐
│ TopAppBar: Title, Undo/Redo, Done   │
├─────────────────────────────────────┤
│                                     │
│   Preview (GLSurfaceView/TextureView)│
│   with real-time shader preview     │
│                                     │
├─────────────────────────────────────┤
│ Bottom Tabs: Adjust | Filters | Crop│
├─────────────────────────────────────┤
│ Adjust Panel:                       │
│  15 sliders (-100 to +100) grouped  │
│  [Light] Exposure, Brilliance, etc. │
│  [Color] Warmth, Tint, Vibrance     │
│  [Detail] Sharpness, Definition, NR │
│  [Auto] button                      │
├─────────────────────────────────────┤
│ Filters Panel:                      │
│  Horizontal scroll of preset chips  │
│  Thumbnail preview via LUT shader   │
│  iPhone preset section              │
├─────────────────────────────────────┤
│ Crop Panel:                         │
│  Aspect ratio chips                 │
│  Rotate 90°, Flip buttons           │
│  Perspective correction handles     │
│  Free-form drag handles             │
└─────────────────────────────────────┘
```

### 1.5 iPhone Presets

**File**: `app/src/main/java/com/pixelforge/app/edit/presets/IPhonePresets.kt`

- Define 10-15 iPhone-style presets (Original, Vivid, Dramatic, Silvertone, Noir, etc.)
- Each preset = `PhotoAdjustments` + optional `.cube` LUT file
- LUTs stored in `assets/luts/iphone/`
- Thumbnail generation: render 128x128 preview with preset applied

### 1.6 Integration with Camera Flow

**File**: `app/src/main/java/com/pixelforge/app/ui/CameraViewModel.kt`

- After capture (`lastSavedUri` or `lastEnhancedUri` set), navigate to `PhotoEditorScreen`
- Pass source URI to `PhotoEditorViewModel`
- On editor "Done", return enhanced URI to camera UI

---

## Phase 2: Video Timeline Editor

### 2.1 Timeline Data Models

**File**: `app/src/main/java/com/pixelforge/app/edit/timeline/TimelineModels.kt`

```kotlin
data class Timeline(
    val tracks: List<Track> = emptyList(),
    val durationUs: Long,
    val playbackPositionUs: Long = 0,
)

data class Track(
    val id: String,
    val type: TrackType,  // VIDEO, AUDIO, OVERLAY
    val clips: List<Clip> = emptyList(),
    val isEnabled: Boolean = true,
    val isLocked: Boolean = false,
)

data class Clip(
    val id: String,
    val sourceUri: Uri,
    val startTimeUs: Long,      // Position on timeline
    val trimStartUs: Long,      // In-point in source
    val trimEndUs: Long,        // Out-point in source
    val adjustments: VideoAdjustments = VideoAdjustments(), // Keyframeable
    val keyframes: List<Keyframe> = emptyList(),
    val opacity: Float = 1f,
    val transform: Transform = Transform(),
)

data class Keyframe(
    val timeUs: Long,
    val property: KeyframeProperty,  // POSITION, SCALE, ROTATION, OPACITY, ADJUSTMENT_*
    val value: FloatArray,
    val interpolation: Interpolation = Interpolation.LINEAR,
)

enum class KeyframeProperty {
    POSITION_X, POSITION_Y,
    SCALE_X, SCALE_Y,
    ROTATION,
    OPACITY,
    EXPOSURE, CONTRAST, ... // All 15 adjustments
}
```

### 2.2 VideoEditorViewModel

**File**: `app/src/main/java/com/pixelforge/app/edit/VideoEditorViewModel.kt`

- Timeline state management (add/remove/move clips, trim, split)
- Keyframe animation for all properties
- Real-time preview via GPU shader pipeline (frame-accurate)
- Playback controls (scrub, play/pause, loop)
- Multi-track compositing (blend modes, opacity)
- Export via `VideoExportPipeline`

### 2.3 GPU Video Pipeline

**Files**:
- `app/src/main/java/com/pixelforge/app/edit/gpu/VideoShaderPipeline.kt` - Extends photo pipeline for video
- `app/src/main/java/com/pixelforge/app/edit/gpu/ComputeExportPipeline.kt` - GPU compute shader export
- Uses `SurfaceTexture` + `MediaCodec` for frame input
- Compute shaders for final export (avoids CPU readback)

### 2.4 Timeline UI (Compose)

**File**: `app/src/main/java/com/pixelforge/app/ui/VideoEditorScreen.kt`

Layout:
```
┌─────────────────────────────────────┐
│ TopAppBar: Project, Undo, Export    │
├─────────────────────────────────────┤
│ Preview (TextureView + Shader)      │
├─────────────────────────────────────┤
│ Timeline Ruler (timecode, zoom)     │
├─────────────────────────────────────┤
│ Track Headers + Clips (draggable)   │
│  ▼ Video 1  [clip][clip][clip]      │
│  ▼ Video 2  [clip]                  │
│  ▼ Audio 1  [waveform]              │
├─────────────────────────────────────┤
│ Inspector Panel (context-sensitive) │
│  Clip: Trim, Adjustments, Keyframes │
│  Track: Blend, Opacity, Lock        │
│  Project: Resolution, FPS           │
└─────────────────────────────────────┘
```

### 2.5 Video Export

- `VideoExportPipeline` using `MediaCodec` + `MediaMuxer`
- GPU compute shader for frame processing (matching preview)
- Audio: copy original or apply `AudioTimeStretch` for speed changes
- Support HEVC/H.264, configurable bitrate

---

## Technical Details

### GPU Shader Implementation

**Adjustment Fragment Shader** (`adjustment.frag`):
```glsl
#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform float uExposure;      // -1 to 1
uniform float uBrilliance;    // -1 to 1
uniform float uVignette;      // 0 to 1
uniform float uNoiseReduction; // 0 to 1
uniform float uDefinition;    // -1 to 1
uniform float uSharpness;     // 0 to 1
uniform float uTint;          // -1 to 1
uniform float uWarmth;        // -1 to 1
uniform float uVibrance;      // -1 to 1
uniform float uBlackPoint;    // -1 to 1
uniform float uBrightness;    // -1 to 1
uniform float uContrast;      // -1 to 1
uniform float uShadows;       // -1 to 1
uniform float uHighlights;    // -1 to 1
uniform sampler3D uLut;       // Optional LUT
uniform float uLutIntensity;  // 0 to 1

// ... implementation combining all adjustments in one pass
```

**Performance Targets**:
- Preview: 60fps on Mali-G52 (Redmi 13C)
- Export: <2x realtime for 4K on same device
- Memory: <200MB during edit

### Dependencies to Add

```kotlin
// build.gradle.kts
dependencies {
    // OpenGL ES 3.2
    implementation("androidx.glance:glance:1.0.0") // For GLSurfaceView interop
    
    // Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:glide-compiler:4.16.0")
    
    // LUT parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines for async export
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

### Permissions

No new permissions needed (uses existing MediaStore access).

---

## Implementation Tasks

### Phase 1 Tasks

| # | Task | File(s) | Notes |
|---|------|---------|-------|
| 1 | Create data models | `PhotoEditState.kt` | Adjustments, Crop, Presets |
| 2 | Write GLSL shaders | `assets/shaders/*.frag`, `*.vert` | Single-pass adjustment shader |
| 3 | Shader pipeline | `ShaderPipeline.kt`, `AdjustmentShader.kt`, `LutShader.kt`, `CropShader.kt` | Compile, link, uniform management |
| 4 | PhotoEditorViewModel | `PhotoEditorViewModel.kt` | State, preview, export, undo/redo |
| 5 | PhotoEditorScreen | `PhotoEditorScreen.kt` | Compose UI with 3 tab panels |
| 6 | iPhone presets | `IPhonePresets.kt`, `assets/luts/iphone/*.cube` | 10-15 presets with LUTs |
| 7 | Auto-enhance | `AutoEnhance.kt` | Histogram analysis → optimal adjustments |
| 8 | Camera integration | Modify `CameraViewModel.kt`, `CameraScreen.kt` | Navigate to editor after capture |
| 9 | Export pipeline | `PhotoExportPipeline.kt` | GPU compute or CPU fallback |
| 10 | Tests | Unit tests for adjustments, shader compilation | |

### Phase 2 Tasks (Outline)

| # | Task | File(s) |
|---|------|---------|
| 1 | Timeline data models | `TimelineModels.kt` |
| 2 | VideoEditorViewModel | `VideoEditorViewModel.kt` |
| 3 | Video shader pipeline | `VideoShaderPipeline.kt`, `ComputeExportPipeline.kt` |
| 4 | VideoEditorScreen | `VideoEditorScreen.kt` |
| 5 | Timeline Compose components | `TimelineTrack.kt`, `TimelineClip.kt`, `TimelineRuler.kt` |
| 6 | Keyframe editor | `KeyframeEditor.kt` |
| 7 | Video export | `VideoExportPipeline.kt` |
| 8 | Audio integration | `AudioTrackEditor.kt` |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| GPU shader complexity | Start with CPU ColorMatrix fallback; validate each adjustment in isolation |
| Mali-G52 performance | Profile each shader; use tile-based rendering; reduce LUT size to 16³ |
| Memory pressure | Bitmap pooling; release textures immediately; use `inBitmap` |
| LUT color space | Ensure sRGB linearization; test with known reference images |
| Video timeline complexity | Phase 2 only after Phase 1 stable; start with single-track |

---

## Validation Plan

### Phase 1
- [ ] All 15 sliders respond in real-time (60fps preview)
- [ ] Presets apply correctly (visual comparison with reference)
- [ ] Crop: rotate, aspect lock, perspective work
- [ ] Auto-enhance produces reasonable results
- [ ] Export matches preview (pixel-perfect)
- [ ] Undo/redo stack works
- [ ] Memory < 200MB during edit session

### Phase 2
- [ ] Multi-track playback at 30fps preview
- [ ] Keyframe interpolation smooth
- [ ] Trim/split operations frame-accurate
- [ ] Export matches timeline preview
- [ ] Audio sync maintained

---

## Open Questions

1. **LUT format**: Use `.cube` (text) or binary `.3dl`? `.cube` is standard but slower to parse.
2. **Perspective correction**: Full 4-point or just keystone? Full 4-point needs matrix in shader.
3. **RAW editing**: Support DNG input? Requires demosaicing before shader pipeline.
4. **HDR editing**: Support HDR10/HLG? Need 10-bit pipeline and PQ/HLG tone mapping.
5. **Cloud sync**: Not in scope (offline-first per architecture).

---

## File Structure (New)

```
app/src/main/java/com/pixelforge/app/
├── edit/
│   ├── PhotoEditState.kt
│   ├── PhotoEditorViewModel.kt
│   ├── PhotoExportPipeline.kt
│   ├── AutoEnhance.kt
│   ├── presets/
│   │   ├── PhotoPreset.kt
│   │   ├── IPhonePresets.kt
│   │   └── PresetManager.kt
│   ├── gpu/
│   │   ├── ShaderProgram.kt
│   │   ├── AdjustmentShader.kt
│   │   ├── LutShader.kt
│   │   ├── CropShader.kt
│   │   ├── ShaderPipeline.kt
│   │   └── ComputeExportPipeline.kt
│   └── timeline/          # Phase 2
│       ├── TimelineModels.kt
│       ├── VideoEditorViewModel.kt
│       ├── VideoExportPipeline.kt
│       └── KeyframeEditor.kt
├── ui/
│   ├── PhotoEditorScreen.kt
│   └── VideoEditorScreen.kt  # Phase 2
└── assets/
    ├── shaders/
    │   ├── vertex.vert
    │   ├── adjustment.frag
    │   ├── lut.frag
    │   └── crop.frag
    └── luts/
        └── iphone/
            ├── vivid.cube
            ├── dramatic.cube
            └── ...
```