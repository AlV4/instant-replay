import SwiftUI

struct LiveModeOverlay: View {
    @EnvironmentObject var appState: AppStateManager
    
    @State private var showSaveConfirmation = false
    @State private var saveConfirmationOpacity: Double = 0
    
    var body: some View {
        VStack {
            // Top bar with LIVE indicator and buffer info
            HStack {
                ModeIndicator(text: "LIVE", color: .red, isAnimated: true)
                
                Spacer()
                
                bufferIndicator
            }
            .padding()
            
            Spacer()
            
            // Action buttons
            VStack(spacing: 20) {
                // Save Moment button
                Button(action: {
                    appState.saveMoment()
                    showSaveAnimation()
                }) {
                    HStack(spacing: 12) {
                        Image(systemName: "square.and.arrow.down.fill")
                            .font(.title2)
                        
                        Text("Save Moment")
                            .font(.title2)
                            .fontWeight(.semibold)
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 20)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.red)
                    )
                }
                .padding(.horizontal, 40)
                
                // Stop Live button
                Button(action: {
                    appState.stopLive()
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "stop.fill")
                            .font(.caption)
                        
                        Text("Stop Live")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                    .foregroundColor(.white.opacity(0.9))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(
                        Capsule()
                            .fill(Color.black.opacity(0.5))
                            .overlay(
                                Capsule()
                                    .strokeBorder(Color.white.opacity(0.3), lineWidth: 1)
                            )
                    )
                }
            }
            .padding(.bottom, 60)
            
            // Save confirmation overlay
            if showSaveConfirmation {
                saveConfirmationOverlay
                    .opacity(saveConfirmationOpacity)
            }
        }
    }
    
    private var bufferIndicator: some View {
        HStack(spacing: 8) {
            Image(systemName: "memories")
                .font(.caption)
            
            if let stats = appState.bufferStats {
                Text(String(format: "%.1fs", stats.durationSeconds))
                    .font(.caption)
                    .fontWeight(.medium)
                    .monospacedDigit()
            } else {
                Text("0.0s")
                    .font(.caption)
                    .fontWeight(.medium)
                    .monospacedDigit()
            }
            
            Text("/ 15s")
                .font(.caption2)
                .foregroundColor(.white.opacity(0.7))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            Capsule()
                .fill(Color.black.opacity(0.6))
        )
    }
    
    private var saveConfirmationOverlay: some View {
        VStack {
            Spacer()
            
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
                Text("Saving to Photos...")
                    .fontWeight(.medium)
            }
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(
                Capsule()
                    .fill(Color.black.opacity(0.8))
            )
            
            Spacer()
        }
    }
    
    private func showSaveAnimation() {
        showSaveConfirmation = true
        withAnimation(.easeIn(duration: 0.2)) {
            saveConfirmationOpacity = 1.0
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeOut(duration: 0.3)) {
                saveConfirmationOpacity = 0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                showSaveConfirmation = false
            }
        }
    }
}

#Preview {
    ZStack {
        Color.gray
        LiveModeOverlay()
            .environmentObject(AppStateManager())
    }
}
