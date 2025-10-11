package com.example.clashroyalelogger.util

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager

data class ScreenMetrics(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val rotation: Int
)

object CoordinateStabilizer {
    fun captureMetrics(context: Context?): ScreenMetrics {
        return try {
            val wm = context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            // Prefer real display metrics (include system bars) so they align with rawX/rawY
            val displayMetrics: DisplayMetrics = DisplayMetrics()
            try {
                @Suppress("DEPRECATION")
                val d = wm?.defaultDisplay
                if (d != null) {
                    @Suppress("DEPRECATION")
                    d.getRealMetrics(displayMetrics)
                } else {
                    // Fallback to system metrics if defaultDisplay is unavailable
                    val sys = Resources.getSystem().displayMetrics
                    displayMetrics.setTo(sys)
                }
            } catch (_: Exception) {
                val sys = if (context != null) context.resources.displayMetrics else Resources.getSystem().displayMetrics
                displayMetrics.setTo(sys)
            }
            val rotation = try {
                @Suppress("DEPRECATION")
                when (wm?.defaultDisplay?.rotation) {
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }
            } catch (_: Exception) { 0 }

            ScreenMetrics(
                width = displayMetrics.widthPixels,
                height = displayMetrics.heightPixels,
                densityDpi = displayMetrics.densityDpi,
                rotation = rotation
            )
        } catch (_: Exception) {
            val dm = Resources.getSystem().displayMetrics
            ScreenMetrics(dm.widthPixels, dm.heightPixels, dm.densityDpi, 0)
        }
    }

    fun clampAndRound(x: Float, y: Float, metrics: ScreenMetrics): Pair<Float, Float> {
        val cx = x.coerceIn(0f, (metrics.width - 1).toFloat())
        val cy = y.coerceIn(0f, (metrics.height - 1).toFloat())
        return Pair(kotlin.math.round(cx), kotlin.math.round(cy))
    }
}
