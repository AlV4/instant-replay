package com.instantreplay.app.state

import android.content.Context
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instantreplay.app.buffer.LiveBufferManager
import com.instantreplay.app.camera.CameraManager
import com.instantreplay.app.recorder.ClipRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Main ViewModel that manages app state and coordinates between components.
 */
class AppStateManager : ViewModel() {
    
    companion object {
        private const val TAG = "AppStateManager"
        private const val STATS_UPDATE_INTERVAL_MS = 500L
    }
    
    // State flows
    private val _currentMode = MutableStateFlow(AppMode.PREVIEW)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()
    
    private val _cameraPermissionState = MutableStateFlow(CameraPermissionState.UNKNOWN)
    val cameraPermissionState: StateFlow<CameraPermissionState> = _cameraPermissionState.asStateFlow()
    
    private val _bufferStats = MutableStateFlow(BufferStats())
    val bufferStats: StateFlow<BufferStats> = _bufferStats.asStateFlow()
    
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()
    
    private val _exportCount = MutableStateFlow(0)
    val exportCount: StateFlow<Int> = _exportCount.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Managers
    private val bufferManager = LiveBufferManager(bufferDurationUs = 15_000_000L) // 15 seconds
    private var cameraManager: CameraManager? = null
    private var clipRecorder: ClipRecorder? = null
    
    // Jobs
    private var statsJob: Job? = null
    
    /**
     * Initialize managers with context
     */
    fun initialize(context: Context) {
        if (cameraManager == null) {
            cameraManager = CameraManager(context, bufferManager)
            clipRecorder = ClipRecorder(context)
            Log.i(TAG, "Managers initialized")
        }
    }
    
    /**
     * Update camera permission state
     */
    fun updatePermissionState(granted: Boolean) {
        _cameraPermissionState.value = if (granted) {
            CameraPermissionState.GRANTED
        } else {
            CameraPermissionState.DENIED
        }
    }
    
    /**
     * Start camera preview
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        cameraManager?.startCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }
    
    /**
     * Start Live mode - begin buffering
     */
    fun startLive() {
        if (_currentMode.value == AppMode.LIVE) return
        if (_cameraPermissionState.value != CameraPermissionState.GRANTED) {
            Log.w(TAG, "Cannot start Live: camera not authorized")
            return
        }
        
        Log.i(TAG, "Starting Live mode")
        _currentMode.value = AppMode.LIVE
        bufferManager.start()
        startStatsUpdates()
    }
    
    /**
     * Stop Live mode - clear buffer
     */
    fun stopLive() {
        if (_currentMode.value == AppMode.PREVIEW) return
        
        Log.i(TAG, "Stopping Live mode")
        _currentMode.value = AppMode.PREVIEW
        bufferManager.stop()
        stopStatsUpdates()
        _bufferStats.value = BufferStats()
    }
    
    /**
     * Save the current buffer contents
     */
    fun saveMoment() {
        if (_currentMode.value != AppMode.LIVE) {
            Log.w(TAG, "Cannot save: not in Live mode")
            return
        }
        
        Log.i(TAG, "Saving moment")
        val frames = bufferManager.snapshot()
        
        if (frames.isEmpty()) {
            _errorMessage.value = "No frames to save"
            return
        }
        
        _exportState.value = ExportState.Exporting
        _exportCount.value++
        
        viewModelScope.launch {
            val result = clipRecorder?.exportClip(frames)
            
            result?.fold(
                onSuccess = { exportResult ->
                    Log.i(TAG, "Moment saved: ${exportResult.durationSeconds}s")
                    _exportState.value = ExportState.Success(exportResult)
                    
                    // Reset to idle after a delay
                    delay(2000)
                    _exportState.value = ExportState.Idle
                },
                onFailure = { error ->
                    Log.e(TAG, "Save failed: ${error.message}")
                    _exportState.value = ExportState.Error(error.message ?: "Unknown error")
                    
                    // Reset to idle after a delay
                    delay(3000)
                    _exportState.value = ExportState.Idle
                }
            )
        }
    }
    
    /**
     * Start periodic buffer stats updates
     */
    private fun startStatsUpdates() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (isActive) {
                _bufferStats.value = bufferManager.getStats()
                delay(STATS_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop buffer stats updates
     */
    private fun stopStatsUpdates() {
        statsJob?.cancel()
        statsJob = null
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Handle app going to background
     */
    fun onBackground() {
        if (_currentMode.value == AppMode.LIVE) {
            stopLive()
        }
    }
    
    /**
     * Clean up resources
     */
    override fun onCleared() {
        super.onCleared()
        stopLive()
        cameraManager?.shutdown()
        Log.i(TAG, "AppStateManager cleared")
    }
}
