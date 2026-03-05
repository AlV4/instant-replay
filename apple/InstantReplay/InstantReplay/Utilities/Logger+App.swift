import Foundation
import os.log

extension Logger {
    private static let subsystem = Bundle.main.bundleIdentifier ?? "com.instantreplay"
    
    static let app = Logger(subsystem: subsystem, category: "App")
    static let camera = Logger(subsystem: subsystem, category: "Camera")
    static let buffer = Logger(subsystem: subsystem, category: "Buffer")
    static let recorder = Logger(subsystem: subsystem, category: "Recorder")
}
