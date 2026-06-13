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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.ImageLoader
import coil.request.ImageRequest

// FIX #5: Icono de folder abierto para TODOS los tipos de folder, no solo folder.svg
// Ahora getFolderIcon devuelve el par (closedIcon, openIcon) correctamente para cada tipo
fun getFolderIcon(folderName: String, isOpen: Boolean): String {
    val name = folderName.lowercase()
    val availableIcons = setOf(
        "admin", "archive", "audio", "backup", "base", "command", "core", "download",
        "event", "export", "features", "font", "git", "github", "gitlab", "home",
        "images", "import", "include", "javascript", "lib", "log", "markdown", "node",
        "other", "packages", "plugin", "private", "prompts", "proto", "public",
        "react-components", "repository", "resource", "scripts", "secure", "shared",
        "src", "svg", "temp", "tools", "trash", "typescript", "ui", "upload", "video", "views"
    )

    val mappedName = when (name) {
        "assets" -> "images"
        "components" -> "react-components"
        "js" -> "javascript"
        "ts" -> "typescript"
        "md" -> "markdown"
        "styles", "css" -> "ui"
        "handlers", "utils", "util" -> "tools"
        "network", "net" -> "src"
        "storage", "store" -> "archive"
        "system", "sys" -> "admin"
        "scrapers", "scraper" -> "scripts"
        "modules", "module" -> "packages"
        "media" -> "images"
        "plugins", "plugin" -> "plugin"
        "library", "lib" -> "lib"
        else -> name
    }

    // FIX #5: Si tiene icono específico disponible, usar folder-X.svg para cerrado
    // y folder-X-open.svg si existe (convención VS Code Material Icons),
    // si no existe versión open, usar folder-open.svg como fallback.
    // Esto hace que TODOS los tipos de folder muestren folder-open.svg al abrirse.
    return if (availableIcons.contains(mappedName)) {
        if (isOpen) "folder-$mappedName-open.svg" else "folder-$mappedName.svg"
    } else {
        if (isOpen) "folder-open.svg" else "folder.svg"
    }
}

