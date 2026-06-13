package com.example.editor.core

import java.util.Stack
import java.util.regex.Pattern

enum class DiagnosticLevel {
    ERROR,
    WARNING,
    INFO
}

data class JsDiagnostic(
    val id: String,
    val level: DiagnosticLevel,
    val message: String,
    val line: Int,      // 1-indexed
    val column: Int,    // 0-indexed column within the line
    val term: String,   // The token/keyword with issues
    val fixAction: QuickFixAction? = null
)

data class QuickFixAction(
    val title: String,
    val replacementText: String,
    val rangeToReplace: IntRange, // Character range within the whole document
    val lineToReplace: Int        // 1-indexed line
)

enum class SymbolType {
    FUNCTION,
    CLASS,
    VARIABLE
}

data class JsSymbol(
    val name: String,
    val type: SymbolType,
    val line: Int,           // 1-indexed start line
    val endLine: Int,        // 1-indexed end line
    val signature: String
)

data class FoldingRange(
    val startLine: Int, // 1-indexed
    val endLine: Int,   // 1-indexed
    var isFolded: Boolean = false
)

object JsParser {

    /**
     * Parses the JavaScript code to return:
     * 1. Diagnostics (Warnings, errors based on syntax rules)
     * 2. Symbols (Functions, Classes for tree/outline navigation)
     * 3. Foldable ranges (Blocks spanned by matching { ... })
     */
    fun analyze(code: String, fileExtension: String = "js"): ParseResult {
        val diagnostics = ArrayList<JsDiagnostic>()
        val symbols = ArrayList<JsSymbol>()
        val folds = ArrayList<FoldingRange>()

        val lines = code.split("\n")
        
        // 1. Bracket Matching Stack
        // Holds Pair(Bracket Character, Pair(Line position (1-indexed), Column Position (0-indexed)))
        val bracketStack = Stack<Pair<Char, Pair<Int, Int>>>()
        
        // 2. Simple Variable tracking to find duplicates and missing declarations
        val declaredVariables = HashSet<String>()

        // 3. Line-By-Line analysis
        for (i in lines.indices) {
            val lineNum = i + 1
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            // Skip fully commented or empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue
            }

            // Check common typos like conts when it's JS
            if (fileExtension == "js") {
                val typoPattern = Pattern.compile("\\b(conts|functon|funtion|rturn|viod)\\b")
                val typoMatcher = typoPattern.matcher(trimmed)
                if (typoMatcher.find()) {
                    val typo = typoMatcher.group(1) ?: ""
                    val correct = when(typo) {
                        "conts" -> "const"
                        "functon", "funtion" -> "function"
                        "rturn" -> "return"
                        "viod" -> "void"
                        else -> "const"
                    }
                    val offsetPatternStart = getLineStartIndex(code, i) + rawLine.indexOf(typo)
                    diagnostics.add(
                        JsDiagnostic(
                            id = "typo_${lineNum}",
                            level = DiagnosticLevel.ERROR,
                            message = "Posible error tipográfico: '$typo'. ¿Quisiste decir '$correct'?",
                            line = lineNum,
                            column = rawLine.indexOf(typo),
                            term = typo,
                            fixAction = QuickFixAction(
                                    title = "Cambiar a '$correct'",
                                    replacementText = correct,
                                    rangeToReplace = offsetPatternStart until (offsetPatternStart + typo.length),
                                    lineToReplace = lineNum
                            )
                        )
                    )
                }
            }

            // Check Bracket matching by scanning characters
            var inSingleString = false
            var inDoubleString = false
            var inBacktickString = false
            var escaped = false

            for (col in rawLine.indices) {
                val c = rawLine[col]

                // Handle escape sequences inside strings
                if (escaped) {
                    escaped = false
                    continue
                }
                if (c == '\\') {
                    escaped = true
                    continue
                }

                // String boundaries checks
                if (c == '\'' && !inDoubleString && !inBacktickString) {
                    inSingleString = !inSingleString
                    continue
                }
                if (c == '"' && !inSingleString && !inBacktickString) {
                    inDoubleString = !inDoubleString
                    continue
                }
                if (c == '`' && !inSingleString && !inDoubleString) {
                    inBacktickString = !inBacktickString
                    continue
                }

                // Ignore characters inside strings
                if (inSingleString || inDoubleString || inBacktickString) {
                    continue
                }

                // Non-string bracket matches
                if (c == '{' || c == '(' || c == '[') {
                    bracketStack.push(Pair(c, Pair(lineNum, col)))
                } else if (c == '}' || c == ')' || c == ']') {
                    val expectedOpening = when (c) {
                        '}' -> '{'
                        ')' -> '('
                        ']' -> '['
                        else -> ' '
                    }

                    if (bracketStack.isEmpty()) {
                        diagnostics.add(
                            JsDiagnostic(
                                id = "unmatched_close_${lineNum}_${col}",
                                level = DiagnosticLevel.ERROR,
                                message = "Llave o paréntesis '$c' de cierre inesperado sin apertura previa",
                                line = lineNum,
                                column = col,
                                term = c.toString()
                            )
                        )
                    } else {
                        val top = bracketStack.pop()
                        if (top.first != expectedOpening) {
                            diagnostics.add(
                                JsDiagnostic(
                                    id = "mismatched_${lineNum}_${col}",
                                    level = DiagnosticLevel.ERROR,
                                    message = "Símbolo de cierre de bloque incorrecto. Se esperaba '${openingToClosing(top.first)}' pero se recibió '$c'.",
                                    line = lineNum,
                                    column = col,
                                    term = c.toString()
                                )
                            )
                        } else {
                            // Valid matching closed block. Generate code folding range if multiline!
                            val startLine = top.second.first
                            if (startLine < lineNum) {
                                folds.add(FoldingRange(startLine, lineNum))
                            }
                        }
                    }
                }
            }

            // Unclosed string detection at line end (Except template strings ` which can be multi-lined)
            if (inSingleString || inDoubleString) {
                diagnostics.add(
                    JsDiagnostic(
                        id = "unclosed_str_${lineNum}",
                        level = DiagnosticLevel.ERROR,
                        message = "Cadena de texto sin cerrar (comillas faltantes al final de la línea)",
                        line = lineNum,
                        column = rawLine.length - 1,
                        term = if (inSingleString) "'" else "\""
                    )
                )
            }

            // 4. Missing semicolon warnings (for standard statements return, let, const, assignment)
            if (!inBacktickString && trimmed.length > 2 && fileExtension == "js") {
                val shouldEndWithSemicolon = (trimmed.startsWith("let ") || 
                                              trimmed.startsWith("const ") || 
                                              trimmed.startsWith("var ") || 
                                              trimmed.startsWith("return") || 
                                              trimmed.startsWith("throw ") || 
                                              trimmed.contains(" = ")) && 
                                              !trimmed.endsWith(";") && 
                                              !trimmed.endsWith("{") && 
                                              !trimmed.endsWith("}") && 
                                              !trimmed.endsWith(",") && 
                                              !trimmed.endsWith("[") && 
                                              !trimmed.endsWith("]") && 
                                              !trimmed.endsWith("(") && 
                                              !trimmed.endsWith(")")

                if (shouldEndWithSemicolon) {
                    // Calculate character range for quick fix
                    val lineOffset = getLineStartIndex(code, i)
                    val lineLength = rawLine.length
                    diagnostics.add(
                        JsDiagnostic(
                            id = "missing_semi_${lineNum}",
                            level = DiagnosticLevel.WARNING,
                            message = "Falta punto y coma (;) al final de la instrucción",
                            line = lineNum,
                            column = rawLine.length,
                            term = "",
                            fixAction = QuickFixAction(
                                title = "Insertar ';' al final",
                                replacementText = ";",
                                rangeToReplace = (lineOffset + lineLength)..(lineOffset + lineLength),
                                lineToReplace = lineNum
                            )
                        )
                    )
                }
            }

            // 5. Check variable duplication
            val varDeclPattern = Pattern.compile("\\b(?:let|const|var)\\s+([a-zA-Z_\\$][a-zA-Z0-9_\\$]*)\\b")
            val varMatcher = varDeclPattern.matcher(trimmed)
            if (varMatcher.find()) {
                val varName = varMatcher.group(1) ?: ""
                if (varName.isNotEmpty() && varName != "for" && varName != "if") {
                    if (declaredVariables.contains(varName)) {
                        diagnostics.add(
                            JsDiagnostic(
                                id = "dup_var_${lineNum}_$varName",
                                level = DiagnosticLevel.WARNING,
                                message = "Declaración redundante de la variable '$varName' en este archivo",
                                line = lineNum,
                                column = rawLine.indexOf(varName),
                                term = varName,
                                fixAction = QuickFixAction(
                                    title = "Convertir declaración redundante en asignación directa",
                                    replacementText = "",
                                    rangeToReplace = getVarDeclarationRange(code, i, varName),
                                    lineToReplace = lineNum
                                )
                            )
                        )
                    } else {
                        declaredVariables.add(varName)
                    }
                }
            }

            // 6. Discover functions & classes symbols
            // Standard Function Matcher
            val funcPattern = Pattern.compile("\\bfunction\\s+([a-zA-Z_\\$][a-zA-Z0-9_\\$]*)\\s*\\(")
            val funcMatcher = funcPattern.matcher(trimmed)
            if (funcMatcher.find()) {
                val name = funcMatcher.group(1) ?: "anonymous"
                symbols.add(
                    JsSymbol(
                        name = "$name()",
                        type = SymbolType.FUNCTION,
                        line = lineNum,
                        endLine = lineNum, // Updated later during fold matches
                        signature = "function $name(...)"
                    )
                )
            } else {
                // Check Arrow function
                val arrowPattern = Pattern.compile("\\b(const|let|var)\\s+([a-zA-Z_\\$][a-zA-Z0-9_\\$]*)\\s*=\\s*(?:\\([^)]*\\)|[a-zA-Z0-9_\\$]+)\\s*=>")
                val arrowMatcher = arrowPattern.matcher(trimmed)
                if (arrowMatcher.find()) {
                    val name = arrowMatcher.group(2) ?: "lambda"
                    symbols.add(
                        JsSymbol(
                            name = "$name() =>",
                            type = SymbolType.FUNCTION,
                            line = lineNum,
                            endLine = lineNum,
                            signature = "arrow function $name"
                        )
                    )
                }
            }

            // Class Matcher
            val classPattern = Pattern.compile("\\bclass\\s+([a-zA-Z_\\$][a-zA-Z0-9_\\$]*)\\b")
            val classMatcher = classPattern.matcher(trimmed)
            if (classMatcher.find()) {
                val className = classMatcher.group(1) ?: "AnonymousClass"
                symbols.add(
                    JsSymbol(
                        name = "class $className",
                        type = SymbolType.CLASS,
                        line = lineNum,
                        endLine = lineNum,
                        signature = "class $className"
                    )
                )
            }
        }

