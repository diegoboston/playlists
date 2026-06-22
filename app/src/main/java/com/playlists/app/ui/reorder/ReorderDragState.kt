package com.playlists.app.ui.reorder

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ReorderDragState {
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var dragVisualTop by mutableFloatStateOf(0f)
        private set
    var orderDirty by mutableStateOf(false)
        private set

    val isDragging: Boolean get() = draggingKey != null

    fun currentDragOffset(listState: LazyListState): Float {
        val dragKey = draggingKey ?: return 0f
        val layoutOffset = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == dragKey }
            ?.offset
            ?.toFloat() ?: 0f
        return dragVisualTop - layoutOffset
    }

    fun onDragStart(key: String, listState: LazyListState) {
        draggingKey = key
        dragVisualTop = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }
            ?.offset
            ?.toFloat() ?: 0f
        orderDirty = false
    }

    fun onDrag(delta: Float, listState: LazyListState, displayedKeys: MutableList<String>): Boolean {
        dragVisualTop += delta
        val dragKey = draggingKey ?: return false
        val swapped = handleLazyListDrag(listState, dragKey, dragVisualTop, displayedKeys)
        if (swapped) orderDirty = true
        return swapped
    }

    fun finishDrag(persist: () -> Unit) {
        if (orderDirty) persist()
        draggingKey = null
        dragVisualTop = 0f
        orderDirty = false
    }

    fun cancelDrag() {
        draggingKey = null
        dragVisualTop = 0f
        orderDirty = false
    }
}
