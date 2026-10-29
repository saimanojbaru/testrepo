package com.sortora.app.data.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.planning.SortPlanner
import com.sortora.app.domain.repository.FileOrganizer
import com.sortora.app.domain.repository.OrganizerBatch
import com.sortora.app.domain.repository.TransferOutcome
import com.sortora.app.domain.repository.TransferRequest
import com.sortora.app.domain.repository.UndoResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/**
 * Copies (and, for Move, then deletes) files into the user's output folder via the Storage
 * Access Framework.
 *
 * Safety rules:
 *  - The original is deleted only in MOVE mode, only after the copy finished, and only if the
 *    number of bytes written matches the source size.
 *  - A failed or cancelled copy removes its partial output.
 *  - Existing files in the destination are never overwritten; a numeric suffix is added instead.
 */
class SafFileOrganizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileOrganizer {

    private val resolver get() = context.contentResolver

    override suspend fun openBatch(destinationTreeUri: String): OrganizerBatch = withContext(Dispatchers.IO) {
        val tree = destinationTreeUri.toUri()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val reachable = runCatching {
            resolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() }
        }.getOrNull() == true
        if (!reachable) throw IOException("The output folder is no longer accessible. Choose it again in Settings.")
        Batch(tree, root)
    }

    override suspend fun renameInPlace(sourceUri: String, newName: String): TransferOutcome = withContext(Dispatchers.IO) {
        val renamed = try {
            DocumentsContract.renameDocument(resolver, sourceUri.toUri(), newName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FileNotFoundException) {
            return@withContext TransferOutcome(ApplyStatus.FAILED, null, null, "The original file no longer exists.")
        } catch (e: Exception) {
            null
        } ?: return@withContext TransferOutcome(
            ApplyStatus.FAILED, null, null,
            "This location doesn't allow renaming. Pick the folder instead of single files, or use Copy.",
        )
        // Providers may adjust the name (e.g. add " (1)" if it was taken); log what was actually written.
        TransferOutcome(ApplyStatus.SUCCESS, displayName(renamed) ?: newName, renamed.toString(), null)
    }

    override suspend fun deleteCopy(targetUri: String, expectedSize: Long): UndoResult = withContext(Dispatchers.IO) {
        val uri = targetUri.toUri()
        val size = sizeOf(uri) ?: return@withContext UndoResult(true, "Already gone")
        if (expectedSize > 0 && size != expectedSize) {
            return@withContext UndoResult(false, "The copy was changed since it was made, so it was kept.")
        }
        val deleted = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
        if (deleted) UndoResult(true) else UndoResult(false, "Couldn't delete the copy.")
    }

    override suspend fun restoreMoved(targetUri: String, originalName: String, parentUri: String, expectedSize: Long): UndoResult =
        withContext(Dispatchers.IO) {
            val moved = targetUri.toUri()
            if (sizeOf(moved) == null) return@withContext UndoResult(false, "The moved file is no longer in the output folder.")
            val parent = toDocumentUri(parentUri)
            val restored = try {
                DocumentsContract.createDocument(resolver, parent, mimeFor(originalName, "application/octet-stream"), originalName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return@withContext UndoResult(false, "No access to the original folder. Undo again and choose a folder.")
            val copied = try {
                copyStream(moved, restored)
            } catch (e: CancellationException) {
                runCatching { DocumentsContract.deleteDocument(resolver, restored) }
                throw e
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, restored) }
                return@withContext UndoResult(false, "Couldn't copy the file back: ${e.message}")
            }
            if (expectedSize > 0 && copied != expectedSize) {
                runCatching { DocumentsContract.deleteDocument(resolver, restored) }
                return@withContext UndoResult(false, "Restore incomplete; the moved file was kept.")
            }
            runCatching { DocumentsContract.deleteDocument(resolver, moved) }
            UndoResult(true)
        }

    override suspend fun renameBack(targetUri: String, originalName: String): UndoResult = withContext(Dispatchers.IO) {
        val result = runCatching { DocumentsContract.renameDocument(resolver, targetUri.toUri(), originalName) }.getOrNull()
        if (result != null) UndoResult(true) else UndoResult(false, "Couldn't rename the file back (it may have been moved or deleted).")
    }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) (if (c.isNull(0)) -1L else c.getLong(0)) else null
        }
    }.getOrNull()

    private fun displayName(doc: Uri): String? = runCatching {
        resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** A picked folder comes as a tree URI; creating files needs the folder's document URI. */
    private fun toDocumentUri(uri: String): Uri {
        val parsed = uri.toUri()
        return if (DocumentsContract.isDocumentUri(context, parsed)) {
            parsed
        } else {
            DocumentsContract.buildDocumentUriUsingTree(parsed, DocumentsContract.getTreeDocumentId(parsed))
        }
    }

    private suspend fun copyStream(source: Uri, target: Uri): Long {
        val input = resolver.openInputStream(source) ?: throw FileNotFoundException()
        val output = resolver.openOutputStream(target, "w") ?: throw IOException("Can't write the file")
        var total = 0L
        input.use { inp ->
            output.use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = inp.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
            }
        }
        return total
    }

    private inner class Batch(private val tree: Uri, private val root: Uri) : OrganizerBatch {
        /** Lower-case relative path → folder document. */
        private val folders = HashMap<String, Uri>().apply { put("", root) }
        /** Folder document → lower-case names already present (kept up to date as we write). */
        private val takenNames = HashMap<Uri, MutableSet<String>>()

        override suspend fun transfer(request: TransferRequest, mode: OperationMode): TransferOutcome = withContext(Dispatchers.IO) {
            val folder = try {
                folderFor(request.targetFolder)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext failed("Couldn't create folder “${request.targetFolder}”: ${e.message}")
            }

            val names = namesIn(folder)
            val separator = if ('-' in request.targetName && '_' !in request.targetName) '-' else '_'
            val finalName = SortPlanner.reserveName(request.targetName, separator, names)
            val target = try {
                DocumentsContract.createDocument(resolver, folder, mimeFor(finalName, request.mimeType), finalName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return@withContext failed("Couldn't create the file in the output folder.")

            val copied = try {
                copy(request.sourceUri.toUri(), target)
            } catch (e: CancellationException) {
                deleteQuietly(target)
                throw e
            } catch (e: FileNotFoundException) {
                deleteQuietly(target)
                return@withContext failed("The original file no longer exists.")
            } catch (e: SecurityException) {
                deleteQuietly(target)
                return@withContext failed("Access to the original was lost. Pick it again.")
            } catch (e: IOException) {
                deleteQuietly(target)
                return@withContext failed("Copy failed: ${e.message ?: "I/O error"}")
            }

            if (request.sizeBytes > 0 && copied != request.sizeBytes) {
                deleteQuietly(target)
                return@withContext failed("Copy incomplete ($copied of ${request.sizeBytes} bytes). Original kept.")
            }

            val writtenName = displayName(target) ?: finalName
            names += writtenName.lowercase()

            if (mode == OperationMode.MOVE) {
                val deleted = runCatching { DocumentsContract.deleteDocument(resolver, request.sourceUri.toUri()) }.getOrDefault(false)
                if (!deleted) {
                    return@withContext TransferOutcome(
                        ApplyStatus.COPIED_ORIGINAL_KEPT, writtenName, target.toString(),
                        "Copied, but the original couldn't be removed (its location doesn't allow deleting).",
                    )
                }
            }
            TransferOutcome(ApplyStatus.SUCCESS, writtenName, target.toString(), null)
        }

        override suspend fun writeTextFile(name: String, mimeType: String, content: String): String? = withContext(Dispatchers.IO) {
            val names = namesIn(root)
            val finalName = SortPlanner.reserveName(name, '_', names)
            val doc = DocumentsContract.createDocument(resolver, root, mimeType, finalName) ?: return@withContext null
            resolver.openOutputStream(doc, "w")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            finalName
        }

        /** Finds or creates each segment of "A/B/C" under the output folder. */
        private fun folderFor(path: String): Uri {
            var current = root
            var currentKey = ""
            for (segment in path.split('/').filter { it.isNotBlank() }) {
                val key = if (currentKey.isEmpty()) segment.lowercase() else "$currentKey/${segment.lowercase()}"
                current = folders.getOrPut(key) {
                    findChildFolder(current, segment)
                        ?: DocumentsContract.createDocument(resolver, current, DocumentsContract.Document.MIME_TYPE_DIR, segment)
                        ?: throw IOException("provider refused to create it")
                }
                currentKey = key
            }
            return current
        }

        private fun findChildFolder(parent: Uri, name: String): Uri? {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
            resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val isDir = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    if (isDir && cursor.getString(1).equals(name, ignoreCase = true)) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    }
                }
            }
            return null
        }

        private fun namesIn(folder: Uri): MutableSet<String> = takenNames.getOrPut(folder) {
            val names = HashSet<String>()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder))
            resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(1)?.let { names += it.lowercase() }
            }
            names
        }

        private suspend fun copy(source: Uri, target: Uri): Long = copyStream(source, target)

        private fun deleteQuietly(doc: Uri) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
        }

        private fun failed(message: String) = TransferOutcome(ApplyStatus.FAILED, null, null, message)
    }

    /**
     * Providers append an extension when the MIME type and the name disagree (e.g. "a.heic" as
     * image/heif becomes "a.heic.heif"), so derive the type from the extension we are writing.
     */
    private fun mimeFor(name: String, fallback: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: fallback.takeIf { ext.isEmpty() && it.isNotBlank() }
            ?: "application/octet-stream"
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
