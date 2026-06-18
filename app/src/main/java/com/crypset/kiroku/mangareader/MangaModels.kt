package com.crypset.kiroku.mangareader

data class MangaItem(
    val name: String,
    val uri: String,
    val chapters: List<Chapter> = emptyList(),
    val progress: MangaReadingProgress? = null
)

data class Chapter(
    val name: String,
    val uri: String,
    val images: List<String>,
    val progress: ChapterReadingProgress? = null
)
