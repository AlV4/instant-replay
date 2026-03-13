package com.instantreplay.app.state

/**
 * Represents the current mode of the application
 */
enum class AppMode {
    PREVIEW,
    LIVE
}

/**
 * Represents the camera permission state
 */
enum class CameraPermissionState {
    UNKNOWN,
    GRANTED,
    DENIED
}

/**
 * Represents buffer statistics
 */
data class BufferStats(
    val sampleCount: Int = 0,
    val durationSeconds: Double = 0.0,
    val oldestTimestampUs: Long? = null,
    val newestTimestampUs: Long? = null
)

/**
 * Represents the result of an export operation
 */
data class ExportResult(
    val durationSeconds: Double,
    val fileSizeBytes: Long,
    val savedAt: Long
)

/**
 * Sealed class for export state
 */
sealed class ExportState {
    object Idle : ExportState()
    object Exporting : ExportState()
    data class Success(val result: ExportResult) : ExportState()
    data class Error(val message: String) : ExportState()
}
