package com.example.clashroyalelogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.clashroyalelogger.service.RecordingController

class SimpleRecordingService : Service() {
    
    companion object {
        private const val TAG = "SimpleRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_channel"
        const val ACTION_START = "start_recording"
        const val ACTION_STOP = "stop_recording"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        @Volatile var lowResRecording: Boolean = false
        // Use direct property assignment from callers: SimpleRecordingService.lowResRecording = true/false
    }
    
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Initialize and reset via RecordingController
        RecordingController.initialize(this)
        RecordingController.forceReset()
        
        // Sync service state with data manager
        isRecording = RecordingController.isRecording()
        Log.d(TAG, "Service created, isRecording synced: $isRecording")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received")
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                Log.d(TAG, "resultCode: $resultCode, data: $data")
                if (resultCode == -1 && data != null) { // RESULT_OK is -1 in Android
                    Log.d(TAG, "Starting recording with valid parameters")
                    startRecording(resultCode, data)
                } else {
                    Log.e(TAG, "Invalid parameters for recording: resultCode=$resultCode, data=$data")
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopRecording()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startRecording(resultCode: Int, data: Intent) {
        Log.d(TAG, "startRecording called")
        Log.d(TAG, "Service isRecording state: $isRecording, Controller isRecording: ${RecordingController.isRecording()}")
        
        // Sync service state with data manager
        if (RecordingController.isRecording() && !isRecording) {
            Log.w(TAG, "State mismatch detected, syncing service state")
            isRecording = true
        } else if (!RecordingController.isRecording() && isRecording) {
            Log.w(TAG, "Service state stale, resetting")
            isRecording = false
        }
        
        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring request")
            return
        }
        
        try {
            // Start foreground service FIRST for media projection
            Log.d(TAG, "Starting foreground service")
            startForeground(NOTIFICATION_ID, createNotification())
            
            Log.d(TAG, "Starting data logging")
            // Start data logging (delay overlay until after media starts)
            if (!RecordingController.startDataLogging(startOverlay = false)) {
                Log.e(TAG, "Failed to start data logging")
                stopForeground(true)
                return
            }
            Log.d(TAG, "Data logging started successfully")
            
            // Setup media projection
            Log.d(TAG, "Setting up media projection")
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            Log.d(TAG, "Media projection created: $mediaProjection")
            
            // Register callback for newer Android versions
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    stopRecording()
                }
            }, null)
            
            // Setup media recorder
            val (capWidth, capHeight) = setupMediaRecorder() ?: run {
                Log.e(TAG, "MediaRecorder prepare failed; aborting start")
                // Ensure we unwind any partial state
                try { RecordingController.stopDataLogging(stopOverlay = true) } catch (_: Exception) {}
                stopForeground(true)
                cleanup()
                return
            }
            
            // Create virtual display
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)

            // Use the same width/height as MediaRecorder to avoid scaling/incompatibilities
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecording",
                capWidth,
                capHeight,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )
            
            mediaRecorder?.start()
            isRecording = true
            
            // Ensure overlay is enabled for this session and start it
            com.example.clashroyalelogger.service.RecordingController.setOverlayEnabled(this, true)
            RecordingController.startOverlay()
            
            Log.d(TAG, "Recording started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            // Ensure data logging and overlay stopped via controller
            RecordingController.stopDataLogging(stopOverlay = true)
            stopForeground(true)
            cleanup()
            isRecording = false
            // Stop the service since recording failed
            stopSelf()
        }
    }
    
    private fun stopRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.stop()
            RecordingController.stopDataLogging(stopOverlay = true)
            
            Log.d(TAG, "Recording stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            cleanup()
            isRecording = false
            Log.d(TAG, "Service isRecording set to false: $isRecording")
            stopForeground(true)
            // Stop the service after recording is complete
            stopSelf()
        }
    }
    
    private fun setupMediaRecorder(): Pair<Int, Int>? {
        val sessionId = SimpleDataManager.getCurrentSessionId()
        val videoFile = File(getVideoFolder(), "session_${sessionId}_video.mp4")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaRecorder = MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            mediaRecorder = MediaRecorder()
        }
        
        // Determine capture size (even values) and configure
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(dm)
        var width = dm.widthPixels
        var height = dm.heightPixels
        if (lowResRecording) {
            val portrait = height >= width
            width = if (portrait) 720 else 1280
            height = if (portrait) 1280 else 720
        }
        // Ensure even dimensions for H.264
        if ((width and 1) == 1) width -= 1
        if ((height and 1) == 1) height -= 1

        fun configureRecorder(w: Int, h: Int) {
            mediaRecorder?.reset()
            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                // Bitrate scaled to resolution (~4 bpp)
                val bitrate = (w * h * 4).coerceAtMost(12_000_000)
                setVideoEncodingBitRate(bitrate)
                setVideoFrameRate(if (lowResRecording) 24 else 30)
                setVideoSize(w, h)
                // Optional: orientation hint makes playback consistent
                try {
                    @Suppress("DEPRECATION")
                    val rotation = wm.defaultDisplay.rotation
                    val hint = when (rotation) {
                        android.view.Surface.ROTATION_90 -> 90
                        android.view.Surface.ROTATION_180 -> 180
                        android.view.Surface.ROTATION_270 -> 270
                        else -> 0
                    }
                    setOrientationHint(hint)
                } catch (_: Exception) {}
                setOutputFile(videoFile.absolutePath)
                prepare()
            }
        }

        return try {
            configureRecorder(width, height)
            Log.d(TAG, "MediaRecorder prepared at ${width}x${height}")
            Pair(width, height)
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder prepare failed at ${width}x${height} -> attempting 1280x720 fallback", e)
            // Fallback to a common supported size (720p) with same orientation
            val portrait = height >= width
            val fw = if (portrait) 720 else 1280
            val fh = if (portrait) 1280 else 720
            return try {
                configureRecorder(fw, fh)
                Log.d(TAG, "MediaRecorder prepared at fallback ${fw}x${fh}")
                Pair(fw, fh)
            } catch (e2: Exception) {
                Log.e(TAG, "MediaRecorder prepare failed at fallback size as well", e2)
                null
            }
        }
    }
    
    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        
        mediaRecorder?.release()
        mediaRecorder = null
        
        mediaProjection?.stop()
        mediaProjection = null
    }
    
    private fun getVideoFolder(): File {
        // Use app-scoped external storage to comply with scoped storage on Android 10+
        val base = getExternalFilesDir(null) ?: filesDir
        val folder = File(base, "ClashRoyaleLogger")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen and touch recording service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Touch Logger")
            .setContentText("Recording screen and touch data...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service being destroyed")
        stopRecording()
        // Reset state to ensure clean startup next time
        RecordingController.reset()
    }
}
