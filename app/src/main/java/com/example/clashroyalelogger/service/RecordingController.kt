package com.example.clashroyalelogger.service

import android.content.Context
import android.util.Log
import com.example.clashroyalelogger.SimpleDataManager
import com.example.clashroyalelogger.SimpleTouchLogger
import com.example.clashroyalelogger.capture.GetEventCapture
import com.example.clashroyalelogger.util.RootChecker

object RecordingController {
    private const val TAG = "RecordingController"
    private var initialized = false
    @Volatile private var overlayEnabled = true
    @Volatile private var rootAvailable = false
    @Volatile private var captureMode: CaptureMode = CaptureMode.AUTO

    fun initialize(context: Context) {
        if (!initialized) {
            SimpleDataManager.initialize(context.applicationContext)
            initialized = true
            Log.d(TAG, "Initialized with application context")
            // Cache root availability for initial info (we'll re-check dynamically when needed)
            rootAvailable = RootChecker.isRootAvailable()
            Log.d(TAG, "Root available (initial): $rootAvailable")
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        captureMode = mode
        Log.d(TAG, "Capture mode set to $captureMode")
    }

    fun getCaptureMode(): CaptureMode = captureMode

    fun isRootAvailable(): Boolean = RootChecker.isRootAvailable()

    private fun wantsRoot(): Boolean {
        return when (captureMode) {
            CaptureMode.ROOT_PLUS_OVERLAY -> true
            CaptureMode.AUTO -> RootChecker.isRootAvailable()
            else -> false
        }
    }

    private fun wantsOverlay(): Boolean {
        return when (captureMode) {
            CaptureMode.VIDEO_ONLY -> false
            else -> true
        }
    }

    fun forceReset() {
        try {
            SimpleTouchLogger.stopTouchCapture()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping overlay during forceReset", e)
        }
        SimpleDataManager.forceReset()
        Log.d(TAG, "Force reset completed")
    }

    fun reset() {
        try {
            SimpleTouchLogger.stopTouchCapture()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping overlay during reset", e)
        }
        SimpleDataManager.resetState()
        Log.d(TAG, "Reset completed")
    }

    fun startDataLogging(startOverlay: Boolean = true): Boolean {
        if (SimpleDataManager.isRecording) {
            Log.w(TAG, "Data logging already active")
            return true
        }
        val started = SimpleDataManager.startRecording()
        if (!started) {
            Log.e(TAG, "Failed to start data logging")
            return false
        }
        // Attempt to start root-based getevent capture (non-blocking; logs if unavailable)
        if (wantsRoot()) {
            try {
                if (!GetEventCapture.isRunning()) {
                    GetEventCapture.start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "getevent capture not available or failed to start", e)
            }
        } else {
            Log.d(TAG, "Skipping getevent capture based on capture mode or root unavailability")
        }
        if (startOverlay && overlayEnabled && wantsOverlay()) {
            try {
                SimpleTouchLogger.startTouchCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start overlay after data logging", e)
            }
        }
        Log.d(TAG, "Data logging started (overlayRequested=$startOverlay, overlayEnabled=$overlayEnabled, mode=$captureMode)")
        return true
    }

    fun startOverlay() {
        if (!overlayEnabled || !wantsOverlay()) {
            Log.d(TAG, "Overlay not started because overlayEnabled=false")
            return
        }
        try {
            SimpleTouchLogger.startTouchCapture()
            Log.d(TAG, "Overlay started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start overlay", e)
        }
    }

    fun stopOverlay() {
        try {
            SimpleTouchLogger.stopTouchCapture()
            Log.d(TAG, "Overlay stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop overlay", e)
        }
    }

    fun stopDataLogging(stopOverlay: Boolean = true) {
        if (!SimpleDataManager.isRecording) {
            Log.w(TAG, "Data logging not active")
        }
        if (stopOverlay) {
            stopOverlay()
        }
        // Stop root-based getevent capture if running
        try {
            if (GetEventCapture.isRunning()) {
                GetEventCapture.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop getevent capture", e)
        }
        try {
            SimpleDataManager.stopRecording()
            Log.d(TAG, "Data logging stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping data logging", e)
        }
    }

    fun isRecording(): Boolean = SimpleDataManager.isRecording
    fun currentSessionId(): String = SimpleDataManager.getCurrentSessionId()

    fun isOverlayEnabled(): Boolean = overlayEnabled

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        overlayEnabled = enabled
        if (enabled) {
            if (SimpleDataManager.isRecording) startOverlay()
        } else {
            stopOverlay()
        }
        Log.d(TAG, "Overlay enabled set to $overlayEnabled")
    }

    fun toggleOverlay(context: Context): Boolean {
        setOverlayEnabled(context, !overlayEnabled)
        return overlayEnabled
    }

    fun performClick(x: Float, y: Float): Boolean {
        // Try root-based input tap first if available
        if (RootChecker.isRootAvailable()) {
            try {
                val cmd = arrayOf("su", "-c", "input tap ${x.toInt()} ${y.toInt()}")
                ProcessBuilder(*cmd).start()
                Log.d(TAG, "Performed root tap at ($x,$y)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Root tap failed, falling back to accessibility", e)
            }
        }

        // Fallback to AccessibilityService gesture injection
        val ok = SimpleTouchLogger.tapAt(x, y)
        if (ok) {
            Log.d(TAG, "Performed accessibility tap at ($x,$y)")
            return true
        }
        Log.e(TAG, "Failed to perform click at ($x,$y)")
        return false
    }
}
