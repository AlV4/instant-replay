import AVFoundation
import UIKit
import os.log

final class CameraManager: NSObject, ObservableObject {
    
    // MARK: - Published Properties
    
    @Published private(set) var isSessionRunning = false
    @Published private(set) var sessionError: Error?
    
    // MARK: - Public Properties
    
    let captureSession = AVCaptureSession()
    
    var onSampleBuffer: ((CMSampleBuffer) -> Void)?
    
    // MARK: - Private Properties
    
    private let cameraQueue = DispatchQueue(label: "com.instantreplay.camera", qos: .userInitiated)
    private var videoOutput: AVCaptureVideoDataOutput?
    private var videoDevice: AVCaptureDevice?
    private var isConfigured = false
    
    private let logger = Logger.camera
    
    // MARK: - Initialization
    
    override init() {
        super.init()
        setupNotifications()
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    
    // MARK: - Public Methods
    
    func configure() {
        guard !isConfigured else { return }
        
        cameraQueue.async { [weak self] in
            self?.configureSession()
        }
    }
    
    func startSession() {
        cameraQueue.async { [weak self] in
            guard let self = self else { return }
            
            if !self.captureSession.isRunning {
                self.logger.info("Starting capture session")
                self.captureSession.startRunning()
                
                DispatchQueue.main.async {
                    self.isSessionRunning = self.captureSession.isRunning
                }
            }
        }
    }
    
    func stopSession() {
        cameraQueue.async { [weak self] in
            guard let self = self else { return }
            
            if self.captureSession.isRunning {
                self.logger.info("Stopping capture session")
                self.captureSession.stopRunning()
                
                DispatchQueue.main.async {
                    self.isSessionRunning = false
                }
            }
        }
    }
    
    // MARK: - Private Methods
    
    private func configureSession() {
        logger.info("Configuring capture session")
        
        captureSession.beginConfiguration()
        defer { captureSession.commitConfiguration() }
        
        // Set session preset
        if captureSession.canSetSessionPreset(.hd1280x720) {
            captureSession.sessionPreset = .hd1280x720
        } else {
            logger.warning("720p preset not available, using default")
        }
        
        // Get back camera
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            logger.error("Back camera not available")
            return
        }
        
        videoDevice = camera
        
        // Configure camera for 30 FPS
        do {
            try camera.lockForConfiguration()
            
            let targetFrameDuration = CMTime(value: 1, timescale: 30)
            camera.activeVideoMinFrameDuration = targetFrameDuration
            camera.activeVideoMaxFrameDuration = targetFrameDuration
            
            camera.unlockForConfiguration()
            logger.info("Camera configured for 30 FPS")
        } catch {
            logger.error("Failed to configure camera: \(error.localizedDescription)")
        }
        
        // Add video input
        do {
            let videoInput = try AVCaptureDeviceInput(device: camera)
            
            if captureSession.canAddInput(videoInput) {
                captureSession.addInput(videoInput)
                logger.info("Video input added")
            } else {
                logger.error("Cannot add video input")
                return
            }
        } catch {
            logger.error("Failed to create video input: \(error.localizedDescription)")
            return
        }
        
        // Add video output
        let output = AVCaptureVideoDataOutput()
        output.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
        ]
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: cameraQueue)
        
        if captureSession.canAddOutput(output) {
            captureSession.addOutput(output)
            videoOutput = output
            
            // Set video orientation
            if let connection = output.connection(with: .video) {
                if connection.isVideoRotationAngleSupported(90) {
                    if #available(iOS 17.0, *) {
                        connection.videoRotationAngle = 90
                    } else {
                        // Fallback on earlier versions
                    }
                }
            }
            
            logger.info("Video output added")
        } else {
            logger.error("Cannot add video output")
            return
        }
        
        isConfigured = true
        logger.info("Capture session configured successfully")
    }
    
    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(sessionRuntimeError),
            name: .AVCaptureSessionRuntimeError,
            object: captureSession
        )
    }
    
    @objc private func sessionRuntimeError(notification: Notification) {
        guard let error = notification.userInfo?[AVCaptureSessionErrorKey] as? AVError else { return }
        
        logger.error("Capture session runtime error: \(error.localizedDescription)")
        
        DispatchQueue.main.async {
            self.sessionError = error
        }
        
        // Attempt to restart if media services were reset
        if error.code == .mediaServicesWereReset {
            cameraQueue.async { [weak self] in
                self?.captureSession.startRunning()
            }
        }
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate

extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {
    
    func captureOutput(_ output: AVCaptureOutput,
                      didOutput sampleBuffer: CMSampleBuffer,
                      from connection: AVCaptureConnection) {
        onSampleBuffer?(sampleBuffer)
    }
    
    func captureOutput(_ output: AVCaptureOutput,
                      didDrop sampleBuffer: CMSampleBuffer,
                      from connection: AVCaptureConnection) {
        logger.debug("Frame dropped")
    }
}
