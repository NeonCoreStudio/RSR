package com.example.clashroyalelogger

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.clashroyalelogger.util.CoordinateStabilizer
import com.example.clashroyalelogger.util.ScreenMetrics
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.*

object SimpleDataManager {
    private const val TAG = "SimpleDataManager"
    private const val FOLDER_NAME = "ClashRoyaleLogger"
    
    // Broadcast constants
    const val ACTION_RECORDING_STATE_CHANGED = "com.example.clashroyalelogger.RECORDING_STATE_CHANGED"
    const val EXTRA_IS_RECORDING = "is_recording"
    const val EXTRA_SESSION_ID = "session_id"
    
    private var context: Context? = null
    
    var isRecording = false
        private set
    
    private var currentSessionId: String = ""
    private var touchWriter: FileWriter? = null
    private var bufferedWriter: BufferedWriter? = null
    private var writerThread: Thread? = null
    @Volatile private var writerRunning: Boolean = false
    private var eventQueue: LinkedBlockingQueue<String>? = null
    private var sessionStartTime: Long = 0L
    private var screenMetrics: ScreenMetrics? = null
    @Volatile private var optimizedLogging: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Lightweight in-process touch log listeners (for calibration/demo)
    interface TouchLogListener {
        fun onTouchLogged(timestamp: Long, eventType: String, x: Float, y: Float, pressure: Float, size: Float)
    }
    private val touchLogListeners: MutableList<TouchLogListener> = java.util.concurrent.CopyOnWriteArrayList()
    fun addTouchLogListener(listener: TouchLogListener) { touchLogListeners.add(listener) }
    fun removeTouchLogListener(listener: TouchLogListener) { touchLogListeners.remove(listener) }
    
    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    // Expose application context for components that need storage paths
    fun appContext(): Context? = context

    fun setOptimizedLogging(enabled: Boolean) {
        optimizedLogging = enabled
    }
    
    fun getCurrentSessionId(): String = currentSessionId
    
    fun resetState() {
        Log.d(TAG, "Resetting recording state")
        isRecording = false
        currentSessionId = ""
        touchWriter?.close()
        touchWriter = null
        sessionStartTime = 0L
        broadcastStateChange()
    }
    
    fun forceReset() {
        Log.w(TAG, "Force resetting all recording state")
        try {
            touchWriter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing touch writer during force reset", e)
        }
        isRecording = false
        currentSessionId = ""
        touchWriter = null
        sessionStartTime = 0L
        broadcastStateChange()
    }
    
    private fun broadcastStateChange() {
        context?.let { ctx ->
            val intent = Intent(ACTION_RECORDING_STATE_CHANGED).apply {
                putExtra(EXTRA_IS_RECORDING, isRecording)
                putExtra(EXTRA_SESSION_ID, currentSessionId)
            }
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
            Log.d(TAG, "Broadcasting state change: isRecording=$isRecording, sessionId=$currentSessionId")
        }
    }
    
    fun startRecording(): Boolean {
        if (isRecording) return false
        
        try {
            currentSessionId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            sessionStartTime = System.currentTimeMillis()
            screenMetrics = CoordinateStabilizer.captureMetrics(context)
            
            val folder = getDataFolder()
            if (!folder.exists()) {
                folder.mkdirs()
            }
            
            val touchFile = File(folder, "session_${currentSessionId}_touches.csv")
            if (optimizedLogging) {
                bufferedWriter = BufferedWriter(FileWriter(touchFile), 64 * 1024)
                bufferedWriter?.write("timestamp,eventType,x,y,pressure,size\n")
                bufferedWriter?.newLine()
                eventQueue = LinkedBlockingQueue()
                writerRunning = true
                writerThread = Thread {
                    try {
                        var counter = 0
                        while (writerRunning || (eventQueue?.isEmpty() == false)) {
                            val line = eventQueue?.poll(200, TimeUnit.MILLISECONDS)
                            if (line != null) {
                                bufferedWriter?.write(line)
                                bufferedWriter?.newLine()
                                counter++
                                if (counter % 50 == 0) {
                                    bufferedWriter?.flush()
                                }
                            }
                        }
                        bufferedWriter?.flush()
                    } catch (e: Exception) {
                        Log.e(TAG, "Writer thread error", e)
                    }
                }.apply { name = "touch-writer"; start() }
            } else {
                touchWriter = FileWriter(touchFile)
                touchWriter?.write("timestamp,eventType,x,y,pressure,size\n")
            }
            
            isRecording = true
            broadcastStateChange()
            Log.d(TAG, "Recording started: $currentSessionId")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopRecording()
            return false
        }
    }
    
