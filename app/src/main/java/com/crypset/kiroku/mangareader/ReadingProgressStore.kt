package com.crypset.kiroku.mangareader

import android.content.Context
import org.json.JSONObject

class ReadingProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveProgress(
        mangaUri: String,
        mangaName: String,
        chapterUri: String,
        chapterName: String,
        pageIndex: Int,
        totalPages: Int
    ) {
        if (mangaUri.isBlank() || chapterUri.isBlank()) return

        val entries = readEntries()
        val previous = entries.optJSONObject(chapterUri)
        val maxPageIndex = (totalPages - 1).coerceAtLeast(0)
        val currentPageIndex = pageIndex.coerceIn(0, maxPageIndex)
        val completed = previous?.optBoolean(KEY_COMPLETED, false) == true ||
                (totalPages > 0 && currentPageIndex >= totalPages - 1)

        entries.put(
            chapterUri,
            JSONObject()
                .put(KEY_MANGA_URI, mangaUri)
                .put(KEY_MANGA_NAME, mangaName)
                .put(KEY_CHAPTER_URI, chapterUri)
                .put(KEY_CHAPTER_NAME, chapterName)
                .put(KEY_PAGE_INDEX, currentPageIndex)
                .put(KEY_TOTAL_PAGES, totalPages)
                .put(KEY_COMPLETED, completed)
                .put(KEY_UPDATED_AT, System.currentTimeMillis())
        )

        preferences.edit()
            .putString(KEY_PROGRESS_ENTRIES, entries.toString())
            .apply()
    }

    fun getChapterProgress(chapterUri: String): ChapterReadingProgress? {
        val entry = readEntries().optJSONObject(chapterUri) ?: return null
        return entry.toChapterProgress()
    }

    fun getMangaProgress(mangaUri: String): MangaReadingProgress? {
        val entries = readEntries()
        val names = entries.names() ?: return null
        val chapterProgress = mutableListOf<ChapterReadingProgress>()

        for (index in 0 until names.length()) {
            val key = names.optString(index)
            val entry = entries.optJSONObject(key) ?: continue
            if (entry.optString(KEY_MANGA_URI) == mangaUri) {
                chapterProgress.add(entry.toChapterProgress())
            }
        }

        if (chapterProgress.isEmpty()) return null

        val latest = chapterProgress.maxByOrNull { it.updatedAt } ?: return null
        return MangaReadingProgress(
            mangaUri = mangaUri,
            mangaName = latest.mangaName,
            lastChapterName = latest.chapterName,
            lastPageNumber = latest.pageNumber,
            lastTotalPages = latest.totalPages,
            startedChapters = chapterProgress.size,
            completedChapters = chapterProgress.count { it.completed },
            updatedAt = latest.updatedAt
        )
    }

    fun clearAll() {
        preferences.edit()
            .remove(KEY_PROGRESS_ENTRIES)
            .apply()
    }

    private fun readEntries(): JSONObject {
        val rawEntries = preferences.getString(KEY_PROGRESS_ENTRIES, null) ?: return JSONObject()
        return try {
            JSONObject(rawEntries)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun JSONObject.toChapterProgress(): ChapterReadingProgress {
        val totalPages = optInt(KEY_TOTAL_PAGES, 0)
        val pageIndex = optInt(KEY_PAGE_INDEX, 0).coerceAtLeast(0)
        return ChapterReadingProgress(
            mangaUri = optString(KEY_MANGA_URI),
            mangaName = optString(KEY_MANGA_NAME),
            chapterUri = optString(KEY_CHAPTER_URI),
            chapterName = optString(KEY_CHAPTER_NAME),
            pageIndex = pageIndex,
            pageNumber = if (totalPages > 0) (pageIndex + 1).coerceAtMost(totalPages) else 0,
            totalPages = totalPages,
            completed = optBoolean(KEY_COMPLETED, false),
            updatedAt = optLong(KEY_UPDATED_AT, 0L)
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "reading_progress"
        private const val KEY_PROGRESS_ENTRIES = "progress_entries"
        private const val KEY_MANGA_URI = "manga_uri"
        private const val KEY_MANGA_NAME = "manga_name"
        private const val KEY_CHAPTER_URI = "chapter_uri"
        private const val KEY_CHAPTER_NAME = "chapter_name"
        private const val KEY_PAGE_INDEX = "page_index"
        private const val KEY_TOTAL_PAGES = "total_pages"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
