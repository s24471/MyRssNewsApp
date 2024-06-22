package com.example.myrssnewsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ProfileActivity : AppCompatActivity() {

    private lateinit var rssAdapter: RssAdapter
    private val rssItems = mutableListOf<RssItem>()
    private val readArticles = mutableSetOf<String>()
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val email = auth.currentUser?.email
        val tvUsername = findViewById<TextView>(R.id.tvUserEmail)
        tvUsername.text = email

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            logout()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.rvRssFeed)
        recyclerView.layoutManager = LinearLayoutManager(this)
        rssAdapter = RssAdapter(rssItems, readArticles) { rssItem ->
            onArticleClicked(rssItem)
        }
        recyclerView.adapter = rssAdapter

        fetchReadArticles()
    }

    private fun fetchReadArticles() {
        val userId = auth.currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val document = firestore.collection("readArticles").document(userId).get().await()
            val readLinks = document.get("links") as? List<String> ?: emptyList()
            readArticles.addAll(readLinks)
            fetchRssFeed()
        }
    }

    private fun fetchRssFeed() {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "https://wiadomosci.gazeta.pl/pub/rss/wiadomosci_kraj.htm"
            val doc = Jsoup.connect(url).get()
            val items = doc.select("item")

            items.forEach { item ->
                val title = item.select("title").text()
                val descriptionHtml = item.select("description").text()
                val descriptionDoc: Document = Jsoup.parse(descriptionHtml)
                val description = descriptionDoc.text()
                val imageUrl = descriptionDoc.select("img").attr("src")
                val link = item.select("link").text()
                val rssItem = RssItem(title, description, imageUrl, link)
                rssItems.add(rssItem)
            }

            withContext(Dispatchers.Main) {
                rssAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun onArticleClicked(rssItem: RssItem) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("URL", rssItem.link)
        startActivity(intent)

        // Oznacz artykuł jako przeczytany i zapisz w Firestore
        readArticles.add(rssItem.link)
        rssAdapter.notifyDataSetChanged()
        saveReadArticles()
    }

    private fun saveReadArticles() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("readArticles").document(userId)
            .set(mapOf("links" to readArticles.toList()))
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
