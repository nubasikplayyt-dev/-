package app.lingvo.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import app.lingvo.MainActivity
import app.lingvo.notify.Notifications
import kotlin.math.abs

/**
 * Плавающая капсула поверх других приложений (TYPE_APPLICATION_OVERLAY).
 * Требует разрешения «Поверх других приложений», которое пользователь включает сам.
 * Показ — только пока идёт сессия и приложение свёрнуто; без фонового сервиса.
 * Нажатие — открыть тренировку, перетаскивание — переместить, долгое нажатие — скрыть.
 */
class CapsuleOverlay(private val context: Context) {
    data class Model(
        val sessionId: Long,
        val language: String,
        val label: String,
        val progress: Float,
        val animate: Boolean,
        val streak: Int = 0,
        /** Цвета выбранного существа — капсула окрашивается в них. */
        val colorA: Int = 0xFF5B3FD1.toInt(),
        val colorB: Int = 0xFF2F6BFF.toInt(),
    )

    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: CapsuleView? = null
    private var dismissedSessionId: Long? = null
    private var params: WindowManager.LayoutParams? = null

    fun show(model: Model) {
        if (dismissedSessionId == model.sessionId) return
        if (!IslandController.canDrawOverlays(context)) return
        val v = view ?: createView().also { view = it }
        v.update(model)
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    fun dismissUntilNextSession() {
        dismissedSessionId = view?.model?.sessionId
        hide()
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): CapsuleView {
        val v = CapsuleView(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Ниже строки состояния и выреза камеры — системные элементы не перекрываются.
            y = statusBarHeight() + dp(8f).toInt()
        }
        params = lp
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val longPress = Runnable { if (!moved) dismissUntilNextSession() }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                    v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        v.removeCallbacks(longPress)
                        lp.x = startX + dx.toInt()
                        lp.y = (startY + dy.toInt()).coerceIn(statusBarHeight(), context.resources.displayMetrics.heightPixels - v.height - navBarReserve())
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (!moved) openSession(v.model)
                }
                MotionEvent.ACTION_CANCEL -> v.removeCallbacks(longPress)
            }
            true
        }
        v.contentDescription = "Активная тренировка. Нажмите, чтобы продолжить"
        wm.addView(v, lp)
        return v
    }

    private fun openSession(model: Model?) {
        val route = model?.let { "review/${it.language}" } ?: "home"
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Notifications.EXTRA_OPEN, route)
        )
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24f).toInt()
    }

    private fun navBarReserve(): Int = dp(64f).toInt()

    /** Отрисовка капсулы вручную: тёмная/светлая тема, прогресс-полоса, анимация. */
    class CapsuleView(context: Context) : View(context) {
        var model: Model? = null
            private set
        private var shownProgress = 0f
        private var animator: ValueAnimator? = null
        private val density = resources.displayMetrics.density
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val track = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * resources.configuration.fontScale.coerceIn(1f, 1.3f) * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val rect = RectF()

        private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun applyTheme() {
            val m = model
            // Яркий градиент в цветах существа-спутника; текст белый в любой теме.
            bg.shader = android.graphics.LinearGradient(
                0f, 0f, width.coerceAtLeast(1).toFloat(), 0f,
                intArrayOf(0xFF2B2F6B.toInt(), m?.colorA ?: 0xFF5B3FD1.toInt(), m?.colorB ?: 0xFF2F6BFF.toInt()),
                null, android.graphics.Shader.TileMode.CLAMP,
            )
            text.color = 0xFFFFFFFF.toInt()
            track.color = 0x44FFFFFF
            bar.color = 0xFFFFD166.toInt()
            dot.color = 0xFFFFD166.toInt()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            applyTheme()
        }

        fun update(m: Model) {
            val previous = model
            model = m
            applyTheme()
            animator?.cancel()
            if (m.animate && previous != null) {
                animator = ValueAnimator.ofFloat(shownProgress, m.progress).apply {
                    duration = 350
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { shownProgress = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else {
                shownProgress = m.progress
            }
            requestLayout()
            invalidate()
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            applyTheme(); requestLayout(); invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val label = model?.let { it.label + if (it.streak > 0) "  🔥${it.streak}" else "" }.orEmpty()
            val w = (text.measureText(label) + 36 * density).toInt().coerceAtLeast((120 * density).toInt())
            val h = (text.textSize + 26 * density).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            val m = model ?: return
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            val r = height / 2f
            canvas.drawRoundRect(rect, r, r, bg)
            val baseline = height / 2f - (text.descent() + text.ascent()) / 2f - 2 * density
            canvas.drawText(m.label + if (m.streak > 0) "  🔥${m.streak}" else "", 18 * density, baseline, text)
            val barTop = height - 7 * density
            val left = 18 * density
            val right = width - 18 * density
            rect.set(left, barTop, right, barTop + 3 * density)
            canvas.drawRoundRect(rect, 2 * density, 2 * density, track)
            rect.set(left, barTop, left + (right - left) * shownProgress.coerceIn(0f, 1f), barTop + 3 * density)
            canvas.drawRoundRect(rect, 2 * density, 2 * density, bar)
        }
    }
}
