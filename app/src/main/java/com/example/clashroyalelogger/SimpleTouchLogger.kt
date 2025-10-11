package com.example.clashroyalelogger

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.example.clashroyalelogger.di.RepositoryProvider
import com.example.clashroyalelogger.domain.TouchEvent
import com.example.clashroyalelogger.util.CoordinateStabilizer

class SimpleTouchLogger : AccessibilityService() {

    companion object {
        private const val TAG = "SimpleTouchLogger"

        @Volatile var forcePassThrough: Boolean = false
        @Volatile var optimizedOverlay: Boolean = false
        @Volatile var bubbleToggle: Boolean = false

        var instance: SimpleTouchLogger? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null

        fun logTouchEvent(x: Float, y: Float, action: String, pressure: Float = 1.0f) {
            instance?.logTouch(x, y, action, pressure)
        }

        fun startTouchCapture() {
            instance?.createOverlay()
        }

        fun stopTouchCapture() {
            instance?.removeOverlay()
        }

        fun tapAt(x: Float, y: Float, durationMs: Long = 100L): Boolean {
            return instance?.performTap(x, y, durationMs) == true
        }

        fun setBubbleEnabled(enabled: Boolean) {
            bubbleToggle = enabled
            instance?.let { svc ->
                if (enabled) svc.showControlBubble() else svc.hideControlBubble()
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var overlayView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var diagText: TextView? = null
    private var closeButton: TextView? = null
    private var resetButton: TextView? = null
    private var recordButton: TextView? = null

    private var controlView: TextView? = null
    private var controlLayoutParams: WindowManager.LayoutParams? = null

    private var isOverlayActive = false
    private var relayTaps = false
    private var touchCount = 0
    private var lastKeyTime: Long = 0L
    private var escapeCount = 0
    @Volatile private var isInjecting = false
    private var lastDownUptime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (bubbleToggle) showControlBubble()
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { removeOverlay() } catch (_: Exception) {}
        try { hideControlBubble() } catch (_: Exception) {}
        instance = null
        Log.d(TAG, "Service destroyed")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no-op */ }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        removeOverlay()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastKeyTime > 1500) escapeCount = 0
            lastKeyTime = now
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                escapeCount += 1
                if (escapeCount >= 3) {
                    removeOverlay()
                    com.example.clashroyalelogger.service.RecordingController.setOverlayEnabled(this, false)
                    android.widget.Toast.makeText(this, "Overlay disabled", android.widget.Toast.LENGTH_SHORT).show()
                    escapeCount = 0
                }
                return false
            }
        }
        return false
    }

    private fun createOverlay() {
        if (isOverlayActive) return
        if (!canDrawOverlays()) {
            Log.w(TAG, "Overlay permission not granted; cannot create overlay")
            return
        }
        try {
            // Decide if we relay taps via accessibility injection
            relayTaps = when (com.example.clashroyalelogger.service.RecordingController.getCaptureMode()) {
                com.example.clashroyalelogger.service.CaptureMode.NON_ROOT -> true
                com.example.clashroyalelogger.service.CaptureMode.AUTO -> true
                com.example.clashroyalelogger.service.CaptureMode.ROOT_PLUS_OVERLAY -> true
                else -> true
            }
            if (forcePassThrough) relayTaps = false

            touchCount = 0

            val container = FrameLayout(this)
            overlayContainer = container

            val overlayMetrics = CoordinateStabilizer.captureMetrics(this)
            overlayView = object : View(this) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (isInjecting && event.action != MotionEvent.ACTION_UP) return false
                    val metrics = if (optimizedOverlay) overlayMetrics else CoordinateStabilizer.captureMetrics(this@SimpleTouchLogger)
                    val x = event.rawX
                    val y = event.rawY
                    val (sx, sy) = CoordinateStabilizer.clampAndRound(x, y, metrics)

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastDownUptime = event.downTime
                            if (SimpleDataManager.isRecording) {
                                val pressure = runCatching { event.pressure }.getOrDefault(1.0f)
                                val size = runCatching { event.size }.getOrDefault(1.0f)
                                logTouch(sx, sy, "DOWN", pressure, size)
                                touchCount += 1
                                updateDiagnostics(sx, sy)
                            }
                            if (relayTaps) {
                                setOverlayTouchable(false)
                                return true
                            }
                            return false
                        }
                        MotionEvent.ACTION_UP -> {
                            val pressure = runCatching { event.pressure }.getOrDefault(1.0f)
                            val size = runCatching { event.size }.getOrDefault(1.0f)
                            if (SimpleDataManager.isRecording) {
                                logTouch(sx, sy, "CLICK", pressure, size)
                                touchCount += 1
                                updateDiagnostics(sx, sy)
                            }
                            if (relayTaps) {
                                isInjecting = true
                                setOverlayTouchable(false)
                                val pressDuration = (event.eventTime - lastDownUptime).coerceAtLeast(50L).coerceAtMost(1500L)
                                val ok = performTap(sx.toFloat(), sy.toFloat(), pressDuration)
                                if (ok) {
                                    postDelayed({
                                        setOverlayTouchable(true)
                                        isInjecting = false
                                    }, pressDuration + 50L)
                                    return true
                                } else {
                                    setOverlayTouchable(true)
                                    isInjecting = false
                                    return false
                                }
                            }
                            return false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (relayTaps) {
                                setOverlayTouchable(true)
                                isInjecting = false
                            }
                            updateDiagnostics(sx, sy)
                            return false
                        }
                        else -> {
                            if (!optimizedOverlay) {
                                updateDiagnostics(sx, sy)
                            } else {
                                val now = System.currentTimeMillis()
                                if (now - lastKeyTime > 100) {
                                    lastKeyTime = now
                                    updateDiagnostics(sx, sy)
                                }
                            }
                            return false
                        }
                    }
                }
            }.apply {
                isClickable = true
                isFocusable = false
                isFocusableInTouchMode = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            // Diagnostics text
            diagText = TextView(this).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(0x55000000)
                textSize = 12f
                setPadding(dp(6), dp(4), dp(6), dp(4))
                text = "Events: 0\nLast: -,-"
            }

            // Close button
            closeButton = TextView(this).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(0xAAFF3B30.toInt())
                textSize = 14f
                setPadding(dp(8), dp(6), dp(8), dp(6))
                text = "X"
                isClickable = true
                setOnClickListener {
                    removeOverlay()
                    com.example.clashroyalelogger.service.RecordingController.setOverlayEnabled(this@SimpleTouchLogger, false)
                    android.widget.Toast.makeText(this@SimpleTouchLogger, "Overlay disabled", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            // Reset button
            resetButton = TextView(this).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(0xAA34C759.toInt())
                textSize = 14f
                setPadding(dp(8), dp(6), dp(8), dp(6))
                text = "Reset"
                isClickable = true
                setOnClickListener {
                    resetAll()
                    android.widget.Toast.makeText(this@SimpleTouchLogger, "Overlay reset", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            // Layout params
            val lpFull = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val lpDiag = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(6)
                rightMargin = dp(6)
            }
            val lpClose = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(6)
                rightMargin = dp(6 + 64)
            }
            val lpReset = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(6)
                rightMargin = dp(6 + 64 + 72)
            }

            container.addView(overlayView, lpFull)
            container.addView(diagText, lpDiag)
            container.addView(closeButton, lpClose)
            container.addView(resetButton, lpReset)

            // Record button (optional; appears when overlay active)
            updateRecordButtonVisibility(true)

            // Window params
            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                }
                flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SPLIT_TOUCH)
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.TOP or Gravity.START
            }
            overlayLayoutParams = params
            windowManager?.addView(container, params)
            isOverlayActive = true
            Log.d(TAG, "Overlay created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay", e)
            isOverlayActive = false
        }
    }

    private fun recordButtonLayoutParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(6)
            rightMargin = dp(6 + 64 + 72 + 78)
        }
    }

    private fun ensureRecordButton() {
        if (recordButton != null) return
        recordButton = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xAA0A84FF.toInt())
            textSize = 14f
            setPadding(dp(8), dp(6), dp(8), dp(6))
            text = if (com.example.clashroyalelogger.service.RecordingController.isRecording()) "Stop" else "Rec"
            isClickable = true
            setOnClickListener {
                try {
                    if (com.example.clashroyalelogger.service.RecordingController.isRecording()) {
                        val stop = android.content.Intent(this@SimpleTouchLogger, SimpleRecordingService::class.java).apply {
                            action = SimpleRecordingService.ACTION_STOP
                        }
                        startService(stop)
                        text = "Rec"
                    } else {
                        val intent = android.content.Intent(this@SimpleTouchLogger, QuickToggleActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        text = "Stop"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Record toggle failed", e)
                }
            }
        }
    }

    private fun updateRecordButtonVisibility(enabled: Boolean) {
        if (!isOverlayActive) return
        if (enabled) {
            if (recordButton == null) {
                ensureRecordButton()
                overlayContainer?.addView(recordButton, recordButtonLayoutParams())
            }
        } else {
            overlayContainer?.removeView(recordButton)
            recordButton = null
        }
    }

    private fun removeOverlay() {
        isOverlayActive = false
        try {
            overlayContainer?.let { container ->
                windowManager?.removeView(container)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing overlay", e)
        } finally {
            overlayContainer = null
            overlayView = null
            overlayLayoutParams = null
            diagText = null
            closeButton = null
            resetButton = null
            updateRecordButtonVisibility(false)
        }
    }

    private fun showControlBubble() {
        if (controlView != null) return
        try {
            val tv = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                text = if (com.example.clashroyalelogger.service.RecordingController.isRecording()) "STOP" else "REC"
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(0xAA34C759.toInt())
                }
                background = bg
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { onControlClick(this) }
            }
            controlView = tv
            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.END
                x = dp(6)
                y = dp(80)
            }
            controlLayoutParams = params
            windowManager?.addView(tv, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show control bubble", e)
        }
    }

    private fun hideControlBubble() {
        try {
            controlView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed removing control bubble", e)
        } finally {
            controlView = null
            controlLayoutParams = null
        }
    }

    private fun onControlClick(label: TextView) {
        try {
            if (com.example.clashroyalelogger.service.RecordingController.isRecording()) {
                val stop = android.content.Intent(this, SimpleRecordingService::class.java).apply {
                    action = SimpleRecordingService.ACTION_STOP
                }
                startService(stop)
            } else {
                val intent = android.content.Intent(this, QuickToggleActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Control bubble action failed", e)
        }
        updateControlAppearance()
    }

    private fun updateControlAppearance() {
        val tv = controlView ?: return
        val rec = com.example.clashroyalelogger.service.RecordingController.isRecording()
        tv.text = if (rec) "STOP" else "REC"
        (tv.background as? android.graphics.drawable.GradientDrawable)?.setColor(
            if (rec) 0xAAFF3B30.toInt() else 0xAA34C759.toInt()
        )
    }

    private fun resetAll() {
        try {
            setOverlayTouchable(true)
            isInjecting = false
            touchCount = 0
            escapeCount = 0
            lastKeyTime = 0L
            updateDiagnostics(-1f, -1f)
            if (isOverlayActive) removeOverlay()
            createOverlay()
            Log.d(TAG, "Overlay reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset overlay", e)
        }
    }

    private fun setOverlayTouchable(enabled: Boolean) {
        val container = overlayContainer ?: return
        val params = overlayLayoutParams ?: return
        params.flags = if (enabled) {
            (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv())
        } else {
            (params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
        try {
            windowManager?.updateViewLayout(container, params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update overlay touchability", e)
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun updateDiagnostics(x: Float, y: Float) {
        diagText?.text = "Events: $touchCount\nLast: ${x.toInt()},${y.toInt()}"
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    fun logTouch(x: Float, y: Float, action: String, pressure: Float = 1.0f, size: Float = 1.0f) {
        if (SimpleDataManager.isRecording) {
            val event = TouchEvent(
                timestampMs = System.currentTimeMillis(),
                eventType = action,
                x = x,
                y = y,
                pressure = pressure,
                size = size
            )
            RepositoryProvider.touchLogRepository.log(event)
            Log.v(TAG, "Touch logged: ($x, $y) - $action")
        }
    }

    fun performTap(x: Float, y: Float, durationMs: Long = 100L): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val metrics = CoordinateStabilizer.captureMetrics(this)
                val (sx, sy) = CoordinateStabilizer.clampAndRound(x, y, metrics)
                val path = Path().apply {
                    moveTo(sx, sy)
                    lineTo(sx, sy)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs, false)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        Log.d(TAG, "dispatchGesture completed at ($sx,$sy)")
                        setOverlayTouchable(true)
                        isInjecting = false
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        Log.w(TAG, "dispatchGesture cancelled at ($sx,$sy)")
                        setOverlayTouchable(true)
                        isInjecting = false
                    }
                }, null)
                Log.d(TAG, "Requested accessibility tap at ($sx,$sy), accepted=$accepted")
                accepted
            } else {
                Log.w(TAG, "Accessibility tap not supported below Android N")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform accessibility tap", e)
            false
        }
    }
}

