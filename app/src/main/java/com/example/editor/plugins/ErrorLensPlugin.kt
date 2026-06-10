package com.example.editor.plugins

import com.example.editor.core.AlignmentOverlay
import com.example.editor.core.JsDiagnostic
import com.example.editor.core.DiagnosticLevel
import com.example.editor.core.Plugin

class ErrorLensPlugin : Plugin {
    override val id = "error_lens"
    override val name = "Error Lens"
    override val description = "Muestra errores y advertencias de sintaxis directamente al final de las líneas afectadas en el editor."
    override val author = "VS Code Built-in"
    override val version = "1.2.0"
    override val iconName = "warning"

    override fun getLineOverlay(line1Indexed: Int, diagnostics: List<JsDiagnostic>): AlignmentOverlay? {
        // Find if this line has any error or warning, prioritizing errors
        val lineDiag = diagnostics.filter { it.line == line1Indexed }
        if (lineDiag.isEmpty()) return null

        val priorityDiag = lineDiag.find { it.level == DiagnosticLevel.ERROR } ?: lineDiag.first()
        val isError = priorityDiag.level == DiagnosticLevel.ERROR
        val prefix = if (isError) "🔴 error: " else "🟡 warning: "

        return AlignmentOverlay(
            text = "$prefix${priorityDiag.message}",
            isError = isError
        )
    }
}
