package com.crypset.kiroku.mangareader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChaptersActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: View
    private lateinit var loadingState: View
    private lateinit var adapter: ChapterAdapter
    private lateinit var libraryStore: LibraryStore
    private lateinit var progressStore: ReadingProgressStore
    private var mangaName: String = "Manga"
    private var mangaUri: String = ""
    private val chapters = mutableListOf<Chapter>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapters)

        val toolbar: MaterialToolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.chaptersRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        loadingState = findViewById(R.id.loadingState)
        libraryStore = LibraryStore(this)
        progressStore = ReadingProgressStore(this)

        mangaName = intent.getStringExtra("manga_name") ?: "Manga"
        mangaUri = intent.getStringExtra("manga_uri") ?: ""

        if (mangaUri.isBlank()) {
            Toast.makeText(this, "Error: Invalid manga URI", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.title = mangaName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ChapterAdapter(chapters) { chapter ->
            openReader(chapter)
        }
        recyclerView.adapter = adapter

        restoreSavedChaptersOrScan()
    }

    override fun onResume() {
        super.onResume()
        refreshChapterProgress()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun restoreSavedChaptersOrScan() {
        showLoading(true)

        scope.launch {
            try {
                val savedChapters = withContext(Dispatchers.IO) {
                    libraryStore.getChapters(mangaUri)
                }

                if (savedChapters.isNotEmpty()) {
                    chapters.clear()
                    chapters.addAll(withChapterProgress(savedChapters))
                    adapter.notifyDataSetChanged()
                    showLoading(false)
                } else {
                    scanChaptersAsync(Uri.parse(mangaUri))
                }
            } catch (e: Exception) {
                Log.e("MangaReader", "Error restoring saved chapters", e)
                scanChaptersAsync(Uri.parse(mangaUri))
            }
        }
    }

    private fun scanChaptersAsync(folderUri: Uri) {
        showLoading(true)

        scope.launch {
            try {
                val scannedChapters = withContext(Dispatchers.IO) {
                    scanChaptersParallel(folderUri)
                }

                chapters.clear()
                chapters.addAll(withChapterProgress(scannedChapters))
                adapter.notifyDataSetChanged()
                withContext(Dispatchers.IO) {
                    libraryStore.saveChapters(mangaUri, mangaName, scannedChapters)
                }

                showLoading(false)

                if (chapters.isEmpty()) {
                    Toast.makeText(
                        this@ChaptersActivity,
                        "No chapters found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MangaReader", "Error scanning chapters", e)
                showLoading(false)
                Toast.makeText(
                    this@ChaptersActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private suspend fun scanChaptersParallel(mangaUri: Uri): List<Chapter> = coroutineScope {
        try {
            val mangaFolder = DocumentFile.fromTreeUri(this@ChaptersActivity, mangaUri)

            if (mangaFolder == null || !mangaFolder.exists()) {
                Log.e("MangaReader", "Manga folder is null or doesn't exist")
                return@coroutineScope emptyList()
            }

            Log.d("MangaReader", "=== Scanning chapters in: ${mangaFolder.name} ===")

            val allFiles = try {
                mangaFolder.listFiles().toList()
            } catch (e: Exception) {
                Log.e("MangaReader", "Error listing files", e)
                return@coroutineScope emptyList()
            }

            val chapterFolders = allFiles.filter { it.isDirectory }
            Log.d("MangaReader", "Found ${chapterFolders.size} chapter folders")

            if (chapterFolders.isEmpty()) {
                val images = allFiles
                    .filter { it.isFile && it.name?.isImageFile() == true }
                    .sortedWith(naturalOrderComparator())

                Log.d("MangaReader", "Single chapter mode: ${images.size} images")

                return@coroutineScope if (images.isNotEmpty()) {
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

            val sortedChapters = chapterFolders.sortedWith(naturalOrderComparator())

            val resultChapters = sortedChapters.map { chapterFolder ->
                async(Dispatchers.IO) {
                    try {
                        val images = chapterFolder.listFiles()
                            .filter { it.isFile && it.name?.isImageFile() == true }
                            .sortedWith(naturalOrderComparator())

                        Log.d("MangaReader", "Chapter '${chapterFolder.name}': ${images.size} images")

                        if (images.isNotEmpty()) {
                            Chapter(
                                name = chapterFolder.name ?: "Unknown Chapter",
                                uri = chapterFolder.uri.toString(),
                                images = images.map { it.uri.toString() }
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("MangaReader", "Error scanning chapter ${chapterFolder.name}", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()

            Log.d("MangaReader", "Total chapters loaded: ${resultChapters.size}")
            resultChapters
        } catch (e: Exception) {
            Log.e("MangaReader", "Fatal error in scanChapters", e)
            throw e
        }
    }

    private fun String.isImageFile(): Boolean {
        return matches(Regex(".*\\.(png|jpg|jpeg|webp|gif)", RegexOption.IGNORE_CASE))
    }

    private fun naturalOrderComparator(): Comparator<DocumentFile> {
        return Comparator { a, b ->
            compareNatural(a.name ?: "", b.name ?: "")
        }
    }

    private fun compareNatural(a: String, b: String): Int {
        try {
            val regex = Regex("(\\d+)|(\\D+)")
            val aParts = regex.findAll(a).map { it.value }.toList()
            val bParts = regex.findAll(b).map { it.value }.toList()

            for (i in 0 until minOf(aParts.size, bParts.size)) {
                val aPart = aParts[i]
                val bPart = bParts[i]

                val aIsNumber = aPart.all { it.isDigit() }
                val bIsNumber = bPart.all { it.isDigit() }

                when {
                    aIsNumber && bIsNumber -> {
                        val aNum = aPart.toLongOrNull() ?: 0L
                        val bNum = bPart.toLongOrNull() ?: 0L
                        val comparison = aNum.compareTo(bNum)
                        if (comparison != 0) return comparison
                    }
                    else -> {
                        val comparison = aPart.compareTo(bPart, ignoreCase = true)
                        if (comparison != 0) return comparison
                    }
                }
            }

            return aParts.size.compareTo(bParts.size)
        } catch (e: Exception) {
            return a.compareTo(b, ignoreCase = true)
        }
    }

    private fun openReader(chapter: Chapter) {
        val intent = Intent(this, ReaderActivity::class.java)
        intent.putStringArrayListExtra("images", ArrayList(chapter.images))
        intent.putExtra("title", chapter.name)
        intent.putExtra("manga_name", mangaName)
        intent.putExtra("manga_uri", mangaUri)
        intent.putExtra("chapter_name", chapter.name)
        intent.putExtra("chapter_uri", chapter.uri)
        startActivity(intent)
    }

    private fun refreshChapterProgress() {
        if (!::adapter.isInitialized || chapters.isEmpty()) return

        for (index in chapters.indices) {
            val chapter = chapters[index]
            chapters[index] = chapter.copy(progress = progressStore.getChapterProgress(chapter.uri))
        }
        adapter.notifyDataSetChanged()
        updateContentVisibility()
    }

    private fun withChapterProgress(chapters: List<Chapter>): List<Chapter> {
        return chapters.map { chapter ->
            chapter.copy(progress = progressStore.getChapterProgress(chapter.uri))
        }
    }

    private fun showLoading(isLoading: Boolean) {
        loadingState.visibility = if (isLoading) View.VISIBLE else View.GONE
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        if (isLoading) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.GONE
        } else {
            updateContentVisibility()
        }
    }

    private fun updateContentVisibility() {
        val hasChapters = chapters.isNotEmpty()
        recyclerView.visibility = if (hasChapters) View.VISIBLE else View.GONE
        emptyState.visibility = if (hasChapters) View.GONE else View.VISIBLE
    }

}

data class Chapter(
    val name: String,
    val uri: String,
    val images: List<String>,
    val progress: ChapterReadingProgress? = null
)
