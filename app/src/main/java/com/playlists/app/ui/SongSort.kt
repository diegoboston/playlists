package com.playlists.app.ui

enum class SongSortCriterion {
    Alpha,
    Added,
    Viewed,
}

data class SongSortState(
    val criterion: SongSortCriterion = SongSortCriterion.Alpha,
    val reversed: Boolean = false,
)
