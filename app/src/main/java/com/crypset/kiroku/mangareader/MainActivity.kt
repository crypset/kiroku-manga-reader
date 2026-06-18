package com.crypset.kiroku.mangareader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: View
    private lateinit var loadingState: View
    private lateinit var adapter: MangaAdapter
    private lateinit var libraryStore: LibraryStore
    private lateinit var progressStore: ReadingProgressStore
    private lateinit var libraryScanner: LibraryScanner
    private val mangaList = mutableListOf<MangaItem>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scanRootMangaFolderAsync(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.topAppBar)
        toolbar.title = getString(R.string.app_name)
        toolbar.subtitle = getString(R.string.library_subtitle)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.chaptersRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        loadingState = findViewById(R.id.loadingState)
        libraryStore = LibraryStore(this)
        progressStore = ReadingProgressStore(this)
        libraryScanner = LibraryScanner(this)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = MangaAdapter(mangaList) { manga ->
            openMangaChapters(manga)
        }
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabSelectFolder).setOnClickListener {
            checkPermissionAndOpenPicker()
        }
        findViewById<View>(R.id.emptySelectFolderButton).setOnClickListener {
            checkPermissionAndOpenPicker()
        }

        restoreSavedManga()
    }

    override fun onResume() {
        super.onResume()
        refreshMangaProgress()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_reading_progress -> {
                confirmClearReadingProgress()
                true
            }
            R.id.action_select_library_folder -> {
                checkPermissionAndOpenPicker()
                true
            }
            R.id.action_rescan_library -> {
                rescanCachedLibrary()
                true
            }
            R.id.action_clear_library_cache -> {
                confirmClearLibraryCache()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun checkPermissionAndOpenPicker() {
        folderPicker.launch(null)
    }

    private fun scanRootMangaFolderAsync(uri: Uri) {
        showLoading(true)

        scope.launch {
            try {
                val scannedManga = withContext(Dispatchers.IO) {
                    libraryScanner.scanLibrary(uri)
                }

                mangaList.clear()
                mangaList.addAll(withMangaProgress(scannedManga))
                adapter.notifyDataSetChanged()
                withContext(Dispatchers.IO) {
                    libraryStore.saveLibrary(uri.toString(), scannedManga)
                }

                showLoading(false)
                showLibraryScanResult()
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning root folder", e)
                showLoading(false)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restoreSavedManga() {
        showLoading(true)

        scope.launch {
            try {
                val savedManga = withContext(Dispatchers.IO) {
                    libraryStore.getManga()
                }

                mangaList.clear()
                mangaList.addAll(withMangaProgress(savedManga))
                adapter.notifyDataSetChanged()
                showLoading(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring saved manga", e)
                withContext(Dispatchers.IO) {
                    libraryStore.clearLibrary()
                }
                showLoading(false)
            }
        }
    }

    private fun clearMangaCache() {
        libraryStore.clearLibrary()
    }

    private fun refreshMangaProgress() {
        if (!::adapter.isInitialized || mangaList.isEmpty()) return

        for (index in mangaList.indices) {
            val manga = mangaList[index]
            mangaList[index] = manga.copy(progress = progressStore.getMangaProgress(manga.uri))
        }
        adapter.notifyDataSetChanged()
        updateContentVisibility()
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
        val hasManga = mangaList.isNotEmpty()
        recyclerView.visibility = if (hasManga) View.VISIBLE else View.GONE
        emptyState.visibility = if (hasManga) View.GONE else View.VISIBLE
    }

    private fun withMangaProgress(manga: List<MangaItem>): List<MangaItem> {
        return manga.map { item ->
            item.copy(progress = progressStore.getMangaProgress(item.uri))
        }
    }

    private fun confirmClearReadingProgress() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_reading_progress_title)
            .setMessage(R.string.clear_reading_progress_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear_reading_progress_action) { _, _ ->
                progressStore.clearAll()
                refreshMangaProgress()
                Toast.makeText(this, R.string.reading_progress_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun rescanCachedLibrary() {
        val rootUri = libraryStore.getRootUri()

        if (rootUri == null) {
            checkPermissionAndOpenPicker()
            return
        }

        scanRootMangaFolderAsync(Uri.parse(rootUri))
    }

    private fun confirmClearLibraryCache() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_library_cache_title)
            .setMessage(R.string.clear_library_cache_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear_library_cache_action) { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        clearMangaCache()
                    }
                    mangaList.clear()
                    adapter.notifyDataSetChanged()
                    updateContentVisibility()
                    Toast.makeText(this@MainActivity, R.string.library_cache_cleared, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun openMangaChapters(manga: MangaItem) {
        startActivity(MangaReaderIntents.chaptersIntent(this, manga))
    }

    private fun showLibraryScanResult() {
        if (mangaList.isEmpty()) {
            Toast.makeText(
                this,
                "No manga folders found",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                R.string.library_rescanned,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private companion object {
        private const val TAG = "MangaReader"
    }
}
