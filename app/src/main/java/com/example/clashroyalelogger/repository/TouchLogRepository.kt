package com.example.clashroyalelogger.repository

import com.example.clashroyalelogger.domain.TouchEvent

interface TouchLogRepository {
    fun startSession(sessionId: String)
    fun log(event: TouchEvent)
    fun stopSession()
}