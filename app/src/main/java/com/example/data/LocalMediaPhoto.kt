package com.example.data

data class LocalMediaPhoto(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val dateAdded: Long,
    val sizeBytes: Long,
    val bucketName: String,
    val mimeType: String,
    val isVideo: Boolean = mimeType.startsWith("video/"),
    val durationMs: Long = 0L
)
