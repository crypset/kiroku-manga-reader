package com.crypset.kiroku.mangareader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private val onChapterClick: (Chapter) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chapterName: TextView = view.findViewById(R.id.chapterName)
        val pageCount: TextView = view.findViewById(R.id.pageCount)
        val statusChip: TextView = view.findViewById(R.id.statusChip)
        val itemIcon: ImageView = view.findViewById(R.id.itemIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters[position]
        val context = holder.itemView.context
        holder.chapterName.text = chapter.name
        holder.itemIcon.setImageResource(R.drawable.ic_book)
        holder.pageCount.text = chapter.progress?.let { progress ->
            when {
                progress.completed -> "${chapter.images.size} pages - Read"
                progress.totalPages > 0 -> "${chapter.images.size} pages - Page ${progress.pageNumber}/${progress.totalPages}"
                else -> "${chapter.images.size} pages - Started"
            }
        } ?: "${chapter.images.size} pages"
        holder.statusChip.visibility = View.VISIBLE
        holder.statusChip.text = chapter.progress?.let { progress ->
            when {
                progress.completed -> context.getString(R.string.item_status_read)
                progress.totalPages > 0 -> "Page ${progress.pageNumber}"
                else -> context.getString(R.string.item_status_started)
            }
        } ?: context.getString(R.string.item_status_new)

        holder.itemView.setOnClickListener {
            onChapterClick(chapter)
        }
    }

    override fun getItemCount() = chapters.size
}
