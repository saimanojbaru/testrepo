package com.sortora.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Face thumbnails live in app-private storage under faces/<sessionId>/ and go with their session. */
@Singleton
class FaceThumbnailStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val root: File get() = File(context.filesDir, "faces")

    fun save(sessionId: Long, itemId: Long, index: Int, jpeg: ByteArray): String {
        val dir = File(root, sessionId.toString()).apply { mkdirs() }
        val file = File(dir, "${itemId}_$index.jpg")
        file.writeBytes(jpeg)
        return file.absolutePath
    }

    /** Deletes thumbnails of sessions that no longer exist. */
    fun retainOnly(sessionIds: Set<Long>) {
        root.listFiles()?.forEach { dir ->
            if (dir.name.toLongOrNull() !in sessionIds) dir.deleteRecursively()
        }
    }
}
