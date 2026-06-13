package com.example.editor.core

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class TokenType {
    COMMENT,
    STRING,
    NUMBER,
    KEYWORD_CONTROL,  // if, else, return, flow control
    KEYWORD_DECL,     // function, class, const, let, var, import
    BUILTIN,          // console, document, Math, JSON, window
    FUNCTION_NAME,    // words followed by (
    CLASS_NAME,       // PascalCase class declarations
    OPERATOR,         // +, -, *, /, =, &&, || etc.
    IDENTIFIER,       // normal variables
    HEX_COLOR,        // #FFFFFF, #FFF etc.
    TEXT,
    BRACE_CURLY,      // { }
    BRACE_SQUARE,     // [ ]
    BRACE_PAREN       // ( )
}

data class Token(val type: TokenType, val range: IntRange, val originalString: String? = null)

class JsThemeColors(
    val comment: Color,
    val string: Color,
    val number: Color,
    val keywordControl: Color,
    val keywordDecl: Color,
    val builtin: Color,
    val functionName: Color,
    val className: Color,
    val operator: Color,
    val identifier: Color,
    val text: Color,
    val background: Color,
    val braceCurly: Color,
    val braceSquare: Color,
    val braceParen: Color
) {
    companion object {
        val VSCODE_DARK = JsThemeColors(
            comment = Color(0xFF6A9955),        // Muted Green
            string = Color(0xFFCE9178),         // Salmon Pink
            number = Color(0xFFB5CEA8),         // Light Green-Yellow
            keywordControl = Color(0xFFC586C0), // Purple-Magenta
            keywordDecl = Color(0xFF569CD6),    // Light Blue
            builtin = Color(0xFF4FC1FF),        // Deep Sky Blue
            functionName = Color(0xFFDCDCAA),   // Golden Yellow
            className = Color(0xFF4EC9B0),      // Teal Green
            operator = Color(0xFFD4D4D4),       // White-Grey
            identifier = Color(0xFF9CDCFE),     // Light Blue-Grey
            text = Color(0xFFD4D4D4),           // Soft White
            background = Color(0xFF1E1E1E),     // Classic VS Code Dark
            braceCurly = Color(0xFFFFA500),     // Orange
            braceSquare = Color(0xFFE06C75),    // Purple-Red
            braceParen = Color(0xFFE5C07B)      // Yellow
        )

        val VSCODE_LIGHT = JsThemeColors(
            comment = Color(0xFF008000),        // Dark Green
            string = Color(0xFFA31515),         // Deep Red
            number = Color(0xFF098658),         // Emerald Green
            keywordControl = Color(0xFFAF00DB), // Purple
            keywordDecl = Color(0xFF0000FF),    // Blue
            builtin = Color(0xFF267F99),        // Cyan
            functionName = Color(0xFF795E26),   // Brown-Gold
            className = Color(0xFF267F99),      // Teal
            operator = Color(0xFF333333),       // Charcoal
            identifier = Color(0xFF001080),     // Dark Navy
            text = Color(0xFF333333),           // Soft Black
            background = Color(0xFFF3F3F3),     // Light Slate Grey
            braceCurly = Color(0xFFA05A00),     // Dark Orange
            braceSquare = Color(0xFF982531),    // Dark Purple-Red
            braceParen = Color(0xFF7A6836)      // Dark Yellow
        )
    }
}

class JsSyntaxHighlighter(val isDark: Boolean, val diagnostics: List<JsDiagnostic> = emptyList()) : VisualTransformation {
    
    var cursorPosition: Int = -1
    
    val colors = if (isDark) JsThemeColors.VSCODE_DARK else JsThemeColors.VSCODE_LIGHT

    // Keywords patterns
    private val ctrlKeywords = setOf("if", "else", "return", "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw", "async", "await", "yield")
    private val declKeywords = setOf("function", "class", "const", "let", "var", "import", "export", "from", "default", "new", "this", "super", "extends", "typeof", "instanceof", "in", "of")
    private val builtins = setOf("console", "window", "document", "process", "Math", "JSON", "Promise", "Object", "Array", "String", "Number", "Boolean", "fetch", "setTimeout", "setInterval")

