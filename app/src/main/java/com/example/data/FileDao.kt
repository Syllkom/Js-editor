package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM virtual_files ORDER BY isSystemSample DESC, name ASC")
    fun getAllFiles(): Flow<List<VirtualFile>>

    @Query("SELECT * FROM virtual_files WHERE id = :id")
    suspend fun getFileById(id: Long): VirtualFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VirtualFile): Long

    @Update
    suspend fun updateFile(file: VirtualFile)

    @Delete
    suspend fun deleteFile(file: VirtualFile)

    @Query("SELECT COUNT(*) FROM virtual_files")
    suspend fun getCount(): Int
}
