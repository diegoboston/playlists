package com.playlists.app.ui.reorder

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState

fun handleLazyListDrag(
    listState: LazyListState,
    draggingKey: String,
    dragVisualTop: Float,
    displayedKeys: MutableList<String>,
): Boolean {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    val draggedItem = visibleItems.firstOrNull { it.key == draggingKey } ?: return false
    val draggedCenter = dragVisualTop + draggedItem.size / 2f

    val fromIdx = displayedKeys.indexOf(draggingKey).takeIf { it >= 0 } ?: return false

    val step = if (draggedCenter > draggedItem.offset + draggedItem.size / 2f) 1 else -1
    var target: LazyListItemInfo? = null
    var probeIdx = fromIdx + step
    while (probeIdx in displayedKeys.indices) {
        val info = visibleItems.firstOrNull { it.key == displayedKeys[probeIdx] } ?: break
        val infoCenter = info.offset + info.size / 2f
        val crossed = if (step > 0) draggedCenter > infoCenter else draggedCenter < infoCenter
        if (!crossed) break
        target = info
        probeIdx += step
    }
    val targetItem = target ?: return false

    val targetKey = targetItem.key.toString()
    val toIdx = displayedKeys.indexOf(targetKey)
    if (toIdx < 0 || toIdx == fromIdx) return false

    displayedKeys.removeAt(fromIdx)
    displayedKeys.add(toIdx, draggingKey)
    return true
}

fun syncDisplayedKeys(
    displayedKeys: MutableList<String>,
    draggingKey: String?,
    newKeys: List<String>,
) {
    if (draggingKey == null && displayedKeys.toList() != newKeys) {
        displayedKeys.clear()
        displayedKeys.addAll(newKeys)
    }
}
