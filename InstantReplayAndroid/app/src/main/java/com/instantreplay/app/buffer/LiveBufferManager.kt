package com.instantreplay.app.buffer

import android.media.Image
import android.util.Log
import com.instantreplay.app.state.BufferStats
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents a single buffered video frame
 */
data class BufferedFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val timestampUs: Long,
    val format: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BufferedFrame
        return timestampUs == other.timestampUs
    }

    override fun hashCode(): Int = timestampUs.hashCode()
}

/**
 * Thread-safe ring buffer for video frames.
 * Stores the last N seconds of video data for instant replay functionality.
 */
class LiveBufferManager(
    private val bufferDurationUs: Long = 15_000_000L // 15 seconds in microseconds
) {
    companion object {
        private const val TAG = "LiveBufferManager"
    }

    private val frames = ConcurrentLinkedDeque<BufferedFrame>()
    private val isActive = AtomicBoolean(false)
    
    /**
     * Start buffering frames
     */
    fun start() {
        isActive.set(true)
        Log.i(TAG, "Buffer started")
    }
    
    /**
     * Stop buffering and clear all frames
     */
    fun stop() {
        isActive.set(false)
        clear()
        Log.i(TAG, "Buffer stopped")
    }
    
    /**
     * Check if buffer is active
     */
    fun isBuffering(): Boolean = isActive.get()
    
    /**
     * Add a new frame to the buffer
     */
    fun addFrame(image: Image, timestampUs: Long) {
        if (!isActive.get()) return
        
        try {
            // Convert Image to byte array (YUV_420_888 format)
            val frame = imageToBufferedFrame(image, timestampUs)
            frames.addLast(frame)
            
            // Evict old frames
            evictOldFrames(timestampUs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding frame: ${e.message}")
        }
    }
    
    /**
     * Add a frame from raw data (used with ImageReader)
     */
    fun addFrame(
        yPlane: ByteBuffer,
        uPlane: ByteBuffer,
        vPlane: ByteBuffer,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        width: Int,
        height: Int,
        timestampUs: Long,
        format: Int
    ) {
        if (!isActive.get()) return
        
        try {
            // Create a compact NV21 representation for encoding
            val nv21 = yuv420ToNv21(
                yPlane, uPlane, vPlane,
                yRowStride, uvRowStride, uvPixelStride,
                width, height
            )
            
            val frame = BufferedFrame(
                data = nv21,
                width = width,
                height = height,
                timestampUs = timestampUs,
                format = format
            )
            
            frames.addLast(frame)
            evictOldFrames(timestampUs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding frame: ${e.message}")
        }
    }
    
    /**
     * Get a snapshot of all buffered frames for export
     */
    fun snapshot(): List<BufferedFrame> {
        val snapshot = frames.toList()
        Log.i(TAG, "Created snapshot with ${snapshot.size} frames")
        return snapshot
    }
    
    /**
     * Clear all buffered frames
     */
    fun clear() {
        val count = frames.size
        frames.clear()
        Log.i(TAG, "Buffer cleared ($count frames removed)")
    }
    
    /**
     * Get current buffer statistics
     */
    fun getStats(): BufferStats {
        val frameList = frames.toList()
        if (frameList.isEmpty()) {
            return BufferStats()
        }
        
        val oldest = frameList.firstOrNull()?.timestampUs
        val newest = frameList.lastOrNull()?.timestampUs
        
        val durationSeconds = if (oldest != null && newest != null) {
            (newest - oldest) / 1_000_000.0
        } else {
            0.0
        }
        
        return BufferStats(
            sampleCount = frameList.size,
            durationSeconds = durationSeconds,
            oldestTimestampUs = oldest,
            newestTimestampUs = newest
        )
    }
    
    /**
     * Remove frames older than the buffer duration
     */
    private fun evictOldFrames(currentTimestampUs: Long) {
        val cutoffTime = currentTimestampUs - bufferDurationUs
        
        while (frames.isNotEmpty()) {
            val oldest = frames.peekFirst()
            if (oldest != null && oldest.timestampUs < cutoffTime) {
                frames.pollFirst()
            } else {
                break
            }
        }
    }
    
    /**
     * Convert Image to BufferedFrame
     */
    private fun imageToBufferedFrame(image: Image, timestampUs: Long): BufferedFrame {
        val planes = image.planes
        val yPlane = planes[0].buffer
        val uPlane = planes[1].buffer
        val vPlane = planes[2].buffer
        
        val nv21 = yuv420ToNv21(
            yPlane, uPlane, vPlane,
            planes[0].rowStride,
            planes[1].rowStride,
            planes[1].pixelStride,
            image.width,
            image.height
        )
        
        return BufferedFrame(
            data = nv21,
            width = image.width,
            height = image.height,
            timestampUs = timestampUs,
            format = image.format
        )
    }
    
    /**
     * Convert YUV_420_888 to NV21 format for MediaCodec
     */
    private fun yuv420ToNv21(
        yPlane: ByteBuffer,
        uPlane: ByteBuffer,
        vPlane: ByteBuffer,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        width: Int,
        height: Int
    ): ByteArray {
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)
        
        // Copy Y plane
        if (yRowStride == width) {
            yPlane.get(nv21, 0, ySize)
        } else {
            for (row in 0 until height) {
                yPlane.position(row * yRowStride)
                yPlane.get(nv21, row * width, width)
            }
        }
        
        // Copy UV planes (interleaved as VU for NV21)
        val uvHeight = height / 2
        val uvWidth = width / 2
        
        if (uvPixelStride == 2 && uvRowStride == width) {
            // Already interleaved, just need to copy
            vPlane.position(0)
            vPlane.get(nv21, ySize, uvSize)
        } else {
            // Need to interleave manually
            var uvIndex = ySize
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvOffset = row * uvRowStride + col * uvPixelStride
                    nv21[uvIndex++] = vPlane.get(uvOffset)
                    nv21[uvIndex++] = uPlane.get(uvOffset)
                }
            }
        }
        
        // Reset buffer positions
        yPlane.rewind()
        uPlane.rewind()
        vPlane.rewind()
        
        return nv21
    }
}
