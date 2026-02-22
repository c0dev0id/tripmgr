package com.tripmgr.data.drive

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.tripmgr.data.model.TripMetadata
import com.tripmgr.util.MimeUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level wrapper around the Google Drive REST API.
 * All methods run on Dispatchers.IO.
 */
@Singleton
class DriveServiceWrapper @Inject constructor() {

    private val gson = Gson()

    var driveService: Drive? = null

    private fun requireDrive(): Drive =
        driveService ?: throw IllegalStateException("Drive service not initialized. Sign in first.")

    // ── Folder operations ──

    suspend fun createFolder(name: String, parentId: String? = null): File =
        withContext(Dispatchers.IO) {
            val metadata = File().apply {
                this.name = name
                this.mimeType = MimeUtils.MIME_FOLDER
                parentId?.let { parents = listOf(it) }
            }
            requireDrive().files().create(metadata)
                .setFields("id, name, parents, createdTime, modifiedTime")
                .execute()
        }

    suspend fun listFolderContents(folderId: String): List<File> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<File>()
            var pageToken: String? = null
            do {
                val response = requireDrive().files().list()
                    .setQ("'$folderId' in parents and trashed = false")
                    .setFields("nextPageToken, files(id, name, mimeType, parents, createdTime, modifiedTime)")
                    .setPageSize(100)
                    .setPageToken(pageToken)
                    .execute()
                result.addAll(response.files ?: emptyList())
                pageToken = response.nextPageToken
            } while (pageToken != null)
            result
        }

    suspend fun renameFile(fileId: String, newName: String): File =
        withContext(Dispatchers.IO) {
            val metadata = File().apply { name = newName }
            requireDrive().files().update(fileId, metadata)
                .setFields("id, name")
                .execute()
        }

    suspend fun deleteFile(fileId: String) =
        withContext(Dispatchers.IO) {
            requireDrive().files().delete(fileId).execute()
        }

    suspend fun moveFile(fileId: String, newParentId: String): File =
        withContext(Dispatchers.IO) {
            val file = requireDrive().files().get(fileId)
                .setFields("parents")
                .execute()
            val previousParents = file.parents?.joinToString(",") ?: ""
            requireDrive().files().update(fileId, null)
                .setAddParents(newParentId)
                .setRemoveParents(previousParents)
                .setFields("id, name, parents")
                .execute()
        }

    suspend fun copyFile(fileId: String, newName: String, parentId: String? = null): File =
        withContext(Dispatchers.IO) {
            val metadata = File().apply {
                name = newName
                parentId?.let { parents = listOf(it) }
            }
            requireDrive().files().copy(fileId, metadata)
                .setFields("id, name, parents")
                .execute()
        }

    // ── App root folder ──

    suspend fun getOrCreateAppRoot(): File {
        return withContext(Dispatchers.IO) {
            val query = "name = 'TripMgr' and mimeType = '${MimeUtils.MIME_FOLDER}' and 'root' in parents and trashed = false"
            val result = requireDrive().files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute()
            result.files?.firstOrNull() ?: createFolder("TripMgr")
        }
    }

    // ── Trip metadata (trip.json) ──

    suspend fun writeTripMetadata(tripFolderId: String, metadata: TripMetadata) =
        withContext(Dispatchers.IO) {
            val json = gson.toJson(metadata)
            val content = ByteArrayContent.fromString(MimeUtils.MIME_JSON, json)

            // Check if trip.json already exists
            val existing = findFile(tripFolderId, MimeUtils.TRIP_METADATA_FILE)
            if (existing != null) {
                requireDrive().files().update(existing.id, null, content).execute()
            } else {
                val fileMetadata = File().apply {
                    name = MimeUtils.TRIP_METADATA_FILE
                    parents = listOf(tripFolderId)
                    mimeType = MimeUtils.MIME_JSON
                }
                requireDrive().files().create(fileMetadata, content)
                    .setFields("id")
                    .execute()
            }
        }

    suspend fun readTripMetadata(tripFolderId: String): TripMetadata? =
        withContext(Dispatchers.IO) {
            val file = findFile(tripFolderId, MimeUtils.TRIP_METADATA_FILE) ?: return@withContext null
            val outputStream = ByteArrayOutputStream()
            requireDrive().files().get(file.id).executeMediaAndDownloadTo(outputStream)
            val json = outputStream.toString("UTF-8")
            gson.fromJson(json, TripMetadata::class.java)
        }

    // ── File upload/download ──

    suspend fun uploadFile(
        parentFolderId: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): File = withContext(Dispatchers.IO) {
        val fileMetadata = File().apply {
            name = fileName
            parents = listOf(parentFolderId)
        }
        val mediaContent = InputStreamContent(mimeType, inputStream)
        requireDrive().files().create(fileMetadata, mediaContent)
            .setFields("id, name, mimeType")
            .execute()
    }

    suspend fun downloadFile(fileId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            requireDrive().files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toByteArray()
        }

    suspend fun getFileMetadata(fileId: String): File =
        withContext(Dispatchers.IO) {
            requireDrive().files().get(fileId)
                .setFields("id, name, mimeType, size, createdTime, modifiedTime, webContentLink, thumbnailLink")
                .execute()
        }

    // ── Helpers ──

    private suspend fun findFile(parentId: String, name: String): File? =
        withContext(Dispatchers.IO) {
            val query = "'$parentId' in parents and name = '$name' and trashed = false"
            val result = requireDrive().files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute()
            result.files?.firstOrNull()
        }

    /**
     * Recursively copies an entire folder and its contents.
     */
    suspend fun copyFolderRecursive(sourceFolderId: String, newName: String, destParentId: String): File {
        val newFolder = createFolder(newName, destParentId)
        val contents = listFolderContents(sourceFolderId)
        for (file in contents) {
            if (file.mimeType == MimeUtils.MIME_FOLDER) {
                copyFolderRecursive(file.id, file.name, newFolder.id)
            } else {
                copyFile(file.id, file.name, newFolder.id)
            }
        }
        return newFolder
    }
}
