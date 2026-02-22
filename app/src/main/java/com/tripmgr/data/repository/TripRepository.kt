package com.tripmgr.data.repository

import com.tripmgr.data.drive.DriveServiceWrapper
import com.tripmgr.data.model.*
import com.tripmgr.util.MimeUtils
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val driveService: DriveServiceWrapper
) {
    private var appRootId: String? = null

    suspend fun ensureAppRoot(): String {
        if (appRootId == null) {
            appRootId = driveService.getOrCreateAppRoot().id
        }
        return appRootId!!
    }

    // ── Folder operations ──

    suspend fun listItems(parentFolderId: String? = null): List<TripListItem> {
        val folderId = parentFolderId ?: ensureAppRoot()
        val files = driveService.listFolderContents(folderId)

        val items = mutableListOf<TripListItem>()

        for (file in files) {
            if (file.mimeType == MimeUtils.MIME_FOLDER) {
                // Check if it's a trip folder (contains trip.json) or a regular folder
                val metadata = driveService.readTripMetadata(file.id)
                if (metadata != null) {
                    items.add(
                        TripListItem.TripItem(
                            Trip(
                                driveFolderId = file.id,
                                name = metadata.name,
                                description = metadata.description,
                                createdAt = metadata.createdAt,
                                updatedAt = metadata.updatedAt,
                                sectionCount = metadata.sections.size
                            )
                        )
                    )
                } else {
                    items.add(
                        TripListItem.FolderItem(
                            TripFolder(
                                driveFolderId = file.id,
                                name = file.name,
                                parentFolderId = folderId
                            )
                        )
                    )
                }
            }
        }

        return items.sortedWith(
            compareBy<TripListItem> { it is TripListItem.TripItem }
                .thenBy {
                    when (it) {
                        is TripListItem.FolderItem -> it.folder.name.lowercase()
                        is TripListItem.TripItem -> it.trip.name.lowercase()
                    }
                }
        )
    }

    suspend fun createFolder(name: String, parentFolderId: String? = null): TripFolder {
        val parentId = parentFolderId ?: ensureAppRoot()
        val file = driveService.createFolder(name, parentId)
        return TripFolder(
            driveFolderId = file.id,
            name = name,
            parentFolderId = parentId
        )
    }

    suspend fun renameItem(itemId: String, newName: String, isTrip: Boolean) {
        driveService.renameFile(itemId, newName)
        if (isTrip) {
            val metadata = driveService.readTripMetadata(itemId)
            if (metadata != null) {
                driveService.writeTripMetadata(itemId, metadata.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    suspend fun deleteItem(itemId: String) {
        driveService.deleteFile(itemId)
    }

    suspend fun moveItem(itemId: String, newParentId: String) {
        driveService.moveFile(itemId, newParentId)
    }

    suspend fun copyTrip(tripFolderId: String, newName: String, destParentId: String? = null) {
        val parentId = destParentId ?: ensureAppRoot()
        val newFolder = driveService.copyFolderRecursive(tripFolderId, newName, parentId)
        // Update the metadata in the copy
        val metadata = driveService.readTripMetadata(newFolder.id)
        if (metadata != null) {
            driveService.writeTripMetadata(newFolder.id, metadata.copy(
                name = newName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    // ── Trip operations ──

    suspend fun createTrip(name: String, parentFolderId: String? = null): Trip {
        val parentId = parentFolderId ?: ensureAppRoot()
        val folder = driveService.createFolder(name, parentId)
        val metadata = TripMetadata(
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        driveService.writeTripMetadata(folder.id, metadata)
        return Trip(
            driveFolderId = folder.id,
            name = name,
            createdAt = metadata.createdAt,
            updatedAt = metadata.updatedAt
        )
    }

    suspend fun getTripDetail(tripFolderId: String): TripMetadata? {
        return driveService.readTripMetadata(tripFolderId)
    }

    suspend fun updateTripMetadata(tripFolderId: String, metadata: TripMetadata) {
        driveService.writeTripMetadata(tripFolderId, metadata.copy(
            updatedAt = System.currentTimeMillis()
        ))
    }

    // ── Section operations ──

    suspend fun addSection(tripFolderId: String, label: String): Section {
        val metadata = driveService.readTripMetadata(tripFolderId) ?: TripMetadata(name = "")
        val newSection = Section(
            id = UUID.randomUUID().toString(),
            label = label,
            order = metadata.sections.size
        )
        val updated = metadata.copy(
            sections = metadata.sections + newSection,
            updatedAt = System.currentTimeMillis()
        )
        driveService.writeTripMetadata(tripFolderId, updated)
        return newSection
    }

    suspend fun renameSection(tripFolderId: String, sectionId: String, newLabel: String) {
        val metadata = driveService.readTripMetadata(tripFolderId) ?: return
        val updated = metadata.copy(
            sections = metadata.sections.map {
                if (it.id == sectionId) it.copy(label = newLabel) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        driveService.writeTripMetadata(tripFolderId, updated)
    }

    suspend fun deleteSection(tripFolderId: String, sectionId: String) {
        val metadata = driveService.readTripMetadata(tripFolderId) ?: return
        val section = metadata.sections.find { it.id == sectionId } ?: return
        // Delete all files in the section from Drive
        for (fileRef in section.files) {
            try {
                driveService.deleteFile(fileRef.driveFileId)
            } catch (_: Exception) {
                // File may already be deleted
            }
        }
        val updated = metadata.copy(
            sections = metadata.sections.filter { it.id != sectionId }
                .mapIndexed { index, s -> s.copy(order = index) },
            updatedAt = System.currentTimeMillis()
        )
        driveService.writeTripMetadata(tripFolderId, updated)
    }

    suspend fun reorderSections(tripFolderId: String, sectionIds: List<String>) {
        val metadata = driveService.readTripMetadata(tripFolderId) ?: return
        val sectionMap = metadata.sections.associateBy { it.id }
        val reordered = sectionIds.mapIndexedNotNull { index, id ->
            sectionMap[id]?.copy(order = index)
        }
        driveService.writeTripMetadata(tripFolderId, metadata.copy(
            sections = reordered,
            updatedAt = System.currentTimeMillis()
        ))
    }

    // ── File operations within sections ──

    suspend fun addFileToSection(
        tripFolderId: String,
        sectionId: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): TripFileRef {
        val driveFile = driveService.uploadFile(tripFolderId, fileName, mimeType, inputStream)
        val fileType = MimeUtils.fileTypeFromMime(mimeType)
        val fileRef = TripFileRef(
            driveFileId = driveFile.id,
            name = fileName,
            type = fileType,
            mimeType = mimeType
        )

        val metadata = driveService.readTripMetadata(tripFolderId) ?: return fileRef
        val updated = metadata.copy(
            sections = metadata.sections.map {
                if (it.id == sectionId) it.copy(files = it.files + fileRef) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        driveService.writeTripMetadata(tripFolderId, updated)
        return fileRef
    }

    suspend fun removeFileFromSection(
        tripFolderId: String,
        sectionId: String,
        driveFileId: String
    ) {
        try {
            driveService.deleteFile(driveFileId)
        } catch (_: Exception) {}

        val metadata = driveService.readTripMetadata(tripFolderId) ?: return
        val updated = metadata.copy(
            sections = metadata.sections.map {
                if (it.id == sectionId) it.copy(files = it.files.filter { f -> f.driveFileId != driveFileId })
                else it
            },
            updatedAt = System.currentTimeMillis()
        )
        driveService.writeTripMetadata(tripFolderId, updated)
    }

    suspend fun downloadFile(fileId: String): ByteArray {
        return driveService.downloadFile(fileId)
    }

    // ── List folders for move/copy destination picker ──

    suspend fun listFoldersOnly(parentFolderId: String? = null): List<TripFolder> {
        val folderId = parentFolderId ?: ensureAppRoot()
        val files = driveService.listFolderContents(folderId)
        return files
            .filter { it.mimeType == MimeUtils.MIME_FOLDER }
            .filter { driveService.readTripMetadata(it.id) == null } // exclude trip folders
            .map { TripFolder(driveFolderId = it.id, name = it.name, parentFolderId = folderId) }
            .sortedBy { it.name.lowercase() }
    }
}
