package com.example.clashroyalelogger

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SessionData(
    val sessionId: String,
    val timestamp: Long,
    val csvFile: File?,
    val metadataFile: File?,
    val videoFile: File?,
    val touchEventCount: Int = 0,
    val duration: Long = 0 // in milliseconds
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    
    val formattedDuration: String
        get() = if (duration > 0) {
            val seconds = duration / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            when {
                hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
                minutes > 0 -> String.format("%02d:%02d", minutes, seconds % 60)
                else -> "${seconds}s"
            }
        } else "Unknown"
    
    val sizeInMB: String
        get() {
            var totalSize = 0L
            csvFile?.let { if (it.exists()) totalSize += it.length() }
            metadataFile?.let { if (it.exists()) totalSize += it.length() }
            videoFile?.let { if (it.exists()) totalSize += it.length() }
            return String.format("%.2f MB", totalSize / (1024.0 * 1024.0))
        }
    
    val hasAllFiles: Boolean
        get() = csvFile?.exists() == true && metadataFile?.exists() == true && videoFile?.exists() == true
    
    val isComplete: Boolean
        get() = hasAllFiles && touchEventCount > 0 && duration > 0
}