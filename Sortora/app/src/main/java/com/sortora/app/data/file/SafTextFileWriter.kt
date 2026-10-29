package com.sortora.app.data.file

import android.content.Context
import androidx.core.net.toUri
import com.sortora.app.domain.repository.TextFileWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class SafTextFileWriter @Inject constructor(@ApplicationContext private val context: Context) : TextFileWriter {
    override suspend fun write(uri: String, content: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: throw IOException("Can't open file for writing")
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }
}
