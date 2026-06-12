package com.example.ui.editor

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VirtualFile
import com.example.editor.core.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val isDark by viewModel.themeIsDark.collectAsStateWithLifecycle()
    val isSidebarOpen by viewModel.isSidebarOpen.collectAsStateWithLifecycle()
    val sidebarTab by viewModel.activeSidebarTab.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()

    // Workspace Editor styling tokens
    val editorBackground = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)
    val activityBarBackground = if (isDark) Color(0xFF181818) else Color(0xFFE1E1E1)
    val sidebarBackground = if (isDark) Color(0xFF252526) else Color(0xFFEBEBEB)
    val statusBarBackground = if (isDark) Color(0xFF181818) else Color(0xFF005FB8) // Match night theme vs blue
    val textColor = if (isDark) Color(0xFFD4D4D4) else Color(0xFF333333)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            VscodeStatusBar(viewModel, statusBarBackground)
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(editorBackground)
        ) {
            // 1. ACTIVITY BAR (Thin leftmost layout sidebar for fast navigation toggles)
            ActivityBar(
                viewModel = viewModel,
                isDark = isDark,
                sidebarTab = sidebarTab,
                isSidebarOpen = isSidebarOpen,
                background = activityBarBackground
            )

            // 2. DYNAMIC SIDEBAR (Width: 260.dp. Expands / Collapses)
            AnimatedVisibility(
                visible = isSidebarOpen,
                enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = spring()) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = spring()) + fadeOut()
            ) {
                SidebarPane(
                    viewModel = viewModel,
                    isDark = isDark,
                    activeTab = sidebarTab,
                    background = sidebarBackground
                )
            }

            // 3. EDITOR WORKSPACE (Main workspace: open tabs, canvas, search float, inline diagnostics, minimap)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(editorBackground)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Open Tabs horizontal row
                    TabSystem(viewModel, isDark)

                    // Global Search and Replace inside workspace
                    if (isSearchOpen) {
                        SearchAndReplacePanel(viewModel, isDark)
                    }

                    // Main Canvas containing Gutter + Custom BasicTextField editing row + Minimap
                    if (activeFile != null) {
                        val scrollState = rememberScrollState()

                        Row(modifier = Modifier.weight(1f)) {
                            // Code Text Pane
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                EditorCanvas(viewModel, isDark, editorBackground, scrollState)
                                
                                if (isSidebarOpen) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Transparent)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                viewModel.toggleSidebar()
                                            }
                                    )
                                }
                                
                                // Optional Minimap overlaying the Canvas
                                val isMinimapOpen by viewModel.isMinimapOpen.collectAsStateWithLifecycle()
                                if (isMinimapOpen) {
                                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                        MinimapPane(viewModel, isDark, scrollState)
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty Workspace state
                        EmptyEditorState(isDark, textColor)
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityBar(
    viewModel: EditorViewModel,
    isDark: Boolean,
    sidebarTab: String,
    isSidebarOpen: Boolean,
    background: Color
) {
    val activeTint = if (isDark) Color.White else Color(0xFF1E1E1E)
    val inactiveTint = if (isDark) Color.Gray else Color(0xFF777777)

    Column(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .background(background)
            .drawBehind {
                // border highlight
                drawLine(
                    color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFCCCCCC),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // File Explorer
            ActivityBarItem(
                icon = Icons.Default.List,
                contentDescription = "Explorador de Archivos",
                isSelected = isSidebarOpen && sidebarTab == "explorer",
                activeTint = activeTint,
                inactiveTint = inactiveTint,
                onClick = { 
                    if (isSidebarOpen && sidebarTab == "explorer") viewModel.toggleSidebar()
                    else viewModel.setSidebarTab("explorer")
                }
            )

            // Search and Replace
            ActivityBarItem(
                icon = Icons.Default.Search,
                contentDescription = "Buscar y Reemplazar",
                isSelected = isSidebarOpen && sidebarTab == "search",
                activeTint = activeTint,
                inactiveTint = inactiveTint,
                onClick = { 
                    if (isSidebarOpen && sidebarTab == "search") viewModel.toggleSidebar()
                    else viewModel.setSidebarTab("search")
                }
            )

            // Symbols Outline
            ActivityBarItem(
                icon = Icons.Default.Menu,
                contentDescription = "Navegación de Símbolos",
                isSelected = isSidebarOpen && sidebarTab == "outline",
                activeTint = activeTint,
                inactiveTint = inactiveTint,
                onClick = {
                    if (isSidebarOpen && sidebarTab == "outline") viewModel.toggleSidebar()
                    else viewModel.setSidebarTab("outline")
                }
            )

            // Plugin extension puzzle icon 
            ActivityBarItem(
                icon = Icons.Default.Warning, // Map to warning / plugins placeholder icon
                contentDescription = "Administrador de Plugins",
                isSelected = isSidebarOpen && sidebarTab == "plugins",
                activeTint = activeTint,
                inactiveTint = inactiveTint,
                onClick = {
                    if (isSidebarOpen && sidebarTab == "plugins") viewModel.toggleSidebar()
                    else viewModel.setSidebarTab("plugins")
                }
            )
            
            // Settings
            ActivityBarItem(
                icon = Icons.Default.Settings,
                contentDescription = "Configuración",
                isSelected = isSidebarOpen && sidebarTab == "settings",
                activeTint = activeTint,
                inactiveTint = inactiveTint,
                onClick = {
                    if (isSidebarOpen && sidebarTab == "settings") viewModel.toggleSidebar()
                    else viewModel.setSidebarTab("settings")
                }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Prettify button
            IconButton(
                onClick = { viewModel.formatCurrentCode() },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Formatear con Prettier",
                    tint = if (isDark) Color(0xFF4EC9B0) else Color(0xFF008000)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ActivityBarItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    activeTint: Color,
    inactiveTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Active line marker indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(26.dp)
                    .background(activeTint)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) activeTint else inactiveTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SidebarPane(
    viewModel: EditorViewModel,
    isDark: Boolean,
    activeTab: String,
    background: Color
) {
    val borderColor = if (isDark) Color(0xFF2D2D2D) else Color(0xFFCCCCCC)
    val paneHeaderTextColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF333333)

    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(background)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        // Sidebar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (activeTab) {
                    "explorer" -> "EXPLORADOR: JS WORKSPACE"
                    "search" -> "BUSCAR Y REEMPLAZAR"
                    "outline" -> "SÍMBOLOS Y MÉTODOS"
                    "plugins" -> "SISTEMA DE PLUGINS"
                    "settings" -> "CONFIGURACIÓN"
                    else -> "PANEL DE CONTROL"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = paneHeaderTextColor,
                letterSpacing = 0.5.sp
            )
            IconButton(
                onClick = { viewModel.toggleSidebar() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar Panel",
                    tint = paneHeaderTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(color = borderColor, thickness = 0.5.dp)

        // Sidebar content lists
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                "explorer" -> ExplorerTabContent(viewModel, isDark)
                "search" -> SearchTabContent(viewModel, isDark)
                "outline" -> OutlineTabContent(viewModel, isDark)
                "plugins" -> PluginsTabContent(viewModel, isDark)
                "settings" -> SettingsTabContent(viewModel, isDark)
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

// --------------------- EXPLORER TAB CONTENT ---------------------
@Composable
fun ExplorerTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val safTreeUri by viewModel.safTreeUri.collectAsStateWithLifecycle()
    val safTreeName by viewModel.safTreeName.collectAsStateWithLifecycle()
    val safFiles by viewModel.safFiles.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showFolderCreateDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<String?>(null) }
    var renameDefaultName by remember { mutableStateOf("") }
    
    val itemTextColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF1E1E1E)
    val secondaryColor = if (isDark) Color(0xFF888888) else Color(0xFF555555)
    
    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.setSafTreeUri(it, context)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = secondaryColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Explorador",
                    fontSize = 11.sp,
                    color = secondaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (safTreeUri != null) {
                    IconButton(
                        onClick = { showFolderCreateDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nueva Carpeta",
                            tint = itemTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nuevo Archivo",
                        tint = itemTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Button(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(if (safTreeUri != null) "Cambiar Carpeta" else "Abrir Carpeta...", fontSize = 12.sp)
        }

        if (safTreeUri == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Selecciona una carpeta", fontSize = 12.sp, color = secondaryColor)
            }
        } else {
            // Folder title ALWAYS shown if safTreeUri is not null
            Text(
                text = (safTreeName ?: "Carpeta").uppercase(),
                fontSize = 10.sp,
                color = secondaryColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
            
            if (safFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Carpeta vacía", fontSize = 12.sp, color = secondaryColor)
                }
            } else {
                LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(safFiles, key = { it.uri }) { file ->
                    val isActive = file.uri == activeFile?.path
                    val itemBg = if (isActive) {
                        if (isDark) Color(0xFF37373D) else Color(0xFFD0D0D0)
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(itemBg)
                            .clickable {
                                if (file.isDirectory) {
                                    // Expand/Collapse folder if we had a tree, 
                                    // for flat structure maybe just ignore or handle navigation
                                } else {
                                    viewModel.openFileInTab(file)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            // Basic indent for level if we do tree
                            .padding(start = (file.level * 12).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (file.isDirectory) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Carpeta",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            // JS Icon representation
                            val isJS = file.name.endsWith(".js")
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isJS) Color(0xFFF1DC50) else Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isJS) "JS" else "TXT",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontSize = 13.sp,
                                color = if (isActive) {
                                    if (isDark) Color.White else Color.Black
                                } else itemTextColor,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Options
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { 
                                    renameTargetFile = file.uri 
                                    renameDefaultName = file.name
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Renombrar",
                                    tint = secondaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.deleteFile(file) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Borrar",
                                    tint = if (isDark) Color(0xFFF44336) else Color(0xFFD32F2F),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showCreateDialog) {
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Crear archivo") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nombre del archivo") },
                    placeholder = { Text("ejemplo.js") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileName.isNotBlank()) {
                            viewModel.createNewFile(fileName, isDirectory = false)
                            showCreateDialog = false
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showFolderCreateDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolderCreateDialog = false },
            title = { Text("Crear carpeta") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nombre de la carpeta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createNewFile(folderName, isDirectory = true)
                            showFolderCreateDialog = false
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderCreateDialog = false }) { Text("Cancelar") }
            }
        )
    }

    renameTargetFile?.let { uriStr ->
        var fileName by remember { mutableStateOf(renameDefaultName) }
        AlertDialog(
            onDismissRequest = { renameTargetFile = null },
            title = { Text("Renombrar") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileName.isNotBlank() && fileName != renameDefaultName) {
                            viewModel.renameFile(uriStr, fileName)
                            renameTargetFile = null
                        }
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetFile = null }) { Text("Cancelar") }
            }
        )
    }
}

// --------------------- SEARCH TAB CONTENT ---------------------
@Composable
fun SearchTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val replaceQuery by viewModel.replaceQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val focusedIndex by viewModel.searchFocusedIndex.collectAsStateWithLifecycle()

    val itemTextColor = if (isDark) Color.White else Color.Black
    val descriptionColor = if (isDark) Color.Gray else Color(0xFF666666)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Buscar", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        )

        OutlinedTextField(
            value = replaceQuery,
            onValueChange = { viewModel.updateReplaceQuery(it) },
            label = { Text("Reemplazar con", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Search Metrics & Switch Actions
        if (searchResults.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${focusedIndex + 1} de ${searchResults.size} coincidencias",
                    fontSize = 11.sp,
                    color = descriptionColor
                )

                Row {
                    IconButton(
                        onClick = { viewModel.prevSearchResult() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("◀", fontSize = 10.sp, color = itemTextColor)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { viewModel.nextSearchResult() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("▶", fontSize = 10.sp, color = itemTextColor)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.replaceCurrent() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF3C3C3C) else Color(0xFFE0E0E0), contentColor = itemTextColor),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Reemplazar", fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.replaceAll() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF007ACC) else Color(0xFF005FB8)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Reemplazar Todo", fontSize = 11.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Escribe arriba para encontrar coincidencias de texto en el archivo actual.",
                    fontSize = 12.sp,
                    color = descriptionColor,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

// --------------------- OUTLINE TAB CONTENT ---------------------
@Composable
fun OutlineTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val symbols by viewModel.symbols.collectAsStateWithLifecycle()
    val secondaryColor = if (isDark) Color.Gray else Color(0xFF666666)
    val itemTextColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF333333)

    if (symbols.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No hay símbolos (clases o funciones) descubiertos en este archivo.",
                fontSize = 12.sp,
                color = secondaryColor,
                modifier = Modifier.padding(14.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            items(symbols) { symbol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateCursor(0) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = when (symbol.type) {
                        SymbolType.CLASS -> Color(0xFF4EC9B0)
                        SymbolType.FUNCTION -> Color(0xFFDCDCAA)
                        else -> Color.Gray
                    }
                    val badgeLetter = when (symbol.type) {
                        SymbolType.CLASS -> "C"
                        SymbolType.FUNCTION -> "f"
                        else -> "v"
                    }

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            badgeLetter,
                            fontSize = 10.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = symbol.name,
                            fontSize = 12.sp,
                            color = itemTextColor,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Línea ${symbol.line} - ${symbol.endLine}",
                            fontSize = 10.sp,
                            color = secondaryColor
                        )
                    }
                }
            }
        }
    }
}

// --------------------- PLUGINS TAB CONTENT ---------------------
@Composable
fun PluginsTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val enabledPluginIds by viewModel.enabledPluginIds.collectAsStateWithLifecycle()
    val textThemeColor = if (isDark) Color.White else Color.Black
    val descThemeColor = if (isDark) Color.Gray else Color(0xFF555555)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(PluginManager.allPlugins) { plugin ->
            val isEnabled = enabledPluginIds.contains(plugin.id)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF2A2A2B) else Color(0xFFF0F0F0)
                ),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF3E3E3F) else Color(0xFFD4D4D4)),
                                contentAlignment = Alignment.Center
                            ) {
                                val symbolText = when(plugin.iconName) {
                                    "brush" -> "🎨"
                                    "warning" -> "🔴"
                                    "brackets" -> "{}"
                                    "lightbulb" -> "💡"
                                    else -> "🔌"
                                }
                                Text(symbolText, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = plugin.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textThemeColor
                                )
                                Text(
                                    text = "v${plugin.version} por ${plugin.author}",
                                    fontSize = 10.sp,
                                    color = descThemeColor
                                )
                            }
                        }
                        
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.togglePlugin(plugin.id) },
                            modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = plugin.description,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = descThemeColor
                    )
                }
            }
        }
    }
}

