package com.example.editor.core

import androidx.compose.ui.graphics.vector.ImageVector

data class TextChangeResult(
    val newText: String,
    val newCursorPosition: Int
)

interface Plugin {
    val id: String
    val name: String
    val description: String
    val author: String
    val version: String
    val iconName: String // Label of icon, e.g., "code", "brush", "warning", "link", "folder", "map"
    
    /**
     * Called when the text in the active editor buffer is changed.
     * Can return a modified string and cursor offset if the plugin wants to intercept or auto-complete.
     */
    fun onTextChange(
        text: String,
        cursorPosition: Int,
        addedChar: Char?
    ): TextChangeResult? {
        return null
    }

    /**
     * Hook used by formatting plugins (like Prettier)
     */
    fun formatCode(text: String): String {
        return text
    }

    /**
     * Hook used by Error Lens to draw error descriptions inline next to the line.
     */
    fun getLineOverlay(line1Indexed: Int, diagnostics: List<JsDiagnostic>): AlignmentOverlay? {
        return null
    }

    /**
     * Hook used by Code Actions to suggest immediate code modifications.
     */
    fun getCodeActions(line1Indexed: Int, diagnostics: List<JsDiagnostic>): List<QuickFixAction> {
        return emptyList()
    }
}

data class AlignmentOverlay(
    val text: String,
    val isError: Boolean
)
