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
            comment = Color(0xFF6A9955),
            string = Color(0xFFCE9178),
            number = Color(0xFFB5CEA8),
            keywordControl = Color(0xFFC586C0),
            keywordDecl = Color(0xFF569CD6),
            builtin = Color(0xFF4FC1FF),
            functionName = Color(0xFFDCDCAA),
            className = Color(0xFF4EC9B0),
            operator = Color(0xFFD4D4D4),
            identifier = Color(0xFF9CDCFE),
            text = Color(0xFFD4D4D4),
            background = Color(0xFF1E1E1E),
            braceCurly = Color(0xFFFFA500),
            braceSquare = Color(0xFFE06C75),
            braceParen = Color(0xFFE5C07B)
        )

        val VSCODE_LIGHT = JsThemeColors(
            comment = Color(0xFF008000),
            string = Color(0xFFA31515),
            number = Color(0xFF098658),
            keywordControl = Color(0xFFAF00DB),
            keywordDecl = Color(0xFF0000FF),
            builtin = Color(0xFF267F99),
            functionName = Color(0xFF795E26),
            className = Color(0xFF267F99),
            operator = Color(0xFF333333),
            identifier = Color(0xFF001080),
            text = Color(0xFF333333),
            background = Color(0xFFF3F3F3),
            braceCurly = Color(0xFFA05A00),
            braceSquare = Color(0xFF982531),
            braceParen = Color(0xFF7A6836)
        )
    }
}

class JsSyntaxHighlighter(val isDark: Boolean, val diagnostics: List<JsDiagnostic> = emptyList()) : VisualTransformation {

    var cursorPosition: Int = -1

    val colors = if (isDark) JsThemeColors.VSCODE_DARK else JsThemeColors.VSCODE_LIGHT

    // FIX #1: Keywords como sets para O(1) lookup — sin cambios, ya era correcto
    private val ctrlKeywords = setOf("if", "else", "return", "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw", "async", "await", "yield")
    private val declKeywords = setOf("function", "class", "const", "let", "var", "import", "export", "from", "default", "new", "this", "super", "extends", "typeof", "instanceof", "in", "of")
    private val builtins = setOf("console", "window", "document", "process", "Math", "JSON", "Promise", "Object", "Array", "String", "Number", "Boolean", "fetch", "setTimeout", "setInterval")

    companion object {
        // FIX #2: Regex compiladas UNA SOLA VEZ como constantes del companion object.
        // Antes se compilaban dentro del loop tokenize() con cada carácter → O(n) compilaciones.
        private val NUMBER_REGEX = Regex("^[0-9]+(\\.[0-9]+)?\\b")
        private val WORD_REGEX = Regex("^[a-zA-Z_\$][a-zA-Z0-9_\$]*\\b")
        private val COLOR_REGEX = Regex(
            "(#[0-9a-fA-F]{3,8})|(rgba?\\([\\d\\s,.]+\\))",
            RegexOption.IGNORE_CASE
        )

        // FIX #3: Chars de operadores como set para lookup O(1) en lugar de String.contains()
        private val OPERATOR_CHARS = setOf('+', '-', '*', '/', '%', '=', '&', '|', '^', '!', '~', '?', ':', ';', '.', ',', '<', '>', '@')
    }

    fun highlight(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder(text)

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
                        colors.string
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

        // Diagnostics squiggly underlines
        if (diagnostics.isNotEmpty()) {
            val lines = text.split("\n")
            var currentIndex = 0
            for ((i, lineStr) in lines.withIndex()) {
                val line1Idx = i + 1
                val lineDiags = diagnostics.filter { it.line == line1Idx }
                for (diag in lineDiags) {
                    val errorColor = if (diag.level == DiagnosticLevel.ERROR) Color(0xFFFF5555) else Color(0xFFFFAA00)

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
                currentIndex += lineStr.length + 1
            }
        }

        // FIX #4: Reutilizar la misma COLOR_REGEX del companion en lugar de compilarla aquí
        COLOR_REGEX.findAll(text).forEach { match ->
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
                        if (tType == TokenType.OPERATOR || tType == TokenType.BRACE_CURLY ||
                            tType == TokenType.BRACE_SQUARE || tType == TokenType.BRACE_PAREN) {
                            return c
                        }
                    }
                }
                return null
            }

            val pairs = mapOf('(' to ')', '{' to '}', '[' to ']', ')' to '(', '}' to '{', ']' to '[')

