package com.tripmgr.data.repository

import android.provider.DocumentsContract
import com.tripmgr.data.model.*
import com.tripmgr.data.storage.StorageService
import com.tripmgr.util.MimeUtils
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val storage: StorageService
) {
    fun getRootDocumentId(): String = storage.getRootDocumentId()

    // ── Folder operations ──

    suspend fun listItems(parentDocId: String? = null): List<TripListItem> {
        val docId = parentDocId ?: getRootDocumentId()
        val items = storage.listFolderContents(docId)

        val result = mutableListOf<TripListItem>()

        for (item in items) {
            if (item.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val metadata = storage.readTripMetadata(item.documentId)
                if (metadata != null) {
                    result.add(
                        TripListItem.TripItem(
                            Trip(
                                folderId = item.documentId,
                                name = metadata.name,
                                description = metadata.description,
                                createdAt = metadata.createdAt,
                                updatedAt = metadata.updatedAt,
                                sectionCount = metadata.sections.size
                            )
                        )
                    )
                } else {
                    result.add(
                        TripListItem.FolderItem(
                            TripFolder(
                                folderId = item.documentId,
                                name = item.displayName,
                                parentFolderId = docId
                            )
                        )
                    )
                }
            }
        }

        return result.sortedWith(
            compareBy<TripListItem> { it is TripListItem.TripItem }
                .thenBy {
                    when (it) {
                        is TripListItem.FolderItem -> it.folder.name.lowercase()
                        is TripListItem.TripItem -> it.trip.name.lowercase()
                    }
                }
        )
    }

    suspend fun createFolder(name: String, parentDocId: String? = null): TripFolder {
        val parentId = parentDocId ?: getRootDocumentId()
        val item = storage.createFolder(name, parentId)
        return TripFolder(
            folderId = item.documentId,
            name = name,
            parentFolderId = parentId
        )
    }

    suspend fun renameItem(itemId: String, newName: String, isTrip: Boolean) {
        storage.renameFile(itemId, newName)
        if (isTrip) {
            val metadata = storage.readTripMetadata(itemId)
            if (metadata != null) {
                storage.writeTripMetadata(itemId, metadata.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    suspend fun deleteItem(itemId: String) {
        storage.deleteFile(itemId)
    }

    suspend fun moveItem(itemId: String, sourceParentId: String, newParentId: String) {
        storage.moveFile(itemId, sourceParentId, newParentId)
    }

    suspend fun copyTrip(tripDocId: String, newName: String, destParentDocId: String? = null) {
        val parentId = destParentDocId ?: getRootDocumentId()
        val newFolderDocId = storage.copyFolderRecursive(tripDocId, parentId, newName)
        val metadata = storage.readTripMetadata(newFolderDocId)
        if (metadata != null) {
            storage.writeTripMetadata(newFolderDocId, metadata.copy(
                name = newName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    // ── Trip operations ──

    suspend fun createTrip(name: String, parentDocId: String? = null): Trip {
        val parentId = parentDocId ?: getRootDocumentId()
        val folder = storage.createFolder(name, parentId)
        val metadata = TripMetadata(
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        storage.writeTripMetadata(folder.documentId, metadata)
        return Trip(
            folderId = folder.documentId,
            name = name,
            createdAt = metadata.createdAt,
            updatedAt = metadata.updatedAt
        )
    }

    suspend fun getTripDetail(tripDocId: String): TripMetadata? {
        return storage.readTripMetadata(tripDocId)
    }

    suspend fun updateTripMetadata(tripDocId: String, metadata: TripMetadata) {
        storage.writeTripMetadata(tripDocId, metadata.copy(
            updatedAt = System.currentTimeMillis()
        ))
    }

    // ── Section operations ──

    suspend fun addSection(tripDocId: String, label: String): Section {
        val metadata = storage.readTripMetadata(tripDocId) ?: TripMetadata(name = "")
        val newSection = Section(
            id = UUID.randomUUID().toString(),
            label = label,
            order = metadata.sections.size
        )
        val updated = metadata.copy(
            sections = metadata.sections + newSection,
            updatedAt = System.currentTimeMillis()
        )
        storage.writeTripMetadata(tripDocId, updated)
        return newSection
    }

    suspend fun renameSection(tripDocId: String, sectionId: String, newLabel: String) {
        val metadata = storage.readTripMetadata(tripDocId) ?: return
        val updated = metadata.copy(
            sections = metadata.sections.map {
                if (it.id == sectionId) it.copy(label = newLabel) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        storage.writeTripMetadata(tripDocId, updated)
    }

    suspend fun deleteSection(tripDocId: String, sectionId: String) {
        val metadata = storage.readTripMetadata(tripDocId) ?: return
        val section = metadata.sections.find { it.id == sectionId } ?: return
        for (fileRef in section.files) {
            try {
                storage.deleteFile(fileRef.fileId)
            } catch (_: Exception) {}
        }
        val updated = metadata.copy(
            sections = metadata.sections.filter { it.id != sectionId }
                .mapIndexed { index, s -> s.copy(order = index) },
            updatedAt = System.currentTimeMillis()
        )
        storage.writeTripMetadata(tripDocId, updated)
    }

    suspend fun reorderSections(tripDocId: String, sectionIds: List<String>) {
        val metadata = storage.readTripMetadata(tripDocId) ?: return
        val sectionMap = metadata.sections.associateBy { it.id }
        val reordered = sectionIds.mapIndexedNotNull { index, id ->
            sectionMap[id]?.copy(order = index)
        }
        storage.writeTripMetadata(tripDocId, metadata.copy(
            sections = reordered,
            updatedAt = System.currentTimeMillis()
        ))
    }

    // ── File operations within sections ──

    suspend fun addFileToSection(
        tripDocId: String,
        sectionId: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): TripFileRef {
        val storageItem = storage.uploadFile(tripDocId, fileName, mimeType, inputStream)
        val fileType = MimeUtils.fileTypeFromMime(mimeType)
        val fileRef = TripFileRef(
            fileId = storageItem.documentId,
            name = storageItem.displayName,
            type = fileType,
            mimeType = mimeType
        )

        val metadata = storage.readTripMetadata(tripDocId) ?: return fileRef
        val updated = metadata.copy(
            sections = metadata.sections.map {
                if (it.id == sectionId) it.copy(files = it.files + fileRef) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        storage.writeTripMetadata(tripDocId, updated)
        return fileRef
    }

    suspend fun removeFileFromSection(
        tripDocId: String,
        sectionId: String,
        fileId: String
    ) {
        try {
            storage.deleteFile(fileId)
        } catch (_: Exception) {}

        val metadata = storage.readTripMetadata(tripDocId) ?: return
        val updated = metadata.copy(
            sections = metadata.sections.map {
                if (it.id == sectionId) it.copy(files = it.files.filter { f -> f.fileId != fileId })
                else it
            },
            updatedAt = System.currentTimeMillis()
        )
        storage.writeTripMetadata(tripDocId, updated)
    }

    suspend fun downloadFile(fileId: String): ByteArray {
        return storage.downloadFile(fileId)
    }

    // ── List folders for move/copy destination picker ──

    suspend fun listFoldersOnly(parentDocId: String? = null): List<TripFolder> {
        val docId = parentDocId ?: getRootDocumentId()
        val items = storage.listFolderContents(docId)
        return items
            .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
            .filter { storage.readTripMetadata(it.documentId) == null }
            .map { TripFolder(folderId = it.documentId, name = it.displayName, parentFolderId = docId) }
            .sortedBy { it.name.lowercase() }
    }
}
