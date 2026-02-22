package com.tripmgr.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.tripmgr.data.model.TripMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class StorageItem(
    val documentId: String,
    val displayName: String,
    val mimeType: String
)

@Singleton
class StorageService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("tripmgr_storage", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_ROOT_URI = "storage_root_uri"
        const val TRIP_METADATA_FILE = "trip.json"
    }

    // ── Root management ──

    var rootTreeUri: Uri?
        get() = prefs.getString(PREF_ROOT_URI, null)?.let { Uri.parse(it) }
        private set(value) {
            prefs.edit().putString(PREF_ROOT_URI, value?.toString()).apply()
        }

    fun hasStorageRoot(): Boolean = rootTreeUri != null

    fun setStorageRoot(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        rootTreeUri = treeUri
    }

    fun clearStorageRoot() {
        rootTreeUri = null
    }

    fun getRootDocumentId(): String {
        val root = rootTreeUri ?: throw IllegalStateException("No storage root set")
        return DocumentsContract.getTreeDocumentId(root)
    }

    // ── URI helpers ──

    private fun docUri(documentId: String): Uri {
        val root = rootTreeUri ?: throw IllegalStateException("No storage root set")
        return DocumentsContract.buildDocumentUriUsingTree(root, documentId)
    }

    private fun childrenUri(documentId: String): Uri {
        val root = rootTreeUri ?: throw IllegalStateException("No storage root set")
        return DocumentsContract.buildChildDocumentsUriUsingTree(root, documentId)
    }

    // ── Folder operations ──

    suspend fun createFolder(name: String, parentDocId: String): StorageItem =
        withContext(Dispatchers.IO) {
            val parentUri = docUri(parentDocId)
            val newUri = DocumentsContract.createDocument(
                context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name
            ) ?: throw IOException("Failed to create folder: $name")
            val docId = DocumentsContract.getDocumentId(newUri)
            StorageItem(docId, name, DocumentsContract.Document.MIME_TYPE_DIR)
        }

    suspend fun listFolderContents(folderDocId: String): List<StorageItem> =
        withContext(Dispatchers.IO) {
            val uri = childrenUri(folderDocId)
            val items = mutableListOf<StorageItem>()
            context.contentResolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    items.add(
                        StorageItem(
                            documentId = cursor.getString(0),
                            displayName = cursor.getString(1),
                            mimeType = cursor.getString(2)
                        )
                    )
                }
            }
            items
        }

    suspend fun renameFile(docId: String, newName: String): String =
        withContext(Dispatchers.IO) {
            val uri = docUri(docId)
            val newUri = DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                ?: throw IOException("Failed to rename")
            DocumentsContract.getDocumentId(newUri)
        }

    suspend fun deleteFile(docId: String) =
        withContext(Dispatchers.IO) {
            val uri = docUri(docId)
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }

    suspend fun moveFile(docId: String, sourceParentDocId: String, destParentDocId: String): String =
        withContext(Dispatchers.IO) {
            val uri = docUri(docId)
            val sourceParentUri = docUri(sourceParentDocId)
            val destParentUri = docUri(destParentDocId)
            val newUri = DocumentsContract.moveDocument(
                context.contentResolver, uri, sourceParentUri, destParentUri
            ) ?: throw IOException("Failed to move file")
            DocumentsContract.getDocumentId(newUri)
        }

    // ── Trip metadata ──

    suspend fun writeTripMetadata(tripFolderDocId: String, metadata: TripMetadata) =
        withContext(Dispatchers.IO) {
            val json = gson.toJson(metadata)
            val existing = findChild(tripFolderDocId, TRIP_METADATA_FILE)
            val fileUri = if (existing != null) {
                docUri(existing)
            } else {
                val parentUri = docUri(tripFolderDocId)
                DocumentsContract.createDocument(
                    context.contentResolver, parentUri, "application/json", TRIP_METADATA_FILE
                ) ?: throw IOException("Failed to create $TRIP_METADATA_FILE")
            }
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Failed to write $TRIP_METADATA_FILE")
        }

    suspend fun readTripMetadata(tripFolderDocId: String): TripMetadata? =
        withContext(Dispatchers.IO) {
            val fileDocId = findChild(tripFolderDocId, TRIP_METADATA_FILE)
                ?: return@withContext null
            val uri = docUri(fileDocId)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val json = input.bufferedReader().readText()
                    gson.fromJson(json, TripMetadata::class.java)
                }
            } catch (_: Exception) {
                null
            }
        }

    // ── File upload/download ──

    suspend fun uploadFile(
        parentDocId: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): StorageItem = withContext(Dispatchers.IO) {
        val parentUri = docUri(parentDocId)
        val newUri = DocumentsContract.createDocument(
            context.contentResolver, parentUri, mimeType, fileName
        ) ?: throw IOException("Failed to create file: $fileName")

        context.contentResolver.openOutputStream(newUri)?.use { os ->
            inputStream.copyTo(os)
        } ?: throw IOException("Failed to write file: $fileName")

        val docId = DocumentsContract.getDocumentId(newUri)
        val actualName = getDisplayName(docId) ?: fileName
        StorageItem(docId, actualName, mimeType)
    }

    suspend fun downloadFile(docId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val uri = docUri(docId)
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Failed to read file")
        }

    // ── Recursive copy ──

    suspend fun copyFolderRecursive(sourceDocId: String, destParentDocId: String, newName: String): String =
        withContext(Dispatchers.IO) {
            val newFolder = createFolder(newName, destParentDocId)
            val children = listFolderContents(sourceDocId)
            for (child in children) {
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    copyFolderRecursive(child.documentId, newFolder.documentId, child.displayName)
                } else {
                    copyFile(child.documentId, newFolder.documentId, child.displayName)
                }
            }
            newFolder.documentId
        }

    // ── Helpers ──

    private fun findChild(parentDocId: String, name: String): String? {
        val uri = childrenUri(parentDocId)
        context.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return cursor.getString(0)
                }
            }
        }
        return null
    }

    private fun copyFile(sourceDocId: String, destParentDocId: String, newName: String): String {
        val sourceUri = docUri(sourceDocId)
        val mimeType = getMimeType(sourceDocId) ?: "application/octet-stream"
        val destParentUri = docUri(destParentDocId)
        val destUri = DocumentsContract.createDocument(
            context.contentResolver, destParentUri, mimeType, newName
        ) ?: throw IOException("Failed to create copy")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            }
        }
        return DocumentsContract.getDocumentId(destUri)
    }

    private fun getDisplayName(docId: String): String? {
        val uri = docUri(docId)
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun getMimeType(docId: String): String? {
        val uri = docUri(docId)
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}
