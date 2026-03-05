import AVFoundation
import Photos
import os.log

final class ClipRecorder: ObservableObject {
    
    // MARK: - Types
    
    enum RecordingError: LocalizedError {
        case noSamples
        case assetWriterFailed(Error?)
        case photoLibraryDenied
        case exportFailed(String)
        
        var errorDescription: String? {
            switch self {
            case .noSamples:
                return "No video samples available to save"
            case .assetWriterFailed(let error):
                return "Failed to write video: \(error?.localizedDescription ?? "Unknown error")"
            case .photoLibraryDenied:
                return "Photo library access denied"
            case .exportFailed(let message):
                return "Export failed: \(message)"
            }
        }
    }
    
    struct ExportResult {
        let duration: TimeInterval
        let fileSize: Int64
        let savedAt: Date
    }
    
    // MARK: - Published Properties
    
    @Published private(set) var isExporting = false
    @Published private(set) var exportCount = 0
    @Published private(set) var lastError: RecordingError?
    @Published private(set) var lastExportResult: ExportResult?
    
    // MARK: - Private Properties
    
    private let exportQueue = DispatchQueue(label: "com.instantreplay.export", qos: .userInitiated)
    private let logger = Logger.recorder
    private var activeExports = 0
    
    // MARK: - Public Methods
    
    /// Export buffered samples to Photos library
    func exportClip(from samples: [CMSampleBuffer], completion: ((Result<ExportResult, RecordingError>) -> Void)? = nil) {
        
        guard !samples.isEmpty else {
            logger.warning("No samples to export")
            lastError = .noSamples
            completion?(.failure(.noSamples))
            return
        }
        
        incrementExportCount()
        
        exportQueue.async { [weak self] in
            self?.performExport(samples: samples, completion: completion)
        }
    }
    
    // MARK: - Private Methods
    
    private func incrementExportCount() {
        DispatchQueue.main.async {
            self.activeExports += 1
            self.isExporting = true
            self.exportCount += 1
        }
    }
    
    private func decrementExportCount() {
        DispatchQueue.main.async {
            self.activeExports -= 1
            if self.activeExports <= 0 {
                self.activeExports = 0
                self.isExporting = false
            }
        }
    }
    
