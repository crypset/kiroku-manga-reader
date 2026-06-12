package com.crypset.kiroku.mangareader

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class ReaderActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ImagePagerAdapter
    private lateinit var pageIndicator: TextView
    private lateinit var toolbar: View
    private lateinit var rotateButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private var isToolbarVisible = true
    private var isLandscape = false
    private var currentScale = 1.0f
    private var containerWidthPercent = DEFAULT_CONTAINER_WIDTH_PERCENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        viewPager = findViewById(R.id.imageViewPager)
        pageIndicator = findViewById(R.id.pageIndicator)
        toolbar = findViewById(R.id.readerToolbar)
        rotateButton = findViewById(R.id.rotateButton)
        settingsButton = findViewById(R.id.settingsButton)
        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        containerWidthPercent = getPreferences(MODE_PRIVATE).getInt(
            PREF_CONTAINER_WIDTH_PERCENT,
            DEFAULT_CONTAINER_WIDTH_PERCENT
        )

        val images = intent.getStringArrayListExtra("images") ?: arrayListOf()
        val title = intent.getStringExtra("title") ?: "Chapter"

        supportActionBar?.title = title

        // Адаптер з обробкою масштабу
        adapter = ImagePagerAdapter(
            images = images,
            onImageClick = { toggleToolbar() },
            onScaleChanged = { scale -> handleScaleChange(scale) },
            fillContainerWidth = isLandscape
        )
        viewPager.adapter = adapter
        applyContainerWidth()

        // Важливо: блокуємо ViewPager2 при зумі
        viewPager.isUserInputEnabled = true

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position + 1, images.size)
            }
        })

        rotateButton.setOnClickListener {
            toggleOrientation()
        }
        settingsButton.setOnClickListener {
            showReaderSettings()
        }

        updatePageIndicator(1, images.size)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        adapter.setFillContainerWidth(isLandscape)
        applyContainerWidth()
    }

    private fun handleScaleChange(scale: Float) {
        currentScale = scale
        // Блокуємо свайпи ViewPager2 коли зображення зумлене
        viewPager.isUserInputEnabled = scale <= 1.01f
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

    private fun showReaderSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val valueLabel = TextView(this).apply {
            text = getString(R.string.reader_container_width_value, containerWidthPercent)
        }
        val seekBar = SeekBar(this).apply {
            max = MAX_CONTAINER_WIDTH_PERCENT - MIN_CONTAINER_WIDTH_PERCENT
            progress = containerWidthPercent - MIN_CONTAINER_WIDTH_PERCENT
        }
        content.addView(valueLabel)
        content.addView(
            seekBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueLabel.text = getString(
                    R.string.reader_container_width_value,
                    progress + MIN_CONTAINER_WIDTH_PERCENT
                )
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.reader_settings_title)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                containerWidthPercent = seekBar.progress + MIN_CONTAINER_WIDTH_PERCENT
                getPreferences(MODE_PRIVATE).edit()
                    .putInt(PREF_CONTAINER_WIDTH_PERCENT, containerWidthPercent)
                    .apply()
                applyContainerWidth()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyContainerWidth() {
        viewPager.post {
            val parentWidth = (viewPager.parent as View).width
            val width = (parentWidth * containerWidthPercent / 100f).toInt()
            viewPager.layoutParams = (viewPager.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
                this.width = width
                addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL)
            }
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

    companion object {
        private const val PREF_CONTAINER_WIDTH_PERCENT = "container_width_percent"
        private const val DEFAULT_CONTAINER_WIDTH_PERCENT = 100
        private const val MIN_CONTAINER_WIDTH_PERCENT = 50
        private const val MAX_CONTAINER_WIDTH_PERCENT = 100
    }
}
