# Instant Replay - iOS Video Buffer App

## Overview
Instant Replay is an iOS app that continuously buffers the last 15 seconds of video from your camera. At any moment, you can tap "Save Moment" to save those 15 seconds to your Photos library.

## Requirements
- Xcode 15.0 or later
- iOS 16.0 or later
- Physical iOS device (camera features don't work in Simulator)
- Apple Developer account (free account works for device testing)

## Quick Start

1. **Open the project in Xcode:**
   - Double-click `InstantReplay.xcodeproj` to open in Xcode

2. **Configure signing:**
   - Select the project in the navigator
   - Go to "Signing & Capabilities" tab
   - Select your Team under "Signing"
   - Ensure "Automatically manage signing" is checked

3. **Connect your device:**
   - Connect your iPhone via USB
   - Trust the computer on the device if prompted
   - Select your device from the scheme selector (top of Xcode)

4. **Build and Run:**
   - Press `Cmd + R` or click the Play button
   - Accept camera and photo library permissions when prompted

## Usage

### Preview Mode
- Camera preview is active
- Aim and frame your shot
- Tap "Start Live" to begin buffering

### Live Mode
- Camera continues showing preview
- App continuously buffers the last 15 seconds
- Watch the buffer indicator fill up (top-right)
- Tap "Save Moment" to save the last 15 seconds to Photos
- You can save multiple times in a row
- Tap "Stop Live" to return to Preview mode

### Debug Overlay
- Tap the info icon (top-right) to see debug information
- Shows current mode, camera status, buffer stats, and export status

## Architecture

```
InstantReplay/
├── App/
│   ├── InstantReplayApp.swift    # App entry point
│   └── ContentView.swift         # Main view container
├── Managers/
│   ├── CameraManager.swift       # AVCaptureSession handling
│   ├── LiveBufferManager.swift   # Ring buffer for video samples
│   ├── ClipRecorder.swift        # AVAssetWriter export to Photos
│   └── AppStateManager.swift     # App lifecycle and state
├── Views/
│   ├── CameraPreviewView.swift   # UIViewRepresentable for preview
│   ├── PreviewModeOverlay.swift  # UI for Preview mode
│   ├── LiveModeOverlay.swift     # UI for Live mode
│   └── DebugOverlayView.swift    # Debug info panel
├── Models/
│   └── AppModels.swift           # Enums and data types
└── Utilities/
    └── Logger+App.swift          # os.Logger extensions
```

## Technical Details

- **Resolution:** 720p (1280x720)
- **Frame Rate:** 30 FPS
- **Buffer Duration:** 15 seconds
- **Output Format:** MP4 (H.264)
- **Audio:** Not captured (MVP)

## Known Limitations

1. Memory usage: ~200-400MB during Live mode
2. Portrait orientation only
3. Back camera only
4. No audio recording
5. No background recording (iOS restriction)

## License
MIT License
