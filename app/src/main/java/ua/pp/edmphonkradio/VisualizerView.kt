package ua.pp.edmphonkradio

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var visualizer: Visualizer? = null
    private var magnitudes: FloatArray = FloatArray(32)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barCount = 32

    fun attachToSession(audioSessionId: Int) {
        release()
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft ?: return
                        updateMagnitudes(fft)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            // Some devices/emulators may not support Visualizer — fail silently, bars stay flat
        }
    }

    private fun updateMagnitudes(fft: ByteArray) {
        val n = fft.size / 2
        val step = (n / barCount).coerceAtLeast(1)
        for (i in 0 until barCount) {
            val idx = (i * step).coerceIn(0, n - 1)
            val re = fft[idx * 2].toInt()
            val im = if (idx * 2 + 1 < fft.size) fft[idx * 2 + 1].toInt() else 0
            val magnitude = Math.sqrt((re * re + im * im).toDouble()).toFloat()
            magnitudes[i] = (magnitude / 40f).coerceIn(0.05f, 1f)
        }
        postInvalidate()
    }

    fun release() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val barWidth = w / (barCount * 1.6f)
        val gap = barWidth * 0.6f

        paint.shader = LinearGradient(
            0f, h, 0f, 0f,
            intArrayOf(0xFF7C3AED.toInt(), 0xFF22D3EE.toInt()),
            null, Shader.TileMode.CLAMP
        )

        var x = 0f
        for (i in 0 until barCount) {
            val mag = if (i < magnitudes.size) magnitudes[i] else 0.05f
            val barHeight = (h * mag).coerceAtLeast(h * 0.04f)
            val top = h - barHeight
            canvas.drawRoundRect(x, top, x + barWidth, h, barWidth / 2, barWidth / 2, paint)
            x += barWidth + gap
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
