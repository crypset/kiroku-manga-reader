package com.crypset.kiroku.mangareader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MangaAdapter(
    private val mangaList: List<MangaItem>,
    private val onMangaClick: (MangaItem) -> Unit
) : RecyclerView.Adapter<MangaAdapter.MangaViewHolder>() {

    class MangaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mangaName: TextView = view.findViewById(R.id.chapterName)
        val subtitle: TextView = view.findViewById(R.id.pageCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return MangaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        val manga = mangaList[position]
        holder.mangaName.text = manga.name
        holder.subtitle.text = manga.progress?.let { progress ->
            val pageText = if (progress.lastTotalPages > 0) {
                "page ${progress.lastPageNumber}/${progress.lastTotalPages}"
            } else {
                "page ${progress.lastPageNumber}"
            }
            val chapterText = if (progress.startedChapters > 1 || progress.completedChapters > 0) {
                " - ${progress.completedChapters}/${progress.startedChapters} read"
            } else {
                ""
            }
            "Last read: ${progress.lastChapterName}, $pageText$chapterText"
        } ?: "Tap to view chapters"

        holder.itemView.setOnClickListener {
            onMangaClick(manga)
        }
    }

    override fun getItemCount() = mangaList.size
}
