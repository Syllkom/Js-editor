package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_files")
data class VirtualFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String,
    val path: String = "/",
    val createdAt: Long = System.currentTimeMillis(),
    val isSystemSample: Boolean = false
) {
    val isJavaScript: Boolean
        get() = name.endsWith(".js") || name.endsWith(".jsx")
}
