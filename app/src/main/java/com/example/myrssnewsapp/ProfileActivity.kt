package com.example.myrssnewsapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class ProfileActivity : AppCompatActivity() {

    private lateinit var rssAdapter: RssAdapter
    private lateinit var favRssAdapter: RssAdapter
    private val rssItems = mutableListOf<RssItem>()
    private val favRssItems = mutableListOf<RssItem>()
    private val readArticles = mutableSetOf<String>()
    private val favoriteArticles = mutableSetOf<String>()
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private var showingFavorites = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val email = auth.currentUser?.email
        findViewById<TextView>(R.id.tvUserEmail).text = email

        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshRssFeed() }

        findViewById<Button>(R.id.btnAllArticles).setOnClickListener {
            showingFavorites = false
            recyclerView.adapter = rssAdapter
            rssAdapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btnFavArticles).setOnClickListener {
            showingFavorites = true
            recyclerView.adapter = favRssAdapter
            favRssAdapter.notifyDataSetChanged()
        }

        recyclerView = findViewById(R.id.rvRssFeed)
        recyclerView.layoutManager = LinearLayoutManager(this)
        rssAdapter = RssAdapter(this, rssItems, readArticles, favoriteArticles, ::onArticleClicked, ::onArticleFavorited)
        favRssAdapter = RssAdapter(this, favRssItems, readArticles, favoriteArticles, ::onArticleClicked, ::onArticleFavorited)
        recyclerView.adapter = rssAdapter

        CoroutineScope(Dispatchers.Main).launch {
            fetchUserData()
            refreshRssFeed()
        }
        setupInitialWork()
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }

    private suspend fun fetchUserData() {
        val userId = auth.currentUser?.uid ?: return
        val favoriteArticlesDocument = firestore.collection("favoriteArticles").document(userId).get().await()
        val readArticlesDocument = firestore.collection("readArticles").document(userId).get().await()

        favoriteArticles.clear()
        favoriteArticles.addAll(favoriteArticlesDocument.get("links") as? List<String> ?: emptyList())

        readArticles.clear()
        readArticles.addAll(readArticlesDocument.get("links") as? List<String> ?: emptyList())
    }

    private fun refreshRssFeed() {
        CoroutineScope(Dispatchers.IO).launch {
            val newRssItems = fetchRssItems()
            updateRssItems(newRssItems)
        }
    }

    private suspend fun fetchRssItems(): List<RssItem> {
        val url = "https://wiadomosci.gazeta.pl/pub/rss/wiadomosci_kraj.htm"
        val doc = Jsoup.connect(url).get()
        val items = doc.select("item")

        return items.map { item ->
            val link = item.select("link").text()
            val title = item.select("title").text()
            val descriptionHtml = item.select("description").text()
            val descriptionDoc: Document = Jsoup.parse(descriptionHtml)
            val description = descriptionDoc.text()
            val imageUrl = descriptionDoc.select("img").attr("src")
            RssItem(title, description, imageUrl, link)
        }
    }

    private suspend fun updateRssItems(newRssItems: List<RssItem>) {
        withContext(Dispatchers.Main) {
            rssItems.clear()
            rssItems.addAll(newRssItems)

            favRssItems.clear()
            favRssItems.addAll(newRssItems.filter { favoriteArticles.contains(it.link) })

            prefetchImages(newRssItems)
        }
    }

    private fun prefetchImages(items: List<RssItem>) {
        CoroutineScope(Dispatchers.IO).launch {
            items.forEach { item ->
                try {
                    if (item.imageUrl.isNotEmpty()) {
                        val bitmap = Picasso.get().load(item.imageUrl).get()
                        item.imageBitmap = bitmap
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                rssAdapter.notifyDataSetChanged()
                favRssAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun onArticleClicked(rssItem: RssItem) {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("URL", rssItem.link)
            putExtra("TITLE", rssItem.title)
        }
        startActivity(intent)

        readArticles.add(rssItem.link)
        saveReadArticles()
        rssAdapter.notifyDataSetChanged()
        favRssAdapter.notifyDataSetChanged()
    }

    private fun onArticleFavorited(rssItem: RssItem, isFavorited: Boolean) {
        if (isFavorited) {
            favoriteArticles.add(rssItem.link)
            favRssItems.add(rssItem)
        } else {
            favoriteArticles.remove(rssItem.link)
            favRssItems.remove(rssItem)
        }
        saveFavoriteArticles()
        rssAdapter.notifyDataSetChanged()
        favRssAdapter.notifyDataSetChanged()
    }

    private fun saveReadArticles() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("readArticles").document(userId)
            .set(mapOf("links" to readArticles.toList()))
    }

    private fun saveFavoriteArticles() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("favoriteArticles").document(userId)
            .set(mapOf("links" to favoriteArticles.toList()))
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun setupInitialWork() {
        val initialWorkRequest = OneTimeWorkRequestBuilder<RssWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "RssWorker",
            ExistingWorkPolicy.REPLACE,
            initialWorkRequest
        )
        Log.d("ProfileActivity", "Initial work setup")
    }
}
