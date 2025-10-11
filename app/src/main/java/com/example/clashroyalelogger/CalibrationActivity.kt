package com.example.clashroyalelogger

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.clashroyalelogger.util.CoordinateStabilizer
import kotlin.math.abs
import kotlin.math.hypot

class CalibrationActivity : AppCompatActivity(), SimpleDataManager.TouchLogListener {

    private lateinit var tvInfo: TextView
    private lateinit var btnStartDemo: Button
    private lateinit var btnStartEdge: Button
    // Demo only; no apply/save in measurement-only mode
    private lateinit var targetView: TargetView
    private val results = mutableListOf<ResultItem>()

    private var mode: Mode = Mode.DEMO
    private var running = false
    private var currentIndex = 0
    private var targets: List<Pair<Float, Float>> = emptyList()
    private var baseW: Int = 0
    private var baseH: Int = 0
    private var currentTx: Float = -1f
    private var currentTy: Float = -1f
    private var expectedScreenX: Float = 0f
    private var expectedScreenY: Float = 0f

    private data class ResultItem(val expectedX: Float, val expectedY: Float, val loggedX: Float, val loggedY: Float, val dtMs: Long)
    private enum class Mode { DEMO, EDGE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Calibration"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        tvInfo = TextView(this)
        btnStartDemo = Button(this).apply { text = "Start Accuracy Demo" }
        btnStartEdge = Button(this).apply { text = "Start Edge Calibration" }
        targetView = TargetView(this)

        layout.addView(tvInfo)
        layout.addView(btnStartDemo)
        layout.addView(btnStartEdge)
        layout.addView(targetView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(layout)

        btnStartDemo.setOnClickListener {
            mode = Mode.DEMO
            startSequence()
        }
        btnStartEdge.setOnClickListener {
            mode = Mode.EDGE
            startSequence()
        }
        // No-op apply; this activity only measures.
    }

    override fun onResume() {
        super.onResume()
        SimpleDataManager.addTouchLogListener(this)
    }

    override fun onPause() {
        super.onPause()
        SimpleDataManager.removeTouchLogListener(this)
    }

    private fun startSequence() {
        results.clear()
        running = true
        currentIndex = 0
        tvInfo.text = "Follow the dots and tap each target."

        // Ensure recording is active so touches are logged
        try { com.example.clashroyalelogger.service.RecordingController.startDataLogging(startOverlay = true) } catch (_: Exception) {}

        val metrics = CoordinateStabilizer.captureMetrics(this)
        baseW = metrics.width
        baseH = metrics.height
        val w = baseW.toFloat()
        val h = baseH.toFloat()
        targetView.setBase(baseW, baseH)
        val inset = 48f

        targets = if (mode == Mode.DEMO) {
            listOf(
                Pair(w/2f, h/2f),
                Pair(inset, inset),
                Pair(w - inset, inset),
                Pair(w - inset, h - inset),
                Pair(inset, h - inset),
                Pair(w/2f, inset),
                Pair(w/2f, h - inset),
                Pair(inset, h/2f),
                Pair(w - inset, h/2f),
            )
        } else {
            listOf(
                Pair(inset, inset),
                Pair(w - inset, inset),
                Pair(w - inset, h - inset),
                Pair(inset, h - inset),
            )
        }
        showCurrentTarget()
    }

    private fun showCurrentTarget() {
        if (!running || currentIndex >= targets.size) {
            finishSequence()
            return
        }
        val (tx, ty) = targets[currentIndex]
        currentTx = tx
        currentTy = ty
        targetView.setTarget(tx, ty)
        // Compute expected on-screen position of the drawn target (accounts for top UI offset)
        targetView.post { updateExpectedScreenPosition() }
    }

    private fun updateExpectedScreenPosition() {
        if (currentTx < 0f || currentTy < 0f) return
        val loc = IntArray(2)
        targetView.getLocationOnScreen(loc)
        val viewLeft = loc[0].toFloat()
        val viewTop = loc[1].toFloat()
        val sx = (currentTx / baseW.toFloat()) * targetView.width
        val sy = (currentTy / baseH.toFloat()) * targetView.height
        expectedScreenX = viewLeft + sx
        expectedScreenY = viewTop + sy
    }

    override fun onTouchLogged(timestamp: Long, eventType: String, x: Float, y: Float, pressure: Float, size: Float) {
        if (!running) return
        if (eventType != "CLICK" && eventType != "UP" && eventType != "TOUCH_UP") return
        if (currentIndex >= targets.size) return
        // Use the actual on-screen position of the drawn target as expected
        results.add(ResultItem(expectedScreenX, expectedScreenY, x, y, timestamp))
        currentIndex += 1
        runOnUiThread { showCurrentTarget() }
    }

    private fun finishSequence() {
        running = false
        if (results.isEmpty()) {
            tvInfo.text = "No results captured. Ensure recording is active and overlay is running."
            return
        }
        // Compute errors
        var sum = 0.0
        var maxE = 0.0
        results.forEach { r ->
            val e = hypot((r.loggedX - r.expectedX).toDouble(), (r.loggedY - r.expectedY).toDouble())
            sum += e
            if (e > maxE) maxE = e
        }
        val mean = sum / results.size
        tvInfo.text = "Captured ${results.size} taps. Mean error: ${"%.1f".format(mean)} px, Max error: ${"%.1f".format(maxE)} px."

        if (mode == Mode.EDGE) {
            // Compute best-fit coefficients and show them, but do not apply/save.
            val msg = computeBestFitMessage()
            tvInfo.text = tvInfo.text.toString() + "\nSuggested (not applied): " + msg
            Toast.makeText(this, "Computed calibration (not applied)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun computeBestFitMessage(): String {
        if (results.size < 2) return "insufficient points"
        val lx = results.map { it.loggedX }
        val ly = results.map { it.loggedY }
        val tx = results.map { it.expectedX }
        val ty = results.map { it.expectedY }
        fun fit1D(l: List<Float>, t: List<Float>): Pair<Float, Float> {
            val n = l.size
            val meanL = l.sum() / n
            val meanT = t.sum() / n
            var num = 0.0
            var den = 0.0
            for (i in 0 until n) {
                val dl = (l[i] - meanL)
                num += (dl * (t[i] - meanT)).toDouble()
                den += (dl * dl).toDouble()
            }
            val a = if (den != 0.0) (num / den).toFloat() else 1f
            val b = (meanT - a * meanL)
            return a to b
        }
        val (a, b) = fit1D(lx, tx)
        val (c, d) = fit1D(ly, ty)
        return "x≈${"%.4f".format(a)}·x+${"%.1f".format(b)}, y≈${"%.4f".format(c)}·y+${"%.1f".format(d)}"
    }

    private class TargetView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.MAGENTA; style = Paint.Style.FILL }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
        private var tx = -1f
        private var ty = -1f
        private var baseW = context.resources.displayMetrics.widthPixels
        private var baseH = context.resources.displayMetrics.heightPixels
        fun setTarget(x: Float, y: Float) { tx = x; ty = y; invalidate() }
        fun setBase(w: Int, h: Int) { baseW = w; baseH = h; invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (tx < 0 || ty < 0) return
            // Map screen coordinates to this view's canvas (assumes full-screen view)
            val sx = tx / baseW.toFloat() * width
            val sy = ty / baseH.toFloat() * height
            canvas.drawCircle(sx, sy, 18f, paint)
            canvas.drawCircle(sx, sy, 28f, ring)
        }
    }
}
