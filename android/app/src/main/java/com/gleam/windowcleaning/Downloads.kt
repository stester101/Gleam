package com.gleam.windowcleaning

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object Downloads {
    /** Writes a text file to Downloads/Gleam. Returns true only if the whole file was written. */
    fun write(context: Context, rawName: String, mimeType: String, text: String): Boolean {
        val name = File(rawName).name.ifBlank { "gleam-file.txt" }
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Gleam")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create download")
                try {
                    resolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not write download")
                    resolver.update(uri, ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                // API 26-28: app-specific folder (no storage permission needed)
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Gleam")
                dir.mkdirs()
                File(dir, name).writeText(text)
            }
            true
        }.getOrDefault(false)
    }
}
