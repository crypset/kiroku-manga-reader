package com.crypset.kiroku.mangareader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class LibraryScanner(context: Context) {
    private val appContext = context.applicationContext

    suspend fun scanLibrary(rootUri: Uri): List<MangaItem> = coroutineScope {
        val rootFolder = DocumentFile.fromTreeUri(appContext, rootUri)

        if (rootFolder == null || !rootFolder.exists()) {
            Log.e(TAG, "Root folder is null or doesn't exist")
            return@coroutineScope emptyList()
        }

        Log.d(TAG, "=== Scanning root folder: ${rootFolder.name} ===")

        val mangaFolders = rootFolder.safeListFiles("Error listing manga folders")
            .filter { it.isDirectory }
            .sortedWith(NaturalSort.documentFileComparator)

        Log.d(TAG, "Found ${mangaFolders.size} manga folders")

        mangaFolders.map { mangaFolder ->
            async(Dispatchers.IO) {
                MangaItem(
                    name = mangaFolder.name ?: "Unknown Manga",
                    uri = mangaFolder.uri.toString(),
                    chapters = scanChapters(mangaFolder)
                )
            }
        }.awaitAll()
    }

    suspend fun scanChapters(mangaUri: Uri): List<Chapter> = coroutineScope {
        val mangaFolder = DocumentFile.fromTreeUri(appContext, mangaUri)

        if (mangaFolder == null || !mangaFolder.exists()) {
            Log.e(TAG, "Manga folder is null or doesn't exist")
            return@coroutineScope emptyList()
        }

        Log.d(TAG, "=== Scanning chapters in: ${mangaFolder.name} ===")
        scanChapters(mangaFolder)
    }

    private suspend fun scanChapters(mangaFolder: DocumentFile): List<Chapter> = coroutineScope {
        val allFiles = mangaFolder.safeListFiles("Error listing files for ${mangaFolder.name}")
        if (allFiles.isEmpty()) return@coroutineScope emptyList()

        val chapterFolders = allFiles.filter { it.isDirectory }
        Log.d(TAG, "Found ${chapterFolders.size} chapter folders")

        if (chapterFolders.isEmpty()) {
            return@coroutineScope scanSingleChapter(mangaFolder, allFiles)
        }

        chapterFolders
            .sortedWith(NaturalSort.documentFileComparator)
            .map { chapterFolder ->
                async(Dispatchers.IO) {
                    scanChapterFolder(chapterFolder)
                }
            }
            .awaitAll()
            .filterNotNull()
            .also { Log.d(TAG, "Total chapters loaded: ${it.size}") }
    }

    private fun scanSingleChapter(
        mangaFolder: DocumentFile,
        allFiles: List<DocumentFile>
    ): List<Chapter> {
        val images = allFiles.supportedImages()
        Log.d(TAG, "Single chapter mode: ${images.size} images")

        return if (images.isNotEmpty()) {
            listOf(
                Chapter(
                    name = mangaFolder.name ?: "Chapter",
                    uri = mangaFolder.uri.toString(),
                    images = images.map { it.uri.toString() }
                )
            )
        } else {
            emptyList()
        }
    }

    private fun scanChapterFolder(chapterFolder: DocumentFile): Chapter? {
        val images = chapterFolder
            .safeListFiles("Error scanning chapter ${chapterFolder.name}")
            .supportedImages()

        Log.d(TAG, "Chapter '${chapterFolder.name}': ${images.size} images")

        return if (images.isNotEmpty()) {
            Chapter(
                name = chapterFolder.name ?: "Unknown Chapter",
                uri = chapterFolder.uri.toString(),
                images = images.map { it.uri.toString() }
            )
        } else {
            null
        }
    }

    private fun DocumentFile.safeListFiles(errorMessage: String): List<DocumentFile> {
        return try {
            listFiles().toList()
        } catch (e: Exception) {
            Log.e(TAG, errorMessage, e)
            emptyList()
        }
    }

    private fun List<DocumentFile>.supportedImages(): List<DocumentFile> {
        return filter { it.isFile && it.name?.isSupportedImageFile() == true }
            .sortedWith(NaturalSort.documentFileComparator)
    }

    private companion object {
        private const val TAG = "MangaReader"
    }
}
