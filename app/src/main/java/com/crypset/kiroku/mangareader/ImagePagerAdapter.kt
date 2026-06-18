package com.crypset.kiroku.mangareader

import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.min
import kotlin.math.roundToInt

class ImagePagerAdapter(
    private val images: List<String>,
    private var pageContainerWidthPercent: Int,
    private val onImageClick: () -> Unit,
    private val onScaleChange: (Boolean) -> Unit
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pageContainer: FrameLayout = view.findViewById(R.id.mangaPageContainer)
        val imageView: PhotoView = view.findViewById(R.id.mangaImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manga_page, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUri = Uri.parse(images[position])

        applyPageContainerWidth(holder)

        holder.imageView.apply {
            isZoomable = true

            setOnScaleChangeListener { _, _, _ ->
                val isZoomed = scale > minimumScale + 0.1f
                onScaleChange(isZoomed)
                parent.parent?.requestDisallowInterceptTouchEvent(isZoomed)
            }

            setOnMatrixChangeListener {
                val isZoomed = scale > minimumScale + 0.1f
                if (isZoomed) {
                    parent.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }

        Glide.with(holder.imageView.context)
            .load(imageUri)
            .fitCenter()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    holder.imageView.post {
                        configureZoomToFillWidth(holder.imageView)
                    }
                    return false
                }
            })
            .into(holder.imageView)

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
                        holder.imageView.setScale(holder.imageView.mediumScale, x, y, true)
                    } else {
                        holder.imageView.setScale(holder.imageView.minimumScale, true)
                    }
                    return true
                }
            }
        )

        holder.imageView.setOnTouchListener { view, event ->
            var handled = false

            if (event.pointerCount > 1) {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                handled = view.onTouchEvent(event)
            } else {
                handled = if (gestureDetector.onTouchEvent(event)) {
                    true
                } else {
                    view.onTouchEvent(event)
                }
            }

            handled
        }
    }

    override fun getItemCount() = images.size

    fun updatePageContainerWidthPercent(widthPercent: Int) {
        pageContainerWidthPercent = widthPercent
        notifyDataSetChanged()
    }

    private fun applyPageContainerWidth(holder: ImageViewHolder) {
        val parentWidth = holder.itemView.width
        if (parentWidth <= 0) {
            holder.itemView.post { applyPageContainerWidth(holder) }
            return
        }

        val targetWidth = (parentWidth * (pageContainerWidthPercent / 100f))
            .roundToInt()
            .coerceAtLeast(1)
        val layoutParams = holder.pageContainer.layoutParams as FrameLayout.LayoutParams

        if (layoutParams.width != targetWidth) {
            layoutParams.width = targetWidth
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.gravity = Gravity.CENTER
            holder.pageContainer.layoutParams = layoutParams
        }
    }

    private fun configureZoomToFillWidth(imageView: PhotoView) {
        val drawable = imageView.drawable ?: return
        val viewWidth = imageView.width
        val viewHeight = imageView.height
        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight

        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            imageView.post { configureZoomToFillWidth(imageView) }
            return
        }

        val widthScale = viewWidth / imageWidth.toFloat()
        val heightScale = viewHeight / imageHeight.toFloat()
        val baseFitCenterScale = min(widthScale, heightScale)

        if (baseFitCenterScale <= 0f) return

        val minimumScale = (widthScale / baseFitCenterScale).coerceAtLeast(1f)
        val mediumScale = minimumScale * 2f
        val maximumScale = minimumScale * 4f

        imageView.maximumScale = maximumScale
        imageView.mediumScale = mediumScale
        imageView.minimumScale = minimumScale
        imageView.setScale(minimumScale, false)
        onScaleChange(false)
    }
}
