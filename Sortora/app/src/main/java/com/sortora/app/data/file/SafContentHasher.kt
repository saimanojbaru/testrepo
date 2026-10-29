package com.sortora.app.data.file

import android.content.Context
import androidx.core.net.toUri
import com.sortora.app.domain.repository.ContentHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject

/** Streams a file through SHA-256. Only used for files that share a size with another file. */
class SafContentHasher @Inject constructor(@ApplicationContext private val context: Context) : ContentHasher {
    override suspend fun sha256(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return@runCatching null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
