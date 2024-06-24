package com.example.myrssnewsapp

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    private lateinit var articleUrl: String
    private lateinit var articleTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        val webView = findViewById<WebView>(R.id.webView)
        webView.webViewClient = WebViewClient()

        articleUrl = intent.getStringExtra("URL") ?: "https://www.example.com"
        articleTitle = intent.getStringExtra("TITLE") ?: "Article"

        webView.loadUrl(articleUrl)

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val btnShare = findViewById<Button>(R.id.btnShare)
        btnShare.setOnClickListener {
            shareArticle()
        }
    }

    private fun shareArticle() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "$articleTitle\n\n$articleUrl")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share article via"))
    }
}
