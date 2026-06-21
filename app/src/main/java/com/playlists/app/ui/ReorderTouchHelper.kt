package com.playlists.app.ui

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import com.playlists.app.R

/**
 * Long-press drag reorder for RecyclerView, mirroring NoTube HistoryScreen's
 * DraggableItem + handleHistoryDrag behaviour (center-vs-center swaps).
 */
class ReorderTouchHelper(
    private val recyclerView: RecyclerView,
    private val onOrderChanged: (List<String>) -> Unit,
    var onItemMoved: ((from: Int, to: Int) -> Unit)? = null,
) {
    private var draggingKey: String? = null
    private var draggedView: View? = null
    private var dragVisualTop = 0f
    private var dragOffset = 0f
    private var dragStartRawY = 0f
    private var orderDirty = false
    private val displayedKeys = mutableListOf<String>()
    private var longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var touchSlop = ViewConfiguration.get(recyclerView.context).scaledTouchSlop

    var keys: List<String>
        get() = displayedKeys.toList()
        set(value) {
            displayedKeys.clear()
            displayedKeys.addAll(value)
        }

    private fun keyAt(position: Int): String =
        displayedKeys.getOrNull(position).orEmpty()

    @SuppressLint("ClickableViewAccessibility")
    fun attachToViewHolder(holder: RecyclerView.ViewHolder, dragEnabled: Boolean = true) {
        if (holder.itemView.getTag(R.id.reorder_attached) == true) return
        holder.itemView.setTag(R.id.reorder_attached, true)
        val itemView = holder.itemView
        itemView.setOnTouchListener(object : View.OnTouchListener {
            private var downY = 0f
            private var downRawY = 0f
            private var longPressTriggered = false
            private var movedPastSlop = false
            private val longPressRunnable = Runnable {
                if (movedPastSlop || !dragEnabled) return@Runnable
                longPressTriggered = true
                val position = holder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@Runnable
                val key = keyAt(position)
                if (key.isEmpty()) return@Runnable
                startDrag(key, downRawY, itemView)
                itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.y
                        downRawY = event.rawY
                        longPressTriggered = false
                        movedPastSlop = false
                        itemView.postDelayed(longPressRunnable, longPressTimeout)
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!longPressTriggered) {
                            val dy = kotlin.math.abs(event.y - downY)
                            if (dy > touchSlop) {
                                movedPastSlop = true
                                itemView.removeCallbacks(longPressRunnable)
                            }
                        } else {
                            dragOffset += event.rawY - dragStartRawY
                            dragStartRawY = event.rawY
                            draggedView?.let { dragVisualTop = it.top + dragOffset }
                            updateLiftVisuals()
                            if (performSwap()) orderDirty = true
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        itemView.removeCallbacks(longPressRunnable)
                        if (longPressTriggered) {
                            endDrag()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun startDrag(key: String, rawY: Float, itemView: View) {
        draggingKey = key
        draggedView = itemView
        dragStartRawY = rawY
        dragOffset = 0f
        dragVisualTop = itemView.top.toFloat()
        recyclerView.requestDisallowInterceptTouchEvent(true)
        updateLiftVisuals()
    }

    private fun endDrag() {
        val key = draggingKey
        draggingKey = null
        draggedView = null
        dragOffset = 0f
        recyclerView.requestDisallowInterceptTouchEvent(false)
        resetVisuals()
        if (orderDirty && key != null) {
            orderDirty = false
            onOrderChanged(displayedKeys.toList())
        }
    }

    private fun performSwap(): Boolean {
        val key = draggingKey ?: return false
        val tops = mutableMapOf<String, Float>()
        val heights = mutableMapOf<String, Float>()
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val pos = recyclerView.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val childKey = keyAt(pos)
            tops[childKey] = child.top.toFloat()
            heights[childKey] = child.height.toFloat()
        }
        val fromIdx = displayedKeys.indexOf(draggingKey).takeIf { it >= 0 } ?: return false
        if (ReorderLogic.handleDrag(key, dragVisualTop, displayedKeys, tops, heights)) {
            val toIdx = displayedKeys.indexOf(key)
            if (toIdx >= 0 && toIdx != fromIdx) {
                onItemMoved?.invoke(fromIdx, toIdx)
                dragOffset = 0f
                draggedView?.let { view ->
                    dragVisualTop = view.top.toFloat()
                }
            }
            return true
        }
        return false
    }

    private fun updateLiftVisuals() {
        val dragged = draggedView ?: return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val isDragged = child === dragged
            child.translationZ = if (isDragged) 8f else 0f
            child.translationY = if (isDragged) dragOffset else 0f
        }
    }

    private fun resetVisuals() {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            child.translationY = 0f
            child.translationZ = 0f
        }
    }
}
