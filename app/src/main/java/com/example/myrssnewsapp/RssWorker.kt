package com.example.myrssnewsapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class RssWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        Log.d("RssWorker", "Worker started")

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.d("RssWorker", "User not logged in")
            return Result.failure()
        }

        val document = firestore.collection("readArticles").document(userId).get().await()
        val readLinks = document.get("links") as? List<String> ?: emptyList()
        val readArticles = readLinks.toSet()

        val notifiedDocument = firestore.collection("notifiedArticles").document(userId).get().await()
        val notifiedLinks = notifiedDocument.get("links") as? List<String> ?: emptyList()
        val notifiedArticles = notifiedLinks.toMutableSet()

        val url = "https://wiadomosci.gazeta.pl/pub/rss/wiadomosci_kraj.htm"
        val doc = Jsoup.connect(url).get()
        val items = doc.select("item")

        val newArticles = mutableListOf<RssItem>()

        for (item in items) {
            val link = item.select("link").text()
            if (!readArticles.contains(link) && !notifiedArticles.contains(link)) {
                val title = item.select("title").text()
                val descriptionHtml = item.select("description").text()
                val descriptionDoc = Jsoup.parse(descriptionHtml)
                val description = descriptionDoc.text()
                val imageUrl = descriptionDoc.select("img").attr("src")
                val rssItem = RssItem(title, description, imageUrl, link)
                newArticles.add(rssItem)
                notifiedArticles.add(link)
            }
        }

        if (newArticles.isNotEmpty()) {
            newArticles.forEachIndexed { index, article ->
                showNotification(index, article.title, article.link)
            }
            firestore.collection("notifiedArticles").document(userId)
                .set(mapOf("links" to notifiedArticles.toList()))
        }

        scheduleNextCheck()

        return Result.success()
    }

    private fun scheduleNextCheck() {
        val nextWorkRequest = OneTimeWorkRequestBuilder<RssWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "RssWorker",
            ExistingWorkPolicy.REPLACE,
            nextWorkRequest
        )

        Log.d("RssWorker", "Scheduled next check")
    }

    private fun showNotification(notificationId: Int, title: String, link: String) {
        Log.d("RssWorker", "Showing notification: $title")

        createNotificationChannel()

        val intent = Intent(applicationContext, WebViewActivity::class.java).apply {
            putExtra("URL", link)
            putExtra("TITLE", title)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(applicationContext, "rss_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New Article Available")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("RssWorker", "Notification permission not granted")
                return
            }
            notify(notificationId, notificationBuilder.build())
            Log.d("RssWorker", "Notification sent")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "RSS Channel"
            val descriptionText = "Channel for RSS feed notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("rss_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("RssWorker", "Notification channel created")
        }
    }
}