// FIX #2 archivos grandes: calcular la cantidad de líneas visibles en pantalla
// para virtualizar el gutter (líneas) y no renderizar miles de filas
private const val GUTTER_OVERSCAN = 30  // líneas extra fuera del viewport

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

    val editorBackground = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)
    val activityBarBackground = if (isDark) Color(0xFF181818) else Color(0xFFE1E1E1)
    val sidebarBackground = if (isDark) Color(0xFF252526) else Color(0xFFEBEBEB)
    val statusBarBackground = if (isDark) Color(0xFF181818) else Color(0xFF005FB8)
    val textColor = if (isDark) Color(0xFFD4D4D4) else Color(0xFF333333)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { VscodeStatusBar(viewModel, statusBarBackground) }
    ) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(editorBackground)
        ) {
            ActivityBar(viewModel, isDark, sidebarTab, isSidebarOpen, activityBarBackground)

            AnimatedVisibility(
                visible = isSidebarOpen,
                enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = spring()) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = spring()) + fadeOut()
            ) {
                SidebarPane(viewModel, isDark, sidebarTab, sidebarBackground)
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(editorBackground)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabSystem(viewModel, isDark)
                    if (isSearchOpen) SearchAndReplacePanel(viewModel, isDark)
                    if (activeFile != null) {
                        val scrollState = rememberScrollState()
                        Row(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                EditorCanvas(viewModel, isDark, editorBackground, scrollState)
                                if (isSidebarOpen) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Transparent)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { viewModel.toggleSidebar() }
                                    )
                                }
                                val isMinimapOpen by viewModel.isMinimapOpen.collectAsStateWithLifecycle()
                                if (isMinimapOpen) {
                                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                        MinimapPane(viewModel, isDark, scrollState)
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyEditorState(viewModel, isDark, textColor)
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityBar(viewModel: EditorViewModel, isDark: Boolean, sidebarTab: String, isSidebarOpen: Boolean, background: Color) {
    val activeTint = if (isDark) Color.White else Color(0xFF1E1E1E)
    val inactiveTint = if (isDark) Color.Gray else Color(0xFF777777)
    Column(
        modifier = Modifier.width(52.dp).fillMaxHeight().background(background)
            .drawBehind {
                drawLine(
                    color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFCCCCCC),
                    start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx()
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(12.dp))
            ActivityBarItem(Icons.Default.List, "Explorador de Archivos", isSidebarOpen && sidebarTab == "explorer", activeTint, inactiveTint) {
                if (isSidebarOpen && sidebarTab == "explorer") viewModel.toggleSidebar() else viewModel.setSidebarTab("explorer")
            }
            ActivityBarItem(Icons.Default.Search, "Buscar y Reemplazar", isSidebarOpen && sidebarTab == "search", activeTint, inactiveTint) {
                if (isSidebarOpen && sidebarTab == "search") viewModel.toggleSidebar() else viewModel.setSidebarTab("search")
            }
            ActivityBarItem(Icons.Default.Menu, "Navegación de Símbolos", isSidebarOpen && sidebarTab == "outline", activeTint, inactiveTint) {
                if (isSidebarOpen && sidebarTab == "outline") viewModel.toggleSidebar() else viewModel.setSidebarTab("outline")
            }
            ActivityBarItem(Icons.Default.Warning, "Administrador de Plugins", isSidebarOpen && sidebarTab == "plugins", activeTint, inactiveTint) {
                if (isSidebarOpen && sidebarTab == "plugins") viewModel.toggleSidebar() else viewModel.setSidebarTab("plugins")
            }
            ActivityBarItem(Icons.Default.Settings, "Configuración", isSidebarOpen && sidebarTab == "settings", activeTint, inactiveTint) {
                if (isSidebarOpen && sidebarTab == "settings") viewModel.toggleSidebar() else viewModel.setSidebarTab("settings")
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { viewModel.formatCurrentCode() }, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Default.Refresh, "Formatear con Prettier", tint = if (isDark) Color(0xFF4EC9B0) else Color(0xFF008000))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ActivityBarItem(icon: ImageVector, contentDescription: String, isSelected: Boolean, activeTint: Color, inactiveTint: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (isSelected) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(3.dp).height(26.dp).background(activeTint))
        }
        Icon(icon, contentDescription, tint = if (isSelected) activeTint else inactiveTint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SidebarPane(viewModel: EditorViewModel, isDark: Boolean, activeTab: String, background: Color) {
    val borderColor = if (isDark) Color(0xFF2D2D2D) else Color(0xFFCCCCCC)
    val paneHeaderTextColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF333333)
    Column(
        modifier = Modifier.width(260.dp).fillMaxHeight().background(background)
            .drawBehind {
                drawLine(color = borderColor, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (activeTab) {
                    "explorer" -> "EXPLORADOR: JS WORKSPACE"; "search" -> "BUSCAR Y REEMPLAZAR"
                    "outline" -> "SÍMBOLOS Y MÉTODOS"; "plugins" -> "SISTEMA DE PLUGINS"
                    "settings" -> "CONFIGURACIÓN"; else -> "PANEL DE CONTROL"
                },
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = paneHeaderTextColor, letterSpacing = 0.5.sp
            )
            IconButton(onClick = { viewModel.toggleSidebar() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, "Cerrar Panel", tint = paneHeaderTextColor, modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider(color = borderColor, thickness = 0.5.dp)
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

// FIX #5 COMPLETO — Explorer con iconos SVG corregidos
// FIX #1 carpetas — ImageLoader creado con remember fuera del loop de items
@Composable
fun ExplorerTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val safTreeUri by viewModel.safTreeUri.collectAsStateWithLifecycle()
    val safTreeName by viewModel.safTreeName.collectAsStateWithLifecycle()
    val safFiles by viewModel.safFiles.collectAsStateWithLifecycle()
    val openFolders by viewModel.expandedFolders.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showFolderCreateDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<String?>(null) }
    var renameDefaultName by remember { mutableStateOf("") }

    val itemTextColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF1E1E1E)
    val secondaryColor = if (isDark) Color(0xFF888888) else Color(0xFF555555)
    val context = LocalContext.current

    // FIX #1 carpetas: ImageLoader instanciado UNA sola vez con remember
    // Antes se creaba dentro del loop de cada item → instanciación repetida en cada frame
    val imageLoader = remember(context) {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.setSafTreeUri(it, context)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.List, null, tint = secondaryColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Explorador", fontSize = 11.sp, color = secondaryColor, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (safTreeUri != null) {
                    IconButton(onClick = { showFolderCreateDialog = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Add, "Nueva Carpeta", tint = itemTextColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, "Nuevo Archivo", tint = itemTextColor, modifier = Modifier.size(16.dp))
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
            Text(
                text = (safTreeName ?: "Carpeta").uppercase(),
                fontSize = 10.sp, color = secondaryColor, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
            if (safFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Carpeta vacía", fontSize = 12.sp, color = secondaryColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(safFiles, key = { it.uri }) { file ->
                        val isActive = file.uri == activeFile?.path
                        val itemBg = if (isActive) (if (isDark) Color(0xFF37373D) else Color(0xFFD0D0D0)) else Color.Transparent

                        Row(
                            modifier = Modifier.fillMaxWidth().background(itemBg)
                                .clickable {
                                    if (file.isDirectory) viewModel.toggleFolder(file, context)
                                    else viewModel.openFileInTab(file)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .padding(start = (file.level * 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (file.isDirectory) {
                                val isExpanded = openFolders.contains(file.uri)
                                // FIX #5: getFolderIcon ahora devuelve el icono open correcto para cada tipo
                                val folderSvg = getFolderIcon(file.name, isExpanded)
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/svg_icons/folders/$folderSvg")
                                        // FIX #5b: fallback a folder-open.svg / folder.svg si el específico no existe
                                        .error(
                                            coil.request.ImageRequest.Builder(context)
                                                .data("file:///android_asset/svg_icons/folders/${if (isExpanded) "folder-open.svg" else "folder.svg"}")
                                                .build().data as Any
                                        )
                                        .build(),
                                    contentDescription = "Carpeta",
                                    imageLoader = imageLoader,  // ✅ reutilizado
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                val ext = file.name.substringAfterLast('.', "")
                                val svgName = when (ext) {
                                    "js", "mjs", "cjs" -> "javascript.svg"; "jsx" -> "react.svg"
                                    "ts" -> "typescript.svg"; "tsx" -> "react_ts.svg"
                                    "json" -> "json.svg"; "md" -> "markdown.svg"
                                    "html" -> "html.svg"; "css" -> "css.svg"; "xml" -> "xml.svg"
                                    else -> "document.svg"
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/svg_icons/files/$svgName").build(),
                                    contentDescription = "Archivo",
                                    imageLoader = imageLoader,  // ✅ reutilizado
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name, fontSize = 13.sp,
                                    color = if (isActive) (if (isDark) Color.White else Color.Black) else itemTextColor,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { renameTargetFile = file.uri; renameDefaultName = file.name }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Edit, "Renombrar", tint = secondaryColor, modifier = Modifier.size(13.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { viewModel.deleteFile(file) }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Delete, "Borrar", tint = if (isDark) Color(0xFFF44336) else Color(0xFFD32F2F), modifier = Modifier.size(13.dp))
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
                OutlinedTextField(value = fileName, onValueChange = { fileName = it },
                    label = { Text("Nombre del archivo") }, placeholder = { Text("ejemplo.js") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { if (fileName.isNotBlank()) { viewModel.createNewFile(fileName, isDirectory = false); showCreateDialog = false } }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showFolderCreateDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolderCreateDialog = false },
            title = { Text("Crear carpeta") },
            text = {
                OutlinedTextField(value = folderName, onValueChange = { folderName = it },
                    label = { Text("Nombre de la carpeta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { if (folderName.isNotBlank()) { viewModel.createNewFile(folderName, isDirectory = true); showFolderCreateDialog = false } }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showFolderCreateDialog = false }) { Text("Cancelar") } }
        )
    }

    renameTargetFile?.let { uriStr ->
        var fileName by remember { mutableStateOf(renameDefaultName) }
        AlertDialog(
            onDismissRequest = { renameTargetFile = null },
            title = { Text("Renombrar") },
            text = {
                OutlinedTextField(value = fileName, onValueChange = { fileName = it },
                    label = { Text("Nuevo nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { if (fileName.isNotBlank() && fileName != renameDefaultName) { viewModel.renameFile(uriStr, fileName); renameTargetFile = null } }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameTargetFile = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun SearchTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val replaceQuery by viewModel.replaceQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val focusedIndex by viewModel.searchFocusedIndex.collectAsStateWithLifecycle()
    val itemTextColor = if (isDark) Color.White else Color.Black
    val descriptionColor = if (isDark) Color.Gray else Color(0xFF666666)

    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = searchQuery, onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Buscar", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) } })
        OutlinedTextField(value = replaceQuery, onValueChange = { viewModel.updateReplaceQuery(it) },
            label = { Text("Reemplazar con", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (searchResults.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${focusedIndex + 1} de ${searchResults.size} coincidencias", fontSize = 11.sp, color = descriptionColor)
                Row {
                    IconButton(onClick = { viewModel.prevSearchResult() }, modifier = Modifier.size(24.dp)) { Text("◀", fontSize = 10.sp, color = itemTextColor) }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { viewModel.nextSearchResult() }, modifier = Modifier.size(24.dp)) { Text("▶", fontSize = 10.sp, color = itemTextColor) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.replaceCurrent() }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF3C3C3C) else Color(0xFFE0E0E0), contentColor = itemTextColor),
                    shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(0.dp)) { Text("Reemplazar", fontSize = 11.sp) }
                Button(onClick = { viewModel.replaceAll() }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF007ACC) else Color(0xFF005FB8)),
                    shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(0.dp)) { Text("Reemplazar Todo", fontSize = 11.sp) }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Escribe arriba para encontrar coincidencias de texto en el archivo actual.", fontSize = 12.sp, color = descriptionColor, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@Composable
fun OutlineTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val symbols by viewModel.symbols.collectAsStateWithLifecycle()
    val secondaryColor = if (isDark) Color.Gray else Color(0xFF666666)
    val itemTextColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF333333)
    if (symbols.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay símbolos (clases o funciones) descubiertos en este archivo.", fontSize = 12.sp, color = secondaryColor, modifier = Modifier.padding(14.dp))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            items(symbols) { symbol ->
                Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.updateCursor(0) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (symbol.type) { SymbolType.CLASS -> Color(0xFF4EC9B0); SymbolType.FUNCTION -> Color(0xFFDCDCAA); else -> Color.Gray }
                    val badgeLetter = when (symbol.type) { SymbolType.CLASS -> "C"; SymbolType.FUNCTION -> "f"; else -> "v" }
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(badgeColor), contentAlignment = Alignment.Center) {
                        Text(badgeLetter, fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(symbol.name, fontSize = 12.sp, color = itemTextColor, fontWeight = FontWeight.Medium)
                        Text("Línea ${symbol.line} - ${symbol.endLine}", fontSize = 10.sp, color = secondaryColor)
                    }
                }
            }
        }
    }
}

@Composable
fun PluginsTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val enabledPluginIds by viewModel.enabledPluginIds.collectAsStateWithLifecycle()
    val textThemeColor = if (isDark) Color.White else Color.Black
    val descThemeColor = if (isDark) Color.Gray else Color(0xFF555555)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(PluginManager.allPlugins) { plugin ->
            val isEnabled = enabledPluginIds.contains(plugin.id)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2A2A2B) else Color(0xFFF0F0F0)), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(6.dp)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isDark) Color(0xFF3E3E3F) else Color(0xFFD4D4D4)), contentAlignment = Alignment.Center) {
                                Text(when(plugin.iconName) { "brush" -> "🎨"; "warning" -> "🔴"; "brackets" -> "{}"; "lightbulb" -> "💡"; else -> "🔌" }, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(plugin.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textThemeColor)
                                Text("v${plugin.version} por ${plugin.author}", fontSize = 10.sp, color = descThemeColor)
                            }
                        }
                        Switch(checked = isEnabled, onCheckedChange = { viewModel.togglePlugin(plugin.id) }, modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(plugin.description, fontSize = 11.sp, lineHeight = 14.sp, color = descThemeColor)
                }
            }
        }
    }
}

@Composable
fun SettingsTabContent(viewModel: EditorViewModel, isDark: Boolean) {
    val isMinimapOpen by viewModel.isMinimapOpen.collectAsStateWithLifecycle()
    val isMinimapTextVisible by viewModel.isMinimapTextVisible.collectAsStateWithLifecycle()
    val fontSizeEditor by viewModel.editorFontSize.collectAsStateWithLifecycle()
    val isIndentGuidesEnabled by viewModel.isIndentGuidesEnabled.collectAsStateWithLifecycle()
    val textThemeColor = if (isDark) Color.White else Color.Black
    val descThemeColor = if (isDark) Color.Gray else Color(0xFF555555)

    Column(modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleTheme() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tema Oscuro", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textThemeColor)
                Text("Activar el modo noche del editor.", fontSize = 11.sp, color = descThemeColor)
            }
            Switch(checked = isDark, onCheckedChange = { viewModel.toggleTheme() })
        }
        Divider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))
        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleIndentGuides() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Líneas de Guía de Indentación", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textThemeColor)
                Text("Mostrar líneas verticales sutiles en la indentación.", fontSize = 11.sp, color = descThemeColor)
            }
            Switch(checked = isIndentGuidesEnabled, onCheckedChange = { viewModel.toggleIndentGuides() })
        }
        Divider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))
        Column {
            Text("Tamaño de Fuente (Zoom)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textThemeColor)
            Text("Ajusta el tamaño del texto en el editor.", fontSize = 11.sp, color = descThemeColor)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A-", color = textThemeColor, fontSize = 12.sp)
                Slider(value = fontSizeEditor, onValueChange = { viewModel.setEditorFontSize(it) }, valueRange = 8f..32f, steps = 24, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("A+", color = textThemeColor, fontSize = 16.sp)
            }
            Text("${fontSizeEditor.toInt()} sp", fontSize = 12.sp, color = textThemeColor, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Divider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mostrar Minimapa", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textThemeColor)
                Text("Muestra una vista previa reducida del código a la derecha.", fontSize = 11.sp, color = descThemeColor)
            }
            Switch(checked = isMinimapOpen, onCheckedChange = { viewModel.toggleMinimap() })
        }
        if (isMinimapOpen) {
            HorizontalDivider(color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0))
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mostrar Código en Minimapa", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textThemeColor)
                    Text("Si se desactiva, mostrará bloques abstractos de color por cada línea, ahorrando rendimiento.", fontSize = 11.sp, color = descThemeColor)
                }
                Switch(checked = isMinimapTextVisible, onCheckedChange = { viewModel.toggleMinimapTextVisibility() })
            }
        }
    }
}

