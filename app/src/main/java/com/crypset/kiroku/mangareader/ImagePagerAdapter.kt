package com.crypset.kiroku.mangareader

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class ImagePagerAdapter(
    private val images: List<String>,
    private val onImageClick: () -> Unit,
    private val onScaleChanged: (Float) -> Unit
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: PhotoView = view.findViewById(R.id.mangaImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manga_page, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUri = Uri.parse(images[position])

        // Налаштування PhotoView
        holder.imageView.apply {
            maximumScale = 5.0f
            mediumScale = 2.5f
            minimumScale = 1.0f

            // Скидання зуму при зміні сторінки
            setScale(1.0f, false)

            // Відстеження зміни масштабу
            setOnScaleChangeListener { scaleFactor, _, _ ->
                onScaleChanged(scaleFactor)
            }

            // Клік для приховування/показу тулбару
            setOnClickListener {
                onImageClick()
            }

            // Подвійний тап для зуму
            setOnDoubleTapListener(object : android.view.GestureDetector.OnDoubleTapListener {
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    val currentScale = scale
                    if (currentScale >= mediumScale) {
                        // Якщо зумлено - повернутись до 1.0
                        setScale(1.0f, e.x, e.y, true)
                    } else {
                        // Якщо не зумлено - зумити до mediumScale
                        setScale(mediumScale, e.x, e.y, true)
                    }
                    return true
                }

                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    onImageClick()
                    return true
                }

                override fun onDoubleTapEvent(e: android.view.MotionEvent): Boolean {
                    return false
                }
            })
        }

        // Завантаження зображення
        Glide.with(holder.imageView.context)
            .load(imageUri)
            .fitCenter()
            .into(holder.imageView)
    }

    override fun getItemCount() = images.size
}