package top.xjunz.tasker.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider

/**
 * @author xjunz 2022/10/07
 */
class LeftCorneredTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatTextView(context, attrs) {

    inline val Number.dpFloat get() = context.resources.displayMetrics.density * toFloat()

    private val paint by lazy {
        Paint().apply {
            color = context.getColor(com.google.android.material.R.color.material_on_surface_stroke)
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2.dpFloat
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                val r = 16.dpFloat
                val path = Path()
                path.moveTo(0F, 0F)
                path.lineTo(w, 0F)
                path.lineTo(w, h)
                path.lineTo(r, h)
                path.arcTo(0F, h - 2 * r, 2 * r, h, 90F, 90F, false)
                path.close()
                @Suppress("DEPRECATION")
                outline.setConvexPath(path)
            }
        }
        invalidateOutline()
    }

    private val radius = 16.dpFloat

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        canvas.drawLine(w, h , radius, h , paint)
        canvas.drawArc(
            0F, h - 2 * radius, 2 * radius, h ,
            90F, 90F, false, paint
        )
    }
}