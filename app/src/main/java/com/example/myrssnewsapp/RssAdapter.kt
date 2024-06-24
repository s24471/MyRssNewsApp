package com.example.myrssnewsapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class RssAdapter(private val context: Context, private val items: List<RssItem>, private val readArticles: Set<String>, private val onItemClicked: (RssItem) -> Unit) : RecyclerView.Adapter<RssAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.tvTitle)
        val descriptionTextView: TextView = view.findViewById(R.id.tvDescription)
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val shareButton: Button = view.findViewById(R.id.btnShare)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rss, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleTextView.text = item.title
        holder.descriptionTextView.text = item.description
        if (item.imageBitmap != null) {
            holder.imageView.setImageBitmap(item.imageBitmap)
        } else {
            holder.imageView.setImageResource(R.drawable.placeholder)
        }

        if (readArticles.contains(item.link)) {
            holder.itemView.setBackgroundColor(Color.LTGRAY)
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            onItemClicked(item)
        }

        holder.shareButton.setOnClickListener {
            shareArticle(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun shareArticle(item: RssItem) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "${item.title}\n\n${item.link}")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share article via"))
    }
}
