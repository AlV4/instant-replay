package com.instantreplay.app.recorder

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.instantreplay.app.buffer.BufferedFrame
import com.instantreplay.app.state.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Handles encoding buffered frames to MP4 and saving to device gallery.
 */
class ClipRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "ClipRecorder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val BIT_RATE = 5_000_000 // 5 Mbps
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1 // 1 second between I-frames
    }
    
    /**
     * Export buffered frames to MP4 and save to gallery
     */
    suspend fun exportClip(frames: List<BufferedFrame>): Result<ExportResult> = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) {
            return@withContext Result.failure(Exception("No frames to export"))
        }
        
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Starting export of ${frames.size} frames")
        
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var tempFile: File? = null
        
        try {
            // Get dimensions from first frame
            val width = frames.first().width
            val height = frames.first().height
            
            Log.i(TAG, "Video dimensions: ${width}x${height}")
            
            // Create temp file for output
            tempFile = File(context.cacheDir, "InstantReplay_${System.currentTimeMillis()}.mp4")
            
            // Configure encoder
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }
            
            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            
            // Create muxer
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            var videoTrackIndex = -1
            var muxerStarted = false
            var inputDone = false
            var outputDone = false
            var frameIndex = 0
            
            // Get first frame timestamp to normalize
            val firstTimestampUs = frames.first().timestampUs
            
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            
            while (!outputDone) {
                // Feed input frames
                if (!inputDone) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        
                        if (frameIndex < frames.size) {
                            val frame = frames[frameIndex]
                            inputBuffer?.clear()
                            inputBuffer?.put(convertNv21ToYuv420(frame.data, frame.width, frame.height))
                            
                            val presentationTimeUs = frame.timestampUs - firstTimestampUs
                            encoder.queueInputBuffer(inputBufferIndex, 0, frame.data.size, presentationTimeUs, 0)
                            frameIndex++
                        } else {
                            // Signal end of stream
                            encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }
                
                // Get output
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = encoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                        Log.i(TAG, "Muxer started with format: $newFormat")
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        
                        if (outputBuffer != null && muxerStarted && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                        }
                        
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            
            // Clean up encoder and muxer
            encoder.stop()
            encoder.release()
            encoder = null
            
            muxer.stop()
            muxer.release()
            muxer = null
            
            // Save to gallery
            val fileSize = tempFile.length()
            saveToGallery(tempFile)
            
            // Calculate duration
            val lastTimestamp = frames.last().timestampUs
            val durationSeconds = (lastTimestamp - firstTimestampUs) / 1_000_000.0
            
            val exportTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Export completed in ${exportTime}ms: ${durationSeconds}s, ${fileSize / 1024}KB")
            
            // Delete temp file
            tempFile.delete()
            
            Result.success(
                ExportResult(
                    durationSeconds = durationSeconds,
                    fileSizeBytes = fileSize,
                    savedAt = System.currentTimeMillis()
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            encoder?.release()
            muxer?.release()
            tempFile?.delete()
            Result.failure(e)
        }
    }
    
    /**
     * Convert NV21 to YUV420 planar format for encoder
     */
    private fun convertNv21ToYuv420(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4
        val yuv420 = ByteArray(ySize + uvSize * 2)
        
        // Copy Y plane
        System.arraycopy(nv21, 0, yuv420, 0, ySize)
        
        // Deinterleave UV (NV21 is VUVU, we need U plane then V plane)
        var uIndex = ySize
        var vIndex = ySize + uvSize
        var nvIndex = ySize
        
        for (i in 0 until uvSize) {
            yuv420[vIndex++] = nv21[nvIndex++] // V
            yuv420[uIndex++] = nv21[nvIndex++] // U
        }
        
        return yuv420
    }
    
    /**
     * Save video file to device gallery using MediaStore
     */
    private fun saveToGallery(videoFile: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "InstantReplay_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/InstantReplay")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        uri?.let { videoUri ->
            resolver.openOutputStream(videoUri)?.use { outputStream ->
                videoFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, contentValues, null, null)
            }
            
            Log.i(TAG, "Video saved to gallery: $videoUri")
        } ?: throw Exception("Failed to create MediaStore entry")
    }
}
