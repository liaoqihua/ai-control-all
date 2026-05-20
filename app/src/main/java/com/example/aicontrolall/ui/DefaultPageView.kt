package com.example.aicontrolall.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class DefaultPageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planetGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var planetRadius = 0f
    private var planetCx = 0f
    private var planetCy = 0f
    private var breatheScale = 1f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#1a3344")
    }

    private data class Note(val emoji: String, val color: Int, val angle: Float, val bobOffset: Float)
    private val notes = listOf(
        Note("\u266A", Color.parseColor("#00D4FF"), 0f, 0f),
        Note("\u266B", Color.parseColor("#06D6A0"), PI.toFloat()/4, 0.3f),
        Note("\u2669", Color.parseColor("#8899aa"), PI.toFloat()/2, 0.6f),
        Note("\u266C", Color.parseColor("#00D4FF"), 3*PI.toFloat()/4, 0.9f),
        Note("\u266A", Color.parseColor("#06D6A0"), PI.toFloat(), 1.2f),
        Note("\u266B", Color.parseColor("#8899aa"), 5*PI.toFloat()/4, 0.2f),
        Note("\u2669", Color.parseColor("#00D4FF"), 3*PI.toFloat()/2, 0.5f),
        Note("\u266C", Color.parseColor("#06D6A0"), 7*PI.toFloat()/4, 1.5f)
    )
    private val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; textAlign = Paint.Align.CENTER }
    private var orbitAngle = 0f

    private data class MiniPlanet(val icon: String, val label: String, val color: Int, val baseX: Float, val baseY: Float, val isOnline: Boolean)
    private val miniPlanets = mutableListOf<MiniPlanet>()
    private val miniPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class Star(val x: Float, val y: Float, val r: Float, val alpha: Float, val phase: Float)
    private val stars = mutableListOf<Star>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val orbitAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 16000; repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { orbitAngle = it.animatedValue as Float; invalidate() }
    }
    private val breatheAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        addUpdateListener { breatheScale = 1f + 0.06f * (it.animatedValue as Float); invalidate() }
    }

    init {
        val rng = java.util.Random(42)
        repeat(15) { stars.add(Star(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 1.5f + 0.5f, rng.nextFloat() * 0.4f + 0.3f, rng.nextFloat() * PI.toFloat() * 2)) }
        val cx = 0.5f; val cy = 0.45f
        val positions = listOf(
            Triple(cx + 0.14f, cy - 0.22f, true), Triple(cx + 0.27f, cy - 0.08f, true),
            Triple(cx + 0.20f, cy + 0.20f, true), Triple(cx - 0.22f, cy + 0.22f, true),
            Triple(cx - 0.15f, cy - 0.18f, true), Triple(cx + 0.05f, cy - 0.28f, true),
            Triple(cx - 0.26f, cy + 0.02f, false), Triple(cx + 0.10f, cy + 0.08f, false)
        )
        val icons = listOf("\uD83D\uDCF7", "\uD83C\uDFA4", "\uD83D\uDD0A", "\uD83D\uDDA5", "\uD83C\uDF0E", "\u2699", "\uD83C\uDFA7", "\u231A")
        val labels = listOf("摄像", "麦克", "扬声", "屏幕", "定位", "传感", "耳机", "手表")
        val colors = listOf(Color.parseColor("#ff5e5e"), Color.parseColor("#00D4FF"), Color.parseColor("#ffaa00"), Color.parseColor("#00D4FF"), Color.parseColor("#06D6A0"), Color.parseColor("#8899aa"), Color.parseColor("#f0a030"), Color.parseColor("#f0a030"))
        for (i in positions.indices) {
            miniPlanets.add(MiniPlanet(icons[i], labels[i], colors[i], positions[i].first, positions[i].second, positions[i].third))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        planetRadius = min(w, h) * 0.16f; planetCx = w * 0.5f; planetCy = h * 0.45f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); orbitAnimator.start(); breatheAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow(); orbitAnimator.cancel(); breatheAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawColor(Color.parseColor("#0A0E17"))

        // Stars
        for (star in stars) {
            val a = (star.alpha * (0.6f + 0.4f * sin(System.currentTimeMillis() / 1000f * PI.toFloat() * 2 / 3f + star.phase))).coerceIn(0f, 1f)
            starPaint.alpha = (a * 255).toInt()
            canvas.drawCircle(star.x * w, star.y * h, star.r * 3f, starPaint)
        }

        // Glow ring
        val glowAlpha = (40 + 30 * sin(System.currentTimeMillis() / 2000f * PI.toFloat() * 2)).toInt().coerceIn(0, 255)
        planetGlowPaint.shader = RadialGradient(planetCx, planetCy, planetRadius * 1.15f, Color.argb(glowAlpha, 0, 212, 255), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawCircle(planetCx, planetCy, planetRadius * 1.15f, planetGlowPaint)

        // Planet body
        val br = planetRadius * breatheScale
        planetPaint.shader = RadialGradient(planetCx - br * 0.25f, planetCy - br * 0.3f, br,
            intArrayOf(Color.parseColor("#0a1a2a"), Color.parseColor("#162840"), Color.parseColor("#102030"), Color.parseColor("#060e18")),
            floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(planetCx, planetCy, br, planetPaint)

        // Decorative ring
        canvas.drawOval(planetCx - planetRadius * 1.45f, planetCy - planetRadius * 0.25f, planetCx + planetRadius * 1.45f, planetCy + planetRadius * 0.25f, ringPaint)

        // Orbiting notes
        val orbitRadiusX = planetRadius * 1.45f; val orbitRadiusY = planetRadius * 0.25f
        val orbitRad = Math.toRadians(orbitAngle.toDouble())
        for ((i, note) in notes.withIndex()) {
            val angle = note.angle + orbitRad.toFloat()
            val nx = planetCx + orbitRadiusX * cos(angle); val ny = planetCy + orbitRadiusY * sin(angle)
            val bob = sin((System.currentTimeMillis() / 1000f * PI.toFloat() * 2 / (2.5f + i * 0.3f)) + note.bobOffset * PI.toFloat()) * 12f
            noteTextPaint.color = note.color
            noteTextPaint.alpha = (180 + 75 * abs(sin(System.currentTimeMillis() / 1000f))).toInt()
            canvas.drawText(note.emoji, nx, ny + bob, noteTextPaint)
        }

        // Mini planets
        for ((i, mp) in miniPlanets.withIndex()) {
            val mx = mp.baseX * w; val my = mp.baseY * h
            val drift = sin(System.currentTimeMillis() / 1000f * PI.toFloat() * 2 / (6f + i) + i * 1.5f)
            val dx = drift * 6f; val dy = cos(drift * 2) * 5f
            val mr = planetRadius * 0.22f
            miniPaint.shader = RadialGradient(mx + dx, my + dy, mr, intArrayOf(Color.parseColor("#1a2535"), Color.parseColor("#0a1520")), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(mx + dx, my + dy, mr, miniPaint)
            miniPaint.shader = null
            miniPaint.color = if (mp.isOnline) Color.parseColor("#06D6A0") else Color.parseColor("#f0a030")
            miniPaint.alpha = if (mp.isOnline) 255 else 150
            canvas.drawCircle(mx + dx + mr * 0.3f, my + dy - mr * 0.5f, mr * 0.1f, miniPaint)
            noteTextPaint.color = Color.WHITE; noteTextPaint.alpha = if (mp.isOnline) 255 else 150
            noteTextPaint.textSize = mr * 0.8f
            canvas.drawText(mp.icon, mx + dx, my + dy + mr * 0.15f, noteTextPaint)
            noteTextPaint.textSize = mr * 0.35f; noteTextPaint.color = Color.parseColor("#5a6a80")
            canvas.drawText(mp.label, mx + dx, my + dy + mr * 0.6f, noteTextPaint)
        }

        // Voice wave
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4FF"); alpha = 100 }
        val barHeights = floatArrayOf(8f, 16f, 24f, 16f, 8f)
        val barX = w / 2 - 20f; val barY = h * 0.78f
        for (i in barHeights.indices) {
            val scale = 0.6f + 0.4f * sin(System.currentTimeMillis() / 300f * PI.toFloat() + i * 0.6f)
            canvas.drawRoundRect(barX + i * 8f, barY - barHeights[i] * scale / 2, barX + i * 8f + 4f, barY + barHeights[i] * scale / 2, 2f, 2f, barPaint)
        }

        // Status text
        noteTextPaint.textSize = 30f; noteTextPaint.color = Color.parseColor("#5a6a80")
        noteTextPaint.alpha = (150 + 80 * sin(System.currentTimeMillis() / 1500f)).toInt()
        canvas.drawText("正在聆听…", w / 2f, h * 0.85f, noteTextPaint)
    }
}
