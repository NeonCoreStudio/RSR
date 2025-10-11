package com.example.clashroyalelogger.domain

data class TouchEvent(
    val timestampMs: Long,
    val eventType: String,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float
)