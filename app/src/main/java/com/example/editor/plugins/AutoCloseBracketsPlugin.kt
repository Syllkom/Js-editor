package com.example.editor.plugins

import com.example.editor.core.Plugin
import com.example.editor.core.TextChangeResult

class AutoCloseBracketsPlugin : Plugin {
    override val id = "auto_close_brackets"
    override val name = "Auto Close Brackets"
    override val description = "Cierra de manera automática llaves {}, paréntesis (), corchetes [] y comillas cuando escribes."
    override val author = "VS Code Built-in"
    override val version = "1.0.0"
    override val iconName = "brackets"

    override fun onTextChange(
        text: String,
        cursorPosition: Int,
        addedChar: Char?
    ): TextChangeResult? {
        if (addedChar == null || cursorPosition <= 0 || cursorPosition > text.length) return null

        val charBefore = text[cursorPosition - 1]
        
        // Ensure that our addedChar matches the character before cursor
        if (charBefore != addedChar) return null

        val closingChar = when (addedChar) {
            '{' -> '}'
            '(' -> ')'
            '[' -> ']'
            '"' -> '"'
            '\'' -> '\''
            '`' -> '`'
            else -> null
        } ?: return null

        // Check if there are already symbols ahead. Avoid appending if next char is identical (to skip typed)
        if (cursorPosition < text.length && text[cursorPosition] == closingChar) {
            // User typed the closing bracket that was already auto-closed. 
            // In a real editor, we "swallow" the extra key, meaning we skip writing double.
            // Under Compose, we can just remove the duplicate character.
            val skippedText = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1)
            return TextChangeResult(skippedText, cursorPosition)
        }

        // Auto insert closing bracket
        val left = text.substring(0, cursorPosition)
        val right = text.substring(cursorPosition)
        return TextChangeResult(left + closingChar + right, cursorPosition)
    }
}
