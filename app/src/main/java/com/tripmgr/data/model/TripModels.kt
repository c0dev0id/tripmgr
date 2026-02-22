package com.tripmgr.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the metadata stored in trip.json inside each trip folder.
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
 * Reference to a file stored in the trip folder.
 * The actual file lives alongside trip.json; this tracks its document ID and type.
 */
data class TripFileRef(
    @SerializedName("file_id") val fileId: String,
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
 * Represents a trip as seen in the list UI. Combines folder info with parsed metadata.
 */
data class Trip(
    val folderId: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val sectionCount: Int = 0
)

/**
 * Represents a folder used to organize trips. Maps to a directory
 * inside the app's root storage folder.
 */
data class TripFolder(
    val folderId: String,
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
