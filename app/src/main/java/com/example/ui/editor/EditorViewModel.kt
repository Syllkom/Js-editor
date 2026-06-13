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
import kotlinx.coroutines.runBlocking

class EditorViewModel(private val repository: FileRepository, private val context: Context) : ViewModel() {

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

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var isUndoRedoOperation = false

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentText = _editorText.value
            redoStack.add(currentText)
            val previousText = undoStack.removeLast()
            isUndoRedoOperation = true
            onEditorTextChanged(previousText, null, false)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentText = _editorText.value
            undoStack.add(currentText)
            val nextText = redoStack.removeLast()
            isUndoRedoOperation = true
            onEditorTextChanged(nextText, null, false)
        }
    }

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
    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen.asStateFlow()

    private val _activeSidebarTab = MutableStateFlow("explorer")
    val activeSidebarTab: StateFlow<String> = _activeSidebarTab.asStateFlow()

    private val prefs = context.getSharedPreferences("js_editor_prefs", Context.MODE_PRIVATE)

    private val _themeIsDark = MutableStateFlow(prefs.getBoolean("themeIsDark", true))
    val themeIsDark: StateFlow<Boolean> = _themeIsDark.asStateFlow()

    private val _isMinimapOpen = MutableStateFlow(prefs.getBoolean("isMinimapOpen", true))
    val isMinimapOpen: StateFlow<Boolean> = _isMinimapOpen.asStateFlow()

    private val _isMinimapTextVisible = MutableStateFlow(prefs.getBoolean("isMinimapTextVisible", false))
    val isMinimapTextVisible: StateFlow<Boolean> = _isMinimapTextVisible.asStateFlow()

    private val _isIndentGuidesEnabled = MutableStateFlow(prefs.getBoolean("isIndentGuidesEnabled", true))
    val isIndentGuidesEnabled: StateFlow<Boolean> = _isIndentGuidesEnabled.asStateFlow()

    private val _editorFontSize = MutableStateFlow(prefs.getFloat("editorFontSize", 14f))
    val editorFontSize: StateFlow<Float> = _editorFontSize.asStateFlow()

    // Enabled plugins observer
    val enabledPluginIds: StateFlow<Set<String>> = PluginManager.enabledPluginIds

    // Jobs
    private var parseJob: Job? = null
    private var saveJob: Job? = null

    init {
        // Restore SAF tree URI if saved
        val savedSafTreeUri = prefs.getString("safTreeUri", null)
        if (savedSafTreeUri != null) {
            try {
                val uri = android.net.Uri.parse(savedSafTreeUri)
                setSafTreeUri(uri, context)
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Restore open tabs and active file from prefs
        viewModelScope.launch {
            val fileList = repository.allFiles.filter { it.isNotEmpty() }.first()
            val savedActiveId = prefs.getLong("activeFileId", -1L)
            val savedOpenIds = prefs.getString("openFileIds", "")
                ?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

            val initialOpen = fileList.filter { it.id in savedOpenIds }
            _openTabs.value = initialOpen

            val active = fileList.find { it.id == savedActiveId } ?: initialOpen.firstOrNull()

            if (active != null) {
                // FIX #1: Al restaurar un archivo SAF, releer el contenido actualizado del disco.
                // Antes solo se usaba el content guardado en la DB, que podía estar desactualizado
                // si la app se cerró antes de que el saveJob (delay 1000ms) llegara a ejecutarse.
                var freshContent = active.content
                if (active.path.isNotEmpty() && active.path != "/") {
                    try {
                        val uri = android.net.Uri.parse(active.path)
                        val diskContent = context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                        if (diskContent != null) {
                            freshContent = diskContent
                            // Sincronizar DB con lo que hay en disco
                            repository.updateFile(active.copy(content = freshContent))
                        }
                    } catch (e: Exception) {
                        // Permiso revocado u otro error: usar el content de la DB como fallback
                        e.printStackTrace()
                    }
                }

                _activeFile.value = active.copy(content = freshContent)
                _editorText.value = freshContent
                onEditorTextChanged(freshContent, null, true)
            } else if (fileList.isNotEmpty()) {
                val appJs = fileList.find { it.name == "app.js" } ?: fileList.first()
                openFileInTab(appJs)
            }
        }
    }

    // Directory SAF state
    private val _safTreeUri = MutableStateFlow<String?>(null)
    val safTreeUri: StateFlow<String?> = _safTreeUri.asStateFlow()

    private val _safTreeName = MutableStateFlow<String?>(null)
    val safTreeName: StateFlow<String?> = _safTreeName.asStateFlow()

    private val _safFiles = MutableStateFlow<List<com.example.data.WorkspaceFile>>(emptyList())
    val safFiles: StateFlow<List<com.example.data.WorkspaceFile>> = _safFiles.asStateFlow()

    private fun saveTabsState() {
        prefs.edit().apply {
            putString("openFileIds", _openTabs.value.joinToString(",") { it.id.toString() })
            putLong("activeFileId", _activeFile.value?.id ?: -1L)
            apply()
        }
    }

    fun setSafTreeUri(uri: android.net.Uri, context: Context) {
        _safTreeUri.value = uri.toString()
        prefs.edit().putString("safTreeUri", uri.toString()).apply()
        refreshSafSafTree(context)
    }

    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolders: StateFlow<Set<String>> = _expandedFolders.asStateFlow()

    fun toggleFolder(file: com.example.data.WorkspaceFile, context: Context) {
        val expanded = _expandedFolders.value.toMutableSet()
        if (expanded.contains(file.uri)) expanded.remove(file.uri) else expanded.add(file.uri)
        _expandedFolders.value = expanded
        refreshSafSafTree(context)
    }

    private fun refreshSafSafTree(context: Context) {
        val uriStr = _safTreeUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rootUri = android.net.Uri.parse(uriStr)
                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    _safTreeName.value = rootDoc.name ?: "Carpeta Abierta"
                    val fileList = mutableListOf<com.example.data.WorkspaceFile>()
                    val expanded = _expandedFolders.value

                    fun traverse(doc: androidx.documentfile.provider.DocumentFile, level: Int) {
                        val children = doc.listFiles()
                            .sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() ?: "" }))
                        children.forEach { child ->
                            fileList.add(
                                com.example.data.WorkspaceFile(
                                    uri = child.uri.toString(),
                                    name = child.name ?: "Unknown",
                                    isDirectory = child.isDirectory,
                                    level = level
                                )
                            )
                            if (child.isDirectory && expanded.contains(child.uri.toString())) {
                                traverse(child, level + 1)
                            }
                        }
                    }

                    traverse(rootDoc, 0)
                    _safFiles.value = fileList
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun openFileInTab(file: com.example.data.WorkspaceFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = android.net.Uri.parse(file.uri)
            val currentTabs = _openTabs.value.toMutableList()
            val existing = currentTabs.find { it.path == file.uri }

            if (existing == null) {
                try {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    val existingInDb = repository.allFiles.first().find { it.path == file.uri }
                    val finalFile = if (existingInDb != null) {
                        repository.updateFile(existingInDb.copy(content = content))
                        existingInDb.copy(content = content)
                    } else {
                        val newId = repository.insertFile(
                            VirtualFile(name = file.name, content = content, path = file.uri)
                        )
                        repository.getFileById(newId)!!
                    }
                    viewModelScope.launch(Dispatchers.Main) { openFileInTab(finalFile) }
                } catch (e: Exception) { e.printStackTrace() }
            } else {
                viewModelScope.launch(Dispatchers.Main) { selectTab(existing) }
            }
        }
    }

    fun openFileInTab(file: VirtualFile) {
        val currentTabs = _openTabs.value.toMutableList()
        if (!currentTabs.any { it.id == file.id }) {
            currentTabs.add(file)
            _openTabs.value = currentTabs
            saveTabsState()
        }
        selectTab(file)
    }

    fun selectTab(file: VirtualFile) {
        val prevActive = _activeFile.value
        if (prevActive != null && prevActive.id != file.id) {
            saveFileImmediately(prevActive.id, _editorText.value)
        }

        _activeFile.value = file
        _editorText.value = file.content
        _foldedStartLines.value = emptySet()
        onEditorTextChanged(file.content, addedChar = null, triggerCursorUpdate = true)
        saveTabsState()
    }

    fun closeTab(fileId: Long) {
        val currentTabs = _openTabs.value.toMutableList()
        val index = currentTabs.indexOfFirst { it.id == fileId }
        if (index != -1) {
            val closedFile = currentTabs.removeAt(index)
            _openTabs.value = currentTabs

            if (_activeFile.value?.id == fileId) {
                if (currentTabs.isNotEmpty()) {
                    val nextActive = if (index < currentTabs.size) currentTabs[index] else currentTabs.last()
                    selectTab(nextActive)
                } else {
                    _activeFile.value = null
                    _editorText.value = ""
                    saveTabsState()
                }
            } else {
                saveTabsState()
            }

            viewModelScope.launch {
                repository.getFileById(fileId)?.let {
                    if (it.content != closedFile.content) {
                        repository.updateFile(it.copy(content = closedFile.content))
                    }
                }
            }
        }
    }

    fun createNewFile(name: String, isDirectory: Boolean) {
        val safRoot = _safTreeUri.value
        if (safRoot != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val rootUri = android.net.Uri.parse(safRoot)
                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                    if (rootDoc != null) {
                        val newDoc = if (isDirectory) {
                            rootDoc.createDirectory(name)
                        } else {
                            val ext = if (name.contains(".")) "" else ".js"
                            val mimeType = if (name.endsWith(".js") || ext == ".js") "text/javascript" else "text/plain"
                            rootDoc.createFile(mimeType, "$name$ext")
                        }
                        if (newDoc != null && !isDirectory) {
                            val content = "// Nuevo archivo: ${newDoc.name}\n"
                            context.contentResolver.openOutputStream(newDoc.uri)
                                ?.use { it.write(content.toByteArray()) }
                        }
                        refreshSafSafTree(context)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val extension = if (name.contains(".")) "" else ".js"
                val newFile = VirtualFile(
                    name = "$name$extension",
                    content = "// Nuevo archivo: ${name}${extension}\n"
                )
                val newId = repository.insertFile(newFile)
                val createdFile = repository.getFileById(newId)
                if (createdFile != null) {
                    viewModelScope.launch(Dispatchers.Main) { openFileInTab(createdFile) }
                }
            }
        }
    }

    fun createNewFile(name: String) = createNewFile(name, false)

    fun renameFile(uriStr: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(
                    context, android.net.Uri.parse(uriStr)
                )
                if (doc != null && doc.renameTo(newName)) {
                    refreshSafSafTree(context)
                    repository.allFiles.first().find { it.path == uriStr }?.let { vFile ->
                        repository.updateFile(vFile.copy(name = newName))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun renameFile(fileId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileById(fileId) ?: return@launch
            val updated = file.copy(name = newName)
            repository.updateFile(updated)
            viewModelScope.launch(Dispatchers.Main) {
                _openTabs.value = _openTabs.value.map { if (it.id == fileId) updated else it }
                if (_activeFile.value?.id == fileId) _activeFile.value = updated
            }
        }
    }

    fun deleteFile(file: com.example.data.WorkspaceFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(
                    context, android.net.Uri.parse(file.uri)
                )
                if (doc != null && doc.delete()) {
                    refreshSafSafTree(context)
                    repository.allFiles.first().find { it.path == file.uri }?.let { vFile ->
                        repository.deleteFile(vFile)
                        viewModelScope.launch(Dispatchers.Main) { closeTab(vFile.id) }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteFile(file: VirtualFile) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFile(file)
            viewModelScope.launch(Dispatchers.Main) { closeTab(file.id) }
        }
    }

    fun onEditorTextChanged(
        newContent: String,
        addedChar: Char? = null,
        triggerCursorUpdate: Boolean = false
    ) {
        if (!isUndoRedoOperation && _editorText.value != newContent && _editorText.value.isNotEmpty()) {
            if (undoStack.isEmpty() || undoStack.last() != _editorText.value) {
                undoStack.add(_editorText.value)
                if (undoStack.size > 50) undoStack.removeFirst()
                redoStack.clear()
            }
        }
        isUndoRedoOperation = false

        var processedContent = newContent
        var processedCursor = _cursorPosition.value

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

        // FIX #2: Reducir el delay de guardado a 500ms para reducir el riesgo de pérdida de datos.
        // El guardado real síncronamente en onCleared() cubre el caso del cierre brusco.
        _activeFile.value?.let { file ->
            saveJob?.cancel()
            saveJob = viewModelScope.launch(Dispatchers.IO) {
                delay(500)
                persistContent(file, processedContent)
            }
        }

        // Background parser con debounce de 150ms (sin cambios)
        parseJob?.cancel()
        parseJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            val ext = _activeFile.value?.name?.substringAfterLast('.', "js") ?: "js"
            val result = JsParser.analyze(processedContent, ext)
            _diagnostics.value = result.diagnostics
            _symbols.value = result.symbols
            _foldableRanges.value = result.foldableRanges

            if (_searchQuery.value.isNotEmpty()) {
                calculateSearchResults(processedContent, _searchQuery.value)
            }
        }
    }

    // FIX #3: Lógica de persistencia extraída a función reutilizable
    private suspend fun persistContent(file: VirtualFile, content: String) {
        try {
            repository.updateFile(file.copy(content = content))
            if (file.path.isNotEmpty() && file.path != "/") {
                val uri = android.net.Uri.parse(file.path)
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(content.toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            repository.getFileById(id)?.let { file ->
                persistContent(file, content)
            }
        }
    }

    fun formatCurrentCode() {
        val raw = _editorText.value
        val formatted = PluginManager.formatCode(raw)
        if (formatted != raw) {
            onEditorTextChanged(formatted, triggerCursorUpdate = true)
        }
    }

    fun applyQuickFix(fix: QuickFixAction) {
        val originalText = _editorText.value
        val range = fix.rangeToReplace
        if (range.first >= 0 && range.last <= originalText.length) {
            val left = originalText.substring(0, range.first)
            val right = originalText.substring(range.first + fix.replacementText.length)
            val replacedText = left + fix.replacementText + right

            val linesOffset = originalText.split("\n")
            var newOffset = 0
            for (i in 0 until fix.lineToReplace) {
                if (i < linesOffset.size) newOffset += linesOffset[i].length + 1
            }
            newOffset = minOf(replacedText.length, maxOf(0, newOffset - 1))
            _cursorPosition.value = newOffset
            onEditorTextChanged(replacedText, triggerCursorUpdate = true)
        } else {
            val lines = originalText.split("\n").toMutableList()
            val targetLineIdx = fix.lineToReplace - 1
            if (targetLineIdx in lines.indices) {
                lines[targetLineIdx] = lines[targetLineIdx] + ";"
                onEditorTextChanged(lines.joinToString("\n"), triggerCursorUpdate = true)
            }
        }
    }

    fun toggleFolding(startLine: Int) {
        val current = _foldedStartLines.value.toMutableSet()
        if (current.contains(startLine)) current.remove(startLine) else current.add(startLine)
        _foldedStartLines.value = current
    }

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

    fun updateReplaceQuery(query: String) { _replaceQuery.value = query }

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
        val prevIndex = if (_searchFocusedIndex.value - 1 < 0) results.size - 1
                        else _searchFocusedIndex.value - 1
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
        val newText = _editorText.value.replace(_searchQuery.value, _replaceQuery.value, ignoreCase = true)
        onEditorTextChanged(newText)
    }

    fun toggleSidebar() { _isSidebarOpen.value = !_isSidebarOpen.value }

    fun setSidebarTab(tab: String) {
        _activeSidebarTab.value = tab
        _isSidebarOpen.value = true
    }

    fun toggleTheme() {
        val newVal = !_themeIsDark.value
        _themeIsDark.value = newVal
        prefs.edit().putBoolean("themeIsDark", newVal).apply()
    }

    fun toggleMinimap() {
        val newVal = !_isMinimapOpen.value
        _isMinimapOpen.value = newVal
        prefs.edit().putBoolean("isMinimapOpen", newVal).apply()
    }

    fun toggleMinimapTextVisibility() {
        val newVal = !_isMinimapTextVisible.value
        _isMinimapTextVisible.value = newVal
        prefs.edit().putBoolean("isMinimapTextVisible", newVal).apply()
    }

    fun toggleIndentGuides() {
        val newVal = !_isIndentGuidesEnabled.value
        _isIndentGuidesEnabled.value = newVal
        prefs.edit().putBoolean("isIndentGuidesEnabled", newVal).apply()
    }

    fun setEditorFontSize(size: Float) {
        _editorFontSize.value = size
        prefs.edit().putFloat("editorFontSize", size).apply()
    }

    fun togglePlugin(id: String) { PluginManager.togglePlugin(id) }

    // FIX #4: onCleared guarda síncronamente antes de destruir el ViewModel.
    // El saveJob usa delay(500ms), así que si la app se cierra durante ese delay
    // el job se cancela y los cambios se pierden. runBlocking aquí garantiza que
    // el último estado siempre llega a disco, independientemente del timing del cierre.
    override fun onCleared() {
        parseJob?.cancel()
        saveJob?.cancel()

        val file = _activeFile.value
        val content = _editorText.value
        if (file != null && content.isNotEmpty()) {
            runBlocking(Dispatchers.IO) {
                persistContent(file, content)
            }
        }

        super.onCleared()
    }
}

class EditorViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val repository = FileRepository(database.fileDao())
            @Suppress("UNCHECKED_CAST")
            return EditorViewModel(repository, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
