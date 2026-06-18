package com.crypset.kiroku.mangareader

import android.content.Context
import android.content.Intent

object MangaReaderIntents {
    private const val EXTRA_MANGA_NAME = "manga_name"
    private const val EXTRA_MANGA_URI = "manga_uri"
    private const val EXTRA_IMAGES = "images"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_CHAPTER_NAME = "chapter_name"
    private const val EXTRA_CHAPTER_URI = "chapter_uri"

    fun chaptersIntent(context: Context, manga: MangaItem): Intent {
        return Intent(context, ChaptersActivity::class.java).apply {
            putExtra(EXTRA_MANGA_NAME, manga.name)
            putExtra(EXTRA_MANGA_URI, manga.uri)
        }
    }

    fun mangaArgs(intent: Intent): MangaArgs {
        return MangaArgs(
            name = intent.getStringExtra(EXTRA_MANGA_NAME) ?: "Manga",
            uri = intent.getStringExtra(EXTRA_MANGA_URI).orEmpty()
        )
    }

    fun readerIntent(
        context: Context,
        mangaName: String,
        mangaUri: String,
        chapter: Chapter
    ): Intent {
        return Intent(context, ReaderActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_IMAGES, ArrayList(chapter.images))
            putExtra(EXTRA_TITLE, chapter.name)
            putExtra(EXTRA_MANGA_NAME, mangaName)
            putExtra(EXTRA_MANGA_URI, mangaUri)
            putExtra(EXTRA_CHAPTER_NAME, chapter.name)
            putExtra(EXTRA_CHAPTER_URI, chapter.uri)
        }
    }

    fun readerArgs(intent: Intent): ReaderArgs {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Chapter"
        return ReaderArgs(
            title = title,
            images = intent.getStringArrayListExtra(EXTRA_IMAGES).orEmpty(),
            mangaName = intent.getStringExtra(EXTRA_MANGA_NAME).orEmpty(),
            mangaUri = intent.getStringExtra(EXTRA_MANGA_URI).orEmpty(),
            chapterName = intent.getStringExtra(EXTRA_CHAPTER_NAME) ?: title,
            chapterUri = intent.getStringExtra(EXTRA_CHAPTER_URI).orEmpty()
        )
    }
}

data class MangaArgs(
    val name: String,
    val uri: String
)

data class ReaderArgs(
    val title: String,
    val images: List<String>,
    val mangaName: String,
    val mangaUri: String,
    val chapterName: String,
    val chapterUri: String
)
