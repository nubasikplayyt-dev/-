package app.lingvo.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
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
 * Плавающая капсула поверх других приложений (TYPE_APPLICATION_OVERLAY) — интерактивный остров:
 *  - свёрнута: существо, текущее слово, прогресс;
 *  - нажатие — раскрывается: перевод и кнопки «Не помню» / «Помню» (ответ сохраняется сразу);
 *  - нажатие на заголовок раскрытой капсулы — открыть занятие в приложении;
 *  - перетаскивание — переместить, долгое нажатие — скрыть до следующего занятия.
 * Требует разрешения «Поверх других приложений», которое пользователь включает сам.
 * Показывается только пока идёт занятие и приложение свёрнуто; без фонового сервиса.
 */
class CapsuleOverlay(
    private val context: Context,
    private val onAnswer: (sessionId: Long, entryId: String, remembered: Boolean) -> Unit,
) {
    data class Model(
        val sessionId: Long,
        val entryId: String,
        val language: String,
        val word: String,
        val translation: String,
        val progressLabel: String,
        val progress: Float,
        val animate: Boolean,
        val creature: Bitmap?,
        val answers: Boolean,
        /** Цвет существа-спутника — им окрашены прогресс и акценты. */
        val colorA: Int,
    )

    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: CapsuleView? = null
    private var dismissedSessionId: Long? = null

    fun show(model: Model) {
        if (dismissedSessionId == model.sessionId) return
        if (!IslandController.canDrawOverlays(context)) return
        val v = view ?: runCatching { createView() }.getOrNull()?.also { view = it } ?: return
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
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false; var longFired = false
        val longPress = Runnable { if (!moved) { longFired = true; dismissUntilNextSession() } }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; longFired = false
                    v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        v.removeCallbacks(longPress)
                        lp.x = startX + dx.toInt()
                        lp.y = (startY + dy.toInt()).coerceIn(statusBarHeight(), context.resources.displayMetrics.heightPixels - v.height - dp(64f).toInt())
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (!moved && !longFired) handleTap(v, e.x, e.y)
                }
                MotionEvent.ACTION_CANCEL -> v.removeCallbacks(longPress)
            }
            true
        }
        v.contentDescription = "Остров занятия. Нажмите, чтобы ответить"
        wm.addView(v, lp)
        return v
    }

    private fun handleTap(v: CapsuleView, x: Float, y: Float) {
        val m = v.model ?: return
        when (v.hit(x, y)) {
            CapsuleView.Hit.FORGOT -> { v.collapse(); onAnswer(m.sessionId, m.entryId, false) }
            CapsuleView.Hit.REMEMBERED -> { v.collapse(); onAnswer(m.sessionId, m.entryId, true) }
            CapsuleView.Hit.HEADER -> if (v.expanded || !m.answers) openSession(m) else v.expand()
            CapsuleView.Hit.NONE -> v.collapse()
        }
    }

    private fun openSession(model: Model) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Notifications.EXTRA_OPEN, "review/${model.language}")
        )
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24f).toInt()
    }

    /** Капсула рисуется вручную: тёмный «остров», существо слева, слово, прогресс, кнопки ответа. */
    class CapsuleView(context: Context) : View(context) {
        enum class Hit { HEADER, FORGOT, REMEMBERED, NONE }

        var model: Model? = null
            private set
        var expanded = false
            private set
        private var shownProgress = 0f
        private var animator: ValueAnimator? = null
        private val d = resources.displayMetrics.density
        private val scale = resources.configuration.fontScale.coerceIn(1f, 1.3f)
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2141320.toInt() }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val btn = Paint(Paint.ANTI_ALIAS_FLAG)
        private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale * d; color = 0xFFFFFFFF.toInt(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f * scale * d; color = 0xCCFFFFFF.toInt() }
        private val btnText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * scale * d; color = 0xFFFFFFFF.toInt(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val rect = RectF()
        private val forgotRect = RectF()
        private val rememberedRect = RectF()
        private val headerH get() = 56 * d * scale
        private val bodyH get() = 104 * d * scale

        fun expand() { expanded = true; contentDescription = "Перевод: ${model?.translation}. Кнопки «Не помню» и «Помню»"; requestLayout(); invalidate() }
        fun collapse() { if (expanded) { expanded = false; contentDescription = "Остров занятия. Нажмите, чтобы ответить"; requestLayout(); invalidate() } }

        fun hit(x: Float, y: Float): Hit = when {
            expanded && forgotRect.contains(x, y) -> Hit.FORGOT
            expanded && rememberedRect.contains(x, y) -> Hit.REMEMBERED
            y <= headerH -> Hit.HEADER
            else -> Hit.NONE
        }

        fun update(m: Model) {
            val previous = model
            model = m
            // Новая карточка — капсула снова свёрнута, перевод скрыт.
            if (previous != null && previous.entryId != m.entryId) expanded = false
            bar.color = m.colorA
            animator?.cancel()
            if (m.animate && previous != null) {
                animator = ValueAnimator.ofFloat(shownProgress, m.progress).apply {
                    duration = 350
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { shownProgress = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else shownProgress = m.progress
            requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = (300 * d).toInt().coerceAtMost(resources.displayMetrics.widthPixels - (24 * d).toInt())
            val h = (headerH + if (expanded) bodyH else 0f).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            val m = model ?: return
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            val r = if (expanded) 28 * d else height / 2f
            canvas.drawRoundRect(rect, r, r, bg)
            // Существо слева.
            val icon = 44 * d
            val top = (headerH - icon) / 2
            m.creature?.let { canvas.drawBitmap(it, null, RectF(8 * d, top, 8 * d + icon, top + icon), null) }
            val textLeft = 8 * d + icon + 10 * d
            val textRight = width - 16 * d
            val word = TextUtils.ellipsize(m.word, title, textRight - textLeft, TextUtils.TruncateAt.END).toString()
            canvas.drawText(word, textLeft, headerH / 2 - 2 * d, title)
            val sub = if (expanded) m.progressLabel + " · нажмите сюда, чтобы открыть" else m.progressLabel + if (m.answers) " · нажмите, чтобы ответить" else ""
            canvas.drawText(TextUtils.ellipsize(sub, small, textRight - textLeft, TextUtils.TruncateAt.END).toString(),
                textLeft, headerH / 2 + small.textSize + 1 * d, small)
            // Прогресс.
            val barTop = headerH - 6 * d
            rect.set(textLeft, barTop, textRight, barTop + 3 * d)
            canvas.drawRoundRect(rect, 2 * d, 2 * d, track)
            rect.set(textLeft, barTop, textLeft + (textRight - textLeft) * shownProgress.coerceIn(0f, 1f), barTop + 3 * d)
            canvas.drawRoundRect(rect, 2 * d, 2 * d, bar)
            if (!expanded) return
            // Перевод и кнопки.
            val tr = TextUtils.ellipsize(m.translation, title, width - 32 * d, TextUtils.TruncateAt.END).toString()
            canvas.drawText(tr, 16 * d, headerH + 24 * d * scale, title)
            val bTop = headerH + 40 * d * scale
            val bBottom = height - 12 * d
            val mid = width / 2f
            forgotRect.set(12 * d, bTop, mid - 6 * d, bBottom)
            rememberedRect.set(mid + 6 * d, bTop, width - 12 * d, bBottom)
            val cy = (bTop + bBottom) / 2 - (btnText.descent() + btnText.ascent()) / 2
            btn.color = 0xFFFF6B6B.toInt(); canvas.drawRoundRect(forgotRect, 18 * d, 18 * d, btn)
            canvas.drawText("Не помню", forgotRect.centerX(), cy, btnText)
            btn.color = 0xFF2FBF71.toInt(); canvas.drawRoundRect(rememberedRect, 18 * d, 18 * d, btn)
            canvas.drawText("Помню", rememberedRect.centerX(), cy, btnText)
        }
    }
}
