package com.tripmgr.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the metadata stored in trip.json inside each trip folder on Google Drive.
 */
data class TripMetadata(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerializedName("sections") val sections: List<Section> = emptyList()
)

data class Section(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String,
    @SerializedName("order") val order: Int,
    @SerializedName("files") val files: List<TripFileRef> = emptyList()
)

/**
 * Reference to a file stored in the trip folder on Google Drive.
 * The actual file lives alongside trip.json; this tracks its Drive file ID and type.
 */
data class TripFileRef(
    @SerializedName("drive_file_id") val driveFileId: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: FileType,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("added_at") val addedAt: Long = System.currentTimeMillis()
)

enum class FileType {
    @SerializedName("gpx") GPX,
    @SerializedName("pdf") PDF,
    @SerializedName("image") IMAGE,
    @SerializedName("video") VIDEO
}

/**
 * Represents a trip as seen in the list UI. Combines Drive folder info with parsed metadata.
 */
data class Trip(
    val driveFolderId: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val sectionCount: Int = 0
)

/**
 * Represents a folder used to organize trips. Maps to a Google Drive folder
 * inside the app's root folder.
 */
data class TripFolder(
    val driveFolderId: String,
    val name: String,
    val parentFolderId: String? = null
)

/**
 * Union type for items displayed in the trip list (either a folder or a trip).
 */
sealed class TripListItem {
    data class FolderItem(val folder: TripFolder) : TripListItem()
    data class TripItem(val trip: Trip) : TripListItem()
}
