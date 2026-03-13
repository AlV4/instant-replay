# Instant Replay - Android Video Buffer App

## Overview
Instant Replay is an Android app that continuously buffers the last 15 seconds of video from your camera. At any moment, tap "Save Moment" to save those 15 seconds to your device's gallery.

## Requirements
- **Android Studio** Hedgehog (2023.1.1) or later
- **Android SDK** 34 (Android 14)
- **Min SDK** 26 (Android 8.0)
- **Physical Android device** (camera features may not work properly in emulator)
- **JDK 17** or later

## Quick Start

1. **Open in Android Studio:**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the `InstantReplayAndroid` folder and select it
   - Wait for Gradle sync to complete

2. **Connect your device:**
   - Enable Developer Options on your Android device
   - Enable USB Debugging
   - Connect via USB cable
   - Accept the debugging prompt on the device

3. **Build and Run:**
   - Select your device from the device dropdown
   - Click the Run button (green play icon) or press `Shift+F10`
   - Grant camera permission when prompted

## Usage

### Preview Mode
- Camera preview is active
- Aim and frame your shot
- Tap "Start Live" to begin buffering

### Live Mode
- Camera continues showing preview
- App continuously buffers the last 15 seconds
- Watch the buffer indicator fill up (top-right)
- Tap "Save Moment" to save the last 15 seconds to Gallery
- You can save multiple times in a row
- Tap "Stop Live" to return to Preview mode

### Debug Overlay
- Tap the info icon (top-right) to see debug information
- Shows current mode, camera status, buffer stats, and export status

## Architecture

```
app/src/main/java/com/instantreplay/app/
├── MainActivity.kt              # App entry point
├── ui/
│   ├── InstantReplayApp.kt      # Main Composable screen
│   └── theme/
│       └── Theme.kt             # Material3 theme
├── camera/
│   └── CameraManager.kt         # CameraX handling
├── buffer/
│   └── LiveBufferManager.kt     # Ring buffer for frames
├── recorder/
│   └── ClipRecorder.kt          # MediaCodec encoding
└── state/
    ├── AppModels.kt             # Data classes & enums
    └── AppStateManager.kt       # ViewModel
```

## Technical Details

- **Resolution:** 720p (1280x720)
- **Frame Rate:** 30 FPS (approximate, device-dependent)
- **Buffer Duration:** 15 seconds
- **Output Format:** MP4 (H.264)
- **Audio:** Not captured (MVP)

## Key Technologies

- **Jetpack Compose** - Modern declarative UI
- **CameraX** - Camera abstraction library
- **MediaCodec** - Hardware-accelerated video encoding
- **MediaStore** - Gallery integration
- **Kotlin Coroutines** - Async operations
- **ViewModel** - Lifecycle-aware state management

## Known Limitations

1. **Memory Usage:** Ring buffer holding 15s of 720p frames uses ~200-400MB RAM
2. **Portrait Only:** Video orientation is locked to portrait
3. **Back Camera Only:** Front camera not supported in MVP
4. **No Audio:** Microphone capture not implemented
5. **No Background:** Recording stops when app goes to background

## Troubleshooting

### Build fails with "SDK not found"
- Open Android Studio → Settings → Appearance & Behavior → System Settings → Android SDK
- Install Android SDK 34

### Camera shows black screen
- Ensure you granted camera permission
- Try on a physical device instead of emulator
- Check that no other app is using the camera

### Export fails
- Ensure sufficient storage space
- Grant storage permissions if on older Android versions

### App crashes on older devices
- Minimum supported version is Android 8.0 (API 26)
- Some devices may have limited MediaCodec support

## Future Improvements

1. Configurable buffer duration (5s / 15s / 30s / 60s)
2. Resolution and quality settings
3. Audio recording support
4. Front camera support
5. Landscape orientation
6. Clip trimming before save
7. In-app gallery preview

## License
MIT License
