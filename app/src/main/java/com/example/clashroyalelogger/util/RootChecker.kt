package com.example.clashroyalelogger.util

import android.util.Log
import java.io.File

object RootChecker {
    private const val TAG = "RootChecker"
    @Volatile private var cached: Boolean? = null

    fun isRootAvailable(): Boolean {
        cached?.let { return it }
        val detected = detectRoot()
        cached = detected
        return detected
    }

    fun refresh() {
        cached = detectRoot()
    }

    private fun detectRoot(): Boolean {
        // Try executing a simple su command
        try {
            val process = ProcessBuilder("su", "-c", "echo rooted").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "Root shell available (su succeeded)")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "su execution failed: ${e.message}")
        }

        // Check common su paths
        val candidates = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su"
        )
        if (candidates.any { File(it).exists() }) {
            Log.d(TAG, "su binary found in common paths")
            return true
        }

        Log.d(TAG, "Root not detected")
        return false
    }
}