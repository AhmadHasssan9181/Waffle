package com.noobdev.waffle.core.storage

import android.content.Context
import android.os.StatFs

/** Storage checks before a large offline download starts. */
object StorageUtils {

    fun freeBytes(context: Context): Long =
            runCatching { StatFs(context.filesDir.absolutePath).availableBytes }
                    .getOrDefault(0L)

    fun hasRoomFor(context: Context, megabytes: Int): Boolean {
        val required = megabytes.toLong() * 1024L * 1024L
        // Keep a 200 MB cushion so the phone does not become unusable mid-download.
        return freeBytes(context) > required + 200L * 1024L * 1024L
    }

    fun formatBytes(bytes: Long): String =
            when {
                bytes >= 1024L * 1024L * 1024L ->
                        "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
                bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
}
