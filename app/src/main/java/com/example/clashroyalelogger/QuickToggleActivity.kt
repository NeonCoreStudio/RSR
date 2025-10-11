package com.example.clashroyalelogger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

class QuickToggleActivity : Activity() {
    companion object {
        private const val REQ_CAPTURE = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If recording, stop it immediately and finish
        if (com.example.clashroyalelogger.service.RecordingController.isRecording()) {
            val stop = Intent(this, SimpleRecordingService::class.java).apply { action = SimpleRecordingService.ACTION_STOP }
            startService(stop)
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Request MediaProjection permission to start recording
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpm.createScreenCaptureIntent()
        startActivityForResult(intent, REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent = Intent(this, SimpleRecordingService::class.java).apply {
                    action = SimpleRecordingService.ACTION_START
                    putExtra(SimpleRecordingService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(SimpleRecordingService.EXTRA_DATA, data)
                }
                startForegroundService(serviceIntent)
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}

