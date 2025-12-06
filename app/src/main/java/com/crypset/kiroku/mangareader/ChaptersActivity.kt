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
import kotlinx.coroutines.*

class ChaptersActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ChapterAdapter
    private val chapters = mutableListOf<Chapter>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapters)

        // Ініціалізація views ПЕРШОЮ
        recyclerView = findViewById(R.id.chaptersRecyclerView)
        progressBar = findViewById(R.id.progressBar)

        val mangaName = intent.getStringExtra("manga_name") ?: "Manga"
        val mangaUri = intent.getStringExtra("manga_uri")

        if (mangaUri == null) {
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

        // Сканування в фоновому потоці
        scanChaptersAsync(Uri.parse(mangaUri))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun scanChaptersAsync(mangaUri: Uri) {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        scope.launch {
            try {
                val scannedChapters = withContext(Dispatchers.IO) {
                    scanChapters(mangaUri)
                }

                chapters.clear()
                chapters.addAll(scannedChapters)
                adapter.notifyDataSetChanged()

                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                if (chapters.isEmpty()) {
                    Toast.makeText(
                        this@ChaptersActivity,
                        "No chapters found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MangaReader", "Error scanning chapters", e)
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@ChaptersActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun scanChapters(mangaUri: Uri): List<Chapter> {
        val resultChapters = mutableListOf<Chapter>()

        try {
            val mangaFolder = DocumentFile.fromTreeUri(this, mangaUri)

            if (mangaFolder == null || !mangaFolder.exists()) {
                Log.e("MangaReader", "Manga folder is null or doesn't exist")
                return emptyList()
            }

            Log.d("MangaReader", "=== Scanning chapters in: ${mangaFolder.name} ===")

            val allFiles = try {
                mangaFolder.listFiles().toList()
            } catch (e: Exception) {
                Log.e("MangaReader", "Error listing files", e)
                return emptyList()
            }

            val chapterFolders = allFiles.filter { it.isDirectory }
            Log.d("MangaReader", "Found ${chapterFolders.size} chapter folders")

            if (chapterFolders.isEmpty()) {
                // Якщо немає підпапок - це манга з одним розділом
                val images = allFiles
                    .filter {
                        it.isFile && it.name?.matches(Regex(".*\\.(png|jpg|jpeg)", RegexOption.IGNORE_CASE)) == true
                    }
                    .sortedWith(naturalOrderComparator())

                Log.d("MangaReader", "Single chapter mode: ${images.size} images")

                if (images.isNotEmpty()) {
                    resultChapters.add(Chapter(
                        mangaFolder.name ?: "Chapter",
                        images.map { it.uri.toString() }
                    ))
                }
            } else {
                // Сортуємо глави природним порядком
                val sortedChapters = chapterFolders.sortedWith(naturalOrderComparator())

                sortedChapters.forEach { chapterFolder ->
                    try {
                        val images = chapterFolder.listFiles()
                            .filter {
                                it.isFile && it.name?.matches(Regex(".*\\.(png|jpg|jpeg)", RegexOption.IGNORE_CASE)) == true
                            }
                            .sortedWith(naturalOrderComparator())

                        Log.d("MangaReader", "Chapter '${chapterFolder.name}': ${images.size} images")

                        if (images.isNotEmpty()) {
                            resultChapters.add(Chapter(
                                chapterFolder.name ?: "Unknown Chapter",
                                images.map { it.uri.toString() }
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("MangaReader", "Error scanning chapter ${chapterFolder.name}", e)
                    }
                }
            }

            Log.d("MangaReader", "Total chapters loaded: ${resultChapters.size}")
        } catch (e: Exception) {
            Log.e("MangaReader", "Fatal error in scanChapters", e)
            throw e
        }

        return resultChapters
    }

    private fun naturalOrderComparator(): Comparator<DocumentFile> {
        return Comparator { a, b ->
            try {
                compareNatural(a.name ?: "", b.name ?: "")
            } catch (e: Exception) {
                0
            }
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
                        val aNum = aPart.toIntOrNull() ?: 0
                        val bNum = bPart.toIntOrNull() ?: 0
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
        startActivity(intent)
    }
}

data class Chapter(val name: String, val images: List<String>)