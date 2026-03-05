import Foundation

enum AppMode: String {
    case preview = "Preview"
    case live = "Live"
}

enum CameraStatus: String {
    case unknown = "Unknown"
    case authorized = "Authorized"
    case denied = "Denied"
    case restricted = "Restricted"
    
    var displayName: String {
        rawValue
    }
}

enum SessionStatus: String {
    case stopped = "Stopped"
    case running = "Running"
    case interrupted = "Interrupted"
}
