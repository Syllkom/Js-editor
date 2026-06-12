package com.example.data

data class WorkspaceFile(
    val uri: String,
    val name: String,
    val isDirectory: Boolean = false,
    val level: Int = 0
)
