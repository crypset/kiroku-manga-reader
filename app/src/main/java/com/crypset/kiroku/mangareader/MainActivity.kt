package com.crypset.kiroku.mangareader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: View
    private lateinit var loadingState: View
    private lateinit var adapter: MangaAdapter
    private lateinit var progressStore: ReadingProgressStore
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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openFolderPicker()
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
        progressStore = ReadingProgressStore(this)

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

        restoreCachedManga()
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
            R.id.action_rescan_library -> {
                rescanCachedLibrary()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                openFolderPicker()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                openFolderPicker()
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun openFolderPicker() {
        folderPicker.launch(null)
    }

    private fun scanRootMangaFolderAsync(uri: Uri) {
        showLoading(true)

        scope.launch {
            try {
                val scannedManga = withContext(Dispatchers.IO) {
                    scanRootMangaFolder(uri)
                }

                mangaList.clear()
                mangaList.addAll(withMangaProgress(scannedManga))
                adapter.notifyDataSetChanged()
                saveMangaCache(uri, scannedManga)

                showLoading(false)

                if (mangaList.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "No manga folders found",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.library_rescanned,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MangaReader", "Error scanning root folder", e)
                showLoading(false)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restoreCachedManga() {
        val cachedMangaJson = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(KEY_MANGA_CACHE, null)
            ?: run {
                updateContentVisibility()
                return
            }

        try {
            val cachedManga = buildList {
                val jsonArray = JSONArray(cachedMangaJson)
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(index)
                    add(
                        MangaItem(
                            name = item.getString(KEY_MANGA_NAME),
                            uri = item.getString(KEY_MANGA_URI),
                            chapters = parseChapters(item.optJSONArray(KEY_MANGA_CHAPTERS))
                        )
                    )
                }
            }

            mangaList.clear()
            mangaList.addAll(withMangaProgress(cachedManga))
            adapter.notifyDataSetChanged()
            updateContentVisibility()
        } catch (e: Exception) {
            Log.e("MangaReader", "Error restoring cached manga", e)
            clearMangaCache()
            updateContentVisibility()
        }
    }

    private fun saveMangaCache(rootUri: Uri, manga: List<MangaItem>) {
        val jsonArray = JSONArray()
        manga.forEach { item ->
            jsonArray.put(
                JSONObject()
                    .put(KEY_MANGA_NAME, item.name)
                    .put(KEY_MANGA_URI, item.uri)
                    .put(KEY_MANGA_CHAPTERS, chaptersToJson(item.chapters))
            )
        }

        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_URI, rootUri.toString())
            .putString(KEY_MANGA_CACHE, jsonArray.toString())
            .apply()
    }

    private fun parseChapters(chaptersJson: JSONArray?): List<Chapter> {
        if (chaptersJson == null) return emptyList()

        return buildList {
            for (chapterIndex in 0 until chaptersJson.length()) {
                val chapterJson = chaptersJson.getJSONObject(chapterIndex)
                val imagesJson = chapterJson.optJSONArray(KEY_CHAPTER_IMAGES) ?: JSONArray()
                val images = buildList {
                    for (imageIndex in 0 until imagesJson.length()) {
                        add(imagesJson.getString(imageIndex))
                    }
                }

                add(
                    Chapter(
                        name = chapterJson.getString(KEY_CHAPTER_NAME),
                        uri = chapterJson.getString(KEY_CHAPTER_URI),
                        images = images
                    )
                )
            }
        }
    }

    private fun chaptersToJson(chapters: List<Chapter>): JSONArray {
        val chaptersJson = JSONArray()
        chapters.forEach { chapter ->
            val imagesJson = JSONArray()
            chapter.images.forEach { imageUri ->
                imagesJson.put(imageUri)
            }

            chaptersJson.put(
                JSONObject()
                    .put(KEY_CHAPTER_NAME, chapter.name)
                    .put(KEY_CHAPTER_URI, chapter.uri)
                    .put(KEY_CHAPTER_IMAGES, imagesJson)
            )
        }
        return chaptersJson
    }

    private fun clearMangaCache() {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .remove(KEY_ROOT_URI)
            .remove(KEY_MANGA_CACHE)
            .apply()
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
        val rootUri = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(KEY_ROOT_URI, null)

        if (rootUri == null) {
            checkPermissionAndOpenPicker()
            return
        }

        scanRootMangaFolderAsync(Uri.parse(rootUri))
    }

    private suspend fun scanRootMangaFolder(uri: Uri): List<MangaItem> = coroutineScope {
        try {
            val rootFolder = DocumentFile.fromTreeUri(this@MainActivity, uri)

            if (rootFolder == null || !rootFolder.exists()) {
                Log.e("MangaReader", "Root folder is null or doesn't exist")
                return@coroutineScope emptyList()
            }

            Log.d("MangaReader", "=== Scanning root folder: ${rootFolder.name} ===")

            val mangaFolders = try {
                rootFolder.listFiles()
                    .filter { it.isDirectory }
                    .sortedWith(naturalOrderComparator())
            } catch (e: Exception) {
                Log.e("MangaReader", "Error listing manga folders", e)
                emptyList()
            }

            Log.d("MangaReader", "Found ${mangaFolders.size} manga folders")
            mangaFolders.map { mangaFolder ->
                async(Dispatchers.IO) {
                    MangaItem(
                        name = mangaFolder.name ?: "Unknown Manga",
                        uri = mangaFolder.uri.toString(),
                        chapters = scanChapters(mangaFolder)
                    )
                }
            }.awaitAll()
        } catch (e: Exception) {
            Log.e("MangaReader", "Fatal error in scanRootMangaFolder", e)
            throw e
        }
    }

    private suspend fun scanChapters(mangaFolder: DocumentFile): List<Chapter> = coroutineScope {
        val allFiles = try {
            mangaFolder.listFiles().toList()
        } catch (e: Exception) {
            Log.e("MangaReader", "Error listing files for ${mangaFolder.name}", e)
            return@coroutineScope emptyList()
        }

        val chapterFolders = allFiles.filter { it.isDirectory }

        if (chapterFolders.isEmpty()) {
            val images = allFiles
                .filter { it.isFile && it.name?.isImageFile() == true }
                .sortedWith(naturalOrderComparator())

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

        chapterFolders
            .sortedWith(naturalOrderComparator())
            .map { chapterFolder ->
                async(Dispatchers.IO) {
                    val images = try {
                        chapterFolder.listFiles()
                            .filter { it.isFile && it.name?.isImageFile() == true }
                            .sortedWith(naturalOrderComparator())
                    } catch (e: Exception) {
                        Log.e("MangaReader", "Error scanning chapter ${chapterFolder.name}", e)
                        emptyList()
                    }

                    if (images.isNotEmpty()) {
                        Chapter(
                            name = chapterFolder.name ?: "Unknown Chapter",
                            uri = chapterFolder.uri.toString(),
                            images = images.map { it.uri.toString() }
                        )
                    } else {
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private fun openMangaChapters(manga: MangaItem) {
        val intent = Intent(this, ChaptersActivity::class.java)
        intent.putExtra("manga_name", manga.name)
        intent.putExtra("manga_uri", manga.uri)
        startActivity(intent)
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

    private fun String.isImageFile(): Boolean {
        return matches(Regex(".*\\.(png|jpg|jpeg|webp|gif)", RegexOption.IGNORE_CASE))
    }

    companion object {
        private const val PREFERENCES_NAME = "manga_library"
        private const val KEY_ROOT_URI = "root_uri"
        private const val KEY_MANGA_CACHE = "manga_cache"
        private const val KEY_MANGA_NAME = "name"
        private const val KEY_MANGA_URI = "uri"
        private const val KEY_MANGA_CHAPTERS = "chapters"
        private const val KEY_CHAPTER_NAME = "name"
        private const val KEY_CHAPTER_URI = "uri"
        private const val KEY_CHAPTER_IMAGES = "images"
    }
}

data class MangaItem(
    val name: String,
    val uri: String,
    val chapters: List<Chapter> = emptyList(),
    val progress: MangaReadingProgress? = null
)
