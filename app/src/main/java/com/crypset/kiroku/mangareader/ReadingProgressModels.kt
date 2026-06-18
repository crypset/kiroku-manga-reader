package com.crypset.kiroku.mangareader

data class ChapterReadingProgress(
    val mangaUri: String,
    val mangaName: String,
    val chapterUri: String,
    val chapterName: String,
    val pageIndex: Int,
    val pageNumber: Int,
    val totalPages: Int,
    val completed: Boolean,
    val updatedAt: Long
)

data class MangaReadingProgress(
    val mangaUri: String,
    val mangaName: String,
    val lastChapterName: String,
    val lastPageNumber: Int,
    val lastTotalPages: Int,
    val startedChapters: Int,
    val completedChapters: Int,
    val updatedAt: Long
)
