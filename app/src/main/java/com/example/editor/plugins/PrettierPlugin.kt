package com.example.editor.plugins

import com.example.editor.core.Plugin

class PrettierPlugin : Plugin {
    override val id = "prettier"
    override val name = "Prettier"
    override val description = "Formateador de código automático. Ajusta márgenes, tabulaciones y limpia espacios con un toque."
    override val author = "Prettier Group"
    override val version = "2.8.8"
    override val iconName = "brush"

    override fun formatCode(text: String): String {
        if (text.isBlank()) return text

        val lines = text.split("\n")
        val formattedLines = ArrayList<String>()
        var indentLevel = 0
        val indentSize = 2 // 2 spaces

        for (line in lines) {
            var trimmedContent = line.trim()

            // Skip empty line formatting but preserve spacing
            if (trimmedContent.isEmpty()) {
                formattedLines.add("")
                continue
            }

            // Adjust indentation for closing brackets BEFORE printing current line
            // If line starts with a closing bracket, it should shift back
            val startsWithClose = trimmedContent.startsWith("}") || 
                                 trimmedContent.startsWith("]") || 
                                 trimmedContent.startsWith(")") ||
                                 trimmedContent.startsWith("*/")

            if (startsWithClose) {
                indentLevel = maxOf(0, indentLevel - 1)
            }

            val spaces = " ".repeat(indentLevel * indentSize)
            
            // Clean spacing around assignment and arithmetic operators
            // (Only do simple substitutions that won't corrupt string content)
            var cleanedLine = trimmedContent
            
            // We can add simple comment spacings if desired, or keep it standard
            formattedLines.add(spaces + cleanedLine)

            // Calculate braces/brackets opened vs closed on this line to configure FUTURE lines nesting structure
            val openedBraces = countOccurrences(trimmedContent, '{') + 
                               countOccurrences(trimmedContent, '[') + 
                               countOccurrences(trimmedContent, '(') +
                               (if (trimmedContent.endsWith("/*") || trimmedContent.startsWith("/*")) 1 else 0)

            val closedBraces = countOccurrences(trimmedContent, '}') + 
                               countOccurrences(trimmedContent, ']') + 
                               countOccurrences(trimmedContent, ')') +
                               (if (trimmedContent.endsWith("*/") || trimmedContent.startsWith("*/")) 1 else 0)

            val nets = openedBraces - closedBraces
            
            // Adjust indent index after printing, unless we already shifted back because it started with a close
            if (!startsWithClose) {
                indentLevel = maxOf(0, indentLevel + nets)
            } else {
                // If it already shifted back, let's adjust for any unmatched opens
                indentLevel = maxOf(0, indentLevel + openedBraces - (closedBraces - 1))
            }
        }

        return formattedLines.joinToString("\n")
    }

    private fun countOccurrences(str: String, char: Char): Int {
        var count = 0
        var insideQuote = false
        var quoteChar = ' '
        var escaped = false

        for (c in str) {
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }

            if ((c == '\'' || c == '"' || c == '`')) {
                if (!insideQuote) {
                    insideQuote = true
                    quoteChar = c
                } else if (quoteChar == c) {
                    insideQuote = false
                }
            }

            if (!insideQuote && c == char) {
                count++
            }
        }
        return count
    }
}
