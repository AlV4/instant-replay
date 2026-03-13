package com.instantreplay.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.instantreplay.app.buffer.LiveBufferManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages camera operations using CameraX library.
 * Handles camera preview and frame capture for buffering.
 */
class CameraManager(
    private val context: Context,
    private val bufferManager: LiveBufferManager
) {
    companion object {
        private const val TAG = "CameraManager"
        private const val TARGET_RESOLUTION_WIDTH = 1280
        private const val TARGET_RESOLUTION_HEIGHT = 720
    }
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var isRunning = false
    
    /**
     * Initialize and start the camera
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onError: (String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView)
                isRunning = true
                Log.i(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera: ${e.message}", e)
                onError("Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Bind camera use cases (preview + image analysis)
     */
    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProvider = cameraProvider 
            ?: throw IllegalStateException("Camera provider is null")
        
        // Preview use case
        preview = Preview.Builder()
            .setTargetResolution(android.util.Size(TARGET_RESOLUTION_WIDTH, TARGET_RESOLUTION_HEIGHT))
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image analysis for frame capture
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(TARGET_RESOLUTION_WIDTH, TARGET_RESOLUTION_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }
        
        // Select back camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        
        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            Log.i(TAG, "Camera use cases bound successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Process each frame from the camera
     */
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            // Only process if buffering is active
            if (bufferManager.isBuffering()) {
                val image = imageProxy.image
                if (image != null) {
                    val planes = imageProxy.planes
                    
                    bufferManager.addFrame(
                        yPlane = planes[0].buffer,
                        uPlane = planes[1].buffer,
                        vPlane = planes[2].buffer,
                        yRowStride = planes[0].rowStride,
                        uvRowStride = planes[1].rowStride,
                        uvPixelStride = planes[1].pixelStride,
                        width = imageProxy.width,
                        height = imageProxy.height,
                        timestampUs = imageProxy.imageInfo.timestamp / 1000, // Convert ns to us
                        format = ImageFormat.YUV_420_888
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * Stop the camera
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            isRunning = false
            Log.i(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera: ${e.message}")
        }
    }
    
    /**
     * Check if camera is running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        stopCamera()
        cameraExecutor.shutdown()
        Log.i(TAG, "Camera manager shutdown")
    }
}
