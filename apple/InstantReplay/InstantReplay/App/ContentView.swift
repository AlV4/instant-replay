import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppStateManager
    
    var body: some View {
        ZStack {
            // Camera preview layer (always visible)
            CameraPreviewView(session: appState.cameraManager.captureSession)
                .ignoresSafeArea()
            
            // Mode-specific overlay
            switch appState.currentMode {
            case .preview:
                PreviewModeOverlay()
                    .environmentObject(appState)
            case .live:
                LiveModeOverlay()
                    .environmentObject(appState)
            }
            
            // Debug overlay (top-right corner)
            VStack {
                HStack {
                    Spacer()
                    DebugOverlayView()
                        .environmentObject(appState)
                }
                Spacer()
            }
            .padding()
            
            // Permission denied overlay
            if appState.cameraStatus == .denied || appState.cameraStatus == .restricted {
                permissionDeniedOverlay
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private var permissionDeniedOverlay: some View {
        ZStack {
            Color.black.opacity(0.9)
                .ignoresSafeArea()
            
            VStack(spacing: 20) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.red)
                
                Text("Camera Access Required")
                    .font(.title2)
                    .fontWeight(.bold)
                
                Text("Please enable camera access in Settings to use Instant Replay.")
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)
                    .padding(.horizontal)
                
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(AppStateManager())
}
