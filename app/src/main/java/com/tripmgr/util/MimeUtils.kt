package com.tripmgr.util

import com.tripmgr.data.model.FileType

object MimeUtils {
    const val MIME_FOLDER = "application/vnd.google-apps.folder"
    const val MIME_JSON = "application/json"
    const val TRIP_METADATA_FILE = "trip.json"

    fun fileTypeFromMime(mimeType: String): FileType = when {
        mimeType == "application/gpx+xml" || mimeType.endsWith(".gpx") -> FileType.GPX
        mimeType == "application/xml" -> FileType.GPX // GPX files are XML
        mimeType == "application/pdf" -> FileType.PDF
        mimeType.startsWith("image/") -> FileType.IMAGE
        mimeType.startsWith("video/") -> FileType.VIDEO
        else -> FileType.IMAGE // fallback
    }

    fun fileTypeFromExtension(fileName: String): FileType = when {
        fileName.endsWith(".gpx", ignoreCase = true) -> FileType.GPX
        fileName.endsWith(".pdf", ignoreCase = true) -> FileType.PDF
        fileName.endsWith(".jpg", ignoreCase = true) ||
        fileName.endsWith(".jpeg", ignoreCase = true) ||
        fileName.endsWith(".png", ignoreCase = true) ||
        fileName.endsWith(".webp", ignoreCase = true) ||
        fileName.endsWith(".gif", ignoreCase = true) -> FileType.IMAGE
        fileName.endsWith(".mp4", ignoreCase = true) ||
        fileName.endsWith(".mov", ignoreCase = true) ||
        fileName.endsWith(".avi", ignoreCase = true) ||
        fileName.endsWith(".mkv", ignoreCase = true) -> FileType.VIDEO
        else -> FileType.IMAGE
    }
}
