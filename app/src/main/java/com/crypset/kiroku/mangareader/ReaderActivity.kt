package com.crypset.kiroku.mangareader

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class ReaderActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var toolbar: View
    private lateinit var rotateButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var progressStore: ReadingProgressStore
    private var mangaName: String = ""
    private var mangaUri: String = ""
    private var chapterName: String = ""
    private var chapterUri: String = ""
    private var images: List<String> = emptyList()
    private var isToolbarVisible = true
    private var isLandscape = false
    private var isPageZoomed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        viewPager = findViewById(R.id.imageViewPager)
        pageIndicator = findViewById(R.id.pageIndicator)
        toolbar = findViewById(R.id.readerToolbar)
        rotateButton = findViewById(R.id.rotateButton)
        settingsButton = findViewById(R.id.settingsButton)
        progressStore = ReadingProgressStore(this)

        images = intent.getStringArrayListExtra("images") ?: arrayListOf()
        val title = intent.getStringExtra("title") ?: "Chapter"
        mangaName = intent.getStringExtra("manga_name") ?: ""
        mangaUri = intent.getStringExtra("manga_uri") ?: ""
        chapterName = intent.getStringExtra("chapter_name") ?: title
        chapterUri = intent.getStringExtra("chapter_uri") ?: ""

        supportActionBar?.title = title

        // Адаптер з callback для відстеження зуму
        val adapter = ImagePagerAdapter(
            images = images,
            onImageClick = { toggleToolbar() },
            onScaleChange = { isZoomed ->
                isPageZoomed = isZoomed
                viewPager.isUserInputEnabled = !isZoomed
            }
        )
        viewPager.adapter = adapter

        // КРИТИЧНО: Перехоплюємо touch events на рівні RecyclerView
        setupTouchInterceptor()

        // Callback для зміни сторінок
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position + 1, images.size)
                saveReadingProgress(position)
                // При зміні сторінки дозволяємо свайп
                isPageZoomed = false
                viewPager.isUserInputEnabled = true
            }
        })

        rotateButton.setOnClickListener {
            toggleOrientation()
        }

        settingsButton.setOnClickListener {
            showReaderSettings()
        }

        val initialPageIndex = progressStore.getChapterProgress(chapterUri)
            ?.pageIndex
            ?.coerceIn(0, (images.size - 1).coerceAtLeast(0))
            ?: 0

        if (images.isNotEmpty()) {
            updatePageIndicator(initialPageIndex + 1, images.size)
            viewPager.setCurrentItem(initialPageIndex, false)
            saveReadingProgress(initialPageIndex)
        } else {
            updatePageIndicator(0, 0)
        }
    }

    private fun setupTouchInterceptor() {
        // Отримуємо внутрішній RecyclerView з ViewPager2
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView

        recyclerView?.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // Якщо multi-touch (pinch) - дозволяємо PhotoView обробити
                if (e.pointerCount > 1) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    viewPager.isUserInputEnabled = false
                    return false
                }

                // Якщо сторінка зумлена - блокуємо ViewPager
                if (isPageZoomed) {
                    viewPager.isUserInputEnabled = false
                }

                return false
            }
        })
    }

    private fun updatePageIndicator(current: Int, total: Int) {
        pageIndicator.text = "$current / $total"
    }

    private fun toggleOrientation() {
        isLandscape = !isLandscape
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun toggleToolbar() {
        isToolbarVisible = !isToolbarVisible
        toolbar.visibility = if (isToolbarVisible) View.VISIBLE else View.GONE

        if (isToolbarVisible) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun saveReadingProgress(pageIndex: Int) {
        progressStore.saveProgress(
            mangaUri = mangaUri,
            mangaName = mangaName,
            chapterUri = chapterUri,
            chapterName = chapterName,
            pageIndex = pageIndex,
            totalPages = images.size
        )
    }

    private fun showReaderSettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reader_settings_title)
            .setMessage(R.string.reader_settings_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear_reading_progress_action) { _, _ ->
                progressStore.clearAll()
                Toast.makeText(this, R.string.reading_progress_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
