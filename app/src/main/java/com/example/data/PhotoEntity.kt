package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["localUri"]),
        Index(value = ["syncStatus"]),
        Index(value = ["isEncrypted"]),
        Index(value = ["createdAt"]),
        Index(value = ["deviceMediaId"])
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val localUri: String,
    val localFilePath: String? = null,
    val cloudUrl: String? = null,
    val uploadStatus: String = "PENDING", // PENDING, UPLOADING, UPLOADED, FAILED, LOCAL_ONLY
    val isEncrypted: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "image/jpeg",
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val album: String = "All",
    // Offline-first & local sync tracking
    val syncStatus: String = SYNC_STATUS_LOCAL_ONLY, // LOCAL_ONLY, SYNC_PENDING, SYNCED, SYNC_FAILED
    val lastSyncTimestamp: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val isOfflineAvailable: Boolean = true,
    val deviceMediaId: Long? = null,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L
) {
    companion object {
        const val SYNC_STATUS_LOCAL_ONLY = "LOCAL_ONLY"
        const val SYNC_STATUS_PENDING = "SYNC_PENDING"
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_FAILED = "SYNC_FAILED"
    }
}
