package com.noobdev.waffle.utils

import android.content.Context
import java.io.IOException

// This function reads a file from your "assets" folder and turns it into a String
fun loadJsonFromAsset(context: Context, fileName: String): String? {
    return try {
        // Open the file in assets/disaster_data/
        val inputStream = context.assets.open("disaster_data/$fileName")
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        // Convert bytes to String
        String(buffer, Charsets.UTF_8)
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}