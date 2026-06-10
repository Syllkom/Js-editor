package com.example.editor.plugins

import com.example.editor.core.JsDiagnostic
import com.example.editor.core.Plugin
import com.example.editor.core.QuickFixAction

class CodeActionsPlugin : Plugin {
    override val id = "code_actions"
    override val name = "Code Actions"
    override val description = "Proporciona sugerencias inteligentes y parches rápidos (Quick Fixes) para reparar errores de sintaxis comunes en tus líneas de código."
    override val author = "VS Code Built-in"
    override val version = "1.0.5"
    override val iconName = "lightbulb"

    override fun getCodeActions(line1Indexed: Int, diagnostics: List<JsDiagnostic>): List<QuickFixAction> {
        // Fetch all diagnostics on the current line that provide an interactive quick fix
        return diagnostics
            .filter { it.line == line1Indexed && it.fixAction != null }
            .map { it.fixAction!! }
    }
}
