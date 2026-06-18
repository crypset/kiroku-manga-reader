package com.crypset.kiroku.mangareader

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
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
    private lateinit var settingsButton: ImageButton
    private lateinit var imagePagerAdapter: ImagePagerAdapter
    private lateinit var progressStore: ReadingProgressStore
    private lateinit var readerSettingsStore: ReaderSettingsStore
    private var mangaName: String = ""
    private var mangaUri: String = ""
    private var chapterName: String = ""
    private var chapterUri: String = ""
    private var images: List<String> = emptyList()
    private var pageContainerWidthPercent = ReaderSettingsStore.DEFAULT_PAGE_CONTAINER_WIDTH_PERCENT
    private var isToolbarVisible = true
    private var isPageZoomed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        viewPager = findViewById(R.id.imageViewPager)
        pageIndicator = findViewById(R.id.pageIndicator)
        toolbar = findViewById(R.id.readerToolbar)
        settingsButton = findViewById(R.id.settingsButton)
        progressStore = ReadingProgressStore(this)
        readerSettingsStore = ReaderSettingsStore(this)
        pageContainerWidthPercent = readerSettingsStore.pageContainerWidthPercent

        val readerArgs = MangaReaderIntents.readerArgs(intent)
        images = readerArgs.images
        val title = readerArgs.title
        mangaName = readerArgs.mangaName
        mangaUri = readerArgs.mangaUri
        chapterName = readerArgs.chapterName
        chapterUri = readerArgs.chapterUri

        supportActionBar?.title = title

        // Адаптер з callback для відстеження зуму
        imagePagerAdapter = ImagePagerAdapter(
            images = images,
            pageContainerWidthPercent = pageContainerWidthPercent,
            onImageClick = { toggleToolbar() },
            onScaleChange = { isZoomed ->
                isPageZoomed = isZoomed
                viewPager.isUserInputEnabled = !isZoomed
            }
        )
        viewPager.adapter = imagePagerAdapter

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
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(4))
        }
        val descriptionView = TextView(this).apply {
            setText(R.string.reader_settings_message)
            setPadding(0, 0, 0, dp(16))
        }
        val widthValueView = TextView(this).apply {
            text = getString(R.string.reader_container_width_value, pageContainerWidthPercent)
            setPadding(0, 0, 0, dp(8))
        }
        val widthSeekBar = SeekBar(this).apply {
            max = ReaderSettingsStore.MAX_PAGE_CONTAINER_WIDTH_PERCENT -
                    ReaderSettingsStore.MIN_PAGE_CONTAINER_WIDTH_PERCENT
            progress = pageContainerWidthPercent -
                    ReaderSettingsStore.MIN_PAGE_CONTAINER_WIDTH_PERCENT
        }

        widthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return

                val widthPercent = ReaderSettingsStore.MIN_PAGE_CONTAINER_WIDTH_PERCENT + progress
                widthValueView.text = getString(
                    R.string.reader_container_width_value,
                    widthPercent
                )
                updatePageContainerWidth(widthPercent)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        dialogView.addView(descriptionView)
        dialogView.addView(widthValueView)
        dialogView.addView(widthSeekBar)

        AlertDialog.Builder(this)
            .setTitle(R.string.reader_settings_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.ok, null)
            .setPositiveButton(R.string.clear_reading_progress_action) { _, _ ->
                progressStore.clearAll()
                Toast.makeText(this, R.string.reading_progress_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updatePageContainerWidth(widthPercent: Int) {
        pageContainerWidthPercent = widthPercent.coerceIn(
            ReaderSettingsStore.MIN_PAGE_CONTAINER_WIDTH_PERCENT,
            ReaderSettingsStore.MAX_PAGE_CONTAINER_WIDTH_PERCENT
        )
        readerSettingsStore.pageContainerWidthPercent = pageContainerWidthPercent
        imagePagerAdapter.updatePageContainerWidthPercent(pageContainerWidthPercent)
        isPageZoomed = false
        viewPager.isUserInputEnabled = true
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