// --------------------- SETTINGS TAB CONTENT ---------------------
@Composable
fun SettingsTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val isMinimapOpen by viewModel.isMinimapOpen.collectAsStateWithLifecycle()
    val isMinimapTextVisible by viewModel.isMinimapTextVisible.collectAsStateWithLifecycle()
    val fontSizeEditor by viewModel.editorFontSize.collectAsStateWithLifecycle()
    val isIndentGuidesEnabled by viewModel.isIndentGuidesEnabled.collectAsStateWithLifecycle()

    val textThemeColor = if (isDark) Color.White else Color.Black
    val descThemeColor = if (isDark) Color.Gray else Color(0xFF555555)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Tema
        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleTheme() }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tema Oscuro",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textThemeColor
                )
                Text(
                    text = "Activar el modo noche del editor.",
                    fontSize = 11.sp,
                    color = descThemeColor
                )
            }
            Switch(
                checked = isDark,
                onCheckedChange = { viewModel.toggleTheme() }
            )
        }

        Divider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))

        // Indentation Guides
        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleIndentGuides() }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Líneas de Guía de Indentación",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textThemeColor
                )
                Text(
                    text = "Mostrar líneas verticales sutiles en la indentación.",
                    fontSize = 11.sp,
                    color = descThemeColor
                )
            }
            Switch(
                checked = isIndentGuidesEnabled,
                onCheckedChange = { viewModel.toggleIndentGuides() }
            )
        }

        Divider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))

        // Font Size Zoom
        Column {
            Text(
                text = "Tamaño de Fuente (Zoom)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textThemeColor
            )
            Text(
                text = "Ajusta el tamaño del texto en el editor.",
                fontSize = 11.sp,
                color = descThemeColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A-", color = textThemeColor, fontSize = 12.sp)
                Slider(
                    value = fontSizeEditor,
                    onValueChange = { viewModel.setEditorFontSize(it) },
                    valueRange = 8f..32f,
                    steps = 24,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("A+", color = textThemeColor, fontSize = 16.sp)
            }
            Text(
                text = "${fontSizeEditor.toInt()} sp",
                fontSize = 12.sp,
                color = textThemeColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Divider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))

        // Minimap Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mostrar Minimapa",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textThemeColor
                )
                Text(
                    text = "Muestra una vista previa reducida del código a la derecha.",
                    fontSize = 11.sp,
                    color = descThemeColor
                )
            }
            Switch(
                checked = isMinimapOpen,
                onCheckedChange = { viewModel.toggleMinimap() }
            )
        }

        if (isMinimapOpen) {
            HorizontalDivider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mostrar Código en Minimapa",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textThemeColor
                    )
                    Text(
                        text = "Si se desactiva, mostrará bloques abstractos de color por cada línea, ahorrando rendimiento.",
                        fontSize = 11.sp,
                        color = descThemeColor
                    )
                }
                Switch(
                    checked = isMinimapTextVisible,
                    onCheckedChange = { viewModel.toggleMinimapTextVisibility() }
                )
            }
        }
    }
}

