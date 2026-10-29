package com.sortora.app.data.media

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.repository.MediaSourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Resolves files and folders the user picked with the system file picker.
 *
 * Sortora requests no storage permission: access is limited to exactly what was picked.
 */
class SafMediaSourceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaSourceRepository {

    private val resolver get() = context.contentResolver

    override suspend fun resolveFiles(uris: List<String>): List<MediaFile> = withContext(Dispatchers.IO) {
        uris.distinct().mapNotNull { raw ->
            currentCoroutineContext().ensureActive()
            runCatching { describeDocument(raw.toUri()) }.getOrNull()
        }
    }

    override suspend fun scanFolder(treeUri: String, includeSubfolders: Boolean, excludeTreeUri: String?): List<MediaFile> =
        withContext(Dispatchers.IO) {
            val tree = treeUri.toUri()
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val excludedId = excludeTreeUri?.toUri()
                ?.takeIf { it.authority == tree.authority }
                ?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
            val rootName = treeDisplayName(treeUri) ?: ""

            val found = mutableListOf<MediaFile>()
            val queue = ArrayDeque(listOf(Triple(rootId, rootName, 0)))
            while (queue.isNotEmpty() && found.size < MAX_SCAN_FILES) {
                currentCoroutineContext().ensureActive()
                val (parentId, parentPath, depth) = queue.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
                resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        val mime = cursor.getString(2)
                        if (name.startsWith(".")) continue // hidden files, .thumbnails, .trashed-*
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            // Never re-scan the output folder if it lives inside the source folder.
                            if (includeSubfolders && depth < MAX_DEPTH && docId != excludedId) {
                                queue.addLast(Triple(docId, "$parentPath/$name", depth + 1))
                            }
                            continue
                        }
                        val resolvedMime = resolveMime(mime, name)
                        val kind = kindOf(resolvedMime) ?: continue
                        found += MediaFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString(),
                            displayName = name,
                            mimeType = resolvedMime,
                            sizeBytes = if (cursor.isNull(3)) -1 else cursor.getLong(3),
                            lastModifiedMillis = if (cursor.isNull(4)) null else cursor.getLong(4),
                            kind = kind,
                            pathHint = pathFromDocumentId(docId) ?: parentPath,
                        )
                    }
                }
            }
            found
        }

    override fun persistTreeAccess(treeUri: String): Boolean {
        val uri = treeUri.toUri()
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return runCatching { resolver.takePersistableUriPermission(uri, readWrite) }.isSuccess ||
            runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
    }

    override fun hasTreeAccess(treeUri: String, needWrite: Boolean): Boolean =
        resolver.persistedUriPermissions.any {
            it.uri.toString() == treeUri && it.isReadPermission && (!needWrite || it.isWritePermission)
        }

    override fun releaseTreeAccess(treeUri: String) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(treeUri.toUri(), flags) }
    }

    override fun treeDisplayName(treeUri: String): String? = runCatching {
        val tree = treeUri.toUri()
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Single picked document. Some providers only support OpenableColumns, so read defensively. */
    private fun describeDocument(uri: Uri): MediaFile? {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val name = cursor.string(OpenableColumns.DISPLAY_NAME) ?: uri.lastPathSegment ?: return null
            val mime = resolveMime(cursor.string(DocumentsContract.Document.COLUMN_MIME_TYPE) ?: resolver.getType(uri), name)
            val kind = kindOf(mime) ?: return null
            val docId = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else null
            val (mediaPath, dateTaken) = mediaStoreDetails(uri)
            return MediaFile(
                uri = uri.toString(),
                displayName = name,
                mimeType = mime,
                sizeBytes = cursor.long(OpenableColumns.SIZE) ?: -1,
                lastModifiedMillis = cursor.long(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                kind = kind,
                pathHint = docId?.let(::pathFromDocumentId) ?: mediaPath,
                dateTakenMillis = dateTaken,
            )
        }
        return null
    }

    /**
     * Files picked from the picker's "Images"/"Videos" roots carry no folder path ("image:1234").
     * MediaStore knows the real location (e.g. Pictures/Screenshots) and the date taken, which
     * makes screenshot detection and dating much better. Best effort: any failure is ignored.
     */
    private fun mediaStoreDetails(uri: Uri): Pair<String?, Long?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || uri.authority != MEDIA_DOCUMENTS_AUTHORITY) return null to null
        return runCatching {
            val mediaUri = MediaStore.getMediaUri(context, uri) ?: return@runCatching null to null
            val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATE_TAKEN)
            resolver.query(mediaUri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null to null
                val path = c.getString(0)?.trimEnd('/')?.takeIf { it.isNotEmpty() }
                val taken = if (c.isNull(1)) null else c.getLong(1).takeIf { it > 0 }
                path to taken
            } ?: (null to null)
        }.getOrDefault(null to null)
    }

    /** ExternalStorageProvider ids look like "primary:Pictures/Screenshots/a.png". */
    private fun pathFromDocumentId(docId: String): String? {
        val path = docId.substringAfter(':', missingDelimiterValue = "")
        if ('/' !in path) return null
        return path.substringBeforeLast('/')
    }

    private fun resolveMime(reported: String?, name: String): String {
        if (!reported.isNullOrBlank() && reported != GENERIC_MIME) return reported.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: GENERIC_MIME
    }

    private fun kindOf(mime: String): MediaKind? = when {
        mime == "image/svg+xml" -> null // vector art: nothing for the models to look at
        mime.startsWith("image/") -> MediaKind.IMAGE
        mime.startsWith("video/") -> MediaKind.VIDEO
        else -> null
    }

    private fun Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private companion object {
        const val GENERIC_MIME = "application/octet-stream"
        const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
        const val MAX_DEPTH = 8
        const val MAX_SCAN_FILES = 10_000
        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
