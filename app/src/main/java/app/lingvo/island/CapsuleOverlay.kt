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
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.PathInterpolator
import app.lingvo.MainActivity
import app.lingvo.notify.Notifications
import kotlin.math.abs

/**
 * Остров поверх других приложений (TYPE_APPLICATION_OVERLAY) в стиле системного музыкального плеера.
 *
 * - Компактно: пилюля сверху по центру, поверх строки состояния — существо, курс, счётчик и тонкая полоса прогресса.
 * - Тап или свайп вниз по пилюле — плавно раскрывается в карточку на том же месте: вопрос и «Не помню» / «Помню».
 * - Свайп вверх, тап вне карточки или ответ — плавно сворачивается обратно.
 * - Перетаскивания нет: позиция окна фиксирована и никогда не меняется.
 * - Когда карточек больше нет — элемент плавно исчезает.
 * Требует разрешения «Поверх других приложений»; показывается, пока идёт занятие и приложение свёрнуто.
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
        val courseTitle: String,
        val done: Int,
        val total: Int,
        val animate: Boolean,
        val creature: Bitmap?,
        val answers: Boolean,
        /** Цвет существа-спутника — им окрашена полоса прогресса. */
        val colorA: Int,
    ) {
        val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: IslandView? = null
    /** Занятие, для которого пользователь закрыл остров кнопкой «×». */
    private var dismissedSessionId: Long? = null

    /** Показывает или обновляет остров. Новый остров всегда появляется свёрнутым. */
    fun show(model: Model) {
        if (!IslandController.canDrawOverlays(context)) return
        if (dismissedSessionId == model.sessionId) return
        val v = view ?: runCatching { createView() }.getOrNull()?.also { view = it } ?: return
        v.update(model)
    }

    /** Плавно скрывает остров (занятие закончилось или приложение открыто). */
    fun hide() {
        val v = view ?: return
        view = null
        v.fadeOut { runCatching { wm.removeView(v) } }
    }

    /** Кнопка «×» и долгое нажатие: остров не появляется до следующего занятия. */
    fun dismissUntilNextSession() {
        dismissedSessionId = view?.model?.sessionId
        hide()
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): IslandView {
        val v = IslandView(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // Касания вне острова уходят приложению под ним, а мы получаем ACTION_OUTSIDE — чтобы свернуться.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Фиксированная позиция: сверху по центру, поверх строки состояния (как «остров» у выреза камеры).
            x = 0
            y = dp(6f).toInt()
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val swipe = dp(24f)
        var downX = 0f; var downY = 0f; var gesture = false
        val longPress = Runnable { if (!gesture) dismissUntilNextSession() }
        // Только тап и вертикальный свайп; движения пальца НЕ двигают окно.
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; gesture = false
                    v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (abs(dy) > slop || abs(e.rawX - downX) > slop) v.removeCallbacks(longPress)
                    if (!gesture && dy < -swipe) { gesture = true; v.collapse() }
                    else if (!gesture && dy > swipe) { gesture = true; v.expand() }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    val moved = abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop
                    if (!gesture && !moved) handleTap(v, e.x, e.y)
                }
                MotionEvent.ACTION_CANCEL -> v.removeCallbacks(longPress)
            }
            true
        }
        v.contentDescription = "Остров занятия. Нажмите, чтобы ответить"
        wm.addView(v, lp)
        v.fadeIn()
        return v
    }

    private fun handleTap(v: IslandView, x: Float, y: Float) {
        val m = v.model ?: return
        when (v.hit(x, y)) {
            // Карточка остаётся раскрытой: сразу показывается следующее слово, можно отвечать дальше.
            IslandView.Hit.FORGOT -> { v.markAnswered(false); onAnswer(m.sessionId, m.entryId, false) }
            IslandView.Hit.REMEMBERED -> { v.markAnswered(true); onAnswer(m.sessionId, m.entryId, true) }
            IslandView.Hit.CLOSE -> dismissUntilNextSession()
            IslandView.Hit.COLLAPSE -> v.collapse()
            IslandView.Hit.OPEN -> openSession(m)
            IslandView.Hit.PILL -> if (m.answers) v.expand() else openSession(m)
            IslandView.Hit.NONE -> Unit
        }
    }

    private fun openSession(model: Model) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Notifications.EXTRA_OPEN, "review/${model.language}")
        )
    }

    /**
     * Остров рисуется вручную. Размер, скругление и содержимое зависят от одной доли раскрытия [t] (0..1),
     * которую плавно ведёт аниматор, — поэтому раскрытие и сворачивание согласованы, как у плеера.
     */
    class IslandView(context: Context) : View(context) {
        enum class Hit { PILL, OPEN, FORGOT, REMEMBERED, COLLAPSE, CLOSE, NONE }

        var model: Model? = null
            private set
        private var target = 0f
        private var t = 0f
        private var shownProgress = 0f
        private var expandAnim: ValueAnimator? = null
        private var progressAnim: ValueAnimator? = null
        private val d = resources.displayMetrics.density
        private val fs = resources.configuration.fontScale.coerceIn(1f, 1.3f)
        private val ease = PathInterpolator(0.2f, 0f, 0f, 1f) // «emphasized» из Material 3
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFA121118.toInt() }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val btn = Paint(Paint.ANTI_ALIAS_FLAG)
        private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f * fs * d; color = 0xFFFFFFFF.toInt(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val counter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * fs * d; color = 0xCCFFFFFF.toInt(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        private val question = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f * fs * d; color = 0xFFFFFFFF.toInt(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * fs * d; color = 0x99FFFFFF.toInt() }
        private val btnText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * fs * d; color = 0xFFFFFFFF.toInt(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt()
        }
        private val rect = RectF()
        private val forgotRect = RectF()
        private val rememberedRect = RectF()
        private val openRect = RectF()
        private val closeRect = RectF()
        private val collapseRect = RectF()
        private var answeredAt = 0L
        private var answeredRemembered = false

        // Размеры пилюли и карточки.
        private val pillW get() = 290 * d
        private val pillH get() = 38 * d * fs
        private val cardW get() = (360 * d).coerceAtMost(resources.displayMetrics.widthPixels - 16 * d)
        private val cardH get() = 196 * d * fs

        val expanded: Boolean get() = target == 1f

        fun expand() = animateTo(1f)
        fun collapse() = animateTo(0f)

        private fun animateTo(value: Float) {
            if (target == value) return
            target = value
            contentDescription = if (value == 1f) "Вопрос: ${model?.word}. Кнопки «Не помню» и «Помню». " +
                "Остаётся открытым: свернуть — свайп вверх или кнопка «‒», закрыть — кнопка «×»"
            else "Остров занятия. Нажмите, чтобы ответить"
            expandAnim?.cancel()
            if (model?.animate == false) { t = value; requestLayout(); invalidate(); return }
            expandAnim = ValueAnimator.ofFloat(t, value).apply {
                duration = 380
                interpolator = ease
                addUpdateListener { t = it.animatedValue as Float; requestLayout(); invalidate() }
                start()
            }
        }

        fun fadeIn() {
            alpha = 0f; scaleX = 0.85f; scaleY = 0.85f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(ease).start()
        }

        fun fadeOut(end: () -> Unit) {
            animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(260).setInterpolator(ease).withEndAction(end).start()
        }

        fun hit(x: Float, y: Float): Hit = when {
            t < 0.5f -> Hit.PILL
            closeRect.contains(x, y) -> Hit.CLOSE
            collapseRect.contains(x, y) -> Hit.COLLAPSE
            forgotRect.contains(x, y) -> Hit.FORGOT
            rememberedRect.contains(x, y) -> Hit.REMEMBERED
            openRect.contains(x, y) -> Hit.OPEN
            else -> Hit.NONE
        }

        /** Короткая отметка «ответ принят», пока не пришло следующее слово. */
        fun markAnswered(remembered: Boolean) {
            answeredAt = System.currentTimeMillis()
            answeredRemembered = remembered
            invalidate()
        }

        fun update(m: Model) {
            val previous = model
            model = m
            bar.color = m.colorA
            // После ответа приходит новая карточка: капсула остаётся раскрытой, меняется только содержимое.
            if (previous != null && previous.entryId != m.entryId) answeredAt = 0L
            progressAnim?.cancel()
            if (m.animate && previous != null && previous.progress != m.progress) {
                progressAnim = ValueAnimator.ofFloat(shownProgress, m.progress).apply {
                    duration = 420; interpolator = ease
                    addUpdateListener { shownProgress = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else shownProgress = m.progress
            requestLayout(); invalidate()
        }

        private fun lerp(a: Float, b: Float) = a + (b - a) * t

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(lerp(pillW, cardW).toInt(), lerp(pillH, cardH).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val m = model ?: return
            val w = width.toFloat()
            rect.set(0f, 0f, w, height.toFloat())
            val r = lerp(pillH / 2, 30 * d)
            canvas.drawRoundRect(rect, r, r, bg)

            // Шапка (общая для обоих состояний): чибик, курс, счётчик, тонкая полоса прогресса.
            val pad = lerp(5 * d, 14 * d)
            val icon = lerp(pillH - 10 * d, 40 * d)
            val top = lerp(5 * d, 12 * d)
            m.creature?.let { canvas.drawBitmap(it, null, RectF(pad, top, pad + icon, top + icon), null) }
            val left = pad + icon + 8 * d
            val right = w - lerp(12 * d, 16 * d)
            val count = "${m.done}/${m.total}"
            val baseline = top + icon / 2 - (title.descent() + title.ascent()) / 2 - 2 * d
            // В раскрытой карточке справа стоят кнопки «свернуть» и «закрыть» — счётчик сдвигается левее.
            val controls = lerp(0f, 74 * d)
            val countRight = right - controls
            canvas.drawText(count, countRight, baseline, counter)
            // В пилюле центр оставлен пустым — там вырез камеры: название языка и существо слева от него,
            // счётчик справа. В раскрытой карточке (ниже выреза) название занимает всю ширину.
            val cameraGap = 26 * d
            val titleRight = minOf(countRight - counter.measureText(count) - 8 * d, lerp(w / 2 - cameraGap, w))
            canvas.drawText(TextUtils.ellipsize(m.courseTitle, title, titleRight - left, TextUtils.TruncateAt.END).toString(),
                left, baseline, title)
            val barY = top + icon / 2 + lerp(8 * d, 12 * d)
            rect.set(left, barY, right, barY + 3 * d)
            canvas.drawRoundRect(rect, 1.5f * d, 1.5f * d, track)
            rect.set(left, barY, left + (right - left) * shownProgress.coerceIn(0f, 1f), barY + 3 * d)
            canvas.drawRoundRect(rect, 1.5f * d, 1.5f * d, bar)
            openRect.set(0f, 0f, w, top + icon + 4 * d)

            // Содержимое карточки проявляется во второй половине раскрытия.
            val a = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
            if (a <= 0f) {
                forgotRect.setEmpty(); rememberedRect.setEmpty(); closeRect.setEmpty(); collapseRect.setEmpty(); return
            }
            val alpha = (a * 255).toInt()
            question.alpha = alpha; hint.alpha = (a * 153).toInt(); btnText.alpha = alpha

            // Свернуть в пилюлю и закрыть до следующего занятия — в правом верхнем углу карточки.
            val iconR = 15 * d
            val iconY = top + iconR
            closeRect.set(w - 14 * d - iconR * 2, iconY - iconR, w - 14 * d, iconY + iconR)
            collapseRect.set(closeRect.left - 10 * d - iconR * 2, iconY - iconR, closeRect.left - 10 * d, iconY + iconR)
            // Шапка кликабельна только левее этих кнопок.
            openRect.set(0f, 0f, collapseRect.left - 4 * d, top + icon + 4 * d)
            glyph.alpha = (a * 200).toInt()
            glyph.strokeWidth = 2.2f * d
            // «‒» свернуть
            canvas.drawLine(collapseRect.centerX() - 7 * d, collapseRect.centerY(),
                collapseRect.centerX() + 7 * d, collapseRect.centerY(), glyph)
            // «×» закрыть
            canvas.drawLine(closeRect.centerX() - 6 * d, closeRect.centerY() - 6 * d,
                closeRect.centerX() + 6 * d, closeRect.centerY() + 6 * d, glyph)
            canvas.drawLine(closeRect.centerX() + 6 * d, closeRect.centerY() - 6 * d,
                closeRect.centerX() - 6 * d, closeRect.centerY() + 6 * d, glyph)

            val qY = top + icon + 34 * d * fs
            canvas.drawText(TextUtils.ellipsize(m.word, question, w - 32 * d, TextUtils.TruncateAt.END).toString(), 16 * d, qY, question)
            // Сразу после ответа — короткое подтверждение, потом снова обычная подсказка.
            val justAnswered = System.currentTimeMillis() - answeredAt < 1500
            val subtitle = when {
                justAnswered && answeredRemembered -> "Засчитано: помню ✓"
                justAnswered -> "Засчитано: повторим ещё раз"
                else -> "Вспомните перевод · нажмите на шапку, чтобы открыть занятие"
            }
            canvas.drawText(TextUtils.ellipsize(subtitle, hint, w - 32 * d, TextUtils.TruncateAt.END).toString(),
                16 * d, qY + 20 * d * fs, hint)
            if (justAnswered) postInvalidateDelayed(1500)
            val bTop = qY + 34 * d * fs
            val bBottom = height - 14 * d
            if (bBottom - bTop < 24 * d) { forgotRect.setEmpty(); rememberedRect.setEmpty(); return }

            val mid = w / 2f
            forgotRect.set(14 * d, bTop, mid - 6 * d, bBottom)
            rememberedRect.set(mid + 6 * d, bTop, w - 14 * d, bBottom)
            val cy = (bTop + bBottom) / 2 - (btnText.descent() + btnText.ascent()) / 2
            btn.color = 0xFFFF6B6B.toInt(); btn.alpha = alpha
            canvas.drawRoundRect(forgotRect, 20 * d, 20 * d, btn)
            canvas.drawText("Не помню", forgotRect.centerX(), cy, btnText)
            btn.color = 0xFF2FBF71.toInt(); btn.alpha = alpha
            canvas.drawRoundRect(rememberedRect, 20 * d, 20 * d, btn)
            canvas.drawText("Помню", rememberedRect.centerX(), cy, btnText)
        }
    }
}
