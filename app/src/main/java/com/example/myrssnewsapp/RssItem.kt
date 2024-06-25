package com.example.myrssnewsapp

import android.graphics.Bitmap

data class RssItem(
    val title: String,
    val description: String,
    val imageUrl: String,
    val link: String,
    var imageBitmap: Bitmap? = null
)