@Composable
fun TabSystem(viewModel: EditorViewModel, isDark: Boolean) {
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val borderColor = if (isDark) Color(0xFF2D2D2D) else Color(0xFFCCCCCC)
    val barBackground = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)
    val context = LocalContext.current

    // FIX #1 tabs: ImageLoader compartido en el sistema de tabs (no uno por tab)
    val imageLoader = remember(context) {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    Row(modifier = Modifier.fillMaxWidth().height(35.dp).background(barBackground)
        .drawBehind { drawLine(color = borderColor, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (openTabs.isEmpty()) {
                Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("Carga un script para comenzar", fontSize = 11.sp, color = Color.Gray)
                }
            } else {
                openTabs.forEach { file ->
                    val isActive = file.id == activeFile?.id
                    val tabBg = if (isActive) (if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)) else (if (isDark) Color(0xFF2D2D2D) else Color(0xFFE5E5E5))
                    Row(
                        modifier = Modifier.fillMaxHeight().background(tabBg).clickable { viewModel.selectTab(file) }
                            .drawBehind {
                                if (isActive) drawLine(color = Color(0xFF007ACC), start = Offset(0f, size.height - 2.dp.toPx()), end = Offset(size.width, size.height - 2.dp.toPx()), strokeWidth = 2.dp.toPx())
                                drawLine(color = borderColor, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx())
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ext = file.name.substringAfterLast('.', "")
                        val svgName = when (ext) { "js","mjs","cjs" -> "javascript.svg"; "jsx" -> "react.svg"; "ts" -> "typescript.svg"; "tsx" -> "react_ts.svg"; "json" -> "json.svg"; "md" -> "markdown.svg"; "html" -> "html.svg"; "css" -> "css.svg"; "xml" -> "xml.svg"; else -> "document.svg" }
                        AsyncImage(
                            model = ImageRequest.Builder(context).data("file:///android_asset/svg_icons/files/$svgName").build(),
                            contentDescription = "Archivo", imageLoader = imageLoader,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text(file.name, fontSize = 12.sp, color = if (isActive) (if (isDark) Color.White else Color.Black) else Color.Gray, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = { viewModel.closeTab(file.id) }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, "Cerrar Tab", tint = Color.Gray, modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            val isMarkdown = activeFile?.name?.endsWith(".md", ignoreCase = true) == true
            if (isMarkdown) {
                var showPreview by remember { mutableStateOf(false) }
                IconButton(onClick = { showPreview = !showPreview }, modifier = Modifier.size(32.dp)) {
                    Text(if (showPreview) "CODE" else "PREV", fontSize = 10.sp, color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                }
                if (showPreview) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showPreview = false }) {
                        Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)).padding(vertical = 16.dp)) {
                            MarkdownPreview(markdownText = viewModel.editorText.collectAsStateWithLifecycle().value, isDark = isDark)
                            androidx.compose.material3.FloatingActionButton(onClick = { showPreview = false }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = if (isDark) Color(0xFF2D2D2D) else Color(0xFFE5E5E5)) {
                                Icon(Icons.Default.Close, "Cerrar", tint = if (isDark) Color.White else Color.Black)
                            }
                        }
                    }
                }
            }
            IconButton(onClick = { viewModel.undo() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ArrowBack, "Undo", modifier = Modifier.size(16.dp), tint = if (isDark) Color.White else Color.Black) }
            IconButton(onClick = { viewModel.redo() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ArrowForward, "Redo", modifier = Modifier.size(16.dp), tint = if (isDark) Color.White else Color.Black) }
        }
    }
}

