package com.example.clashroyalelogger.repository.impl

import com.example.clashroyalelogger.SessionDataManager
import com.example.clashroyalelogger.repository.SessionRepository
import com.example.clashroyalelogger.repository.SessionInfo

class FileSessionRepository : SessionRepository {
    override fun listSessions(): List<SessionInfo> {
        val sessions = SessionDataManager.getAllSessions()
        return sessions.map { s ->
            SessionInfo(
                id = s.sessionId,
                hasCsv = s.csvFile?.exists() == true,
                hasMetadata = s.metadataFile?.exists() == true,
                hasVideo = s.videoFile?.exists() == true,
                touchCount = s.touchEventCount,
                durationMs = s.duration
            )
        }
    }

    override fun deleteSession(id: String): Boolean {
        val sessions = SessionDataManager.getAllSessions()
        val target = sessions.find { it.sessionId == id } ?: return false
        return SessionDataManager.deleteSession(target)
    }
}