    fun highlight(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        
        // Match expressions in sequence
        val tokens = tokenize(text)
        for (token in tokens) {
            val style = when (token.type) {
                TokenType.COMMENT -> SpanStyle(color = colors.comment)
                TokenType.STRING -> SpanStyle(color = colors.string)
                TokenType.NUMBER -> SpanStyle(color = colors.number)
                TokenType.KEYWORD_CONTROL -> SpanStyle(color = colors.keywordControl, fontWeight = FontWeight.Bold)
                TokenType.KEYWORD_DECL -> SpanStyle(color = colors.keywordDecl, fontWeight = FontWeight.Bold)
                TokenType.BUILTIN -> SpanStyle(color = colors.builtin, fontWeight = FontWeight.Medium)
                TokenType.FUNCTION_NAME -> SpanStyle(color = colors.functionName)
                TokenType.CLASS_NAME -> SpanStyle(color = colors.className, fontWeight = FontWeight.SemiBold)
                TokenType.OPERATOR -> SpanStyle(color = colors.operator)
                TokenType.IDENTIFIER -> SpanStyle(color = colors.identifier)
                TokenType.HEX_COLOR -> {
                    val hexColorString = token.originalString ?: "#000000"
                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(hexColorString))
                    } catch (e: Exception) {
                        colors.string // fallback
                    }
                    SpanStyle(
                        color = if (parsedColor.luminance() > 0.5f) Color.Black else Color.White,
                        background = parsedColor
                    )
                }
                TokenType.TEXT -> SpanStyle(color = colors.text)
                TokenType.BRACE_CURLY -> SpanStyle(color = colors.braceCurly)
                TokenType.BRACE_SQUARE -> SpanStyle(color = colors.braceSquare)
                TokenType.BRACE_PAREN -> SpanStyle(color = colors.braceParen)
            }
            builder.addStyle(style, token.range.start, token.range.endInclusive + 1)
        }

        // Apply Diagnostics squiggly lines
        if (diagnostics.isNotEmpty()) {
            val lines = text.split("\n")
            var currentIndex = 0
            for ((i, lineStr) in lines.withIndex()) {
                val line1Idx = i + 1
                val lineDiags = diagnostics.filter { it.line == line1Idx }
                for (diag in lineDiags) {
                    val errorColor = if (diag.level == DiagnosticLevel.ERROR) Color(0xFFFF5555) else Color(0xFFFFAA00)
                    
                    // Simple full-line underline if term is empty or not found, else find term
                    var startOffset = currentIndex
                    var endOffset = currentIndex + lineStr.length
                    
                    if (diag.term.isNotEmpty()) {
                        val termIdx = lineStr.indexOf(diag.term)
                        if (termIdx != -1) {
                            startOffset = currentIndex + termIdx
                            endOffset = startOffset + diag.term.length
                        }
                    }

                    builder.addStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline, color = errorColor),
                        startOffset,
                        endOffset
                    )
                }
                currentIndex += lineStr.length + 1 // +1 for '\n'
            }
        }

        // Apply Hex and RGB Color Highlight
        val rxColor = "(#[0-9a-fA-F]{3,8})|(rgba?\\([\\d\\s,]+\\))".toRegex(RegexOption.IGNORE_CASE)
        rxColor.findAll(text).forEach { match ->
            val colorStr = match.value.replace(" ", "")
            val parsedColor = try {
                if (colorStr.startsWith("#")) {
                    Color(android.graphics.Color.parseColor(colorStr))
                } else if (colorStr.startsWith("rgb")) {
                    val numbers = colorStr.substringAfter("(").substringBefore(")").split(",")
                    val r = numbers.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val g = numbers.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val b = numbers.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val a = if (colorStr.startsWith("rgba")) {
                        val alphaFloat = numbers.getOrNull(3)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                        (alphaFloat * 255).toInt()
                    } else 255
                    Color(android.graphics.Color.argb(a, r, g, b))
                } else null
            } catch (e: Exception) { null }

            if (parsedColor != null) {
                builder.addStyle(
                    SpanStyle(
                        color = if (parsedColor.luminance() > 0.5f) Color.Black else Color.White,
                        background = parsedColor
                    ),
                    match.range.start,
                    match.range.endInclusive + 1
                )
            }
        }

        // Bracket matching
        if (cursorPosition >= 0 && cursorPosition <= text.length) {
            val bracketStyle = SpanStyle(
                background = if (isDark) Color(0xFF444444) else Color(0xFFDDDDDD),
                fontWeight = FontWeight.Bold
            )
            
            fun getBracketAt(pos: Int): Char? {
                if (pos < 0 || pos >= text.length) return null
                val c = text[pos]
                if ("(){}[]".contains(c)) {
                    val matchIndex = tokens.binarySearch { token ->
                        if (token.range.last < pos) -1
                        else if (token.range.first > pos) 1
                        else 0
                    }
                    if (matchIndex >= 0) {
                        val tType = tokens[matchIndex].type
                        if (tType == TokenType.OPERATOR || tType == TokenType.BRACE_CURLY || tType == TokenType.BRACE_SQUARE || tType == TokenType.BRACE_PAREN) {
                            return c
                        }
                    }
                }
                return null
            }

            var startHighlight = -1
            var endHighlight = -1

            val pairs = mapOf('(' to ')', '{' to '}', '[' to ']', ')' to '(', '}' to '{', ']' to '[')
            
            var matchTargetPos = -1
            var matchChar: Char? = null

            // Check right of cursor
            val rightChar = getBracketAt(cursorPosition)
            if (rightChar != null) {
                matchTargetPos = cursorPosition
                matchChar = rightChar
            } else if (cursorPosition > 0) {
                // Check left of cursor
                val leftChar = getBracketAt(cursorPosition - 1)
                if (leftChar != null) {
                    matchTargetPos = cursorPosition - 1
                    matchChar = leftChar
                }
            }

            if (matchChar != null && matchTargetPos != -1) {
                val target = pairs[matchChar]!!
                val isForward = matchChar in listOf('(', '{', '[')
                val step = if (isForward) 1 else -1
                var curr = matchTargetPos + step
                var count = 1
                
                while (curr >= 0 && curr < text.length) {
                    val c = getBracketAt(curr)
                    if (c != null) {
                        if (c == matchChar) {
                            count++
                        } else if (c == target) {
                            count--
                            if (count == 0) {
                                startHighlight = matchTargetPos
                                endHighlight = curr
                                break
                            }
                        }
                    }
                    curr += step
                }
            }

            if (startHighlight != -1 && endHighlight != -1) {
                builder.addStyle(bracketStyle, startHighlight, startHighlight + 1)
                builder.addStyle(bracketStyle, endHighlight, endHighlight + 1)
            }
        }

        return builder.toAnnotatedString()
    }

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlight(text.text), OffsetMapping.Identity)
    }

    fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        var index = 0
        val length = text.length

        while (index < length) {
            val remaining = text.substring(index)

            // 1. Comments
            if (remaining.startsWith("//")) {
                val endOfLine = remaining.indexOf('\n')
                val commentLen = if (endOfLine == -1) remaining.length else endOfLine
                tokens.add(Token(TokenType.COMMENT, index until (index + commentLen)))
                index += commentLen
                continue
            }
            
            if (remaining.startsWith("/*")) {
                val endOfBlock = remaining.indexOf("*/")
                val commentLen = if (endOfBlock == -1) remaining.length else endOfBlock + 2
                tokens.add(Token(TokenType.COMMENT, index until (index + commentLen)))
                index += commentLen
                continue
            }

            // 2. Strings (handles single quotes, double quotes, and template literals backticks)
            val firstChar = remaining[0]
            if (firstChar == '\'' || firstChar == '"' || firstChar == '`') {
                var stringLen = 1
                var escaped = false
                while (stringLen < remaining.length) {
                    val c = remaining[stringLen]
                    if (escaped) {
                        escaped = false
                    } else if (c == '\\') {
                        escaped = true
                    } else if (c == firstChar) {
                        stringLen++
                        break
                    }
                    stringLen++
                }
                tokens.add(Token(TokenType.STRING, index until (index + stringLen)))
                index += stringLen
                continue
            }

            // 3. Numbers
            val numberMatch = "^[0-9]+(\\.[0-9]+)?\\b".toRegex().find(remaining)
            if (numberMatch != null) {
                val numLen = numberMatch.value.length
                tokens.add(Token(TokenType.NUMBER, index until (index + numLen)))
                index += numLen
                continue
            }

            // 4. Identifier / Keyword / Builtin / Function definition
            val wordMatch = "^[a-zA-Z_\$][a-zA-Z0-9_\$]*\\b".toRegex().find(remaining)
            if (wordMatch != null) {
                val word = wordMatch.value
                val wordLen = word.length
                val range = index until (index + wordLen)

                val type = when {
                    ctrlKeywords.contains(word) -> TokenType.KEYWORD_CONTROL
                    declKeywords.contains(word) -> TokenType.KEYWORD_DECL
                    builtins.contains(word) -> TokenType.BUILTIN
                    // Standard PascalCase denotes class names
                    word[0].isUpperCase() && !builtins.contains(word) -> TokenType.CLASS_NAME
                    // Peek ahead to see if followed by (
                    index + wordLen < length && text.substring(index + wordLen).trimStart().startsWith("(") -> TokenType.FUNCTION_NAME
                    else -> TokenType.IDENTIFIER
                }

                tokens.add(Token(type, range))
                index += wordLen
                continue
            }

            // 5. Operators & Brackets
            val opChar = remaining[0]
            val type = when (opChar) {
                '{', '}' -> TokenType.BRACE_CURLY
                '[', ']' -> TokenType.BRACE_SQUARE
                '(', ')' -> TokenType.BRACE_PAREN
                else -> {
                    if ("+-*/%=&|^!~?:;.,".contains(opChar)) TokenType.OPERATOR else TokenType.TEXT
                }
            }
            if (type != TokenType.TEXT || "{}[]()+-*/%=&|^!~?:;.,".contains(opChar)) {
                tokens.add(Token(if (type == TokenType.TEXT) TokenType.OPERATOR else type, index..index))
                index++
                continue
            }

            // fallback default character
            tokens.add(Token(TokenType.TEXT, index..index))
            index++
        }

        return tokens
    }
}