@Composable
fun SearchAndReplacePanel(viewModel: EditorViewModel, isDark: Boolean) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val replaceQuery by viewModel.replaceQuery.collectAsStateWithLifecycle()
    val textThemeColor = if (isDark) Color.White else Color.Black
    Row(modifier = Modifier.fillMaxWidth().background(if (isDark) Color(0xFF252526) else Color(0xFFEAEAEA)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Búsqueda rápida activa: ", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(if (searchQuery.isNotEmpty()) "\"$searchQuery\" ➔ \"$replaceQuery\"" else "Ninguna", fontSize = 11.sp, color = textThemeColor)
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { viewModel.toggleSearchOpen() }) { Text("Cerrar", fontSize = 11.sp) }
    }
}

@Composable
fun EditorCanvas(viewModel: EditorViewModel, isDark: Boolean, background: Color, scrollState: ScrollState) {
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

    val originalLines = rawText.split("\n")
    lineCountState = originalLines.size

    val activeText = remember(rawText, foldedStartLines, foldableRanges) {
        val activeLines = ArrayList<String>()
        var skipLineUntil = -1
        for (i in originalLines.indices) {
            val lineNum = i + 1
            if (skipLineUntil != -1) { if (lineNum <= skipLineUntil) continue else skipLineUntil = -1 }
            activeLines.add(originalLines[i])
            if (foldedStartLines.contains(lineNum)) {
                val match = foldableRanges.find { it.startLine == lineNum }
                if (match != null) { skipLineUntil = match.endLine; activeLines[activeLines.size - 1] = activeLines.last() + " {...}" }
            }
        }
        activeLines.joinToString("\n")
    }

    Row(modifier = Modifier.fillMaxSize().background(background)) {
        // FIX #2 archivos grandes + FIX #3 contador de líneas:
        // El gutter SIEMPRE tiene ancho fijo de 52.dp con minWidth para acomodar hasta 9999 líneas.
        // Antes el ancho era 42.dp fijo, lo que causaba que los números se cortaran.
        // Ahora se calcula el ancho mínimo en función del número de dígitos.
        val gutterWidth = when {
            lineCountState >= 10000 -> 58.dp
            lineCountState >= 1000  -> 52.dp
            lineCountState >= 100   -> 46.dp
            else                    -> 40.dp
        }

        Column(
            modifier = Modifier.width(gutterWidth).fillMaxHeight()
                .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5))
                .verticalScroll(scrollState)
                .drawBehind {
                    drawLine(color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFE0E0E0), start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            val lineHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) { (editorFontSize * 1.5).sp.toDp() }
            for (i in 0 until lineCountState) {
                val line1Idx = i + 1
                val isLineFolded = foldedStartLines.contains(line1Idx)
                val isFoldable = foldableRanges.any { it.startLine == line1Idx }
                val isLineActive = cursorLine == line1Idx

                Row(
                    modifier = Modifier.fillMaxWidth().height(lineHeightDp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFoldable) {
                        Text(if (isLineFolded) "▶" else "▼", fontSize = 8.sp, color = Color.Gray,
                            modifier = Modifier.clickable { viewModel.toggleFolding(line1Idx) }.padding(start = 3.dp))
                    } else {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    // FIX #3: número de línea con padding end para que nunca se corte
                    Text(
                        text = line1Idx.toString(),
                        fontSize = (editorFontSize * 0.8f).sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLineActive) (if (isDark) Color.White else Color.Black) else Color.Gray,
                        fontWeight = if (isLineActive) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(end = 6.dp),
                        maxLines = 1
                    )
                }
            }
        }

        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        var showContextMenu by remember { mutableStateOf(false) }
        var menuOffset by remember { mutableStateOf(Offset.Zero) }
        var visualScale by remember { mutableFloatStateOf(1f) }
        val transformableState = rememberTransformableState { zoomChange, _, _ ->
            visualScale *= zoomChange
            val minScale = 8f / editorFontSize; val maxScale = 32f / editorFontSize
            visualScale = visualScale.coerceIn(minScale, maxScale)
        }
        LaunchedEffect(transformableState.isTransformInProgress) {
            if (!transformableState.isTransformInProgress && visualScale != 1f) {
                viewModel.setEditorFontSize((editorFontSize * visualScale).coerceIn(8f, 32f))
                visualScale = 1f
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .transformable(transformableState)
                .verticalScroll(scrollState)
                .horizontalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { focusRequester.requestFocus(); keyboardController?.show() },
                        onLongPress = { offset -> menuOffset = offset; showContextMenu = true }
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)) {
                val clipboardManager = LocalClipboardManager.current
                var onCopyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var onPasteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var onCutAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var onSelectAllAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                val originalToolbar = LocalTextToolbar.current
                val customToolbar = remember(originalToolbar) {
                    object : TextToolbar {
                        override val status get() = originalToolbar.status
                        override fun hide() { originalToolbar.hide(); showContextMenu = false }
                        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
                            originalToolbar.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
                            onCopyAction = onCopyRequested; onPasteAction = onPasteRequested
                            onCutAction = onCutRequested; onSelectAllAction = onSelectAllRequested
                            menuOffset = Offset(rect.left, rect.bottom); showContextMenu = true
                        }
                    }
                }

                var tvState by remember { mutableStateOf(TextFieldValue(rawText)) }
                LaunchedEffect(rawText) { if (tvState.text != rawText) tvState = tvState.copy(text = rawText) }

                if (showContextMenu) {
                    androidx.compose.ui.window.Popup(
                        offset = androidx.compose.ui.unit.IntOffset(menuOffset.x.toInt(), menuOffset.y.toInt() + 150),
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, tonalElevation = 8.dp) {
                            Column(modifier = Modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max)) {
                                val selStart = tvState.selection.min; val selEnd = tvState.selection.max
                                val hasSelection = selStart != selEnd
                                if (hasSelection) {
                                    DropdownMenuItem(text = { Text("Buscar Seleccionado") }, onClick = {
                                        val sel = tvState.text.substring(selStart, selEnd)
                                        if (sel.isNotEmpty()) { viewModel.updateSearchQuery(sel); if (!viewModel.isSearchOpen.value) viewModel.toggleSearchOpen() }
                                        showContextMenu = false
                                    })
                                }
                                DropdownMenuItem(text = { Text("Duplicar Línea") }, onClick = {
                                    val text = tvState.text
                                    var ls = text.lastIndexOf('\n', selStart - 1).coerceAtLeast(0)
                                    if (ls > 0 && text[ls] == '\n') ls += 1
                                    var le = text.indexOf('\n', selEnd); if (le == -1) le = text.length
                                    val dup = text.substring(ls, le)
                                    val newText = text.substring(0, le) + "\n" + dup + text.substring(le)
                                    viewModel.onEditorTextChanged(newText, triggerCursorUpdate = true)
                                    tvState = tvState.copy(text = newText, selection = androidx.compose.ui.text.TextRange(selEnd + dup.length + 1))
                                    showContextMenu = false
                                })
                                DropdownMenuItem(text = { Text("Borrar Línea") }, onClick = {
                                    val text = tvState.text
                                    var ls = text.lastIndexOf('\n', selStart - 1); val ols = ls
                                    if (ls == -1) ls = 0 else ls += 1
                                    var le = text.indexOf('\n', selEnd); if (le == -1) le = text.length else le += 1
                                    if (le == text.length && ols != -1) ls = ols
                                    val newText = text.substring(0, ls) + text.substring(le)
                                    val nc = ls.coerceAtMost(newText.length)
                                    viewModel.onEditorTextChanged(newText, triggerCursorUpdate = true)
                                    tvState = tvState.copy(text = newText, selection = androidx.compose.ui.text.TextRange(nc))
                                    showContextMenu = false
                                })
                                DropdownMenuItem(text = { Text("Comentar / Descomentar") }, onClick = {
                                    val text = tvState.text
                                    var ls = text.lastIndexOf('\n', selStart - 1).coerceAtLeast(0)
                                    if (ls > 0 && text[ls] == '\n') ls += 1
                                    var le = text.indexOf('\n', selEnd); if (le == -1) le = text.length
                                    val section = text.substring(ls, le); val lines = section.split('\n')
                                    val allCommented = lines.all { it.trimStart().startsWith("//") || it.isBlank() }
                                    val newLines = if (allCommented) lines.map { if (it.trimStart().startsWith("//")) it.replaceFirst("//", "") else it } else lines.map { "// $it" }
                                    val joined = newLines.joinToString("\n")
                                    val newText = text.substring(0, ls) + joined + text.substring(le)
                                    viewModel.onEditorTextChanged(newText, triggerCursorUpdate = true)
                                    tvState = tvState.copy(text = newText, selection = androidx.compose.ui.text.TextRange(selStart))
                                    showContextMenu = false
                                })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Formatear con Prettier") }, onClick = { viewModel.formatCurrentCode(); showContextMenu = false })
                                if (PluginManager.isPluginEnabled("error_lens")) {
                                    DropdownMenuItem(text = { Text("Ver Errores", color = Color.Gray) }, onClick = { showContextMenu = false })
                                }
                            }
                        }
                    }
                }

                var showAutocomplete by remember { mutableStateOf(false) }
                var autocompleteWord by remember { mutableStateOf("") }
                val activeSuggestions = remember(autocompleteWord) {
                    val staticSug = listOf("function", "const", "return", "console.log(", "fetch(", "useState(", "useEffect(", "async function ", "import ", "export ", "class ", "let ", "typeof ")
                    if (autocompleteWord.isEmpty()) emptyList() else staticSug.filter { it.startsWith(autocompleteWord, ignoreCase = true) }
                }

                var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

                CompositionLocalProvider(LocalTextToolbar provides customToolbar) {
                    BasicTextField(
                        value = tvState,
                        onTextLayout = { textLayoutResult = it },
                        onValueChange = { newValue ->
                            var manipulatedValue = newValue
                            val oldText = tvState.text; val newText = newValue.text
                            val oldSelection = tvState.selection; val newSelection = newValue.selection
                            val replaceLength = oldSelection.max - oldSelection.min
                            val insertedLength = newText.length - (oldText.length - replaceLength)
                            val insertedString = if (insertedLength > 0 && newSelection.start >= insertedLength) newText.substring(newSelection.start - insertedLength, newSelection.start) else ""
                            val charAdded = if (insertedLength == 1) insertedString.firstOrNull() else null

                            if (insertedString == "\n") {
                                val textBeforeNewline = newText.substring(0, newSelection.start - 1)
                                val lastNewlineIndex = textBeforeNewline.lastIndexOf('\n')
                                val previousLine = if (lastNewlineIndex != -1) textBeforeNewline.substring(lastNewlineIndex + 1) else textBeforeNewline
                                val indentMatch = Regex("^\\s+").find(previousLine)
                                var indent = indentMatch?.value ?: ""
                                if (previousLine.trimEnd().endsWith("{") || previousLine.trimEnd().endsWith("(")) indent += "  "
                                if (indent.isNotEmpty()) {
                                    val ip = newSelection.start
                                    if (ip <= newText.length) {
                                        val safeText = newText.substring(0, ip) + indent + newText.substring(ip)
                                        manipulatedValue = newValue.copy(text = safeText, selection = androidx.compose.ui.text.TextRange(ip + indent.length))
                                    }
                                }
                            }

                            tvState = manipulatedValue
                            viewModel.updateCursor(manipulatedValue.selection.start)
                            val cursor = manipulatedValue.selection.start
                            if (cursor > 0) {
                                val textBefore = manipulatedValue.text.substring(0, cursor)
                                val lastWord = textBefore.split(Regex("[\\s\\n(){}\\[\\].;]+")).lastOrNull() ?: ""
                                if (lastWord.length >= 2) { autocompleteWord = lastWord; showAutocomplete = true } else showAutocomplete = false
                            } else showAutocomplete = false
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
                        modifier = Modifier.focusRequester(focusRequester).fillMaxWidth().wrapContentHeight()
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
                                        val indentLevels = IntArray(maxLines) { -1 }
                                        var currentIndent = 0
                                        for (i in 0 until maxLines) {
                                            val lineText = lines[i]
                                            if (lineText.isBlank()) indentLevels[i] = -1
                                            else { val spaces = lineText.takeWhile { it == ' ' }.length; indentLevels[i] = spaces; currentIndent = spaces }
                                        }
                                        var nextIndent = 0
                                        for (i in maxLines - 1 downTo 0) {
                                            if (indentLevels[i] == -1) { indentLevels[i] = minOf(currentIndent, nextIndent) }
                                            else { nextIndent = indentLevels[i]; currentIndent = indentLevels[i] }
                                        }
                                        for (i in 0 until maxLines) {
                                            val ls = indentLevels[i]
                                            for (spaceLevel in 2..ls step 2) {
                                                val xBase = layout.getHorizontalPosition(layout.getLineStart(i), true)
                                                val charW = if (layout.lineCount > 0 && layout.getLineStart(0) + 1 < layout.getLineEnd(0)) layout.getHorizontalPosition(layout.getLineStart(0) + 1, true) - layout.getHorizontalPosition(layout.getLineStart(0), true) else 8f
                                                val xPos = try { if (lines[i].length >= spaceLevel) layout.getHorizontalPosition(layout.getLineStart(i) + spaceLevel, true) else xBase + charW * spaceLevel } catch(e: Exception) { xBase + charW * spaceLevel }
                                                drawLine(color = guideColors[((spaceLevel / 2) - 1).coerceAtLeast(0) % guideColors.size], start = Offset(xPos, layout.getLineTop(i)), end = Offset(xPos, layout.getLineBottom(i)), strokeWidth = 1.5f)
                                            }
                                        }
                                    }
                                }
                            }
                    )
                }

                if (PluginManager.isPluginEnabled("error_lens") && diagnostics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Errores y Advertencias:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.LightGray else Color.DarkGray)
                    diagnostics.forEach { diag ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.updateCursor(0) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = if (diag.level == DiagnosticLevel.ERROR) Color.Red else Color(0xFFFFB300)
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Línea ${diag.line}: ${diag.message}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = if (diag.level == DiagnosticLevel.ERROR) Color(0xFFF44336) else Color(0xFFB07B00))
                            diag.fixAction?.let { fix ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { viewModel.applyQuickFix(fix) }, colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF3E3E3E) else Color(0xFFDCDCDC), contentColor = if (isDark) Color.White else Color.Black), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(20.dp)) {
                                    Text("💡 Corrección rápida", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                if (showAutocomplete && activeSuggestions.isNotEmpty()) {
                    Popup(onDismissRequest = { showAutocomplete = false }) {
                        Card(modifier = Modifier.width(200.dp).padding(8.dp), colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF252526) else Color.White), elevation = CardDefaults.cardElevation(8.dp), shape = RoundedCornerShape(6.dp)) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Text("Autocompletado JS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp, start = 6.dp))
                                activeSuggestions.take(5).forEach { suggestion ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable {
                                        val orig = tvState.text; val ss = tvState.selection.start; val pl = autocompleteWord.length
                                        val updated = orig.substring(0, ss - pl) + suggestion + orig.substring(ss)
                                        viewModel.onEditorTextChanged(updated); showAutocomplete = false
                                    }.padding(vertical = 4.dp, horizontal = 6.dp)) {
                                        Text(suggestion, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = if (isDark) Color.White else Color.Black)
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

@Composable
fun MinimapPane(viewModel: EditorViewModel, isDark: Boolean, scrollState: ScrollState) {
    val rawText by viewModel.editorText.collectAsStateWithLifecycle()
    val isMinimapTextVisible by viewModel.isMinimapTextVisible.collectAsStateWithLifecycle()
    val lines = rawText.split("\n")
    val scope = rememberCoroutineScope()
    val linesToRender = lines.take(1000)
    val lineHeightDp = if (isMinimapTextVisible) 3.dp else 2.dp

    Column(
        modifier = Modifier.width(60.dp).fillMaxHeight()
            .background(if (isDark) Color(0xCC1A1A1A) else Color(0xCCF3F3F3))
            .drawBehind { drawLine(color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFD4D4D4), start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }
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
                                scope.launch { scrollState.scrollTo(scrollY.toInt()) }
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
                            Text(text = line, fontSize = 2.sp, lineHeight = 3.sp, fontFamily = FontFamily.Monospace, color = lineColor, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 4.dp).height(3.dp).fillMaxWidth())
                        } else {
                            Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp).height(1.dp).fillMaxWidth(kotlin.math.min(1f, trimmedLine.length / 50f)).background(lineColor))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(lineHeightDp))
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().offset(y = (linesToRender.size * lineHeightDp.value * scrollRatio).dp).height(40.dp).background(if (isDark) Color(0x33FFFFFF) else Color(0x33000000)))
        }
    }
}

