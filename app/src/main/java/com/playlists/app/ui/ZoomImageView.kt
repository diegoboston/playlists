package com.playlists.app.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private val baseMatrix = Matrix()
    private val suppMatrix = Matrix()
    private val displayMatrix = Matrix()
    private var minScale = 1f
    private var maxScale = 4f
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = currentScale()
            val factor = detector.scaleFactor
            val target = (scale * factor).coerceIn(minScale, maxScale)
            val applied = target / scale
            if (applied != 1f) {
                suppMatrix.postScale(applied, applied, detector.focusX, detector.focusY)
                applyMatrix()
                updateParentIntercept()
            }
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            if (currentScale() > minScale + 0.01f) {
                suppMatrix.postTranslate(-distanceX, -distanceY)
                applyMatrix()
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale() > minScale + 0.01f) {
                resetZoom()
                return true
            }
            return false
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            resetZoom()
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        val zoomed = currentScale() > minScale + 0.01f
        if (zoomed || scaleDetector.isInProgress) {
            updateParentIntercept()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return false
    }

    fun resetZoom() {
        suppMatrix.reset()
        computeBaseMatrix()
        applyMatrix()
        updateParentIntercept()
    }

    private fun computeBaseMatrix() {
        val drawable = drawable ?: return
        val viewWidth = width - paddingLeft - paddingRight
        val viewHeight = height - paddingTop - paddingBottom
        if (viewWidth <= 0 || viewHeight <= 0) return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f) return

        baseMatrix.reset()
        val scale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)
        val dx = paddingLeft + (viewWidth - drawableWidth * scale) / 2f
        val dy = paddingTop + (viewHeight - drawableHeight * scale) / 2f
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate(dx, dy)
        minScale = 1f
    }

    private fun currentScale(): Float {
        val values = FloatArray(9)
        displayMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun applyMatrix() {
        displayMatrix.set(baseMatrix)
        displayMatrix.postConcat(suppMatrix)
        imageMatrix = displayMatrix
    }

    private fun updateParentIntercept() {
        parent?.requestDisallowInterceptTouchEvent(currentScale() > minScale + 0.01f)
    }
}
