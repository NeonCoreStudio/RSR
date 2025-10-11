package com.example.clashroyalelogger

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Log
import com.example.clashroyalelogger.di.RepositoryProvider

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private lateinit var btnExport: Button
    private lateinit var btnViewData: Button
    private lateinit var btnDiagnostics: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvSessionCount: TextView
    private lateinit var spinnerCaptureMode: Spinner
    
    private var isRecording = false
    
    private lateinit var screenCaptureResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    
    private val recordingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SimpleDataManager.ACTION_RECORDING_STATE_CHANGED) {
                val newIsRecording = intent.getBooleanExtra(SimpleDataManager.EXTRA_IS_RECORDING, false)
                val sessionId = intent.getStringExtra(SimpleDataManager.EXTRA_SESSION_ID) ?: ""
                
                Log.d("MainActivity", "Received state change: isRecording=$newIsRecording, sessionId=$sessionId")
                
                isRecording = newIsRecording
                runOnUiThread {
                    updateUI()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SimpleDataManager
        SimpleDataManager.initialize(this)
        
        initActivityResultLauncher()
        initViews()
        setupClickListeners()
        setupCaptureModeSpinner()
        
        // Register broadcast receiver
        val filter = IntentFilter(SimpleDataManager.ACTION_RECORDING_STATE_CHANGED)
        LocalBroadcastManager.getInstance(this).registerReceiver(recordingStateReceiver, filter)
    }
    
    private fun initActivityResultLauncher() {
        screenCaptureResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("MainActivity", "Screen capture result: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.d("MainActivity", "Screen capture permission granted, starting service")
                val serviceIntent = Intent(this, SimpleRecordingService::class.java).apply {
                    action = SimpleRecordingService.ACTION_START
                    putExtra(SimpleRecordingService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(SimpleRecordingService.EXTRA_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                updateUI()
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "Screen capture permission denied")
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }
        
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("MainActivity", "Overlay permission result received")
            if (canDrawOverlays()) {
                Log.d("MainActivity", "Overlay permission granted")
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "Overlay permission denied")
                Toast.makeText(this, "Overlay permission required for touch capture", Toast.LENGTH_LONG).show()
            }
            updateUI()
        }
    }

    private fun initViews() {
        btnRecord = findViewById(R.id.btnRecord)
        btnExport = findViewById(R.id.btnExport)
        btnViewData = findViewById(R.id.btnViewData)
        btnDiagnostics = findViewById(R.id.btnDiagnostics)
        tvStatus = findViewById(R.id.tvStatus)
        tvSessionCount = findViewById(R.id.tvSessionCount)
        spinnerCaptureMode = findViewById(R.id.spinnerCaptureMode)
    }

    private fun setupClickListeners() {
        btnRecord.setOnClickListener {
            onRecordButtonClick()
        }

        // Long-press to toggle the touch overlay on/off without stopping recording
        btnRecord.setOnLongClickListener {
            val enabled = com.example.clashroyalelogger.service.RecordingController.toggleOverlay(this)
            val message = if (enabled) "Touch overlay enabled" else "Touch overlay disabled (click-through)"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            true
        }

        btnExport.setOnClickListener {
            exportData()
        }

        btnViewData.setOnClickListener {
            val intent = Intent(this, DataManagementActivity::class.java)
            startActivity(intent)
        }

        btnDiagnostics.setOnClickListener {
            val intent = Intent(this, DiagnosticsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupCaptureModeSpinner() {
        val modes = listOf(
            "Auto (prefer root)",
            "Non-root (overlay)",
            "Root + overlay",
            "Video only"
        )
        spinnerCaptureMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modes
        )

        val prefs = getSharedPreferences("capture_prefs", MODE_PRIVATE)
        val selected = prefs.getInt("mode_index", 0)
        if (selected in 0..3) spinnerCaptureMode.setSelection(selected) else spinnerCaptureMode.setSelection(0)

        spinnerCaptureMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val mode = when (position) {
                    1 -> com.example.clashroyalelogger.service.CaptureMode.NON_ROOT
                    2 -> com.example.clashroyalelogger.service.CaptureMode.ROOT_PLUS_OVERLAY
                    3 -> com.example.clashroyalelogger.service.CaptureMode.VIDEO_ONLY
                    else -> com.example.clashroyalelogger.service.CaptureMode.AUTO
                }
                com.example.clashroyalelogger.service.RecordingController.setCaptureMode(mode)
                prefs.edit().putInt("mode_index", position).apply()
                updateUI()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun onRecordButtonClick() {
        Log.d("MainActivity", "Record button clicked")
        
        if (!isAccessibilityServiceEnabled()) {
            Log.d("MainActivity", "Accessibility service not enabled")
            showAccessibilitySettings()
            return
        }
        
        // Overlay permission no longer required for accessibility overlay
        
        if (isRecording) {
            Log.d("MainActivity", "Stopping recording")
            stopRecording()
        } else {
            Log.d("MainActivity", "Starting recording")
            startRecording()
        }
    }

    private fun updateUI() {
        isRecording = SimpleDataManager.isRecording
        
        if (!isAccessibilityServiceEnabled()) {
            tvStatus.text = "Touch detection disabled\nEnable accessibility service"
            btnRecord.text = "Enable Touch Detection"
            btnRecord.isEnabled = true
        } else if (isRecording) {
            val overlayStatus = if (com.example.clashroyalelogger.service.RecordingController.isOverlayEnabled()) "Overlay: ON" else "Overlay: OFF (click-through)"
            tvStatus.text = "Recording in progress...\nTouch events and screen captured\n$overlayStatus"
            btnRecord.text = "Stop Recording"
            btnRecord.isEnabled = true
        } else {
            val overlayStatus = if (com.example.clashroyalelogger.service.RecordingController.isOverlayEnabled()) "Overlay: ON" else "Overlay: OFF (click-through)"
            tvStatus.text = "Ready to record\nTouch detection enabled\n$overlayStatus"
            btnRecord.text = "Start Recording"
            btnRecord.isEnabled = true
        }
        
        val sessionCount = try {
            RepositoryProvider.sessionRepository.listSessions().size
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get session count via repository", e)
            SimpleDataManager.getSessionCount()
        }
        tvSessionCount.text = "Recorded sessions: $sessionCount"
        btnExport.isEnabled = sessionCount > 0
        btnViewData.isEnabled = sessionCount > 0
    }

    private fun startRecording() {
        Log.d("MainActivity", "startRecording() called")
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        Log.d("MainActivity", "Starting screen capture intent")
        screenCaptureResultLauncher.launch(intent)
    }

    private fun stopRecording() {
        val serviceIntent = Intent(this, SimpleRecordingService::class.java).apply {
            action = SimpleRecordingService.ACTION_STOP
        }
        startService(serviceIntent)
        updateUI()
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
    }

    private fun exportData() {
        SimpleDataManager.exportAllSessions(this) { success, message ->
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAccessibilitySettings() {
        Toast.makeText(this, "Please enable Touch Logger accessibility service", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "${packageName}/${SimpleTouchLogger::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedServiceName) == true
    }
    
    // Overlay permission is not required when using TYPE_ACCESSIBILITY_OVERLAY
    private fun canDrawOverlays(): Boolean = true
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "Please enable overlay permission for touch capture", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Synchronize UI state with SimpleDataManager when activity resumes
        isRecording = SimpleDataManager.isRecording
        updateUI()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recordingStateReceiver)
    }

}