import SwiftUI

struct DebugOverlayView: View {
    @EnvironmentObject var appState: AppStateManager
    
    @State private var isExpanded = false
    
    var body: some View {
        VStack(alignment: .trailing, spacing: 4) {
            // Toggle button
            Button(action: {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isExpanded.toggle()
                }
            }) {
                Image(systemName: isExpanded ? "xmark.circle.fill" : "info.circle.fill")
                    .font(.title2)
                    .foregroundColor(.white.opacity(0.7))
            }
            
            if isExpanded {
                VStack(alignment: .leading, spacing: 6) {
                    debugRow("Mode", value: appState.currentMode.rawValue)
                    debugRow("Camera", value: appState.cameraStatus.displayName)
                    debugRow("Session", value: appState.sessionStatus.rawValue)
                    debugRow("Buffering", value: appState.isBuffering ? "YES" : "NO")
                    
                    if let stats = appState.bufferStats {
                        Divider()
                            .background(Color.white.opacity(0.3))
                        debugRow("Samples", value: "\(stats.sampleCount)")
                        debugRow("Duration", value: String(format: "%.2fs", stats.durationSeconds))
                    }
                    
                    if appState.clipRecorder.isExporting {
                        Divider()
                            .background(Color.white.opacity(0.3))
                        debugRow("Exporting", value: "\(appState.clipRecorder.exportCount)")
                    }
                    
                    if let error = appState.clipRecorder.lastError {
                        Divider()
                            .background(Color.white.opacity(0.3))
                        Text("Error: \(error.localizedDescription)")
                            .font(.caption2)
                            .foregroundColor(.red)
                            .lineLimit(2)
                    }
                }
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.black.opacity(0.7))
                )
                .transition(.opacity.combined(with: .scale(scale: 0.9, anchor: .topTrailing)))
            }
        }
    }
    
    private func debugRow(_ label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.caption2)
                .foregroundColor(.white.opacity(0.6))
            Spacer()
            Text(value)
                .font(.caption2)
                .fontWeight(.medium)
                .foregroundColor(.white)
        }
    }
}

#Preview {
    ZStack {
        Color.gray
        VStack {
            HStack {
                Spacer()
                DebugOverlayView()
                    .environmentObject(AppStateManager())
            }
            Spacer()
        }
        .padding()
    }
}
