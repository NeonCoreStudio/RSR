package com.example.clashroyalelogger

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.BlurMaskFilter
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SessionViewerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SessionViewerActivity"
        private const val EXTRA_SESSION_ID = "session_id"
        
        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, SessionViewerActivity::class.java)
            intent.putExtra(EXTRA_SESSION_ID, sessionId)
            context.startActivity(intent)
        }
    }
    
    private lateinit var videoView: VideoView
    private lateinit var touchOverlay: TouchOverlayView
    private lateinit var btnPlayPause: Button
    private lateinit var btnRestart: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvSessionInfo: TextView
    private lateinit var tvTouchInfo: TextView
    private lateinit var switchShowTouches: Switch
    
    private var sessionData: SessionData? = null
    private var touchEvents: List<TouchEvent> = emptyList()
    private var videoStartTime: Long = 0
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0
    private var videoW: Int = 0
    private var videoH: Int = 0
    private var isPlaying = false
    private var videoDuration = 0
    private var currentPosition = 0
    
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && videoView.isPlaying) {
                currentPosition = videoView.currentPosition
                updateUI()
                updateHandler.postDelayed(this, 100) // Update every 100ms
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_viewer)
        
        initViews()
        setupVideoView()
        loadSessionData()
    }
    
    private fun initViews() {
        videoView = findViewById(R.id.videoView)
        touchOverlay = findViewById(R.id.touchOverlay)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnRestart = findViewById(R.id.btnRestart)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        tvSessionInfo = findViewById(R.id.tvSessionInfo)
        tvTouchInfo = findViewById(R.id.tvTouchInfo)
        switchShowTouches = findViewById(R.id.switchShowTouches)
        
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnRestart.setOnClickListener { restartVideo() }
        switchShowTouches.setOnCheckedChangeListener { _, isChecked ->
            touchOverlay.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoView.seekTo(progress)
                    currentPosition = progress
                    updateTouchOverlay()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupVideoView() {
        videoView.setOnPreparedListener { mediaPlayer ->
            videoDuration = mediaPlayer.duration
            seekBar.max = videoDuration
            tvTotalTime.text = formatTime(videoDuration)
            
            mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
                // Adjust touch overlay to match video dimensions
                videoW = width
                videoH = height
                touchOverlay.setVideoDimensions(width, height)
                // Also provide recorded source dimensions so overlay can normalize
                if (sourceWidth > 0 && sourceHeight > 0) {
                    touchOverlay.setSourceDimensions(sourceWidth, sourceHeight)
                }
            }
        }
        
        videoView.setOnCompletionListener {
            isPlaying = false
            btnPlayPause.text = "Play"
            updateHandler.removeCallbacks(updateRunnable)
        }
        
        videoView.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "Video playback error: what=$what, extra=$extra")
            val errorMessage = when (what) {
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown media error occurred"
                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died"
                else -> "Video playback error (code: $what)"
            }
            showError("$errorMessage. Please check if the video file is valid.")
            true // Return true to indicate we handled the error
        }
    }
    
    private fun loadSessionData() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        
        // Load session data
        val sessions = SessionDataManager.getAllSessions()
        sessionData = sessions.find { it.sessionId == sessionId }
        
        sessionData?.let { session ->
            // Display session info
            tvSessionInfo.text = """
                Session: ${session.sessionId}
                Date: ${session.formattedDate}
                Duration: ${session.formattedDuration}
                Events: ${session.touchEventCount}
                Size: ${session.sizeInMB}
            """.trimIndent()
            
            // Load video
            session.videoFile?.let { videoFile ->
                if (videoFile.exists()) {
                    videoView.setVideoURI(Uri.fromFile(videoFile))
                } else {
                    showError("Video file not found")
                    return
                }
            }
            
            // Load touch events and metadata
            loadTouchEvents(session)
            loadMetadata(session)
            // If metadata was loaded before video size is known, still pass source dims
            if (videoW > 0 && videoH > 0 && sourceWidth > 0 && sourceHeight > 0) {
                touchOverlay.setSourceDimensions(sourceWidth, sourceHeight)
            }
        } ?: run {
            showError("Session not found")
        }
    }
    
    private fun loadTouchEvents(session: SessionData) {
        session.csvFile?.let { csvFile ->
            if (csvFile.exists()) {
                try {
                    val events = mutableListOf<TouchEvent>()
                    csvFile.readLines().drop(1).forEach { line -> // Skip header
                        val parts = line.split(",")
                        if (parts.size >= 6) {
                            events.add(TouchEvent(
                                timestamp = parts[0].toLong(),
                                eventType = parts[1],
                                x = parts[2].toFloat(),
                                y = parts[3].toFloat(),
                                pressure = parts[4].toFloat(),
                                size = parts[5].toFloat()
                            ))
                        }
                    }
                    touchEvents = events
                    tvTouchInfo.text = "Loaded ${touchEvents.size} touch events"
                    touchOverlay.setTouchEvents(touchEvents)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading touch events", e)
                    showError("Error loading touch events: ${e.message}")
                }
            }
        }
    }
    
    private fun loadMetadata(session: SessionData) {
        session.metadataFile?.let { metadataFile ->
            if (metadataFile.exists()) {
                try {
                    val json = Gson().fromJson(metadataFile.readText(), JsonObject::class.java)
                    val videoStartStr = json.get("video_start")?.asString
                    if (videoStartStr != null) {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                        videoStartTime = format.parse(videoStartStr)?.time ?: 0
                    }
                    // Read source (recorded screen) dimensions for coordinate normalization
                    val screen = json.getAsJsonObject("screen_metrics")
                    if (screen != null) {
                        sourceWidth = screen.get("width")?.asInt ?: 0
                        sourceHeight = screen.get("height")?.asInt ?: 0
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading metadata", e)
                }
            }
        }
    }
    
    private fun togglePlayPause() {
        if (isPlaying) {
            videoView.pause()
            isPlaying = false
            btnPlayPause.text = "Play"
            updateHandler.removeCallbacks(updateRunnable)
        } else {
            videoView.start()
            isPlaying = true
            btnPlayPause.text = "Pause"
            updateHandler.post(updateRunnable)
        }
    }
    
    private fun restartVideo() {
        videoView.seekTo(0)
        currentPosition = 0
        updateUI()
        updateTouchOverlay()
    }
    
    private fun updateUI() {
        tvCurrentTime.text = formatTime(currentPosition)
        seekBar.progress = currentPosition
        updateTouchOverlay()
    }
    
    private fun updateTouchOverlay() {
        if (switchShowTouches.isChecked) {
            touchOverlay.updateCurrentTime(currentPosition.toLong())
        }
    }
    
    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format("%02d:%02d", minutes, seconds % 60)
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacks(updateRunnable)
    }
}

