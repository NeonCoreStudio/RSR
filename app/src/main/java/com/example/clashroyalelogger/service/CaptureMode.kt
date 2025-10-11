package com.example.clashroyalelogger.service

enum class CaptureMode {
    AUTO,                // Prefer root+overlay if root is available; else overlay-only
    NON_ROOT,            // Accessibility overlay only (no root getevent)
    ROOT_PLUS_OVERLAY,   // Root getevent + accessibility overlay
    VIDEO_ONLY           // Screen recording only; no overlay, no getevent
}