// --------------------- TABS HORIZONTAL SYSTEM ---------------------
@Composable
fun TabSystem(viewModel: EditorViewModel, isDark: Boolean) {
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()

    val borderColor = if (isDark) Color(0xFF2D2D2D) else Color(0xFFCCCCCC)
    val barBackground = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .background(barBackground)
            .horizontalScroll(rememberScrollState())
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (openTabs.isEmpty()) {
            Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("Carga un script para comenzar", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
            openTabs.forEach { file ->
                val isActive = file.id == activeFile?.id
                val tabBg = if (isActive) {
                    if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)
                } else {
                    if (isDark) Color(0xFF2D2D2D) else Color(0xFFE5E5E5)
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(tabBg)
                        .clickable { viewModel.selectTab(file) }
                        .drawBehind {
                            // Bottom active color bar
                            if (isActive) {
                                drawLine(
                                    color = Color(0xFF007ACC),
                                    start = Offset(0f, size.height - 2.dp.toPx()),
                                    end = Offset(size.width, size.height - 2.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            drawLine(
                                color = borderColor,
                                start = Offset(size.width, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "JS",
                        color = Color(0xFFE5A823),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    Text(
                        text = file.name,
                        fontSize = 12.sp,
                        color = if (isActive) {
                            if (isDark) Color.White else Color.Black
                        } else Color.Gray,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { viewModel.closeTab(file.id) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar Tab",
                            tint = Color.Gray,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

// --------------------- SEARCH & RELEACE WORKSPACE DRAWER ---------------------
@Composable
fun SearchAndReplacePanel(viewModel: EditorViewModel, isDark: Boolean) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val replaceQuery by viewModel.replaceQuery.collectAsStateWithLifecycle()
    val textThemeColor = if (isDark) Color.White else Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) Color(0xFF252526) else Color(0xFFEAEAEA))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Búsqueda rápida activa: ",
            fontSize = 11.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (searchQuery.isNotEmpty()) "\"$searchQuery\" ➔ \"$replaceQuery\"" else "Ninguna",
            fontSize = 11.sp,
            color = textThemeColor
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { viewModel.toggleSearchOpen() }) {
            Text("Cerrar", fontSize = 11.sp)
        }
    }
}

// --------------------- CORE CODE EDITOR CANVAS ---------------------
@Composable
fun EditorCanvas(
    viewModel: EditorViewModel,
    isDark: Boolean,
    background: Color,
    scrollState: ScrollState
) {
    val rawText by viewModel.editorText.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val foldedStartLines by viewModel.foldedStartLines.collectAsStateWithLifecycle()
    val foldableRanges by viewModel.foldableRanges.collectAsStateWithLifecycle()
    val cursorLine by viewModel.cursorLine.collectAsStateWithLifecycle()
    val cursorPosition by viewModel.cursorPosition.collectAsStateWithLifecycle()
    val editorFontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()
    val isIndentGuidesEnabled by viewModel.isIndentGuidesEnabled.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var lineCountState by remember { mutableStateOf(1) }

    val highlighter = remember(isDark, diagnostics, cursorPosition) { 
        val h = JsSyntaxHighlighter(isDark, diagnostics)
        h.cursorPosition = cursorPosition
        h
    }

    // Reconstruct lines to handle line numbers and code folding filters
    val originalLines = rawText.split("\n")
    lineCountState = originalLines.size

    val activeText = remember(rawText, foldedStartLines, foldableRanges) {
        val activeLines = ArrayList<String>()
        var skipLineUntil = -1
        
        for (i in originalLines.indices) {
            val lineNum = i + 1
            
            if (skipLineUntil != -1) {
                if (lineNum <= skipLineUntil) {
                    continue // Skip lines inside folded blocks
                } else {
                    skipLineUntil = -1
                }
            }

            activeLines.add(originalLines[i])

            if (foldedStartLines.contains(lineNum)) {
                val match = foldableRanges.find { it.startLine == lineNum }
                if (match != null) {
                    skipLineUntil = match.endLine
                    val last = activeLines.size - 1
                    activeLines[last] = activeLines[last] + " {...}"
                }
            }
        }
        activeLines.joinToString("\n")
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Line-Numbers & Folding Gutter panel
        Column(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5))
                .verticalScroll(scrollState)
                .drawBehind {
                    drawLine(
                        color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFE0E0E0),
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            
            for (i in 0 until lineCountState) {
                val line1Idx = i + 1
                val isLineFolded = foldedStartLines.contains(line1Idx)
                val isFoldable = foldableRanges.any { it.startLine == line1Idx }

                val isLineActiveAndCursorHere = cursorLine == line1Idx

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((editorFontSize * 1.5).dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFoldable) {
                        Text(
                            text = if (isLineFolded) "▶" else "▼",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            modifier = Modifier
                                .clickable { viewModel.toggleFolding(line1Idx) }
                                .padding(start = 3.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Text(
                        text = line1Idx.toString(),
                        fontSize = (editorFontSize * 0.8f).sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLineActiveAndCursorHere) {
                            if (isDark) Color.White else Color.Black
                        } else Color.Gray,
                        fontWeight = if (isLineActiveAndCursorHere) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        // Active Text Field with inline overlays
        var showContextMenu by remember { mutableStateOf(false) }
        var menuOffset by remember { mutableStateOf(Offset.Zero) }

        val transformableState = rememberTransformableState { zoomChange, panChange, rotationChange ->
            val newZoom = (editorFontSize * zoomChange).coerceIn(8f, 32f)
            if (newZoom != editorFontSize) {
                viewModel.setEditorFontSize(newZoom)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .transformable(state = transformableState)
                .verticalScroll(scrollState)
                .horizontalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            menuOffset = offset
                            showContextMenu = true
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                
                val clipboardManager = LocalClipboardManager.current
                
                var onCopyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var onPasteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var onCutAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var onSelectAllAction by remember { mutableStateOf<(() -> Unit)?>(null) }

                val originalToolbar = LocalTextToolbar.current
                
                val customToolbar = remember(originalToolbar) {
                    object : TextToolbar {
                        override val status: androidx.compose.ui.platform.TextToolbarStatus
                            get() = originalToolbar.status

                        override fun hide() {
                            originalToolbar.hide()
                            showContextMenu = false
                        }

                        override fun showMenu(
                            rect: Rect,
                            onCopyRequested: (() -> Unit)?,
                            onPasteRequested: (() -> Unit)?,
                            onCutRequested: (() -> Unit)?,
                            onSelectAllRequested: (() -> Unit)?
                        ) {
                            originalToolbar.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
                            onCopyAction = onCopyRequested
                            onPasteAction = onPasteRequested
                            onCutAction = onCutRequested
                            onSelectAllAction = onSelectAllRequested
                            menuOffset = Offset(rect.left, rect.bottom)
                            showContextMenu = true
                        }
                    }
                }



                var tvState by remember { mutableStateOf(TextFieldValue(rawText)) }
                
                LaunchedEffect(rawText) {
                    if (tvState.text != rawText) {
                        tvState = tvState.copy(text = rawText)
                    }
                }

                if (showContextMenu) {
                    androidx.compose.ui.window.Popup(
                        offset = androidx.compose.ui.unit.IntOffset(menuOffset.x.toInt(), menuOffset.y.toInt() + 150),
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp,
                            tonalElevation = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max)) {
                                val selStart = tvState.selection.min
                                val selEnd = tvState.selection.max
                                val hasSelection = selStart != selEnd
            
                                if (hasSelection) {
                                    DropdownMenuItem(
                                        text = { Text("Buscar Seleccionado") },
                                        onClick = {
                                            val selectedString = tvState.text.substring(selStart, selEnd)
                                            if (selectedString.isNotEmpty()) {
                                                viewModel.updateSearchQuery(selectedString)
                                                if (!viewModel.isSearchOpen.value) {
                                                    viewModel.toggleSearchOpen()
                                                }
                                            }
                                            showContextMenu = false
                                        }
                                    )
                                }
            
                                DropdownMenuItem(
                                    text = { Text("Duplicar Línea") },
                                    onClick = {
                                        val text = tvState.text
                                        var lineStart = text.lastIndexOf('\n', selStart - 1).coerceAtLeast(0)
                                        if (lineStart > 0 && text[lineStart] == '\n') lineStart += 1
                                        var lineEnd = text.indexOf('\n', selEnd)
                                        if (lineEnd == -1) lineEnd = text.length
                                        
                                        val linesToDuplicate = text.substring(lineStart, lineEnd)
                                        val newText = text.substring(0, lineEnd) + "\n" + linesToDuplicate + text.substring(lineEnd)
                                        
                                        viewModel.onEditorTextChanged(newText, triggerCursorUpdate = true)
                                        tvState = tvState.copy(text = newText, selection = androidx.compose.ui.text.TextRange(selEnd + linesToDuplicate.length + 1))
                                        showContextMenu = false
                                    }
                                )
                                
                                DropdownMenuItem(
                                    text = { Text("Borrar Línea") },
                                    onClick = {
                                        val text = tvState.text
                                        var lineStart = text.lastIndexOf('\n', selStart - 1)
                                        val originalLineStart = lineStart
                                        if (lineStart == -1) lineStart = 0 else lineStart += 1
                                        
                                        var lineEnd = text.indexOf('\n', selEnd)
                                        if (lineEnd == -1) lineEnd = text.length else lineEnd += 1 
                                        if (lineEnd == text.length && originalLineStart != -1) {
                                            lineStart = originalLineStart 
                                        }
                                        
                                        val newText = text.substring(0, lineStart) + text.substring(lineEnd)
                                        val newCursor = lineStart.coerceAtMost(newText.length)
                                        
                                        viewModel.onEditorTextChanged(newText, triggerCursorUpdate = true)
                                        tvState = tvState.copy(text = newText, selection = androidx.compose.ui.text.TextRange(newCursor))
                                        showContextMenu = false
                                    }
                                )
                                
                                DropdownMenuItem(
                                    text = { Text("Comentar / Descomentar") },
                                    onClick = {
                                        val text = tvState.text
                                        var lineStart = text.lastIndexOf('\n', selStart - 1).coerceAtLeast(0)
                                        if (lineStart > 0 && text[lineStart] == '\n') lineStart += 1
                                        var lineEnd = text.indexOf('\n', selEnd)
                                        if (lineEnd == -1) lineEnd = text.length
                                        
                                        val section = text.substring(lineStart, lineEnd)
                                        val lines = section.split('\n')
                                        val isAllCommented = lines.all { it.trimStart().startsWith("//") || it.isBlank() }
                                        
                                        val newLines = if (isAllCommented) {
                                            lines.map { 
                                                val trim = it.trimStart()
                                                if (trim.startsWith("//")) it.replaceFirst("//", "") else it
                                            }
                                        } else {
                                            lines.map { "// $it" }
                                        }
                                        
                                        val joined = newLines.joinToString("\n")
                                        val newText = text.substring(0, lineStart) + joined + text.substring(lineEnd)
                                        
                                        viewModel.onEditorTextChanged(newText, triggerCursorUpdate = true)
                                        tvState = tvState.copy(text = newText, selection = androidx.compose.ui.text.TextRange(selStart))
                                        showContextMenu = false
                                    }
                                )
            
                                HorizontalDivider()
            
                                DropdownMenuItem(
                                    text = { Text("Formatear con Prettier") },
                                    onClick = { 
                                        viewModel.formatCurrentCode() 
                                        showContextMenu = false
                                    }
                                )
                                if (PluginManager.isPluginEnabled("error_lens")) {
                                    DropdownMenuItem(
                                        text = { Text("Ver Errores", color = Color.Gray) },
                                        onClick = { showContextMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }

                var showAutocomplete by remember { mutableStateOf(false) }
                var autocompleteWord by remember { mutableStateOf("") }
                val activeSuggestions = remember(autocompleteWord) {
                    val staticSug = listOf(
                        "function", "const", "return", "console.log(", "fetch(", "useState(", "useEffect(", 
                        "async function ", "import ", "export ", "class ", "let ", "typeof "
                    )
                    if (autocompleteWord.isEmpty()) emptyList()
                    else staticSug.filter { it.startsWith(autocompleteWord, ignoreCase = true) }
                }

                var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

                CompositionLocalProvider(LocalTextToolbar provides customToolbar) {
                    BasicTextField(
                        value = tvState,
                        onTextLayout = { textLayoutResult = it },
                        onValueChange = { newValue ->
                        var manipulatedValue = newValue
                        val oldText = tvState.text
                        val newText = newValue.text
                        val oldSelection = tvState.selection
                        val newSelection = newValue.selection
                        var charAdded: Char? = null

                        val replaceLength = oldSelection.max - oldSelection.min
                        val insertedLength = newText.length - (oldText.length - replaceLength)
                        val insertedString = if (insertedLength > 0 && newSelection.start >= insertedLength) {
                            newText.substring(newSelection.start - insertedLength, newSelection.start)
                        } else ""

                        charAdded = if (insertedLength == 1) insertedString.firstOrNull() else null

                        // Auto-indentation
                        if (insertedString == "\n") {
                            val textBeforeNewline = newText.substring(0, newSelection.start - 1)
                            val lastNewlineIndex = textBeforeNewline.lastIndexOf('\n')
                            val previousLine = if (lastNewlineIndex != -1) {
                                textBeforeNewline.substring(lastNewlineIndex + 1)
                            } else {
                                textBeforeNewline
                            }
                            
                            val indentMatch = Regex("^\\s+").find(previousLine)
                            var indent = indentMatch?.value ?: ""
                            
                            // Extra sangría when opening curly braces
                            if (previousLine.trimEnd().endsWith("{") || previousLine.trimEnd().endsWith("(")) {
                                indent += "  " 
                            }
                            
                            if (indent.isNotEmpty()) {
                                val insertionPoint = newSelection.start
                                if (insertionPoint <= newText.length) {
                                    val safeText = newText.substring(0, insertionPoint) + indent + newText.substring(insertionPoint)
                                    manipulatedValue = newValue.copy(
                                        text = safeText,
                                        selection = androidx.compose.ui.text.TextRange(insertionPoint + indent.length)
                                    )
                                }
                            }
                        }
                        
                        tvState = manipulatedValue
                        viewModel.updateCursor(manipulatedValue.selection.start)
                        
                        val cursor = manipulatedValue.selection.start
                        if (cursor > 0) {
                            val textBefore = manipulatedValue.text.substring(0, cursor)
                            val lastWord = textBefore.split(Regex("[\\s\\n(){}\\[\\].;]+")).lastOrNull() ?: ""
                            if (lastWord.length >= 2) {
                                autocompleteWord = lastWord
                                showAutocomplete = true
                            } else {
                                showAutocomplete = false
                            }
                        } else {
                            showAutocomplete = false
                        }

                        viewModel.onEditorTextChanged(manipulatedValue.text, addedChar = charAdded)
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = editorFontSize.sp,
                        lineHeight = (editorFontSize * 1.5).sp,
                        color = if (isDark) Color(0xFFD4D4D4) else Color(0xFF333333)
                    ),
                    visualTransformation = highlighter,
                    cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .drawBehind {
                            textLayoutResult?.let { layout ->
                                if (isIndentGuidesEnabled) {
                                    val guideColors = listOf(
                                        highlighter.colors.keywordDecl.copy(alpha = 0.5f),    
                                        highlighter.colors.functionName.copy(alpha = 0.5f),   
                                        highlighter.colors.keywordControl.copy(alpha = 0.5f), 
                                        highlighter.colors.className.copy(alpha = 0.5f),      
                                        highlighter.colors.string.copy(alpha = 0.5f),         
                                        highlighter.colors.number.copy(alpha = 0.5f)          
                                    )
                                    val lines = tvState.text.split("\n")
                                    val maxLines = minOf(layout.lineCount, lines.size)
                                    for (i in 0 until maxLines) {
                                        val lineText = lines[i]
                                        val leadingSpaces = lineText.takeWhile { it == ' ' }.length
                                        for (spaceLevel in 2..leadingSpaces step 2) {
                                            val xPos = layout.getHorizontalPosition(layout.getLineStart(i) + spaceLevel, true)
                                            val yStart = layout.getLineTop(i)
                                            val yEnd = layout.getLineBottom(i)
                                            val colorIndex = ((spaceLevel / 2) - 1).coerceAtLeast(0) % guideColors.size
                                            drawLine(
                                                color = guideColors[colorIndex],
                                                start = Offset(xPos, yStart),
                                                end = Offset(xPos, yEnd),
                                                strokeWidth = 2f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                )
                }

                if (PluginManager.isPluginEnabled("error_lens") && diagnostics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Errores y Advertencias:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.LightGray else Color.DarkGray
                    )
                    diagnostics.forEach { diag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateCursor(0) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dotColor = if (diag.level == DiagnosticLevel.ERROR) Color.Red else Color(0xFFFFB300)
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Línea ${diag.line}: ${diag.message}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (diag.level == DiagnosticLevel.ERROR) Color(0xFFF44336) else Color(0xFFB07B00)
                            )

                            diag.fixAction?.let { fix ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.applyQuickFix(fix) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) Color(0xFF3E3E3E) else Color(0xFFDCDCDC),
                                        contentColor = if (isDark) Color.White else Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Text("💡 Corrección rápida", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                if (showAutocomplete && activeSuggestions.isNotEmpty()) {
                    Popup(onDismissRequest = { showAutocomplete = false }) {
                        Card(
                            modifier = Modifier
                                .width(200.dp)
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF252526) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Text(
                                    "Autocompletado JS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 6.dp)
                                )
                                activeSuggestions.take(5).forEach { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val original = tvState.text
                                                val selStart = tvState.selection.start
                                                val prefixLen = autocompleteWord.length
                                                val leftText = original.substring(0, selStart - prefixLen)
                                                val rightText = original.substring(selStart)
                                                val updatedVal = leftText + suggestion + rightText
                                                
                                                viewModel.onEditorTextChanged(updatedVal)
                                                showAutocomplete = false
                                            }
                                            .padding(vertical = 4.dp, horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isDark) Color.White else Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------- MINIMAP SCALED CODE DRAWER ---------------------
@Composable
fun MinimapPane(viewModel: EditorViewModel, isDark: Boolean, scrollState: ScrollState) {
    val rawText by viewModel.editorText.collectAsStateWithLifecycle()
    val isMinimapTextVisible by viewModel.isMinimapTextVisible.collectAsStateWithLifecycle()
    val lines = rawText.split("\n")
    val scope = rememberCoroutineScope()

    // We render up to 1000 lines in the minimap to avoid extreme performance issues.
    val linesToRender = lines.take(1000)
    
    // Scale line height based on text visible or not
    val lineHeightDp = if (isMinimapTextVisible) 3.dp else 2.dp

    Column(
        modifier = Modifier
            .width(60.dp)
            .fillMaxHeight()
            .background(if (isDark) Color(0xCC1A1A1A) else Color(0xCCF3F3F3)) // Semi-transparent overlay, more solid but still transparent
            .drawBehind {
                drawLine(
                    color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFD4D4D4),
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .pointerInput(isMinimapTextVisible) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            val change = event.changes.firstOrNull { it.pressed }
                            if (change != null) {
                                val yDp = change.position.y / density
                                val clickedLineIndex = (yDp / lineHeightDp.value).toInt()
                                val maxScroll = scrollState.maxValue
                                val scrollY = (clickedLineIndex.toFloat() / linesToRender.size.coerceAtLeast(1).toFloat()) * maxScroll
                                scope.launch {
                                    scrollState.scrollTo(scrollY.toInt())
                                }
                            }
                        }
                    }
                }
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        val maxScroll = kotlin.math.max(1, scrollState.maxValue)
        val currentScroll = scrollState.value
        val scrollRatio = currentScroll.toFloat() / maxScroll.toFloat()

        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                linesToRender.forEach { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.isNotEmpty()) {
                        val lineColor = when {
                            trimmedLine.startsWith("//") || trimmedLine.startsWith("/*") -> Color(0xFF6A9955)
                            trimmedLine.startsWith("import ") || trimmedLine.startsWith("export ") -> Color(0xFFC586C0)
                            trimmedLine.startsWith("function ") || trimmedLine.startsWith("class ") -> Color(0xFF569CD6)
                            else -> if (isDark) Color(0xFF888888) else Color(0xFF333333)
                        }

                        if (isMinimapTextVisible) {
                            Text(
                                text = line,
                                fontSize = 2.sp,
                                lineHeight = 3.sp,
                                fontFamily = FontFamily.Monospace,
                                color = lineColor,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(3.dp)
                                    .fillMaxWidth()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                    .height(1.dp)
                                    .fillMaxWidth(kotlin.math.min(1f, trimmedLine.length / 50f))
                                    .background(lineColor)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(lineHeightDp))
                    }
                }
            }

            // Overlay indicator for visible scrolled area
            val windowHeight = 40.dp // Estimate viewport proportion
            val offsetDp = (linesToRender.size * lineHeightDp.value * scrollRatio).dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = offsetDp)
                    .height(windowHeight)
                    .background(if (isDark) Color(0x33FFFFFF) else Color(0x33000000))
            )
        }
    }
}

// --------------------- EMPTY EDITOR STATE PLACEHOLDER ---------------------
@Composable
fun EmptyEditorState(isDark: Boolean, textColor: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF2D2D2D) else Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Text("💻", fontSize = 28.sp)
            }
            
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "Visor de Código JavaScript",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Selecciona un archivo del panel izquierdo para comenzar a consultar o editar tu script de JavaScript, formatear con Prettier, realizar búsquedas y corregir errores.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// --------------------- BOTTOM BAR DECORATOR (VS STATUS BAR) ---------------------
@Composable
fun VscodeStatusBar(viewModel: EditorViewModel, background: Color) {
    val cursorLine by viewModel.cursorLine.collectAsStateWithLifecycle()
    val cursorCol by viewModel.cursorColumn.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    val errorsCount = diagnostics.count { it.level == DiagnosticLevel.ERROR }
    val warnsCount = diagnostics.count { it.level == DiagnosticLevel.WARNING }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(background)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "⚡ JavaScript Editor",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔴 $errorsCount", fontSize = 10.sp, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🟡 $warnsCount", fontSize = 10.sp, color = Color.White)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ln $cursorLine, Col $cursorCol",
                fontSize = 10.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Espacios: 2",
                fontSize = 10.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "UTF-8",
                fontSize = 10.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "JS",
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
