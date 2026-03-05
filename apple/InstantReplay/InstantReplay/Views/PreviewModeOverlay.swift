import SwiftUI

struct PreviewModeOverlay: View {
    @EnvironmentObject var appState: AppStateManager
    
    var body: some View {
        VStack {
            // Top indicator
            HStack {
                ModeIndicator(text: "PREVIEW", color: .blue)
                Spacer()
            }
            .padding()
            
            Spacer()
            
            // Start Live button
            Button(action: {
                appState.startLive()
            }) {
                HStack(spacing: 12) {
                    Circle()
                        .fill(Color.red)
                        .frame(width: 16, height: 16)
                    
                    Text("Start Live")
                        .font(.title2)
                        .fontWeight(.semibold)
                }
                .foregroundColor(.white)
                .padding(.horizontal, 32)
                .padding(.vertical, 16)
                .background(
                    Capsule()
                        .fill(Color.black.opacity(0.6))
                        .overlay(
                            Capsule()
                                .strokeBorder(Color.white.opacity(0.3), lineWidth: 1)
                        )
                )
            }
            .disabled(appState.cameraStatus != .authorized)
            .opacity(appState.cameraStatus == .authorized ? 1.0 : 0.5)
            .padding(.bottom, 60)
        }
    }
}

struct ModeIndicator: View {
    let text: String
    let color: Color
    var isAnimated: Bool = false
    
    @State private var isBlinking = false
    
    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: 10, height: 10)
                .opacity(isAnimated && isBlinking ? 0.3 : 1.0)
            
            Text(text)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            Capsule()
                .fill(Color.black.opacity(0.6))
        )
        .onAppear {
            if isAnimated {
                withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                    isBlinking = true
                }
            }
        }
    }
}

#Preview {
    ZStack {
        Color.gray
        PreviewModeOverlay()
            .environmentObject(AppStateManager())
    }
}
