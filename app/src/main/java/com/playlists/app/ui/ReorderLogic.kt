package com.playlists.app.ui

/**
 * Port of the center-vs-center drag-reorder logic from NoTube's HistoryScreen.
 * Long-press lifts an item; dragging past a neighbour's center swaps positions.
 */
object ReorderLogic {
    fun handleDrag(
        draggingKey: String,
        dragVisualTop: Float,
        displayedKeys: MutableList<String>,
        itemTops: Map<String, Float>,
        itemHeights: Map<String, Float>,
    ): Boolean {
        val draggedHeight = itemHeights[draggingKey] ?: return false
        val draggedCenter = dragVisualTop + draggedHeight / 2f
        val draggedLayoutTop = itemTops[draggingKey] ?: return false
        val fromIdx = displayedKeys.indexOf(draggingKey).takeIf { it >= 0 } ?: return false

        val step = if (draggedCenter > draggedLayoutTop + draggedHeight / 2f) 1 else -1
        var targetKey: String? = null
        var probeIdx = fromIdx + step
        while (probeIdx in displayedKeys.indices) {
            val key = displayedKeys[probeIdx]
            val top = itemTops[key] ?: break
            val height = itemHeights[key] ?: break
            val center = top + height / 2f
            val crossed = if (step > 0) draggedCenter > center else draggedCenter < center
            if (!crossed) break
            targetKey = key
            probeIdx += step
        }
        val target = targetKey ?: return false
        val toIdx = displayedKeys.indexOf(target)
        if (toIdx < 0 || toIdx == fromIdx) return false

        displayedKeys.removeAt(fromIdx)
        displayedKeys.add(toIdx, draggingKey)
        return true
    }
}
