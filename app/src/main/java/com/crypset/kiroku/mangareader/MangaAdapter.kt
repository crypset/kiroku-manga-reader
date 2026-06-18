package com.crypset.kiroku.mangareader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MangaAdapter(
    private val mangaList: List<MangaItem>,
    private val onMangaClick: (MangaItem) -> Unit
) : RecyclerView.Adapter<MangaAdapter.MangaViewHolder>() {

    class MangaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mangaName: TextView = view.findViewById(R.id.chapterName)
        val subtitle: TextView = view.findViewById(R.id.pageCount)
        val statusChip: TextView = view.findViewById(R.id.statusChip)
        val itemIcon: ImageView = view.findViewById(R.id.itemIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return MangaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        val manga = mangaList[position]
        val context = holder.itemView.context
        holder.mangaName.text = manga.name
        holder.itemIcon.setImageResource(R.drawable.ic_book)
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
        } ?: context.getString(R.string.item_default_subtitle)
        holder.statusChip.visibility = View.VISIBLE
        holder.statusChip.text = if (manga.progress == null) {
            context.getString(R.string.item_status_new)
        } else {
            context.getString(R.string.item_status_continue)
        }

        holder.itemView.setOnClickListener {
            onMangaClick(manga)
        }
    }

    override fun getItemCount() = mangaList.size
}
