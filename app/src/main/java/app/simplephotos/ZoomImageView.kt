package app.simplephotos

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * An ImageView that shows a picture fitted to the screen and lets you pinch to
 * zoom and drag around when zoomed in.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrix = Matrix()
    private var scale = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val next = (scale * factor).coerceIn(1f, 5f)
                val applied = next / scale
                scale = next
                matrix.postScale(applied, applied, detector.focusX, detector.focusY)
                clamp()
                imageMatrix = matrix
                return true
            }
        }
    )

    private val tapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scale > 1.05f) {
                    resetZoom()
                } else {
                    scale = 2.5f
                    matrix.set(fitMatrix())
                    matrix.postScale(scale, scale, e.x, e.y)
                    clamp()
                    imageMatrix = matrix
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    fun resetZoom() {
        scale = 1f
        matrix.set(fitMatrix())
        imageMatrix = matrix
    }

    private fun fitMatrix(): Matrix {
        val d = drawable ?: return Matrix()
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return Matrix()
        val bw = d.intrinsicWidth.toFloat()
        val bh = d.intrinsicHeight.toFloat()
        val s = min(width / bw, height / bh)
        return Matrix().apply {
            postScale(s, s)
            postTranslate((width - bw * s) / 2f, (height - bh * s) / 2f)
        }
    }

    /** Keeps the picture from being dragged off screen. */
    private fun clamp() {
        val d = drawable ?: return
        val view = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val pic = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matrix.mapRect(pic)

        val dx = if (pic.width() <= view.width()) {
            view.centerX() - pic.centerX()
        } else {
            when {
                pic.left > view.left -> view.left - pic.left
                pic.right < view.right -> view.right - pic.right
                else -> 0f
            }
        }
        val dy = if (pic.height() <= view.height()) {
            view.centerY() - pic.centerY()
        } else {
            when {
                pic.top > view.top -> view.top - pic.top
                pic.bottom < view.bottom -> view.bottom - pic.bottom
                else -> 0f
            }
        }
        matrix.postTranslate(dx, dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        tapDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(scale > 1.01f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && scale > 1.01f && event.pointerCount == 1) {
                    matrix.postTranslate(event.x - lastX, event.y - lastY)
                    clamp()
                    imageMatrix = matrix
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
