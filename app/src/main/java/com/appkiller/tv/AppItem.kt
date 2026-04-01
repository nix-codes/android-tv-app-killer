package com.appkiller.tv

import android.graphics.drawable.Drawable

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    var isSelected: Boolean = false,
    var isWhitelisted: Boolean = false
)