            var matchTargetPos = -1
            var matchChar: Char? = null

            val rightChar = getBracketAt(cursorPosition)
            if (rightChar != null) {
                matchTargetPos = cursorPosition
                matchChar = rightChar
            } else if (cursorPosition > 0) {
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
                        if (c == matchChar) count++
                        else if (c == target) {
                            count--
                            if (count == 0) {
                                builder.addStyle(bracketStyle, matchTargetPos, matchTargetPos + 1)
                                builder.addStyle(bracketStyle, curr, curr + 1)
                                break
                            }
                        }
                    }
                    curr += step
                }
            }
        }

        return builder.toAnnotatedString()
    }

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlight(text.text), OffsetMapping.Identity)
    }

    fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>(text.length / 5)
        var index = 0
        val length = text.length

        while (index < length) {
            val c = text[index]
            // FIX #5: Peek siguiente char sin crear substring
            val c1 = if (index + 1 < length) text[index + 1] else '\u0000'

            // 1. Comentarios de línea: //
            if (c == '/' && c1 == '/') {
                val end = text.indexOf('\n', index + 2).let { if (it == -1) length else it }
                tokens.add(Token(TokenType.COMMENT, index until end))
                index = end
                continue
            }

            // 2. Comentarios de bloque: /* ... */
            if (c == '/' && c1 == '*') {
                val closeIdx = text.indexOf("*/", index + 2)
                val end = if (closeIdx == -1) length else closeIdx + 2
                tokens.add(Token(TokenType.COMMENT, index until end))
                index = end
                continue
            }

            // 3. Strings: ', ", `
            if (c == '\'' || c == '"' || c == '`') {
                var i = index + 1
                var escaped = false
                while (i < length) {
                    val sc = text[i]
                    when {
                        escaped -> escaped = false
                        sc == '\\' -> escaped = true
                        sc == c -> { i++; break }
                    }
                    i++
                }
                tokens.add(Token(TokenType.STRING, index until i))
                index = i
                continue
            }

            // 4. Números: dígito inicial
            if (c.isDigit()) {
                // FIX #6: Usar NUMBER_REGEX del companion (compilada una sola vez)
                // y pasarle el substring solo cuando hay un dígito — que es infrecuente
                val sub = text.substring(index)
                val m = NUMBER_REGEX.find(sub)
                if (m != null) {
                    val end = index + m.value.length
                    tokens.add(Token(TokenType.NUMBER, index until end))
                    index = end
                    continue
                }
            }

            // 5. Identificadores, keywords, builtins, funciones, clases
            if (c.isLetter() || c == '_' || c == '$') {
                // FIX #7: WORD_REGEX del companion, substring solo para letras — mucho menos frecuente que antes
                val sub = text.substring(index)
                val m = WORD_REGEX.find(sub) ?: run {
                    tokens.add(Token(TokenType.TEXT, index..index))
                    index++
                    continue
                }
                val word = m.value
                val wordEnd = index + word.length

                val type = when {
                    ctrlKeywords.contains(word) -> TokenType.KEYWORD_CONTROL
                    declKeywords.contains(word) -> TokenType.KEYWORD_DECL
                    builtins.contains(word) -> TokenType.BUILTIN
                    word[0].isUpperCase() -> TokenType.CLASS_NAME
                    // FIX #8: Peek directo en el String original sin substring
                    else -> {
                        var peek = wordEnd
                        while (peek < length && text[peek] == ' ') peek++
                        if (peek < length && text[peek] == '(') TokenType.FUNCTION_NAME
                        else TokenType.IDENTIFIER
                    }
                }

                tokens.add(Token(type, index until wordEnd))
                index = wordEnd
                continue
            }

            // 6. Brackets
            val bracketType = when (c) {
                '{', '}' -> TokenType.BRACE_CURLY
                '[', ']' -> TokenType.BRACE_SQUARE
                '(', ')' -> TokenType.BRACE_PAREN
                else -> null
            }
            if (bracketType != null) {
                tokens.add(Token(bracketType, index..index))
                index++
                continue
            }

            // 7. Operadores
            if (c in OPERATOR_CHARS) {
                tokens.add(Token(TokenType.OPERATOR, index..index))
                index++
                continue
            }

            // 8. Fallback (espacios, saltos de línea, etc.)
            tokens.add(Token(TokenType.TEXT, index..index))
            index++
        }

        return tokens
    }
}
