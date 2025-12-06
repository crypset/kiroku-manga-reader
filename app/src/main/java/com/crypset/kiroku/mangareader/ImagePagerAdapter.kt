package com.crypset.kiroku.mangareader

import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class ImagePagerAdapter(
    private val images: List<String>,
    private val onImageClick: () -> Unit,
    private val onScaleChange: (Boolean) -> Unit
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

        holder.imageView.apply {
            // Налаштування масштабів
            maximumScale = 4.0f
            mediumScale = 2.5f
            minimumScale = 1.0f

            // Скидаємо масштаб при зміні сторінки
            setScale(1.0f, false)

            // КРИТИЧНО: Дозволяємо PhotoView zoom
            isZoomable = true

            // Відстеження зміни масштабу
            setOnScaleChangeListener { _, _, _ ->
                val isZoomed = scale > minimumScale + 0.1f
                onScaleChange(isZoomed)

                // Блокуємо ViewPager якщо зумлено
                parent.parent?.requestDisallowInterceptTouchEvent(isZoomed)
            }

            // Обробник для блокування ViewPager при взаємодії з PhotoView
            setOnMatrixChangeListener {
                val isZoomed = scale > minimumScale + 0.1f
                if (isZoomed) {
                    parent.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }

        // Завантаження зображення
        Glide.with(holder.imageView.context)
            .load(imageUri)
            .fitCenter()
            .into(holder.imageView)

        // GestureDetector для одинарного і подвійного тапу
        val gestureDetector = GestureDetector(
            holder.imageView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onImageClick()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val x = e.x
                    val y = e.y

                    if (holder.imageView.scale <= holder.imageView.minimumScale + 0.1f) {
                        // Зумити
                        holder.imageView.setScale(holder.imageView.mediumScale, x, y, true)
                    } else {
                        // Вийти з зуму
                        holder.imageView.setScale(holder.imageView.minimumScale, true)
                    }
                    return true
                }
            }
        )

        // Власний OnTouchListener який НЕ блокує PhotoView
        holder.imageView.setOnTouchListener { view, event ->
            // Дозволяємо PhotoView обробити подію першим
            var handled = false

            // Якщо це multi-touch (2+ пальці) - PhotoView обробить pinch
            if (event.pointerCount > 1) {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                handled = view.onTouchEvent(event)
            } else {
                // Для одного пальця - спочатку перевіряємо тапи
                if (gestureDetector.onTouchEvent(event)) {
                    handled = true
                } else {
                    // Потім даємо PhotoView обробити (для panning)
                    handled = view.onTouchEvent(event)
                }
            }

            handled
        }
    }

    override fun getItemCount() = images.size
}