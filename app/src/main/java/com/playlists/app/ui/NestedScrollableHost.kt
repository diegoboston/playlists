package com.playlists.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.sign

/**
 * Wraps a scrollable child (e.g. ViewPager2) nested inside another ViewPager2.
 * Inner pager receives swipes until it hits an edge, then the outer pager takes over.
 */
class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f

    private val parentViewPager: ViewPager2?
        get() {
            var v: View? = parent as? View
            while (v != null && v !is ViewPager2) {
                v = v.parent as? View
            }
            return v as? ViewPager2
        }

    private val child: View? get() = if (childCount > 0) getChildAt(0) else null

    private fun canChildScroll(orientation: Int, delta: Float): Boolean {
        val direction = -delta.sign.toInt()
        val child = child ?: return false
        return when (orientation) {
            ViewPager2.ORIENTATION_HORIZONTAL -> child.canScrollHorizontally(direction)
            ViewPager2.ORIENTATION_VERTICAL -> child.canScrollVertically(direction)
            else -> false
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        handleInterceptTouchEvent(e)
        return super.onInterceptTouchEvent(e)
    }

    private fun handleInterceptTouchEvent(e: MotionEvent) {
        val orientation = parentViewPager?.orientation ?: return

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - initialX
                val dy = e.y - initialY
                val isHorizontal = orientation == ViewPager2.ORIENTATION_HORIZONTAL
                val scaledDx = abs(dx) * if (isHorizontal) 0.5f else 1f
                val scaledDy = abs(dy) * if (isHorizontal) 1f else 0.5f

                if (scaledDx > touchSlop || scaledDy > touchSlop) {
                    if (isHorizontal == (scaledDy > scaledDx)) {
                        parent.requestDisallowInterceptTouchEvent(false)
                    } else if (canChildScroll(orientation, if (isHorizontal) dx else dy)) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    } else {
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        }
    }
}
