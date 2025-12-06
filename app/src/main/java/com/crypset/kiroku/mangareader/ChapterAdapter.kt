package com.crypset.kiroku.mangareader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private val onChapterClick: (Chapter) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chapterName: TextView = view.findViewById(R.id.chapterName)
        val pageCount: TextView = view.findViewById(R.id.pageCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters[position]
        holder.chapterName.text = chapter.name
        holder.pageCount.text = "${chapter.images.size} pages"

        holder.itemView.setOnClickListener {
            onChapterClick(chapter)
        }
    }

    override fun getItemCount() = chapters.size
}