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
    private val onImageClick: () -> Unit
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

        // Налаштування PhotoView для zoom
        holder.imageView.maximumScale = 5.0f
        holder.imageView.mediumScale = 2.5f
        holder.imageView.minimumScale = 1.0f

        Glide.with(holder.imageView.context)
            .load(imageUri)
            .fitCenter()
            .into(holder.imageView)

        holder.imageView.setOnClickListener {
            onImageClick()
        }
    }

    override fun getItemCount() = images.size
}