        // Bracket stack flush out. Any unclosed left on the stack are dangling errors
        while (!bracketStack.isEmpty()) {
            val dangling = bracketStack.pop()
            diagnostics.add(
                JsDiagnostic(
                    id = "unclosed_bracket_${dangling.second.first}",
                    level = DiagnosticLevel.ERROR,
                    message = "Paréntesis o llave '${dangling.first}' abierta en línea ${dangling.second.first} no tiene su contraparte de cierre",
                    line = dangling.second.first,
                    column = dangling.second.second,
                    term = dangling.first.toString()
                )
            )
        }

        // Post-process symbols: match functional scopes with computed folding ranges to accurately determine end lines.
        val updatedSymbols = symbols.map { symbol ->
            val matchingOuterFold = folds.filter { it.startLine == symbol.line }.maxByOrNull { it.endLine }
            if (matchingOuterFold != null) {
                symbol.copy(endLine = matchingOuterFold.endLine)
            } else {
                symbol
            }
        }

        return ParseResult(
            diagnostics = diagnostics.sortedBy { it.line },
            symbols = updatedSymbols,
            foldableRanges = folds.sortedBy { it.startLine }
        )
    }

    private fun openingToClosing(opening: Char): Char {
        return when (opening) {
            '{' -> '}'
            '(' -> ')'
            '[' -> ']'
            else -> ' '
        }
    }

    private fun getLineStartIndex(code: String, lineIndex: Int): Int {
        var start = 0
        val lines = code.split("\n")
        for (i in 0 until lineIndex) {
            if (i < lines.size) {
                start += lines[i].length + 1 // +1 for the newline '\n'
            }
        }
        return start
    }

    private fun getVarDeclarationRange(code: String, lineIndex: Int, varName: String): IntRange {
        val startOffset = getLineStartIndex(code, lineIndex)
        val lines = code.split("\n")
        val line = if (lineIndex < lines.size) lines[lineIndex] else ""
        
        // Find "let name" or "const name" in line
        val p = Pattern.compile("\\b(let|const|var)\\s+$varName\\b")
        val m = p.matcher(line)
        return if (m.find()) {
            val start = startOffset + m.start()
            val end = startOffset + m.end() - varName.length
            start until end
        } else {
            startOffset..startOffset
        }
    }
}

data class ParseResult(
    val diagnostics: List<JsDiagnostic>,
    val symbols: List<JsSymbol>,
    val foldableRanges: List<FoldingRange>
)