    fun stopRecording() {
        if (!isRecording) return
        
        try {
            try {
                writerRunning = false
                writerThread?.join(1000)
            } catch (_: Exception) {}
            try {
                bufferedWriter?.close()
            } catch (_: Exception) {}
            bufferedWriter = null
            writerThread = null
            eventQueue = null
            try {
                touchWriter?.close()
            } catch (_: Exception) {}
            touchWriter = null
            
            // Create metadata file
            createMetadataFile()
            
            Log.d(TAG, "Recording stopped: $currentSessionId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            isRecording = false
            currentSessionId = ""
            broadcastStateChange()
        }
    }
    
    private fun createMetadataFile() {
        try {
            val folder = getDataFolder()
            val metadataFile = File(folder, "session_${currentSessionId}_metadata.json")
            val duration = System.currentTimeMillis() - sessionStartTime
            
            // Count touch events from CSV file
            val csvFile = File(folder, "session_${currentSessionId}_touches.csv")
            var touchEventCount = 0
            if (csvFile.exists()) {
                try {
                    val lines = csvFile.readLines()
                    touchEventCount = lines.drop(1).count { it.trim().isNotEmpty() }
                } catch (e: Exception) {
                    Log.e(TAG, "Error counting touch events for metadata", e)
                }
            }
            
            val sm = screenMetrics
            val metadata = """
                {
                    "sessionId": "$currentSessionId",
                    "startTime": $sessionStartTime,
                    "duration": $duration,
                    "touch_events_count": $touchEventCount,
                    "timestamp": "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sessionStartTime))}",
                    "screen_metrics": {
                        "width": ${sm?.width ?: -1},
                        "height": ${sm?.height ?: -1},
                        "density_dpi": ${sm?.densityDpi ?: -1},
                        "rotation": ${sm?.rotation ?: 0}
                    }
                }
            """.trimIndent()
            
            metadataFile.writeText(metadata)
            Log.d(TAG, "Metadata file created: ${metadataFile.name} with $touchEventCount touch events")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating metadata file", e)
        }
    }
    
    fun logTouchEvent(x: Float, y: Float, action: String, pressure: Float = 1.0f, size: Float = 1.0f) {
        if (!isRecording || touchWriter == null) return
        
        try {
            val timestamp = System.currentTimeMillis() - sessionStartTime
            val sm = screenMetrics ?: CoordinateStabilizer.captureMetrics(context)
            var (sx, sy) = CoordinateStabilizer.clampAndRound(x, y, sm)

            // Calibration correction disabled: measure-only mode retained in CalibrationActivity.

            if (optimizedLogging) {
                eventQueue?.offer("$timestamp,$action,$sx,$sy,$pressure,$size")
            } else {
                touchWriter?.write("$timestamp,$action,$sx,$sy,$pressure,$size\n")
                touchWriter?.flush()
            }

            // Notify listeners (on main thread)
            if (touchLogListeners.isNotEmpty()) {
                val lx = sx; val ly = sy
                mainHandler.post {
                    for (l in touchLogListeners) {
                        try { l.onTouchLogged(timestamp, action, lx, ly, pressure, size) } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging touch event", e)
        }
    }
    
    fun getSessionCount(): Int {
        val folder = getDataFolder()
        if (!folder.exists()) return 0
        
        return folder.listFiles { _, name -> 
            name.endsWith("_touches.csv") 
        }?.size ?: 0
    }
    
    fun exportAllSessions(context: Context, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val folder = getDataFolder()
                if (!folder.exists() || folder.listFiles()?.isEmpty() == true) {
                    callback(false, "No data to export")
                    return@Thread
                }
                
                val exportFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TouchLogger_Export")
                if (!exportFolder.exists()) {
                    exportFolder.mkdirs()
                }
                
                var exportedCount = 0
                folder.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val exportFile = File(exportFolder, file.name)
                        file.copyTo(exportFile, overwrite = true)
                        exportedCount++
                    }
                }
                
                callback(true, "Exported $exportedCount files to Downloads/TouchLogger_Export/")
                
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                callback(false, "Export failed: ${e.message}")
            }
        }.start()
    }
    
    private fun getDataFolder(): File {
        // Prefer app-scoped external storage to comply with scoped storage on Android 10+
        val base = context?.getExternalFilesDir(null) ?: context?.filesDir
        return if (base != null) {
            File(base, FOLDER_NAME).apply { if (!exists()) mkdirs() }
        } else {
            // Fallback (legacy devices)
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME).apply { if (!exists()) mkdirs() }
        }
    }
}
