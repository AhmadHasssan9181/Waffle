package com.noobdev.waffle.core.storage

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Copies the read-only GraphHopper graph out of assets into app storage where the engine can open
 * it as a normal file.
 *
 * A `.copy_complete` marker makes the copy idempotent: an interrupted first run is detected and
 * retried instead of handing GraphHopper a half-written graph.
 */
object AssetCopier {

    private const val TAG = "AssetCopier"
    private const val GRAPH_ASSET_DIR = "graph-cache"
    private const val COMPLETE_MARKER = ".copy_complete"
    private const val BUFFER_SIZE = 64 * 1024

    fun copyGraphCacheToStorage(context: Context): File {
        val destination = File(context.filesDir, GRAPH_ASSET_DIR)
        val marker = File(destination, COMPLETE_MARKER)

        if (destination.exists() && marker.exists()) {
            Log.d(TAG, "Graph cache already present at ${destination.absolutePath}")
            return destination
        }

        if (destination.exists()) {
            Log.w(TAG, "Removing incomplete graph cache at ${destination.absolutePath}")
            destination.deleteRecursively()
        }
        destination.mkdirs()

        Log.d(TAG, "Copying assets/$GRAPH_ASSET_DIR into ${destination.absolutePath}")
        copyAssetDirectory(context, GRAPH_ASSET_DIR, destination)

        marker.writeText("copied")
        val files = destination.listFiles().orEmpty().filter { it.name != COMPLETE_MARKER }
        val totalBytes = files.sumOf { it.length() }
        Log.d(
                TAG,
                "Graph cache ready: ${files.size} files, ${totalBytes / 1024 / 1024} MB"
        )
        return destination
    }

    private fun copyAssetDirectory(context: Context, assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) return

        for (name in children) {
            val childAssetPath = "$assetPath/$name"
            val childDestination = File(destination, name)
            val nested = context.assets.list(childAssetPath).orEmpty()

            if (nested.isNotEmpty()) {
                childDestination.mkdirs()
                copyAssetDirectory(context, childAssetPath, childDestination)
            } else {
                context.assets.open(childAssetPath).use { input ->
                    childDestination.outputStream().use { output ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
            }
        }
    }
}
