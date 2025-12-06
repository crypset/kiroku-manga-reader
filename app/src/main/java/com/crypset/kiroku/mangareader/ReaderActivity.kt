package com.crypset.kiroku.mangareader

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class ReaderActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var toolbar: View
    private lateinit var rotateButton: ImageButton
    private var isToolbarVisible = true
    private var isLandscape = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        viewPager = findViewById(R.id.imageViewPager)
        pageIndicator = findViewById(R.id.pageIndicator)
        toolbar = findViewById(R.id.readerToolbar)
        rotateButton = findViewById(R.id.rotateButton)

        val images = intent.getStringArrayListExtra("images") ?: arrayListOf()
        val title = intent.getStringExtra("title") ?: "Chapter"

        supportActionBar?.title = title

        val adapter = ImagePagerAdapter(images) {
            toggleToolbar()
        }
        viewPager.adapter = adapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position + 1, images.size)
            }
        })

        rotateButton.setOnClickListener {
            toggleOrientation()
        }

        updatePageIndicator(1, images.size)
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
}