    private func performExport(samples: [CMSampleBuffer], completion: ((Result<ExportResult, RecordingError>) -> Void)?) {
        
        let startTime = Date()
        logger.info("Starting export of \(samples.count) samples")
        
        // Create temporary file URL
        let tempDir = FileManager.default.temporaryDirectory
        let fileName = "InstantReplay_\(UUID().uuidString).mp4"
        let outputURL = tempDir.appendingPathComponent(fileName)
        
        // Remove existing file if any
        try? FileManager.default.removeItem(at: outputURL)
        
        // Get format description from first sample
        guard let formatDescription = CMSampleBufferGetFormatDescription(samples[0]) else {
            logger.error("Failed to get format description")
            handleExportError(.exportFailed("Invalid sample format"), completion: completion)
            return
        }
        
        let dimensions = CMVideoFormatDescriptionGetDimensions(formatDescription)
        logger.info("Video dimensions: \(dimensions.width)x\(dimensions.height)")
        
        // Create asset writer
        let assetWriter: AVAssetWriter
        do {
            assetWriter = try AVAssetWriter(outputURL: outputURL, fileType: .mp4)
        } catch {
            logger.error("Failed to create asset writer: \(error.localizedDescription)")
            handleExportError(.assetWriterFailed(error), completion: completion)
            return
        }
        
        // Configure video input
        let videoSettings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: dimensions.width,
            AVVideoHeightKey: dimensions.height,
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: 5_000_000, // 5 Mbps
                AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                AVVideoMaxKeyFrameIntervalKey: 30
            ]
        ]
        
        let writerInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        writerInput.expectsMediaDataInRealTime = false
        
        // Create pixel buffer adaptor
        let sourcePixelBufferAttributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: dimensions.width,
            kCVPixelBufferHeightKey as String: dimensions.height
        ]
        
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: writerInput,
            sourcePixelBufferAttributes: sourcePixelBufferAttributes
        )
        
        guard assetWriter.canAdd(writerInput) else {
            logger.error("Cannot add writer input")
            handleExportError(.exportFailed("Cannot configure video encoder"), completion: completion)
            return
        }
        
        assetWriter.add(writerInput)
        
        // Start writing
        guard assetWriter.startWriting() else {
            logger.error("Failed to start writing: \(assetWriter.error?.localizedDescription ?? "Unknown")")
            handleExportError(.assetWriterFailed(assetWriter.error), completion: completion)
            return
        }
        
        // Calculate time offset (normalize to start from zero)
        let firstPTS = CMSampleBufferGetPresentationTimeStamp(samples[0])
        assetWriter.startSession(atSourceTime: .zero)
        
        // Write samples
        var samplesWritten = 0
        for sample in samples {
            // Wait for writer to be ready
            while !writerInput.isReadyForMoreMediaData {
                Thread.sleep(forTimeInterval: 0.01)
            }
            
            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sample) else {
                continue
            }
            
            // Calculate adjusted presentation time
            let originalPTS = CMSampleBufferGetPresentationTimeStamp(sample)
            let adjustedPTS = originalPTS - firstPTS
            
            if adaptor.append(pixelBuffer, withPresentationTime: adjustedPTS) {
                samplesWritten += 1
            } else {
                logger.warning("Failed to append sample at \(CMTimeGetSeconds(adjustedPTS))s")
            }
        }
        
        logger.info("Wrote \(samplesWritten) samples")
        
        // Finish writing
        writerInput.markAsFinished()
        
        let semaphore = DispatchSemaphore(value: 0)
        var finishError: Error?
        
        assetWriter.finishWriting {
            if assetWriter.status == .failed {
                finishError = assetWriter.error
            }
            semaphore.signal()
        }
        
        semaphore.wait()
        
        if let error = finishError {
            logger.error("Failed to finish writing: \(error.localizedDescription)")
            handleExportError(.assetWriterFailed(error), completion: completion)
            return
        }
        
        // Get file info
        var fileSize: Int64 = 0
        if let attributes = try? FileManager.default.attributesOfItem(atPath: outputURL.path),
           let size = attributes[.size] as? Int64 {
            fileSize = size
        }
        
        let lastPTS = CMSampleBufferGetPresentationTimeStamp(samples[samples.count - 1])
        let duration = CMTimeGetSeconds(lastPTS - firstPTS)
        
        logger.info("Video written: \(duration)s, \(fileSize / 1024)KB")
        
        // Save to Photos
        saveToPhotos(url: outputURL, duration: duration, fileSize: fileSize, startTime: startTime, completion: completion)
    }
    
    private func saveToPhotos(url: URL, duration: TimeInterval, fileSize: Int64, startTime: Date, completion: ((Result<ExportResult, RecordingError>) -> Void)?) {
        
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { [weak self] status in
            guard let self = self else { return }
            
            guard status == .authorized || status == .limited else {
                self.logger.error("Photo library access denied: \(String(describing: status))")
                self.handleExportError(.photoLibraryDenied, completion: completion)
                return
            }
            
            PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
            } completionHandler: { [weak self] success, error in
                guard let self = self else { return }
                
                // Clean up temp file
                try? FileManager.default.removeItem(at: url)
                
                if success {
                    let exportTime = Date().timeIntervalSince(startTime)
                    self.logger.info("Saved to Photos in \(String(format: "%.2f", exportTime))s")
                    
                    let result = ExportResult(
                        duration: duration,
                        fileSize: fileSize,
                        savedAt: Date()
                    )
                    
                    DispatchQueue.main.async {
                        self.lastExportResult = result
                        self.lastError = nil
                    }
                    
                    self.decrementExportCount()
                    completion?(.success(result))
                } else {
                    self.logger.error("Failed to save to Photos: \(error?.localizedDescription ?? "Unknown")")
                    self.handleExportError(.assetWriterFailed(error), completion: completion)
                }
            }
        }
    }
    
    private func handleExportError(_ error: RecordingError, completion: ((Result<ExportResult, RecordingError>) -> Void)?) {
        DispatchQueue.main.async {
            self.lastError = error
        }
        decrementExportCount()
        completion?(.failure(error))
    }
}
