package pf.tiai.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** Couleurs et petits éléments d'interface : gros caractères, forts contrastes. */
object Ui {
    val GROUND = 0xFFF2F5F7.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val INK = 0xFF0F1E2B.toInt()
    val MUTED = 0xFF44566A.toInt()
    val LINE = 0xFFC5CED6.toInt()
    val BLUE = 0xFF0A56A3.toInt()
    val BLUE_SOFT = 0xFFDCE8F5.toInt()
    val RED = 0xFFB91C0C.toInt()
    val RED_DARK = 0xFFA3180A.toInt()
    val GREEN = 0xFF0B6B3A.toInt()
    val AMBER = 0xFF7A4A00.toInt()
    val AMBER_SOFT = 0xFFFFF0CC.toInt()
    val LAGOON = 0xFF0E8F8F.toInt()

    fun box(ctx: Context, fill: Int, stroke: Int? = null, radiusDp: Int = 18): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = ctx.dp(radiusDp).toFloat()
        if (stroke != null) g.setStroke(ctx.dp(2), stroke)
        return g
    }

    private fun pressable(shape: Drawable): Drawable =
        RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null)

    fun text(ctx: Context, s: CharSequence, sizeSp: Float, color: Int = INK, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        t.setLineSpacing(0f, 1.15f)
        return t
    }

    fun button(
        ctx: Context, label: CharSequence, fill: Int, textColor: Int, sizeSp: Float,
        stroke: Int? = null, onClick: () -> Unit
    ): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.textSize = sizeSp
        b.setTextColor(textColor)
        b.typeface = Typeface.DEFAULT_BOLD
        b.gravity = Gravity.CENTER
        b.stateListAnimator = null
        b.background = pressable(box(ctx, fill, stroke))
        b.setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
        b.minHeight = ctx.dp(56)
        b.minimumHeight = ctx.dp(56)
        b.setOnClickListener { onClick() }
        return b
    }
}

/** Fleur de tiare : sept pétales blancs en hélice, cœur jaune, deux feuilles. */
class TiareView(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF8FA89B.toInt()
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f
        line.strokeWidth = r * 0.035f

        // feuilles
        fill.color = 0xFF2E7D4F.toInt()
        for (angle in floatArrayOf(-40f, 140f)) {
            canvas.save()
            canvas.rotate(angle, cx, cy)
            path.reset()
            path.moveTo(cx, cy)
            path.cubicTo(cx + r * 0.45f, cy - r * 0.42f, cx + r * 0.85f, cy - r * 0.2f, cx + r * 0.98f, cy)
            path.cubicTo(cx + r * 0.85f, cy + r * 0.2f, cx + r * 0.45f, cy + r * 0.42f, cx, cy)
            path.close()
            canvas.drawPath(path, fill)
            canvas.restore()
        }

        // pétales, légèrement asymétriques pour l'effet d'hélice
        fill.color = 0xFFFFFFFF.toInt()
        val petals = 7
        for (i in 0 until petals) {
            canvas.save()
            canvas.rotate(i * 360f / petals, cx, cy)
            path.reset()
            path.moveTo(cx, cy)
            path.cubicTo(cx - r * 0.34f, cy - r * 0.30f, cx - r * 0.22f, cy - r * 0.78f, cx + r * 0.06f, cy - r * 0.84f)
            path.cubicTo(cx + r * 0.26f, cy - r * 0.66f, cx + r * 0.20f, cy - r * 0.28f, cx, cy)
            path.close()
            canvas.drawPath(path, fill)
            canvas.drawPath(path, line)
            canvas.restore()
        }

        // cœur
        fill.color = 0xFFF2B705.toInt()
        canvas.drawCircle(cx, cy, r * 0.13f, fill)
    }
}

/** Frise de bas d'écran : vague du lagon et coquillages. */
class ShellBand(ctx: Context) : View(ctx) {
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Ui.LAGOON
        strokeCap = Paint.Cap.ROUND
    }
    private val shellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF0D2A8.toInt() }
    private val shellLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9A6A3A.toInt()
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val unit = h / 4f
        wave.strokeWidth = h * 0.06f
        shellLine.strokeWidth = h * 0.035f

        // vague
        val baseY = h * 0.78f
        val step = unit * 2.2f
        path.reset()
        path.moveTo(0f, baseY)
        var x = 0f
        var up = true
        while (x < w) {
            path.quadTo(x + step / 2f, if (up) baseY - unit * 0.7f else baseY + unit * 0.7f, x + step, baseY)
            x += step
            up = !up
        }
        canvas.drawPath(path, wave)

        // trois coquillages
        val r = h * 0.36f
        shell(canvas, w * 0.18f, h * 0.62f, r)
        shell(canvas, w * 0.50f, h * 0.56f, r * 1.15f)
        shell(canvas, w * 0.82f, h * 0.62f, r)
    }

    /** Coquille en éventail, charnière en bas au point (bx, by). */
    private fun shell(canvas: Canvas, bx: Float, by: Float, r: Float) {
        oval.set(bx - r, by - r, bx + r, by + r)
        path.reset()
        path.moveTo(bx, by)
        path.arcTo(oval, 200f, 140f, false)
        path.close()
        canvas.drawPath(path, shellFill)
        canvas.drawPath(path, shellLine)
        for (i in 1..4) {
            val a = Math.toRadians((200f + i * 28f).toDouble())
            canvas.drawLine(bx, by, bx + (r * cos(a)).toFloat(), by + (r * sin(a)).toFloat(), shellLine)
        }
    }
}
