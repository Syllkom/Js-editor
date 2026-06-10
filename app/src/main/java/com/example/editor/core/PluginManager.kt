package com.example.editor.core

import com.example.editor.plugins.AutoCloseBracketsPlugin
import com.example.editor.plugins.CodeActionsPlugin
import com.example.editor.plugins.ErrorLensPlugin
import com.example.editor.plugins.PrettierPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PluginManager {
    val allPlugins = listOf(
        PrettierPlugin(),
        ErrorLensPlugin(),
        AutoCloseBracketsPlugin(),
        CodeActionsPlugin()
    )

    // Keeps track of active plugins. Initially, all are turned ON for premium experience!
    private val _enabledPluginIds = MutableStateFlow<Set<String>>(allPlugins.map { it.id }.toSet())
    val enabledPluginIds: StateFlow<Set<String>> = _enabledPluginIds.asStateFlow()

    fun togglePlugin(id: String) {
        val current = _enabledPluginIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _enabledPluginIds.value = current
    }

    fun isPluginEnabled(id: String): Boolean {
        return _enabledPluginIds.value.contains(id)
    }

    /**
     * Intercepts key strokes and character entry
     */
    fun onTextChange(text: String, cursorPosition: Int, addedChar: Char?): TextChangeResult? {
        if (!isPluginEnabled("auto_close_brackets")) return null
        
        val autoClose = allPlugins.find { it.id == "auto_close_brackets" } ?: return null
        return autoClose.onTextChange(text, cursorPosition, addedChar)
    }

    /**
     * Runs Prettier formatter on demand
     */
    fun formatCode(text: String): String {
        if (!isPluginEnabled("prettier")) return text
        val prettier = allPlugins.find { it.id == "prettier" } ?: return text
        return prettier.formatCode(text)
    }

    /**
     * Gets inline Error Lens warning decoration
     */
    fun getLineOverlay(line1Indexed: Int, diagnostics: List<JsDiagnostic>): AlignmentOverlay? {
        if (!isPluginEnabled("error_lens")) return null
        val lens = allPlugins.find { it.id == "error_lens" } ?: return null
        return lens.getLineOverlay(line1Indexed, diagnostics)
    }

    /**
     * Gathers quick fix code actions
     */
    fun getCodeActions(line1Indexed: Int, diagnostics: List<JsDiagnostic>): List<QuickFixAction> {
        if (!isPluginEnabled("code_actions")) return emptyList()
        val actions = allPlugins.find { it.id == "code_actions" } ?: return emptyList()
        return actions.getCodeActions(line1Indexed, diagnostics)
    }
}
