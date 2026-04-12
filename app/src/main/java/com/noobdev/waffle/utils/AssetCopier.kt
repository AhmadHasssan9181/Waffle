package com.noobdev.waffle.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream



//This shit is for when app starts like when
// boots up it capies the graph-cache into device internal storage and shit



object AssetCopier {
    private const val TAG = "AssetCopier"

    fun copyGraphCacheToStorage(context: Context): File {
        val destFolder = File(context.filesDir, "graph-cache")
        val assetFiles = context.assets.list("graph-cache").orEmpty()

        Log.d(TAG, "Preparing GraphHopper assets. assetCount=${assetFiles.size}")

        // Only copy if it doesn't exist or is empty
        if (!destFolder.exists() || destFolder.listFiles()?.isEmpty() == true) {
            destFolder.mkdirs()
            Log.d(TAG, "Copying graph-cache into ${destFolder.absolutePath}")

            assetFiles.forEach { filename ->
                val inputStream = context.assets.open("graph-cache/$filename")
                val outFile = File(destFolder, filename)
                val outputStream = FileOutputStream(outFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()

                Log.d(
                        TAG,
                        "Copied $filename size=${outFile.length()} path=${outFile.absolutePath}"
                )
            }
        } else {
            Log.d(
                    TAG,
                    "graph-cache already present in ${destFolder.absolutePath}, skipping copy"
            )
        }

        val copiedFiles = destFolder.listFiles().orEmpty().sortedBy { it.name }
        Log.d(
                TAG,
                "GraphHopper storage ready. fileCount=${copiedFiles.size} files=${copiedFiles.joinToString { "${it.name}:${it.length()}" }}"
        )

        return destFolder
    }
}