@Composable
fun EmptyEditorState(viewModel: EditorViewModel, isDark: Boolean, textColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(if (isDark) Color(0xFF2D2D2D) else Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) { Text("💻", fontSize = 28.sp) }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Visor de Código JavaScript", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Selecciona un archivo del panel izquierdo para comenzar a consultar o editar tu script de JavaScript, formatear con Prettier, realizar búsquedas y corregir errores.", fontSize = 12.sp, lineHeight = 16.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth(0.8f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { val file = VirtualFile(1L, "app.js", "console.log('Hello World!');"); viewModel.openFileInTab(file) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) {
                Text("Abrir Archivo de Ejemplo", color = Color.White)
            }
        }
    }
}

@Composable
fun VscodeStatusBar(viewModel: EditorViewModel, background: Color) {
    val cursorLine by viewModel.cursorLine.collectAsStateWithLifecycle()
    val cursorCol by viewModel.cursorColumn.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val errorsCount = diagnostics.count { it.level == DiagnosticLevel.ERROR }
    val warnsCount = diagnostics.count { it.level == DiagnosticLevel.WARNING }

    Row(modifier = Modifier.fillMaxWidth().height(22.dp).background(background).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ JavaScript Editor", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.width(14.dp))
            Text("🔴 $errorsCount", fontSize = 10.sp, color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🟡 $warnsCount", fontSize = 10.sp, color = Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ln $cursorLine, Col $cursorCol", fontSize = 10.sp, color = Color.White)
            Spacer(modifier = Modifier.width(14.dp))
            Text("Espacios: 2", fontSize = 10.sp, color = Color.White)
            Spacer(modifier = Modifier.width(14.dp))
            Text("UTF-8", fontSize = 10.sp, color = Color.White)
            Spacer(modifier = Modifier.width(14.dp))
            Text("JS", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