// Data class for touch events
data class TouchEvent(
    val timestamp: Long,
    val eventType: String,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float
)

// Custom view for displaying touch overlays
class TouchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var touchEvents: List<TouchEvent> = emptyList()
    private var currentTime: Long = 0
    private var videoWidth = 0
    private var videoHeight = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var srcWidth = 0
    private var srcHeight = 0
    
    // Pretty visualization paints
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 90
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    
    fun setTouchEvents(events: List<TouchEvent>) {
        touchEvents = events
        invalidate()
    }
    
    fun setVideoDimensions(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        invalidate()
    }

    fun setSourceDimensions(width: Int, height: Int) {
        srcWidth = width
        srcHeight = height
        invalidate()
    }
    
    fun updateCurrentTime(timeMs: Long) {
        currentTime = timeMs
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (touchEvents.isEmpty() || videoWidth == 0 || videoHeight == 0) return
        
        // Compute how the video content fits inside this view (letterboxing aware)
        val scaleFit = kotlin.math.min(
            viewWidth.toFloat() / videoWidth.toFloat(),
            viewHeight.toFloat() / videoHeight.toFloat()
        )
        val drawW = videoWidth * scaleFit
        val drawH = videoHeight * scaleFit
        val offsetX = (viewWidth - drawW) / 2f
        val offsetY = (viewHeight - drawH) / 2f
        // Compute how the source content fits inside the recorded video (handles recording-time letterboxing)
        val hasSrc = srcWidth > 0 && srcHeight > 0
        val contentScaleToVideo = if (hasSrc) kotlin.math.min(
            videoWidth.toFloat() / srcWidth.toFloat(),
            videoHeight.toFloat() / srcHeight.toFloat()
        ) else 1f
        val contentW = if (hasSrc) srcWidth * contentScaleToVideo else videoWidth.toFloat()
        val contentH = if (hasSrc) srcHeight * contentScaleToVideo else videoHeight.toFloat()
        val contentOffX = ((videoWidth - contentW) / 2f)
        val contentOffY = ((videoHeight - contentH) / 2f)
        
        // Show touch events within a time window around current time
        val timeWindow = 1000L // 1 second window
        val relevantEvents = touchEvents.filter { event ->
            Math.abs(event.timestamp - currentTime) <= timeWindow
        }
        
        // Draw touch trails (fading)
        val recentEvents = touchEvents.filter { event ->
            event.timestamp <= currentTime && event.timestamp >= currentTime - 2000 // 2 second trail
        }
        
        if (recentEvents.size > 1) {
            for (i in 1 until recentEvents.size) {
                val prev = recentEvents[i - 1]
                val curr = recentEvents[i]
                
                val x1Video = contentOffX + (prev.x / (if (hasSrc) srcWidth.toFloat() else videoWidth.toFloat())) * contentW
                val y1Video = contentOffY + (prev.y / (if (hasSrc) srcHeight.toFloat() else videoHeight.toFloat())) * contentH
                val x2Video = contentOffX + (curr.x / (if (hasSrc) srcWidth.toFloat() else videoWidth.toFloat())) * contentW
                val y2Video = contentOffY + (curr.y / (if (hasSrc) srcHeight.toFloat() else videoHeight.toFloat())) * contentH

                val x1 = offsetX + x1Video * scaleFit
                val y1 = offsetY + y1Video * scaleFit
                val x2 = offsetX + x2Video * scaleFit
                val y2 = offsetY + y2Video * scaleFit
                
                val segAge = (currentTime - curr.timestamp).coerceAtLeast(0)
                val fade = (1f - (segAge / 2000f).coerceIn(0f, 1f))
                trailPaint.alpha = (90 * fade).toInt().coerceIn(0, 90)
                canvas.drawLine(x1, y1, x2, y2, trailPaint)
            }
        }
        
        // Draw current touch points (glassy + glow)
        relevantEvents.forEach { event ->
            val xVideo = contentOffX + (event.x / (if (hasSrc) srcWidth.toFloat() else videoWidth.toFloat())) * contentW
            val yVideo = contentOffY + (event.y / (if (hasSrc) srcHeight.toFloat() else videoHeight.toFloat())) * contentH
            val x = offsetX + xVideo * scaleFit
            val y = offsetY + yVideo * scaleFit
            val baseRadius = 24f + (event.pressure * 14f)
            val ageSince = (currentTime - event.timestamp).toFloat()
            val appearDur = 220f // ms
            val appearT = (ageSince / appearDur).coerceIn(0f, 1f)
            val easeIn = appearT * appearT // accelerating appear
            val radius = baseRadius * (0.6f + 0.4f * easeIn)
            val fade = (1f - (ageSince / 1000f)).coerceIn(0f, 1f)

            val baseColor = when (event.eventType) {
                "TOUCH_DOWN" -> 0xFF34C759.toInt()
                "TOUCH_UP" -> 0xFFFF3B30.toInt()
                else -> 0xFF0A84FF.toInt()
            }
            val aGlow = (80 * fade).toInt().coerceIn(0, 120)
            val aRing = (160 * fade).toInt().coerceIn(0, 200)

            // Outer glow
            glowPaint.color = (aGlow shl 24) or (baseColor and 0x00FFFFFF)
            canvas.drawCircle(x, y, radius * 1.8f, glowPaint)

            // Glassy radial gradient fill
            val shader = RadialGradient(x, y, radius,
                intArrayOf(
                    Color.argb((140 * fade).toInt(), 255, 255, 255),
                    Color.argb((110 * fade).toInt(), (baseColor shr 16) and 0xFF, (baseColor shr 8) and 0xFF, baseColor and 0xFF),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
            gradientPaint.shader = shader
            canvas.drawCircle(x, y, radius, gradientPaint)

            // Subtle ring
            ringPaint.alpha = aRing
            canvas.drawCircle(x, y, radius, ringPaint)
        }
    }
}


