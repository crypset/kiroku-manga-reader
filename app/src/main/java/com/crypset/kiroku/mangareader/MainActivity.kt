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
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: MangaAdapter
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

        recyclerView = findViewById(R.id.chaptersRecyclerView)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = MangaAdapter(mangaList) { manga ->
            openMangaChapters(manga)
        }
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabSelectFolder).setOnClickListener {
            checkPermissionAndOpenPicker()
        }

        restoreCachedManga()
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
                == PackageManager.PERMISSION_GRANTED) {
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
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        scope.launch {
            try {
                val scannedManga = withContext(Dispatchers.IO) {
                    scanRootMangaFolderFast(uri)
                }

                mangaList.clear()
                mangaList.addAll(scannedManga)
                adapter.notifyDataSetChanged()
                saveMangaCache(uri, scannedManga)

                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                if (mangaList.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "No manga folders found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MangaReader", "Error scanning root folder", e)
                progressBar.visibility = View.GONE
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
            ?: return

        try {
            val cachedManga = buildList {
                val jsonArray = JSONArray(cachedMangaJson)
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(index)
                    add(
                        MangaItem(
                            name = item.getString(KEY_MANGA_NAME),
                            uri = item.getString(KEY_MANGA_URI)
                        )
                    )
                }
            }

            mangaList.clear()
            mangaList.addAll(cachedManga)
            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e("MangaReader", "Error restoring cached manga", e)
            clearMangaCache()
        }
    }

    private fun saveMangaCache(rootUri: Uri, manga: List<MangaItem>) {
        val jsonArray = JSONArray()
        manga.forEach { item ->
            jsonArray.put(
                JSONObject()
                    .put(KEY_MANGA_NAME, item.name)
                    .put(KEY_MANGA_URI, item.uri)
            )
        }

        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_URI, rootUri.toString())
            .putString(KEY_MANGA_CACHE, jsonArray.toString())
            .apply()
    }

    private fun clearMangaCache() {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .remove(KEY_ROOT_URI)
            .remove(KEY_MANGA_CACHE)
            .apply()
    }

    private fun scanRootMangaFolderFast(uri: Uri): List<MangaItem> {
        try {
            val rootFolder = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)

            if (rootFolder == null || !rootFolder.exists()) {
                Log.e("MangaReader", "Root folder is null or doesn't exist")
                return emptyList()
            }

            Log.d("MangaReader", "=== Scanning root folder: ${rootFolder.name} ===")

            // ШВИДКА обробка - просто отримуємо список папок без додаткових перевірок
            val mangaFolders = try {
                rootFolder.listFiles()
                    .filter { it.isDirectory }
                    .sortedWith(naturalOrderComparator())
                    .map { folder ->
                        MangaItem(
                            name = folder.name ?: "Unknown Manga",
                            uri = folder.uri.toString()
                        )
                    }
            } catch (e: Exception) {
                Log.e("MangaReader", "Error listing manga folders", e)
                emptyList()
            }

            Log.d("MangaReader", "Found ${mangaFolders.size} manga folders")
            return mangaFolders

        } catch (e: Exception) {
            Log.e("MangaReader", "Fatal error in scanRootMangaFolder", e)
            throw e
        }
    }

    private fun openMangaChapters(manga: MangaItem) {
        val intent = Intent(this, ChaptersActivity::class.java)
        intent.putExtra("manga_name", manga.name)
        intent.putExtra("manga_uri", manga.uri)
        startActivity(intent)
    }

    private fun naturalOrderComparator(): Comparator<androidx.documentfile.provider.DocumentFile> {
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

    companion object {
        private const val PREFERENCES_NAME = "manga_library"
        private const val KEY_ROOT_URI = "root_uri"
        private const val KEY_MANGA_CACHE = "manga_cache"
        private const val KEY_MANGA_NAME = "name"
        private const val KEY_MANGA_URI = "uri"
    }
}

data class MangaItem(val name: String, val uri: String)
