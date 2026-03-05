import AVFoundation
import SwiftUI
import os.log

@MainActor
final class AppStateManager: ObservableObject {
    
    // MARK: - Published Properties
    
    @Published private(set) var currentMode: AppMode = .preview
    @Published private(set) var cameraStatus: CameraStatus = .unknown
    @Published private(set) var sessionStatus: SessionStatus = .stopped
    @Published private(set) var isBuffering = false
    @Published private(set) var bufferStats: LiveBufferManager.BufferStats?
    @Published private(set) var lastSaveTime: Date?
    
    // MARK: - Public Properties
    
    let cameraManager = CameraManager()
    let bufferManager = LiveBufferManager(bufferDurationSeconds: 15.0)
    let clipRecorder = ClipRecorder()
    
    // MARK: - Private Properties
    
    private let logger = Logger.app
    private var statsTimer: Timer?
    private var wasLiveBeforeInterruption = false
    
    // MARK: - Initialization
    
    init() {
        setupCameraCallback()
    }
    
    // MARK: - Public Methods
    
    func initialize() {
        logger.info("Initializing app state")
        checkCameraPermission()
        setupNotifications()
    }
    
    func startLive() {
        guard currentMode == .preview else { return }
        guard cameraStatus == .authorized else {
            logger.warning("Cannot start Live: camera not authorized")
            return
        }
        
        logger.info("Starting Live mode")
        currentMode = .live
        isBuffering = true
        startStatsTimer()
    }
    
    func stopLive() {
        guard currentMode == .live else { return }
        
        logger.info("Stopping Live mode")
        currentMode = .preview
        isBuffering = false
        bufferManager.clear()
        stopStatsTimer()
        bufferStats = nil
    }
    
    func saveMoment() {
        guard currentMode == .live else {
            logger.warning("Cannot save: not in Live mode")
            return
        }
        
        logger.info("Saving moment")
        let samples = bufferManager.snapshot()
        
        clipRecorder.exportClip(from: samples) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let exportResult):
                    self?.logger.info("Moment saved: \(exportResult.duration)s")
                    self?.lastSaveTime = Date()
                case .failure(let error):
                    self?.logger.error("Save failed: \(error.localizedDescription)")
                }
            }
        }
    }
    
    // MARK: - Private Methods
    
    private func setupCameraCallback() {
        cameraManager.onSampleBuffer = { [weak self] sampleBuffer in
            guard let self = self else { return }
            
            // Only buffer in Live mode
            Task { @MainActor in
                if self.isBuffering {
                    self.bufferManager.addSample(sampleBuffer)
                }
            }
        }
    }
    
    private func checkCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            cameraStatus = .authorized
            setupCamera()
            
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    self?.cameraStatus = granted ? .authorized : .denied
                    if granted {
                        self?.setupCamera()
                    }
                }
            }
            
        case .denied:
            cameraStatus = .denied
            
        case .restricted:
            cameraStatus = .restricted
            
        @unknown default:
            cameraStatus = .unknown
        }
    }
    
    private func setupCamera() {
        cameraManager.configure()
        cameraManager.startSession()
        sessionStatus = .running
    }
    
    private func setupNotifications() {
        let nc = NotificationCenter.default
        
        // Session interruptions
        nc.addObserver(
            forName: .AVCaptureSessionWasInterrupted,
            object: cameraManager.captureSession,
            queue: .main
        ) { [weak self] notification in
            self?.handleSessionInterruption(notification)
        }
        
        nc.addObserver(
            forName: .AVCaptureSessionInterruptionEnded,
            object: cameraManager.captureSession,
            queue: .main
        ) { [weak self] _ in
            self?.handleInterruptionEnded()
        }
        
        // App lifecycle
        nc.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleWillResignActive()
        }
        
        nc.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleDidBecomeActive()
        }
        
        nc.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleDidEnterBackground()
        }
        
        nc.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleWillEnterForeground()
        }
        
        // Memory warning
        nc.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleMemoryWarning()
        }
    }
    
    private func handleSessionInterruption(_ notification: Notification) {
        guard let reasonValue = notification.userInfo?[AVCaptureSessionInterruptionReasonKey] as? Int,
              let reason = AVCaptureSession.InterruptionReason(rawValue: reasonValue) else {
            return
        }
        
        logger.warning("Session interrupted: \(String(describing: reason))")
        sessionStatus = .interrupted
        wasLiveBeforeInterruption = (currentMode == .live)
        
        if currentMode == .live {
            // Pause buffering but don't clear buffer yet
            isBuffering = false
        }
    }
    
    private func handleInterruptionEnded() {
        logger.info("Session interruption ended")
        sessionStatus = .running
        
        if wasLiveBeforeInterruption && currentMode == .live {
            isBuffering = true
        }
        wasLiveBeforeInterruption = false
    }
    
    private func handleWillResignActive() {
        logger.info("App will resign active")
        // Keep session running for quick resume
    }
    
    private func handleDidBecomeActive() {
        logger.info("App did become active")
        
        if sessionStatus == .stopped {
            cameraManager.startSession()
            sessionStatus = .running
        }
    }
    
    private func handleDidEnterBackground() {
        logger.info("App did enter background")
        
        // Stop Live mode when going to background
        if currentMode == .live {
            stopLive()
        }
        
        // Stop session to free resources
        cameraManager.stopSession()
        sessionStatus = .stopped
    }
    
    private func handleWillEnterForeground() {
        logger.info("App will enter foreground")
        
        // Session will be restarted in didBecomeActive
    }
    
    private func handleMemoryWarning() {
        logger.warning("Memory warning received")
        
        // If in Live mode, we might need to reduce buffer
        // For MVP, just log the warning - buffer is already bounded
    }
    
    private func startStatsTimer() {
        statsTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.bufferStats = self?.bufferManager.getStats()
            }
        }
    }
    
    private func stopStatsTimer() {
        statsTimer?.invalidate()
        statsTimer = nil
    }
}
