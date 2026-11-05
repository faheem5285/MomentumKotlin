@file:Suppress(
    "FunctionNaming",
    "MagicNumber",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "LargeClass"
)

package com.faheem.habits.features.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Handler
import android.os.Looper
import android.view.View

/** Single source of truth for the charging looks offered in Battery settings. */
internal val BATTERY_ANIMATION_STYLES = listOf(
    "Liquid Glass", "Plasma Storm", "Pulse", "Flow", "Glow",
    "Neon Ring", "Dual Orbit", "Spark", "Wave", "Heartbeat",
    "Rainbow", "Comet", "Solar Flare", "Ripple", "Segment",
    "Liquid", "Aurora", "Matrix", "Firefly", "Prism",
    "Galaxy", "Radar", "Energy Core", "Spiral", "Minimal"
)

private data class Scene(val w: Float, val h: Float, val cx: Float, val cy: Float, val s: Float)

/**
 * Full-screen, fully transparent charging animation. Every style below uses its own drawing
 * routine so no two looks share the same silhouette, motion or palette.
 */
internal class ChargingAnimationView(
    context: Context,
    initialPercent: Float,
    private val style: String
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var phase = 0f
    private var percent = initialPercent.coerceIn(0f, 100f)

    private val frame = object : Runnable {
        override fun run() {
            phase = (phase + 0.011f) % 1f
            invalidate()
            handler.postDelayed(this, 33L)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        handler.post(frame)
    }

    fun updatePercent(value: Float) {
        percent = value.coerceIn(0f, 100f)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val scene = Scene(w, h, w / 2f, h / 2f, minOf(w, h))
        when (style) {
            "Liquid Glass" -> drawLiquidGlass(canvas, scene)
            "Plasma Storm" -> drawPlasmaStorm(canvas, scene)
            "Pulse" -> drawPulse(canvas, scene)
            "Flow" -> drawFlow(canvas, scene)
            "Glow" -> drawGlow(canvas, scene)
            "Neon Ring" -> drawNeonRing(canvas, scene)
            "Dual Orbit" -> drawDualOrbit(canvas, scene)
            "Spark" -> drawSpark(canvas, scene)
            "Wave" -> drawWave(canvas, scene)
            "Heartbeat" -> drawHeartbeat(canvas, scene)
            "Rainbow" -> drawRainbow(canvas, scene)
            "Comet" -> drawComet(canvas, scene)
            "Solar Flare" -> drawSolarFlare(canvas, scene)
            "Ripple" -> drawRipple(canvas, scene)
            "Segment" -> drawSegment(canvas, scene)
            "Liquid" -> drawLiquidDrops(canvas, scene)
            "Aurora" -> drawAurora(canvas, scene)
            "Matrix" -> drawMatrix(canvas, scene)
            "Firefly" -> drawFirefly(canvas, scene)
            "Prism" -> drawPrism(canvas, scene)
            "Galaxy" -> drawGalaxy(canvas, scene)
            "Radar" -> drawRadar(canvas, scene)
            "Energy Core" -> drawEnergyCore(canvas, scene)
            "Spiral" -> drawSpiral(canvas, scene)
            else -> drawMinimal(canvas, scene)
        }
    }

    private fun fill(color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun stroke(color: Int, width: Float) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun drawLiquidGlass(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val cellW = s * 0.34f
        val cellH = s * 0.58f
        val left = scene.cx - cellW / 2f
        val top = scene.cy - cellH / 2f
        val corner = s * 0.05f
        fill(Color.argb(140, 255, 255, 255))
        canvas.drawRoundRect(
            scene.cx - cellW * 0.14f, top - s * 0.035f,
            scene.cx + cellW * 0.14f, top, s * 0.02f, s * 0.02f, paint
        )
        fill(Color.argb(24, 255, 255, 255))
        canvas.drawRoundRect(left, top, left + cellW, top + cellH, corner, corner, paint)

        val inset = s * 0.022f
        val inner = RectF(left + inset, top + inset, left + cellW - inset, top + cellH - inset)
        val level = inner.bottom - inner.height() * (percent / 100f)
        val liquid = Path().apply {
            moveTo(inner.left, inner.bottom)
            lineTo(inner.left, level)
            val steps = 28
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                lineTo(inner.left + inner.width() * t, level + sinTurns(t * 3f + phase) * s * 0.012f)
            }
            lineTo(inner.right, inner.bottom)
            close()
        }
        canvas.save()
        canvas.clipRect(inner)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = LinearGradient(
            0f, level, 0f, inner.bottom,
            intArrayOf(Color.rgb(103, 232, 249), Color.rgb(139, 92, 246), Color.rgb(192, 38, 211)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(liquid, paint)
        paint.shader = null
        canvas.restore()

        stroke(Color.argb(210, 255, 255, 255), s * 0.007f)
        canvas.drawRoundRect(left, top, left + cellW, top + cellH, corner, corner, paint)
        fill(Color.argb(80, 255, 255, 255))
        repeat(5) { i ->
            val x = inner.left + inner.width() * (0.18f + 0.15f * i)
            val y = inner.bottom - inner.height() * (0.12f + 0.13f * i) + sinTurns(phase + i * 0.14f) * s * 0.012f
            canvas.drawCircle(x, y, s * (0.005f + 0.002f * (i % 3)), paint)
        }
    }

    private fun drawPlasmaStorm(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * 0.30f
        stroke(Color.argb(55, 255, 255, 255), s * 0.022f)
        canvas.drawCircle(scene.cx, scene.cy, r, paint)
        stroke(Color.rgb(34, 211, 238), s * 0.022f)
        canvas.drawArc(scene.cx - r, scene.cy - r, scene.cx + r, scene.cy + r, -90f, 360f * percent / 100f, false, paint)
        repeat(8) { i ->
            val base = phase + i / 8f
            val bolt = Path()
            for (segment in 0..8) {
                val t = segment / 8f
                val angle = base + (t - 0.5f) * 0.10f
                val jitter = (0.5f + 0.5f * sinTurns(phase * 8f + i + segment)) * s * 0.03f
                val radius = r * (0.55f + 0.62f * t) + if (segment % 2 == 0) jitter else -jitter
                val x = scene.cx + cosTurns(angle) * radius
                val y = scene.cy + sinTurns(angle) * radius
                if (segment == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y)
            }
            stroke(Color.argb(170, 165, 243, 252), s * 0.004f)
            canvas.drawPath(bolt, paint)
        }
        fill(Color.argb(150, 224, 242, 254))
        canvas.drawCircle(scene.cx, scene.cy, s * 0.012f * wave(phase), paint)
    }

    private fun drawPulse(canvas: Canvas, scene: Scene) {
        val s = scene.s
        repeat(3) { i ->
            val t = (phase + i / 3f) % 1f
            val radius = s * (0.10f + 0.34f * t)
            val alpha = ((1f - t) * 85).toInt().coerceIn(0, 255)
            stroke(Color.argb(alpha, 117, 255, 177), s * (0.014f * (1f - t) + 0.002f))
            canvas.drawCircle(scene.cx, scene.cy, radius, paint)
        }
        fill(Color.argb(45, 117, 255, 177))
        canvas.drawCircle(scene.cx, scene.cy, s * (0.10f + 0.02f * wave(phase)), paint)
    }

    private fun drawFlow(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * 0.26f
        stroke(Color.argb(36, 255, 255, 255), s * 0.0015f)
        canvas.drawCircle(scene.cx, scene.cy, r, paint)
        repeat(3) { orbit ->
            val head = phase + orbit / 3f
            repeat(20) { tail ->
                val t = tail / 20f
                val angle = head - t * 0.16f
                val alpha = ((1f - t) * 210).toInt().coerceIn(0, 255)
                fill(Color.argb(alpha, 103, 232, 249))
                canvas.drawCircle(
                    scene.cx + cosTurns(angle) * r,
                    scene.cy + sinTurns(angle) * r,
                    s * (0.009f * (1f - t) + 0.0015f),
                    paint
                )
            }
            fill(Color.WHITE)
            canvas.drawCircle(scene.cx + cosTurns(head) * r, scene.cy + sinTurns(head) * r, s * 0.010f, paint)
        }
    }

    private fun drawGlow(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * (0.28f + 0.05f * wave(phase))
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = RadialGradient(
            scene.cx, scene.cy, r,
            intArrayOf(Color.argb(150, 117, 255, 177), Color.argb(70, 74, 222, 128), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(scene.cx, scene.cy, r, paint)
        paint.shader = null
    }

    private fun drawNeonRing(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * 0.28f
        stroke(Color.argb(30, 255, 255, 255), s * 0.030f)
        canvas.drawCircle(scene.cx, scene.cy, r, paint)
        stroke(Color.argb(80, 34, 211, 238), s * 0.036f)
        canvas.drawCircle(scene.cx, scene.cy, r, paint)
        val start = -90f + phase * 360f
        stroke(Color.rgb(34, 211, 238), s * 0.030f)
        canvas.drawArc(scene.cx - r, scene.cy - r, scene.cx + r, scene.cy + r, start, 110f, false, paint)
        stroke(Color.WHITE, s * 0.010f)
        canvas.drawArc(scene.cx - r, scene.cy - r, scene.cx + r, scene.cy + r, start, 110f, false, paint)
    }

    private fun drawDualOrbit(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r1 = s * 0.22f
        val r2 = s * 0.32f
        stroke(Color.argb(28, 255, 255, 255), s * 0.0015f)
        canvas.drawCircle(scene.cx, scene.cy, r1, paint)
        canvas.drawCircle(scene.cx, scene.cy, r2, paint)
        val a1 = phase
        val a2 = -phase * 1.6f
        val x1 = scene.cx + cosTurns(a1) * r1
        val y1 = scene.cy + sinTurns(a1) * r1
        val x2 = scene.cx + cosTurns(a2) * r2
        val y2 = scene.cy + sinTurns(a2) * r2
        stroke(Color.argb(70, 255, 255, 255), s * 0.002f)
        canvas.drawLine(x1, y1, x2, y2, paint)
        fill(Color.argb(220, 103, 232, 249))
        canvas.drawCircle(x1, y1, s * 0.016f, paint)
        fill(Color.argb(220, 196, 181, 253))
        canvas.drawCircle(x2, y2, s * 0.012f, paint)
    }

    private fun drawSpark(canvas: Canvas, scene: Scene) {
        val s = scene.s
        repeat(20) { i ->
            val angle = i / 20f + phase * 0.15f
            val flicker = 0.35f + 0.65f * wave(phase * 3f + i * 0.13f)
            val inner = s * 0.13f
            val outer = inner + s * (0.06f + 0.13f * flicker)
            val alpha = (70 + 140 * flicker).toInt().coerceIn(0, 255)
            stroke(Color.argb(alpha, 165, 243, 252), s * 0.0045f)
            canvas.drawLine(
                scene.cx + cosTurns(angle) * inner, scene.cy + sinTurns(angle) * inner,
                scene.cx + cosTurns(angle) * outer, scene.cy + sinTurns(angle) * outer,
                paint
            )
        }
        fill(Color.argb(150, 255, 255, 255))
        canvas.drawCircle(scene.cx, scene.cy, s * 0.010f, paint)
    }

    private fun drawWave(canvas: Canvas, scene: Scene) {
        val s = scene.s
        repeat(5) { row ->
            val baseY = scene.cy + (row - 2) * s * 0.07f
            val path = Path()
            val steps = 64
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = scene.w * t
                val y = baseY + sinTurns(t * 1.5f + phase + row * 0.12f) * s * 0.035f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(Color.argb(70 + row * 22, 103, 232, 249), s * (0.003f + row * 0.0004f))
            canvas.drawPath(path, paint)
        }
    }

    private fun drawHeartbeat(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val baseY = scene.cy
        val path = Path()
        val steps = 240
        fun ecg(u: Float): Float = when {
            u < 0.24f -> 0f
            u < 0.32f -> -s * 0.05f * ((u - 0.24f) / 0.08f)
            u < 0.39f -> -s * 0.05f + s * 0.16f * ((u - 0.32f) / 0.07f)
            u < 0.46f -> s * 0.11f - s * 0.17f * ((u - 0.39f) / 0.07f)
            u < 0.55f -> -s * 0.06f + s * 0.06f * ((u - 0.46f) / 0.09f)
            else -> 0f
        }
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = scene.w * t
            val u = (t * 5f + phase * 2f) % 1f
            val y = baseY + ecg(u)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        stroke(Color.argb(140, 117, 255, 177), s * 0.004f)
        canvas.drawPath(path, paint)
        val dotT = (phase * 0.6f) % 1f
        val dotU = (dotT * 5f + phase * 2f) % 1f
        val dotX = scene.w * dotT
        val dotY = baseY + ecg(dotU)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = RadialGradient(
            dotX, dotY, s * 0.035f,
            intArrayOf(Color.argb(150, 117, 255, 177), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(dotX, dotY, s * 0.035f, paint)
        paint.shader = null
        fill(Color.WHITE)
        canvas.drawCircle(dotX, dotY, s * 0.008f, paint)
    }

    private fun drawRainbow(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val palette = intArrayOf(
            Color.rgb(248, 113, 113), Color.rgb(251, 191, 36), Color.rgb(74, 222, 128),
            Color.rgb(34, 211, 238), Color.rgb(96, 165, 250), Color.rgb(192, 132, 252)
        )
        repeat(6) { i ->
            val r = s * (0.14f + i * 0.038f)
            val direction = if (i % 2 == 0) 1f else -1f
            val start = -90f + phase * 360f * direction + i * 18f
            stroke(
                Color.argb(190 - i * 18, Color.red(palette[i]), Color.green(palette[i]), Color.blue(palette[i])),
                s * 0.010f
            )
            canvas.drawArc(scene.cx - r, scene.cy - r, scene.cx + r, scene.cy + r, start, 250f, false, paint)
        }
    }

    private fun drawComet(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val rx = s * 0.36f
        val ry = s * 0.24f
        val head = phase
        repeat(22) { i ->
            val t = i / 22f
            val angle = head - t * 0.18f
            val alpha = ((1f - t) * 200).toInt().coerceIn(0, 255)
            fill(Color.argb(alpha, 103, 232, 249))
            canvas.drawCircle(
                scene.cx + cosTurns(angle) * rx,
                scene.cy + sinTurns(angle) * ry,
                s * (0.010f * (1f - t) + 0.001f),
                paint
            )
        }
        fill(Color.argb(230, 255, 255, 255))
        canvas.drawCircle(scene.cx + cosTurns(head) * rx, scene.cy + sinTurns(head) * ry, s * 0.014f, paint)
    }

    private fun drawSolarFlare(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val core = s * (0.09f + 0.01f * wave(phase))
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = RadialGradient(
            scene.cx, scene.cy, core * 2.6f,
            intArrayOf(Color.argb(160, 251, 191, 36), Color.argb(60, 245, 158, 11), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(scene.cx, scene.cy, core * 2.6f, paint)
        paint.shader = null
        fill(Color.argb(200, 253, 224, 71))
        canvas.drawCircle(scene.cx, scene.cy, core, paint)
        repeat(24) { i ->
            val angle = i / 24f + phase * 0.25f
            val length = core * 1.6f + s * (0.05f + 0.09f * wave(phase * 2f + i * 0.09f))
            stroke(Color.argb(160, 251, 191, 36), s * 0.004f)
            canvas.drawLine(
                scene.cx + cosTurns(angle) * core * 1.3f, scene.cy + sinTurns(angle) * core * 1.3f,
                scene.cx + cosTurns(angle) * length, scene.cy + sinTurns(angle) * length,
                paint
            )
        }
    }

    private fun drawRipple(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val baseY = scene.h * 0.72f
        repeat(4) { i ->
            val t = (phase * 0.8f + i / 4f) % 1f
            val rx = s * (0.10f + 0.42f * t)
            val ry = rx * 0.32f
            val alpha = ((1f - t) * 130).toInt().coerceIn(0, 255)
            stroke(Color.argb(alpha, 103, 232, 249), s * (0.005f * (1f - t) + 0.001f))
            canvas.drawOval(scene.cx - rx, baseY - ry, scene.cx + rx, baseY + ry, paint)
        }
        fill(Color.argb(160, 165, 243, 252))
        canvas.drawCircle(scene.cx, baseY, s * 0.008f, paint)
    }

    private fun drawSegment(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * 0.27f
        val count = 28
        val active = (count * percent / 100f).toInt()
        repeat(count) { i ->
            val start = i * 360f / count - 90f + phase * 8f
            val lit = i < active
            stroke(
                if (lit) Color.argb(230, 117, 255, 177) else Color.argb(40, 255, 255, 255),
                s * 0.013f
            )
            canvas.drawArc(scene.cx - r, scene.cy - r, scene.cx + r, scene.cy + r, start, 7.5f, false, paint)
        }
    }

    private fun drawLiquidDrops(canvas: Canvas, scene: Scene) {
        val s = scene.s
        repeat(10) { i ->
            val seed = ((i * 37) % 100) / 100f
            val t = (phase * 0.9f + seed) % 1f
            val x = scene.w * ((seed + 0.012f * sinTurns(phase + i * 0.1f)).coerceIn(0f, 1f)).coerceIn(0.06f, 0.94f)
            val y = scene.h * 0.10f + t * scene.h * 0.55f
            stroke(Color.argb(((1f - t) * 200).toInt().coerceIn(0, 255), 103, 232, 249), s * 0.006f)
            canvas.drawLine(x, y - s * 0.05f, x, y, paint)
            fill(Color.argb(220, 165, 243, 252))
            canvas.drawCircle(x, y, s * 0.0045f, paint)
        }
    }

    private fun drawAurora(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val colors = intArrayOf(
            Color.argb(90, 74, 222, 128), Color.argb(80, 34, 211, 238),
            Color.argb(70, 139, 92, 246), Color.argb(60, 236, 72, 153)
        )
        repeat(4) { band ->
            val path = Path()
            val baseY = scene.cy + (band - 1.5f) * s * 0.10f
            val steps = 48
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = scene.w * t
                val y = baseY + sinTurns(t * 1.2f + phase + band * 0.11f) * s * 0.05f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(colors[band], s * 0.022f)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawMatrix(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val columns = 18
        repeat(columns) { i ->
            val colX = scene.w * (i + 0.5f) / columns
            val speed = 0.35f + (i % 5) * 0.09f
            val head = (phase * speed * 2f + i * 0.137f) % 1f
            repeat(9) { k ->
                val y = head * scene.h - k * s * 0.028f
                if (y >= 0f) {
                    val alpha = ((1f - k / 9f) * 200).toInt().coerceIn(0, 255)
                    fill(Color.argb(alpha, 134, 239, 172))
                    canvas.drawRect(colX - s * 0.003f, y - s * 0.009f, colX + s * 0.003f, y, paint)
                }
            }
        }
    }

    private fun drawFirefly(canvas: Canvas, scene: Scene) {
        val s = scene.s
        repeat(16) { i ->
            val seed = (i * 0.618f) % 1f
            val seed2 = (i * 0.414f) % 1f
            val x = scene.w * (0.06f + 0.88f * (seed + 0.05f * sinTurns(phase * (0.5f + seed2) + i * 0.03f)).coerceIn(0f, 1f))
            val y = scene.h * (0.10f + 0.80f * (seed2 + 0.05f * cosTurns(phase * (0.4f + seed) + i * 0.05f)).coerceIn(0f, 1f))
            val flicker = 0.35f + 0.65f * wave(phase * 3f + i * 0.23f)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.shader = RadialGradient(
                x, y, s * 0.022f,
                intArrayOf(Color.argb((110 * flicker).toInt().coerceIn(0, 255), 253, 230, 138), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, s * 0.022f, paint)
            paint.shader = null
            fill(Color.argb((220 * flicker).toInt().coerceIn(0, 255), 253, 230, 138))
            canvas.drawCircle(x, y, s * 0.005f, paint)
        }
    }

    private fun drawPrism(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * 0.23f
        val rotation = phase * 0.5f
        val triangle = Path()
        repeat(3) { i ->
            val angle = rotation + i / 3f
            val x = scene.cx + cosTurns(angle) * r
            val y = scene.cy + sinTurns(angle) * r
            if (i == 0) triangle.moveTo(x, y) else triangle.lineTo(x, y)
        }
        triangle.close()
        stroke(Color.argb(200, 196, 181, 253), s * 0.007f)
        canvas.drawPath(triangle, paint)
        val beamColors = intArrayOf(
            Color.argb(140, 103, 232, 249), Color.argb(140, 244, 114, 182), Color.argb(140, 253, 224, 71)
        )
        repeat(3) { i ->
            val angle = rotation + i / 3f + 1f / 6f
            stroke(beamColors[i], s * 0.010f)
            canvas.drawLine(
                scene.cx, scene.cy,
                scene.cx + cosTurns(angle) * s * 0.38f, scene.cy + sinTurns(angle) * s * 0.38f,
                paint
            )
        }
        fill(Color.argb(200, 255, 255, 255))
        canvas.drawCircle(scene.cx, scene.cy, s * 0.012f, paint)
    }

    private fun drawGalaxy(canvas: Canvas, scene: Scene) {
        val s = scene.s
        repeat(4) { arm ->
            repeat(38) { i ->
                val t = i / 38f
                val angle = arm / 4f + t * 0.55f + phase * 0.5f
                val radius = s * (0.05f + 0.33f * t)
                val alpha = ((1f - t * 0.8f) * 190).toInt().coerceIn(0, 255)
                fill(Color.argb(alpha, if (arm % 2 == 0) 196 else 103, 181, 253))
                canvas.drawCircle(
                    scene.cx + cosTurns(angle) * radius,
                    scene.cy + sinTurns(angle) * radius,
                    s * (0.005f * (1.1f - t) + 0.001f),
                    paint
                )
            }
        }
        fill(Color.argb(200, 255, 255, 255))
        canvas.drawCircle(scene.cx, scene.cy, s * 0.012f, paint)
    }

    private fun drawRadar(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * 0.30f
        repeat(3) { i ->
            stroke(Color.argb(40, 117, 255, 177), s * 0.0015f)
            canvas.drawCircle(scene.cx, scene.cy, r * (i + 1) / 3f, paint)
        }
        stroke(Color.argb(50, 117, 255, 177), s * 0.0015f)
        canvas.drawLine(scene.cx - r, scene.cy, scene.cx + r, scene.cy, paint)
        canvas.drawLine(scene.cx, scene.cy - r, scene.cx, scene.cy + r, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = SweepGradient(
            scene.cx, scene.cy,
            intArrayOf(Color.TRANSPARENT, Color.argb(35, 117, 255, 177), Color.argb(120, 117, 255, 177)),
            floatArrayOf(0f, 0.06f, 0.16f)
        )
        canvas.save()
        canvas.rotate(phase * 360f, scene.cx, scene.cy)
        canvas.drawCircle(scene.cx, scene.cy, r, paint)
        canvas.restore()
        paint.shader = null
        repeat(4) { i ->
            val angle = i * 0.27f
            val radius = r * (0.35f + 0.5f * ((i * 37 % 100) / 100f))
            val flicker = wave(phase * 2f + i * 0.12f)
            fill(Color.argb((200 * flicker).toInt().coerceIn(0, 255), 134, 239, 172))
            canvas.drawCircle(scene.cx + cosTurns(angle) * radius, scene.cy + sinTurns(angle) * radius, s * 0.006f, paint)
        }
    }

    private fun drawEnergyCore(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val core = s * (0.075f + 0.012f * wave(phase))
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = RadialGradient(
            scene.cx, scene.cy, core * 3.2f,
            intArrayOf(Color.argb(220, 165, 243, 252), Color.argb(90, 139, 92, 246), Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(scene.cx, scene.cy, core * 3.2f, paint)
        paint.shader = null
        fill(Color.argb(230, 224, 242, 254))
        canvas.drawCircle(scene.cx, scene.cy, core, paint)
        repeat(2) { ring ->
            val radius = s * (0.15f + ring * 0.06f) + s * 0.012f * wave(phase + ring * 0.2f)
            stroke(Color.argb(110, 139, 92, 246), s * 0.004f)
            canvas.drawCircle(scene.cx, scene.cy, radius, paint)
        }
        repeat(8) { i ->
            val direction = if (i % 2 == 0) 1f else -1f
            val angle = phase * direction + i / 8f
            val radius = s * (0.24f + 0.02f * (i % 3))
            fill(Color.argb(180, 103, 232, 249))
            canvas.drawCircle(scene.cx + cosTurns(angle) * radius, scene.cy + sinTurns(angle) * radius, s * 0.006f, paint)
        }
    }

    private fun drawSpiral(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val path = Path()
        val steps = 240
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val angle = t * 3.2f + phase
            val radius = s * (0.02f + 0.34f * t)
            val x = scene.cx + cosTurns(angle) * radius
            val y = scene.cy + sinTurns(angle) * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        stroke(Color.argb(170, 103, 232, 249), s * 0.004f)
        canvas.drawPath(path, paint)
        fill(Color.argb(220, 255, 255, 255))
        canvas.drawCircle(scene.cx, scene.cy, s * 0.010f, paint)
    }

    private fun drawMinimal(canvas: Canvas, scene: Scene) {
        val s = scene.s
        val r = s * 0.26f
        stroke(Color.argb(32, 255, 255, 255), s * 0.012f)
        canvas.drawCircle(scene.cx, scene.cy, r, paint)
        stroke(Color.argb(235, 255, 255, 255), s * 0.012f)
        canvas.drawArc(
            scene.cx - r, scene.cy - r, scene.cx + r, scene.cy + r,
            -90f, 360f * percent / 100f, false, paint
        )
        val end = (-90f + 360f * percent / 100f) / 360f
        fill(Color.argb(210, 255, 255, 255))
        canvas.drawCircle(scene.cx + cosTurns(end) * r, scene.cy + sinTurns(end) * r, s * 0.008f, paint)
    }
}

private fun sinTurns(turns: Float): Float = kotlin.math.sin(turns * Math.PI * 2.0).toFloat()

private fun cosTurns(turns: Float): Float = kotlin.math.cos(turns * Math.PI * 2.0).toFloat()

private fun wave(turns: Float): Float = 0.5f + 0.5f * sinTurns(turns)
