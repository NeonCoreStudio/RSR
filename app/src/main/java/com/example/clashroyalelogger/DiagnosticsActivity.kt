package com.example.clashroyalelogger

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.clashroyalelogger.service.RecordingController
import com.example.clashroyalelogger.util.RootChecker

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var tvAccessibility: TextView
    private lateinit var tvOverlay: TextView
    private lateinit var tvRoot: TextView
    private lateinit var tvRecording: TextView
    private lateinit var tvOverlayActive: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "Diagnostics"

        val container = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        container.addView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        tvAccessibility = TextView(this)
        tvOverlay = TextView(this)
        tvRoot = TextView(this)
        tvRecording = TextView(this)
        tvOverlayActive = TextView(this)

        val btnOpenAccessibility = Button(this).apply { text = "Open Accessibility Settings" }
        val btnRequestOverlay = Button(this).apply { text = "Request Overlay Permission" }
        val btnToggleOverlay = Button(this).apply { text = "Toggle Overlay" }
        val btnStartStopRecording = Button(this).apply { text = "Start/Stop Recording" }
        val btnTestTap = Button(this).apply { text = "Fire Test Tap (Center)" }
        val btnCheckRoot = Button(this).apply { text = "Recheck Root" }
        val btnOpenCalibration = Button(this).apply { text = "Open Calibration" }

        // Reset section
        val tvResetHeader = TextView(this).apply { text = "\nReset"; textSize = 18f }
        val tvResetInfo = TextView(this).apply { text = "Soft Reset clears in-memory state. Full Reset clears all data files and preferences." }
        val btnSoftReset = Button(this).apply { text = "Soft Reset (state only)" }
        val btnFullReset = Button(this).apply { text = "Full Reset (delete all data)" }

        // Optimizations section
        val tvOptHeader = TextView(this).apply { text = "\nOptimizations"; textSize = 18f }
        val swOptimized = android.widget.Switch(this).apply { text = "Optimized logging + overlay" }
        val swPassThrough = android.widget.Switch(this).apply { text = "Pass-through logging (no relay)" }
        val swLowRes = android.widget.Switch(this).apply { text = "Low-res recording (720p/24fps)" }
        val swBubble = android.widget.Switch(this).apply { text = "Floating record bubble" }

        layout.addView(tvAccessibility)
        layout.addView(tvOverlay)
        layout.addView(tvRoot)
        layout.addView(tvRecording)
        layout.addView(tvOverlayActive)
        layout.addView(btnOpenAccessibility)
        layout.addView(btnRequestOverlay)
        layout.addView(btnToggleOverlay)
        layout.addView(btnStartStopRecording)
        layout.addView(btnTestTap)
        layout.addView(btnCheckRoot)
        layout.addView(btnOpenCalibration)
        layout.addView(tvOptHeader)
        layout.addView(swOptimized)
        layout.addView(swPassThrough)
        layout.addView(swLowRes)
        layout.addView(swBubble)
        layout.addView(tvResetHeader)
        layout.addView(tvResetInfo)
        layout.addView(btnSoftReset)
        layout.addView(btnFullReset)

        setContentView(container)

        // Actions
        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnRequestOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
        btnToggleOverlay.setOnClickListener {
            RecordingController.toggleOverlay(this)
            updateStatus()
        }
        btnStartStopRecording.setOnClickListener {
            if (RecordingController.isRecording()) {
                RecordingController.stopDataLogging(stopOverlay = true)
            } else {
                // Start data logging without media projection just to test touch overlay
                RecordingController.startDataLogging(startOverlay = true)
            }
            updateStatus()
        }
        btnTestTap.setOnClickListener {
            // Use screen center for a test tap
            val dm = resources.displayMetrics
            val cx = dm.widthPixels / 2f
            val cy = dm.heightPixels / 2f
            RecordingController.performClick(cx, cy)
        }
        btnCheckRoot.setOnClickListener {
            RootChecker.refresh()
            updateStatus()
        }
        btnOpenCalibration.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        // Load prefs
        val tprefs = getSharedPreferences("tweaks_prefs", MODE_PRIVATE)
        val optEnabled = tprefs.getBoolean("optimized", false)
        val passEnabled = tprefs.getBoolean("pass_through", false)
        val lowResEnabled = tprefs.getBoolean("lowres", false)
        val bubbleEnabled = tprefs.getBoolean("bubble", false)
        swOptimized.isChecked = optEnabled
        swPassThrough.isChecked = passEnabled
        swLowRes.isChecked = lowResEnabled
        swBubble.isChecked = bubbleEnabled
        // Apply initial values
        SimpleDataManager.setOptimizedLogging(optEnabled)
        SimpleTouchLogger.optimizedOverlay = optEnabled
        SimpleTouchLogger.forcePassThrough = passEnabled
        SimpleRecordingService.lowResRecording = lowResEnabled
        SimpleTouchLogger.setBubbleEnabled(bubbleEnabled)

        swOptimized.setOnCheckedChangeListener { _, isChecked ->
            tprefs.edit().putBoolean("optimized", isChecked).apply()
            SimpleDataManager.setOptimizedLogging(isChecked)
            SimpleTouchLogger.optimizedOverlay = isChecked
            android.widget.Toast.makeText(this, "Optimized mode ${if (isChecked) "ON" else "OFF"}", android.widget.Toast.LENGTH_SHORT).show()
        }
        swPassThrough.setOnCheckedChangeListener { _, isChecked ->
            tprefs.edit().putBoolean("pass_through", isChecked).apply()
            SimpleTouchLogger.forcePassThrough = isChecked
            android.widget.Toast.makeText(this, "Pass-through ${if (isChecked) "ON" else "OFF"}", android.widget.Toast.LENGTH_SHORT).show()
        }
        swLowRes.setOnCheckedChangeListener { _, isChecked ->
            tprefs.edit().putBoolean("lowres", isChecked).apply()
            SimpleRecordingService.lowResRecording = isChecked
            android.widget.Toast.makeText(this, "Low-res recording ${if (isChecked) "ON" else "OFF"}", android.widget.Toast.LENGTH_SHORT).show()
        }
        swBubble.setOnCheckedChangeListener { _, isChecked ->
            tprefs.edit().putBoolean("bubble", isChecked).apply()
            SimpleTouchLogger.setBubbleEnabled(isChecked)
            android.widget.Toast.makeText(this, "Floating bubble ${if (isChecked) "ON" else "OFF"}", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnSoftReset.setOnClickListener {
            try {
                // Stop any running capture/overlay and reset managers
                if (RecordingController.isRecording()) {
                    RecordingController.stopDataLogging(stopOverlay = true)
                } else {
                    RecordingController.stopOverlay()
                }
                RecordingController.forceReset()
                RootChecker.refresh()
                // Ensure overlay is enabled again after reset
                com.example.clashroyalelogger.service.RecordingController.setOverlayEnabled(this, true)
                // Reset runtime toggles to defaults and persist
                SimpleDataManager.setOptimizedLogging(false)
                SimpleTouchLogger.optimizedOverlay = false
                SimpleTouchLogger.forcePassThrough = false
                SimpleRecordingService.lowResRecording = false
                val tprefs = getSharedPreferences("tweaks_prefs", MODE_PRIVATE)
                tprefs.edit().putBoolean("optimized", false)
                    .putBoolean("pass_through", false)
                    .putBoolean("lowres", false)
                    .apply()
                val cprefs = getSharedPreferences("capture_prefs", MODE_PRIVATE)
                cprefs.edit().putInt("mode_index", 0).apply()
                com.example.clashroyalelogger.service.RecordingController.setCaptureMode(com.example.clashroyalelogger.service.CaptureMode.AUTO)
                // Update UI switches
                swOptimized.isChecked = false
                swPassThrough.isChecked = false
                swLowRes.isChecked = false
                android.widget.Toast.makeText(this, "Soft reset completed", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Soft reset failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            updateStatus()
        }

        btnFullReset.setOnClickListener {
            try {
                // Stop services and reset
                if (RecordingController.isRecording()) {
                    RecordingController.stopDataLogging(stopOverlay = true)
                } else {
                    RecordingController.stopOverlay()
                }
                RecordingController.forceReset()

                // Clear stored preferences (capture mode selection)
                getSharedPreferences("capture_prefs", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("tweaks_prefs", MODE_PRIVATE).edit().clear().apply()
                // Reset runtime toggles and UI to defaults
                SimpleDataManager.setOptimizedLogging(false)
                SimpleTouchLogger.optimizedOverlay = false
                SimpleTouchLogger.forcePassThrough = false
                SimpleRecordingService.lowResRecording = false
                swOptimized.isChecked = false
                swPassThrough.isChecked = false
                swLowRes.isChecked = false
                com.example.clashroyalelogger.service.RecordingController.setCaptureMode(com.example.clashroyalelogger.service.CaptureMode.AUTO)

                // Re-enable overlay preference
                com.example.clashroyalelogger.service.RecordingController.setOverlayEnabled(this, true)

                // Delete all stored session data
                val ok = SessionDataManager.deleteAllData()
                android.widget.Toast.makeText(this, if (ok) "Full reset completed" else "Full reset completed with some errors", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Full reset failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            updateStatus()
        }

        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayPerm = canDrawOverlays()
        val rootAvailable = RootChecker.isRootAvailable()
        val recording = RecordingController.isRecording()
        val overlayActive = com.example.clashroyalelogger.service.RecordingController.isOverlayEnabled()

        tvAccessibility.text = "Accessibility Service Enabled: $accessibilityEnabled"
        tvOverlay.text = "Overlay Permission Granted: $overlayPerm"
        tvRoot.text = "Root Available: $rootAvailable"
        tvRecording.text = "Recording Active: $recording"
        tvOverlayActive.text = "Overlay Enabled: $overlayActive"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "$packageName/${SimpleTouchLogger::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedServiceName) == true
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
}
