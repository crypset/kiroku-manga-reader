package com.crypset.kiroku.mangareader

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private lateinit var libraryScanner: LibraryScanner
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
        libraryScanner = LibraryScanner(this)

        val mangaArgs = MangaReaderIntents.mangaArgs(intent)
        mangaName = mangaArgs.name
        mangaUri = mangaArgs.uri

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
                Log.e(TAG, "Error restoring saved chapters", e)
                scanChaptersAsync(Uri.parse(mangaUri))
            }
        }
    }

    private fun scanChaptersAsync(folderUri: Uri) {
        showLoading(true)

        scope.launch {
            try {
                val scannedChapters = withContext(Dispatchers.IO) {
                    libraryScanner.scanChapters(folderUri)
                }

                chapters.clear()
                chapters.addAll(withChapterProgress(scannedChapters))
                adapter.notifyDataSetChanged()
                withContext(Dispatchers.IO) {
                    libraryStore.saveChapters(mangaUri, mangaName, scannedChapters)
                }

                showLoading(false)
                showChapterScanResult()
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning chapters", e)
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

    private fun openReader(chapter: Chapter) {
        startActivity(
            MangaReaderIntents.readerIntent(
                context = this,
                mangaName = mangaName,
                mangaUri = mangaUri,
                chapter = chapter
            )
        )
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

    private fun showChapterScanResult() {
        if (chapters.isEmpty()) {
            Toast.makeText(
                this,
                "No chapters found",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private companion object {
        private const val TAG = "MangaReader"
    }
}
