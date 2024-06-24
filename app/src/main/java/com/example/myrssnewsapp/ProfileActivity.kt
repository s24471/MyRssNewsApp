package com.example.myrssnewsapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class ProfileActivity : AppCompatActivity() {

    private lateinit var rssAdapter: RssAdapter
    private val rssItems = mutableListOf<RssItem>()
    private val readArticles = mutableSetOf<String>()
    private val notifiedArticles = mutableSetOf<String>()
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val email = auth.currentUser?.email
        val tvUserEmail = findViewById<TextView>(R.id.tvUserEmail)
        tvUserEmail.text = email

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            logout()
        }

        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        btnRefresh.setOnClickListener {
            refreshRssFeed()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.rvRssFeed)
        recyclerView.layoutManager = LinearLayoutManager(this)
        rssAdapter = RssAdapter(this, rssItems, readArticles) { rssItem ->
            onArticleClicked(rssItem)
        }
        recyclerView.adapter = rssAdapter

        fetchNotifiedArticles()
        fetchReadArticles()
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

    private fun fetchNotifiedArticles() {
        val userId = auth.currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val document = firestore.collection("notifiedArticles").document(userId).get().await()
            val notifiedLinks = document.get("links") as? List<String> ?: emptyList()
            notifiedArticles.addAll(notifiedLinks)
        }
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

            rssItems.clear() // Clear the list before adding new items

            val newNotifiedArticles = mutableListOf<String>()
            items.forEach { item ->
                val title = item.select("title").text()
                val descriptionHtml = item.select("description").text()
                val descriptionDoc: Document = Jsoup.parse(descriptionHtml)
                val description = descriptionDoc.text()
                val imageUrl = descriptionDoc.select("img").attr("src")
                val link = item.select("link").text()
                val rssItem = RssItem(title, description, imageUrl, link)
                rssItems.add(rssItem)
                newNotifiedArticles.add(link)
            }

            updateNotifiedArticles(newNotifiedArticles)

            withContext(Dispatchers.Main) {
                prefetchImages()
            }
        }
    }

    private fun updateNotifiedArticles(newNotifiedArticles: List<String>) {
        val userId = auth.currentUser?.uid ?: return

        notifiedArticles.addAll(newNotifiedArticles)
        firestore.collection("notifiedArticles").document(userId)
            .set(mapOf("links" to notifiedArticles.toList()))
    }

    private fun prefetchImages() {
        CoroutineScope(Dispatchers.IO).launch {
            rssItems.forEach { item ->
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
            }
        }
    }

    private fun refreshRssFeed() {
        fetchRssFeed() // Re-fetch the RSS feed
    }

    private fun onArticleClicked(rssItem: RssItem) {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("URL", rssItem.link)
            putExtra("TITLE", rssItem.title)
        }
        startActivity(intent)

        // Mark article as read and save in Firestore
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
