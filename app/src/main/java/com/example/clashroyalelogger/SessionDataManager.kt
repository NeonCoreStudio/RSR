package com.example.clashroyalelogger

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SessionDataManager {
    private const val TAG = "SessionDataManager"
    private const val CLASH_ROYALE_LOGGER_DIR = "ClashRoyaleLogger"
    
    fun getStorageDirectory(): File {
        // Prefer app-scoped external storage when available
        val ctx = SimpleDataManager.appContext()
        val base = ctx?.getExternalFilesDir(null) ?: ctx?.filesDir
        return if (base != null) {
            File(base, CLASH_ROYALE_LOGGER_DIR)
        } else {
            // Fallback to legacy public Downloads path
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(docsDir, CLASH_ROYALE_LOGGER_DIR)
        }
    }
    
    fun getAllSessions(): List<SessionData> {
        val sessions = mutableListOf<SessionData>()
        val storageDir = getStorageDirectory()
        
        if (!storageDir.exists()) {
            Log.d(TAG, "Storage directory does not exist")
            return sessions
        }
        
        val sessionMap = mutableMapOf<String, MutableMap<String, File>>()
        
        // Group files by session timestamp
        storageDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val fileName = file.name
                when {
                    fileName.startsWith("session_") && fileName.endsWith("_touches.csv") -> {
                        val sessionId = extractSessionId(fileName, "_touches.csv")
                        sessionMap.getOrPut(sessionId) { mutableMapOf() }["csv"] = file
                    }
                    fileName.startsWith("session_") && fileName.endsWith("_metadata.json") -> {
                        val sessionId = extractSessionId(fileName, "_metadata.json")
                        sessionMap.getOrPut(sessionId) { mutableMapOf() }["metadata"] = file
                    }
                    fileName.startsWith("session_") && fileName.endsWith("_video.mp4") -> {
                        val sessionId = extractSessionId(fileName, "_video.mp4")
                        sessionMap.getOrPut(sessionId) { mutableMapOf() }["video"] = file
                    }
                }
            }
        }
        
        // Create SessionData objects
        sessionMap.forEach { (sessionId, files) ->
            try {
                val timestamp = sessionId.toLongOrNull() ?: 0L
                val csvFile = files["csv"]
                val metadataFile = files["metadata"]
                val videoFile = files["video"]
                
                var touchEventCount = 0
                var duration = 0L
                
                // Read metadata for additional info
                metadataFile?.let { file ->
                    try {
                        val jsonContent = file.readText()
                        val jsonObject = JSONObject(jsonContent)
                        
                        // Try both field names for duration
                        if (jsonObject.has("duration")) {
                            duration = jsonObject.getLong("duration")
                        } else if (jsonObject.has("total_duration_ms")) {
                            duration = jsonObject.getLong("total_duration_ms")
                        }
                        
                        // Try to get touch event count from metadata
                        if (jsonObject.has("touch_events_count")) {
                            touchEventCount = jsonObject.getInt("touch_events_count")
                        } else if (jsonObject.has("events")) {
                            val eventsArray = jsonObject.getJSONArray("events")
                            touchEventCount = eventsArray.length()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading metadata for session $sessionId", e)
                    }
                }
                
                // Count CSV lines if metadata not available or count is 0
                if (touchEventCount == 0 && csvFile?.exists() == true) {
                    try {
                        val lines = csvFile.readLines()
                        // Count non-empty lines excluding header
                        touchEventCount = lines.drop(1).count { it.trim().isNotEmpty() }
                        Log.d(TAG, "Counted $touchEventCount touch events from CSV for session $sessionId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error counting CSV lines for session $sessionId", e)
                        touchEventCount = 0
                    }
                }
                
                sessions.add(
                    SessionData(
                        sessionId = sessionId,
                        timestamp = timestamp,
                        csvFile = csvFile,
                        metadataFile = metadataFile,
                        videoFile = videoFile,
                        touchEventCount = touchEventCount,
                        duration = duration
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating session data for $sessionId", e)
            }
        }
        
        return sessions.sortedByDescending { it.timestamp }
    }
    
    private fun extractSessionId(fileName: String, suffix: String): String {
        return fileName.removePrefix("session_").removeSuffix(suffix)
    }
    
    fun exportSessionAsZip(context: Context, session: SessionData, outputFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
                // Add CSV file
                session.csvFile?.let { file ->
                    if (file.exists()) {
                        addFileToZip(zipOut, file, "touches.csv")
                    }
                }
                
                // Add metadata file
                session.metadataFile?.let { file ->
                    if (file.exists()) {
                        addFileToZip(zipOut, file, "metadata.json")
                    }
                }
                
                // Add video file
                session.videoFile?.let { file ->
                    if (file.exists()) {
                        addFileToZip(zipOut, file, "video.mp4")
                    }
                }
                
                // Add session info file
                val sessionInfo = createSessionInfoText(session)
                val sessionInfoEntry = ZipEntry("session_info.txt")
                zipOut.putNextEntry(sessionInfoEntry)
                zipOut.write(sessionInfo.toByteArray())
                zipOut.closeEntry()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting session as ZIP", e)
            false
        }
    }
    
    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
            
            val buffer = ByteArray(1024)
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                zipOut.write(buffer, 0, length)
            }
            zipOut.closeEntry()
        }
    }
    
    private fun createSessionInfoText(session: SessionData): String {
        return buildString {
            appendLine("Session Information")
            appendLine("==================")
            appendLine("Session ID: ${session.sessionId}")
            appendLine("Date: ${session.formattedDate}")
            appendLine("Duration: ${session.formattedDuration}")
            appendLine("Touch Events: ${session.touchEventCount}")
            appendLine("Total Size: ${session.sizeInMB}")
            appendLine("Complete: ${if (session.isComplete) "Yes" else "No"}")
            appendLine()
            appendLine("Files Included:")
            appendLine("- touches.csv: ${if (session.csvFile?.exists() == true) "✓" else "✗"}")
            appendLine("- metadata.json: ${if (session.metadataFile?.exists() == true) "✓" else "✗"}")
            appendLine("- video.mp4: ${if (session.videoFile?.exists() == true) "✓" else "✗"}")
        }
    }
    
    fun exportAllSessions(context: Context, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val sessions = getAllSessions().filter { it.isComplete }
                
                if (sessions.isEmpty()) {
                    callback(false, "No complete sessions to export")
                    return@Thread
                }
                
                val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TouchLogger_Export")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                val timestamp = System.currentTimeMillis()
                val zipFile = File(exportDir, "sessions_export_$timestamp.zip")
                
                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    sessions.forEachIndexed { index, session ->
                        val sessionPrefix = "session_${index + 1}_${session.sessionId}"
                        
                        // Add CSV file
                        session.csvFile?.let { file ->
                            if (file.exists()) {
                                addFileToZip(zipOut, file, "$sessionPrefix/touches.csv")
                            }
                        }
                        
                        // Add metadata file
                        session.metadataFile?.let { file ->
                            if (file.exists()) {
                                addFileToZip(zipOut, file, "$sessionPrefix/metadata.json")
                            }
                        }
                        
                        // Add video file
                        session.videoFile?.let { file ->
                            if (file.exists()) {
                                addFileToZip(zipOut, file, "$sessionPrefix/video.mp4")
                            }
                        }
                        
                        // Add session info
                        val sessionInfo = createSessionInfoText(session)
                        val sessionInfoEntry = ZipEntry("$sessionPrefix/session_info.txt")
                        zipOut.putNextEntry(sessionInfoEntry)
                        zipOut.write(sessionInfo.toByteArray())
                        zipOut.closeEntry()
                    }
                }
                
                callback(true, "Exported ${sessions.size} sessions to ${zipFile.name}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Export all sessions failed", e)
                callback(false, "Export failed: ${e.message}")
            }
        }.start()
    }
    
    fun exportSessionAsZip(context: Context, session: SessionData, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TouchLogger_Export")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                val zipFile = File(exportDir, "session_${session.sessionId}.zip")
                
                if (exportSessionAsZip(context, session, zipFile)) {
                    callback(true, "Session exported to ${zipFile.name}")
                } else {
                    callback(false, "Failed to export session")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Export session failed", e)
                callback(false, "Export failed: ${e.message}")
            }
        }.start()
    }

    fun deleteSession(session: SessionData): Boolean {
        var success = true
        
        session.csvFile?.let { file ->
            if (file.exists() && !file.delete()) {
                success = false
                Log.e(TAG, "Failed to delete CSV file: ${file.absolutePath}")
            }
        }
        
        session.metadataFile?.let { file ->
            if (file.exists() && !file.delete()) {
                success = false
                Log.e(TAG, "Failed to delete metadata file: ${file.absolutePath}")
            }
        }
        
        session.videoFile?.let { file ->
            if (file.exists() && !file.delete()) {
                success = false
                Log.e(TAG, "Failed to delete video file: ${file.absolutePath}")
            }
        }
        
        return success
    }

    /**
     * Delete all recorded sessions and related files under the app's storage directory.
     * Also attempts to remove the export directory if present.
     */
    fun deleteAllData(): Boolean {
        var success = true
        try {
            // Delete the app-scoped storage directory recursively
            val storage = getStorageDirectory()
            if (storage.exists()) {
                success = deleteRecursive(storage) && success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete storage directory", e)
            success = false
        }

        // Best-effort: remove export directory in public Downloads (may fail on newer Android)
        try {
            val exportDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "TouchLogger_Export")
            if (exportDir.exists()) {
                success = deleteRecursive(exportDir) && success
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete export directory (may be restricted by scoped storage)", e)
        }
        return success
    }

    private fun deleteRecursive(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    deleteRecursive(child)
                }
            }
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete ${file.absolutePath}", e)
            false
        }
    }
}
