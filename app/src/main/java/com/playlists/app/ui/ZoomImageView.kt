package com.playlists.app.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private val baseMatrix = Matrix()
    private val suppMatrix = Matrix()
    private val displayMatrix = Matrix()
    private var baseScale = 1f
    private val minRelativeScale = 1f
    private val maxRelativeScale = 4f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            setParentIntercept(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val relative = relativeScale()
            val target = (relative * detector.scaleFactor).coerceIn(minRelativeScale, maxRelativeScale)
            val applied = target / relative
            if (applied != 1f) {
                suppMatrix.postScale(applied, applied, detector.focusX, detector.focusY)
                applyMatrix()
                setParentIntercept(isZoomed())
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (!isZoomed()) {
                setParentIntercept(false)
            }
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            if (isZoomed()) {
                setParentIntercept(true)
                suppMatrix.postTranslate(-distanceX, -distanceY)
                applyMatrix()
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isZoomed()) {
                resetZoom()
            }
            return true
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
        if (event.pointerCount > 1 || scaleDetector.isInProgress) {
            setParentIntercept(true)
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                if (!isZoomed() && !scaleDetector.isInProgress) {
                    setParentIntercept(false)
                }
        }

        // Must consume the stream so ScaleGestureDetector receives MOVE events.
        return true
    }

    fun resetZoom() {
        suppMatrix.reset()
        computeBaseMatrix()
        applyMatrix()
        setParentIntercept(false)
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
        baseScale = scale
    }

    private fun relativeScale(): Float {
        if (baseScale <= 0f) return 1f
        return currentScale() / baseScale
    }

    private fun isZoomed(): Boolean = relativeScale() > minRelativeScale + 0.01f

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

    private fun setParentIntercept(disallow: Boolean) {
        var parent: ViewParent? = parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            if (parent is ViewPager2) {
                parent.isUserInputEnabled = !disallow
            }
            parent = parent.parent
        }
    }
}
