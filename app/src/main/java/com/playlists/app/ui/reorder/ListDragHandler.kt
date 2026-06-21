package com.playlists.app.ui.reorder

import androidx.compose.foundation.lazy.LazyListState
import com.playlists.app.ui.ReorderLogic

fun handleLazyListDrag(
    listState: LazyListState,
    draggingKey: String,
    dragVisualTop: Float,
    displayedKeys: MutableList<String>,
): Boolean {
    val visible = listState.layoutInfo.visibleItemsInfo
    val tops = visible.associate { info -> info.key.toString() to info.offset.toFloat() }
    val heights = visible.associate { info -> info.key.toString() to info.size.toFloat() }
    return ReorderLogic.handleDrag(draggingKey, dragVisualTop, displayedKeys, tops, heights)
}
