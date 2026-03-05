import AVFoundation
import os.log

/// Thread-safe ring buffer for video samples
final class LiveBufferManager {
    
    // MARK: - Types
    
    struct BufferedSample {
        let sampleBuffer: CMSampleBuffer
        let presentationTime: CMTime
    }
    
    struct BufferStats {
        let sampleCount: Int
        let durationSeconds: Double
        let oldestPTS: CMTime?
        let newestPTS: CMTime?
    }
    
    // MARK: - Properties
    
    private var samples: [BufferedSample] = []
    private let bufferQueue = DispatchQueue(label: "com.instantreplay.buffer", qos: .userInitiated)
    private let bufferDuration: CMTime
    private let logger = Logger.buffer
    
    // MARK: - Initialization
    
    init(bufferDurationSeconds: Double = 15.0) {
        self.bufferDuration = CMTime(seconds: bufferDurationSeconds, preferredTimescale: 600)
        samples.reserveCapacity(500) // Pre-allocate for ~15s at 30fps
        logger.info("Buffer initialized with \(bufferDurationSeconds)s duration")
    }
    
    // MARK: - Public Methods
    
    /// Add a new sample to the buffer, evicting old samples as needed
    func addSample(_ sampleBuffer: CMSampleBuffer) {
        let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        
        guard pts.isValid else {
            logger.warning("Invalid PTS, skipping sample")
            return
        }
        
        bufferQueue.async { [weak self] in
            guard let self = self else { return }
            
            // Create retained copy
            let sample = BufferedSample(sampleBuffer: sampleBuffer, presentationTime: pts)
            self.samples.append(sample)
            
            // Evict old samples
            self.evictOldSamples(currentPTS: pts)
        }
    }
    
    /// Get a thread-safe snapshot of the current buffer for export
    func snapshot() -> [CMSampleBuffer] {
        bufferQueue.sync {
            logger.info("Creating snapshot with \(self.samples.count) samples")
            return samples.map { $0.sampleBuffer }
        }
    }
    
    /// Clear all buffered samples
    func clear() {
        bufferQueue.async { [weak self] in
            guard let self = self else { return }
            let count = self.samples.count
            self.samples.removeAll(keepingCapacity: true)
            self.logger.info("Buffer cleared (\(count) samples removed)")
        }
    }
    
    /// Get current buffer statistics
    func getStats() -> BufferStats {
        bufferQueue.sync {
            let oldest = samples.first?.presentationTime
            let newest = samples.last?.presentationTime
            
            var duration: Double = 0
            if let o = oldest, let n = newest, o.isValid && n.isValid {
                duration = CMTimeGetSeconds(n - o)
            }
            
            return BufferStats(
                sampleCount: samples.count,
                durationSeconds: duration,
                oldestPTS: oldest,
                newestPTS: newest
            )
        }
    }
    
    // MARK: - Private Methods
    
    private func evictOldSamples(currentPTS: CMTime) {
        let cutoffTime = currentPTS - bufferDuration
        
        // Find first sample that is newer than cutoff
        let firstValidIndex = samples.firstIndex { sample in
            CMTimeCompare(sample.presentationTime, cutoffTime) >= 0
        }
        
        if let index = firstValidIndex, index > 0 {
            samples.removeFirst(index)
        }
    }
}
