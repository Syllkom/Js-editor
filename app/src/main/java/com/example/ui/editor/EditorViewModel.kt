package com.example.ui.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FileRepository
import com.example.data.VirtualFile
import com.example.editor.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EditorViewModel(private val repository: FileRepository) : ViewModel() {

    // Files state
    val files: StateFlow<List<VirtualFile>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tabs state
    private val _openTabs = MutableStateFlow<List<VirtualFile>>(emptyList())
    val openTabs: StateFlow<List<VirtualFile>> = _openTabs.asStateFlow()

    private val _activeFile = MutableStateFlow<VirtualFile?>(null)
    val activeFile: StateFlow<VirtualFile?> = _activeFile.asStateFlow()

    // Editor live content buffer
    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    // Cursor statistics
    private val _cursorPosition = MutableStateFlow(0)
    val cursorPosition: StateFlow<Int> = _cursorPosition.asStateFlow()

    private val _cursorLine = MutableStateFlow(1)
    val cursorLine: StateFlow<Int> = _cursorLine.asStateFlow()

    private val _cursorColumn = MutableStateFlow(1)
    val cursorColumn: StateFlow<Int> = _cursorColumn.asStateFlow()

    // Parser results
    private val _diagnostics = MutableStateFlow<List<JsDiagnostic>>(emptyList())
    val diagnostics: StateFlow<List<JsDiagnostic>> = _diagnostics.asStateFlow()

    private val _symbols = MutableStateFlow<List<JsSymbol>>(emptyList())
    val symbols: StateFlow<List<JsSymbol>> = _symbols.asStateFlow()

    private val _foldableRanges = MutableStateFlow<List<FoldingRange>>(emptyList())
    val foldableRanges: StateFlow<List<FoldingRange>> = _foldableRanges.asStateFlow()

    // Active folded (hidden) start lines
    private val _foldedStartLines = MutableStateFlow<Set<Int>>(emptySet())
    val foldedStartLines: StateFlow<Set<Int>> = _foldedStartLines.asStateFlow()

    // Global Search & Replace states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<IntRange>>(emptyList())
    val searchResults: StateFlow<List<IntRange>> = _searchResults.asStateFlow()

    private val _searchFocusedIndex = MutableStateFlow(-1)
    val searchFocusedIndex: StateFlow<Int> = _searchFocusedIndex.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    // UI Panel controller
    private val _isSidebarOpen = MutableStateFlow(true)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen.asStateFlow()

    // "explorer", "plugins", "outline", "settings"
    private val _activeSidebarTab = MutableStateFlow("explorer")
    val activeSidebarTab: StateFlow<String> = _activeSidebarTab.asStateFlow()

    private val _themeIsDark = MutableStateFlow(true)
    val themeIsDark: StateFlow<Boolean> = _themeIsDark.asStateFlow()

    private val _isMinimapOpen = MutableStateFlow(true)
    val isMinimapOpen: StateFlow<Boolean> = _isMinimapOpen.asStateFlow()

    // Enabled plugins observer
    val enabledPluginIds: StateFlow<Set<String>> = PluginManager.enabledPluginIds

    // Jobs
    private var parseJob: Job? = null
    private var saveJob: Job? = null

    init {
        // Collect files to open the first sample file by default
        viewModelScope.launch {
            files.collect { activeFileList ->
                if (activeFileList.isNotEmpty() && _activeFile.value == null) {
                    val appJs = activeFileList.find { it.name == "app.js" } ?: activeFileList.first()
                    openFileInTab(appJs)
                }
            }
        }
    }

    fun openFileInTab(file: VirtualFile) {
        val currentTabs = _openTabs.value.toMutableList()
        if (!currentTabs.any { it.id == file.id }) {
            currentTabs.add(file)
            _openTabs.value = currentTabs
        }
        selectTab(file)
    }

    fun selectTab(file: VirtualFile) {
        // Save current active file before swapping
        val prevActive = _activeFile.value
        if (prevActive != null && prevActive.id != file.id) {
            saveFileImmediately(prevActive.id, _editorText.value)
        }

        _activeFile.value = file
        _editorText.value = file.content
        _foldedStartLines.value = emptySet() // Reset folding state for a new file context
        onEditorTextChanged(file.content, addedChar = null, triggerCursorUpdate = true)
    }

    fun closeTab(fileId: Long) {
        val currentTabs = _openTabs.value.toMutableList()
        val index = currentTabs.indexOfFirst { it.id == fileId }
        if (index != -1) {
            val closedFile = currentTabs.removeAt(index)
            _openTabs.value = currentTabs

            // If closed active tab, open another
            if (_activeFile.value?.id == fileId) {
                if (currentTabs.isNotEmpty()) {
                    val nextActive = if (index < currentTabs.size) currentTabs[index] else currentTabs.last()
                    selectTab(nextActive)
                } else {
                    _activeFile.value = null
                    _editorText.value = ""
                }
            }
            
            // Save closed file to DB
            viewModelScope.launch {
                repository.getFileById(fileId)?.let {
                    if (it.content != closedFile.content) {
                        repository.updateFile(it.copy(content = closedFile.content))
                    }
                }
            }
        }
    }

    fun createNewFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val extension = if (name.contains(".")) "" else ".js"
            val newFile = VirtualFile(
                name = "$name$extension",
                content = """// Nuevo archivo de JavaScript: ${name}${extension}
function init() {
  console.log("¡Hola Mundo!");
}

init();
"""
            )
            val newId = repository.insertFile(newFile)
            val createdFile = repository.getFileById(newId)
            if (createdFile != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    openFileInTab(createdFile)
                }
            }
        }
    }

    fun renameFile(fileId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileById(fileId)
            if (file != null) {
                val updated = file.copy(name = newName)
                repository.updateFile(updated)

                viewModelScope.launch(Dispatchers.Main) {
                    // Update tabs state if open
                    val currentTabs = _openTabs.value.map {
                        if (it.id == fileId) updated else it
                    }
                    _openTabs.value = currentTabs
                    if (_activeFile.value?.id == fileId) {
                        _activeFile.value = updated
                    }
                }
            }
        }
    }

    fun deleteFile(file: VirtualFile) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFile(file)
            viewModelScope.launch(Dispatchers.Main) {
                closeTab(file.id)
            }
        }
    }

    fun onEditorTextChanged(newContent: String, addedChar: Char? = null, triggerCursorUpdate: Boolean = false) {
        var processedContent = newContent
        var processedCursor = _cursorPosition.value

        // Intercept via Plugins (e.g. Auto Close Brackets)
        if (addedChar != null) {
            val interceptResult = PluginManager.onTextChange(newContent, _cursorPosition.value, addedChar)
            if (interceptResult != null) {
                processedContent = interceptResult.newText
                processedCursor = interceptResult.newCursorPosition
            }
        }

        _editorText.value = processedContent
        if (processedCursor != _cursorPosition.value || triggerCursorUpdate) {
            updateCursorValues(processedCursor, processedContent)
        }

        // Auto Save to DB on text alteration with simple debounce
        _activeFile.value?.let { file ->
            saveJob?.cancel()
            saveJob = viewModelScope.launch(Dispatchers.IO) {
                delay(1000)
                repository.updateFile(file.copy(content = processedContent))
            }
        }

        // Background Parser AST Compilation (Debounced 150ms)
        parseJob?.cancel()
        parseJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            val result = JsParser.analyze(processedContent)
            _diagnostics.value = result.diagnostics
            _symbols.value = result.symbols
            _foldableRanges.value = result.foldableRanges

            // Trigger Search matching updates if search query is active
            if (_searchQuery.value.isNotEmpty()) {
                calculateSearchResults(processedContent, _searchQuery.value)
            }
        }
    }

    fun updateCursor(index: Int) {
        _cursorPosition.value = index
        updateCursorValues(index, _editorText.value)
    }

    private fun updateCursorValues(index: Int, text: String) {
        if (index < 0 || index > text.length) return
        
        val substring = text.substring(0, index)
        val lines = substring.split("\n")
        
        _cursorLine.value = lines.size
        _cursorColumn.value = lines.last().length + 1
    }

    private fun saveFileImmediately(id: Long, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getFileById(id)?.let {
                repository.updateFile(it.copy(content = content))
            }
        }
    }

    // Code Format: format with built-in Prettier rules
    fun formatCurrentCode() {
        val raw = _editorText.value
        val formatted = PluginManager.formatCode(raw)
        if (formatted != raw) {
            onEditorTextChanged(formatted, triggerCursorUpdate = true)
        }
    }

    // Direct Fixer: apply Quick Fix Suggestions (from Code Actions)
    fun applyQuickFix(fix: QuickFixAction) {
        val originalText = _editorText.value
        val range = fix.rangeToReplace
        if (range.first >= 0 && range.last <= originalText.length) {
            val left = originalText.substring(0, range.first)
            val right = originalText.substring(range.first + fix.replacementText.length)
            val replacedText = left + fix.replacementText + right
            
            // Adjust cursor position to fixed line end
            val linesOffset = originalText.split("\n")
            var newOffset = 0
            for (i in 0 until fix.lineToReplace) {
                if (i < linesOffset.size) {
                    newOffset += linesOffset[i].length + 1
                }
            }
            newOffset = minOf(replacedText.length, maxOf(0, newOffset - 1))

            _cursorPosition.value = newOffset
            onEditorTextChanged(replacedText, triggerCursorUpdate = true)
        } else {
            // Semicolon insertion failure backup (Append directly to line end)
            val lines = originalText.split("\n").toMutableList()
            val targetLineIdx = fix.lineToReplace - 1
            if (targetLineIdx in lines.indices) {
                lines[targetLineIdx] = lines[targetLineIdx] + ";"
                val joined = lines.joinToString("\n")
                onEditorTextChanged(joined, triggerCursorUpdate = true)
            }
        }
    }

    // Code Folding actions
    fun toggleFolding(startLine: Int) {
        val current = _foldedStartLines.value.toMutableSet()
        if (current.contains(startLine)) {
            current.remove(startLine)
        } else {
            current.add(startLine)
        }
        _foldedStartLines.value = current
    }

    // Global Search & Replace
    fun toggleSearchOpen() {
        _isSearchOpen.value = !_isSearchOpen.value
        if (!_isSearchOpen.value) {
            _searchResults.value = emptyList()
            _searchFocusedIndex.value = -1
        } else {
            if (_searchQuery.value.isNotEmpty()) {
                calculateSearchResults(_editorText.value, _searchQuery.value)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        calculateSearchResults(_editorText.value, query)
    }

    fun updateReplaceQuery(query: String) {
        _replaceQuery.value = query
    }

    private fun calculateSearchResults(text: String, query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            _searchFocusedIndex.value = -1
            return
        }

        val list = ArrayList<IntRange>()
        var foundIdx = text.indexOf(query, 0, ignoreCase = true)
        while (foundIdx != -1) {
            list.add(foundIdx until (foundIdx + query.length))
            foundIdx = text.indexOf(query, foundIdx + query.length, ignoreCase = true)
        }

        _searchResults.value = list
        if (list.isNotEmpty()) {
            _searchFocusedIndex.value = 0
            // Position cursor on the first match
            _cursorPosition.value = list[0].first
        } else {
            _searchFocusedIndex.value = -1
        }
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIndex = (_searchFocusedIndex.value + 1) % results.size
        _searchFocusedIndex.value = nextIndex
        _cursorPosition.value = results[nextIndex].first
        updateCursorValues(results[nextIndex].first, _editorText.value)
    }

    fun prevSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val prevIndex = if (_searchFocusedIndex.value - 1 < 0) results.size - 1 else _searchFocusedIndex.value - 1
        _searchFocusedIndex.value = prevIndex
        _cursorPosition.value = results[prevIndex].first
        updateCursorValues(results[prevIndex].first, _editorText.value)
    }

    fun replaceCurrent() {
        val matches = _searchResults.value
        val activeIdx = _searchFocusedIndex.value
        if (matches.isEmpty() || activeIdx !in matches.indices) return

        val range = matches[activeIdx]
        val text = _editorText.value
        val newText = text.substring(0, range.first) + _replaceQuery.value + text.substring(range.last + 1)
        
        onEditorTextChanged(newText)
    }

    fun replaceAll() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return

        val query = _searchQuery.value
        val replace = _replaceQuery.value
        val currentText = _editorText.value
        val newText = currentText.replace(query, replace, ignoreCase = true)

        onEditorTextChanged(newText)
    }

    // Sidebar panel and theme toggles
    fun toggleSidebar() {
        _isSidebarOpen.value = !_isSidebarOpen.value
    }

    fun setSidebarTab(tab: String) {
        _activeSidebarTab.value = tab
        _isSidebarOpen.value = true
    }

    fun toggleTheme() {
        _themeIsDark.value = !_themeIsDark.value
    }

    fun toggleMinimap() {
        _isMinimapOpen.value = !_isMinimapOpen.value
    }

    fun togglePlugin(id: String) {
        PluginManager.togglePlugin(id)
    }

    override fun onCleared() {
        super.onCleared()
        parseJob?.cancel()
        saveJob?.cancel()
    }
}

class EditorViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val repository = FileRepository(database.fileDao())
            @Suppress("UNCHECKED_CAST")
            return EditorViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
