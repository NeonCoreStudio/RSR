package com.example.clashroyalelogger.repository

data class SessionInfo(
    val id: String,
    val hasCsv: Boolean,
    val hasMetadata: Boolean,
    val hasVideo: Boolean,
    val touchCount: Int,
    val durationMs: Long?
)

interface SessionRepository {
    fun listSessions(): List<SessionInfo>
    fun deleteSession(id: String): Boolean
}