package com.example.clashroyalelogger.capture

import android.util.Log
import com.example.clashroyalelogger.di.RepositoryProvider
import com.example.clashroyalelogger.domain.TouchEvent
import com.example.clashroyalelogger.util.CoordinateStabilizer

/**
 * Root-based touch capture using `su -c getevent`.
 *
 * Reads low-level input events from `/dev/input` and emits true screen coordinates
 * without interfering with user interaction. Requires root.
 *
 * Limitations:
 * - Device must be rooted and allow `su`.
 * - Coordinates are raw device units; we attempt best-effort normalization via
 *   reported ABS ranges when present.
 */
object GetEventCapture {
    private const val TAG = "GetEventCapture"
    @Volatile private var process: Process? = null
    @Volatile private var running: Boolean = false

    // Simple state for primary pointer
    @Volatile private var currentX: Int = -1
    @Volatile private var currentY: Int = -1
    @Volatile private var inContact: Boolean = false

    fun isRunning(): Boolean = running

    fun start(): Boolean {
        if (running) return true
        try {
            // Use -lt for labeled with timestamps
            val cmd = arrayOf("su", "-c", "getevent -lt")
            process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            running = true
            Thread { readLoop() }.start()
            Log.d(TAG, "getevent capture started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start getevent (root required)", e)
            stop()
            return false
        }
    }

    fun stop() {
        running = false
        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null
        Log.d(TAG, "getevent capture stopped")
    }

    private fun readLoop() {
        val p = process ?: return
        try {
            p.inputStream.bufferedReader().use { reader ->
                while (running) {
                    val line = reader.readLine() ?: break
                    parseLine(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getevent read loop", e)
        } finally {
            stop()
        }
    }

    /**
     * Example lines:
     *  [  1234.567890] /dev/input/event2: EV_ABS       ABS_MT_POSITION_X    000003ab
     *  [  1234.567900] /dev/input/event2: EV_ABS       ABS_MT_POSITION_Y    000007cd
     *  [  1234.567950] /dev/input/event2: EV_KEY       BTN_TOUCH            DOWN
     *  [  1234.568000] /dev/input/event2: EV_SYN       SYN_REPORT           00000000
     */
    private fun parseLine(line: String) {
        try {
            val parts = line.trim().split(":", limit = 2)
            if (parts.size < 2) return
            val payload = parts[1].trim()
            val tokens = payload.split(Regex("\\s+"))
            if (tokens.size < 3) return

            val type = tokens[0]
            val code = tokens[1]
            val value = tokens.getOrNull(2) ?: ""

            when (type) {
                "EV_ABS" -> when (code) {
                    "ABS_MT_POSITION_X", "ABS_X" -> currentX = parseHexOrDec(value)
                    "ABS_MT_POSITION_Y", "ABS_Y" -> currentY = parseHexOrDec(value)
                }
                "EV_KEY" -> if (code == "BTN_TOUCH") {
                    inContact = value.equals("DOWN", ignoreCase = true) || value == "00000001"
                }
                "EV_SYN" -> if (code == "SYN_REPORT") {
                    flushSample()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse getevent line: $line", e)
        }
    }

    private fun parseHexOrDec(s: String): Int {
        return try {
            if (s.startsWith("0x") || s.any { it in 'a'..'f' || it in 'A'..'F' }) {
                Integer.parseInt(s.removePrefix("0x"), 16)
            } else {
                Integer.parseInt(s, 16) // getevent prints hex padded
            }
        } catch (_: Exception) {
            s.toIntOrNull() ?: -1
        }
    }

    private fun flushSample() {
        if (currentX < 0 || currentY < 0) return
        val eventType = if (inContact) "DOWN" else "UP"
        val metrics = CoordinateStabilizer.captureMetrics(null)
        val (sx, sy) = CoordinateStabilizer.clampAndRound(currentX.toFloat(), currentY.toFloat(), metrics)
        val event = TouchEvent(
            timestampMs = System.currentTimeMillis(),
            eventType = eventType,
            x = sx,
            y = sy,
            pressure = 1.0f,
            size = 1.0f
        )
        try {
            RepositoryProvider.touchLogRepository.log(event)
            Log.v(TAG, "getevent touch: (${sx.toInt()},${sy.toInt()}) $eventType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log getevent touch", e)
        }
    }
}