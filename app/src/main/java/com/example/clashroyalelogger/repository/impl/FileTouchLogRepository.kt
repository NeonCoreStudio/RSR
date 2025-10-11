package com.example.clashroyalelogger.repository.impl

import android.util.Log
import com.example.clashroyalelogger.SimpleDataManager
import com.example.clashroyalelogger.domain.TouchEvent
import com.example.clashroyalelogger.repository.TouchLogRepository

class FileTouchLogRepository : TouchLogRepository {
    override fun startSession(sessionId: String) {
        // SimpleDataManager manages session lifecycle; ensure recording state aligns elsewhere
        Log.d("FileTouchLogRepository", "startSession $sessionId (delegated)")
    }

    override fun log(event: TouchEvent) {
        if (!SimpleDataManager.isRecording) return
        SimpleDataManager.logTouchEvent(
            event.x,
            event.y,
            event.eventType,
            event.pressure,
            event.size
        )
    }

    override fun stopSession() {
        Log.d("FileTouchLogRepository", "stopSession (delegated)")
    }
}