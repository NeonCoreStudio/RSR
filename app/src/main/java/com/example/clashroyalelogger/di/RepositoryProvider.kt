package com.example.clashroyalelogger.di

import com.example.clashroyalelogger.repository.impl.FileSessionRepository
import com.example.clashroyalelogger.repository.impl.FileTouchLogRepository
import com.example.clashroyalelogger.repository.SessionRepository
import com.example.clashroyalelogger.repository.TouchLogRepository

object RepositoryProvider {
    val touchLogRepository: TouchLogRepository by lazy { FileTouchLogRepository() }
    val sessionRepository: SessionRepository by lazy { FileSessionRepository() }
}