package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY createdAt DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE isEncrypted = 0 ORDER BY createdAt DESC")
    fun getPublicPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE isEncrypted = 1 ORDER BY createdAt DESC")
    fun getVaultPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE cloudUrl IS NOT NULL ORDER BY createdAt DESC")
    fun getCloudPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE isFavorite = 1 AND isEncrypted = 0 ORDER BY createdAt DESC")
    fun getFavoritePhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE syncStatus = :status ORDER BY createdAt DESC")
    fun getPhotosBySyncStatus(status: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE syncStatus = 'SYNC_PENDING' OR (isEncrypted = 0 AND cloudUrl IS NULL) ORDER BY createdAt DESC")
    fun getPendingSyncPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE syncStatus = 'SYNCED' ORDER BY createdAt DESC")
    fun getSyncedPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE localUri = :uri LIMIT 1")
    suspend fun getPhotoByUri(uri: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE deviceMediaId = :mediaId LIMIT 1")
    suspend fun getPhotoByDeviceMediaId(mediaId: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE isEncrypted = 0 AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchPublicPhotos(query: String): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhotos(photos: List<PhotoEntity>): List<Long>

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("UPDATE photos SET cloudUrl = :cloudUrl, uploadStatus = :status, syncStatus = :syncStatus, lastSyncTimestamp = :timestamp WHERE id = :id")
    suspend fun updateSyncDetails(id: Long, cloudUrl: String?, status: String, syncStatus: String, timestamp: Long)

    @Query("UPDATE photos SET cloudUrl = :cloudUrl, uploadStatus = :status WHERE id = :id")
    suspend fun updateCloudStatus(id: Long, cloudUrl: String?, status: String)

    @Query("UPDATE photos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE photos SET isEncrypted = :isEncrypted WHERE id = :id")
    suspend fun updateEncrypted(id: Long, isEncrypted: Boolean)

    @Query("SELECT COUNT(*) FROM photos WHERE syncStatus = 'SYNCED'")
    fun getSyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE syncStatus = 'SYNC_PENDING'")
    fun getPendingSyncCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE syncStatus = 'LOCAL_ONLY'")
    fun getLocalOnlyCount(): Flow<Int>

    @Query("SELECT DISTINCT album FROM photos WHERE isEncrypted = 0 AND album IS NOT NULL AND album != '' ORDER BY album ASC")
    fun getDistinctAlbums(): Flow<List<String>>

    @Query("UPDATE photos SET album = :newAlbumName WHERE album = :oldAlbumName AND isEncrypted = 0")
    suspend fun renameAlbum(oldAlbumName: String, newAlbumName: String)

    @Query("UPDATE photos SET album = 'All' WHERE album = :albumName AND isEncrypted = 0")
    suspend fun deleteAlbum(albumName: String)
}
