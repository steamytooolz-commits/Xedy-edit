package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AgentEvent
import com.example.data.ChatMessageEntity
import com.example.data.OpenTab
import com.example.ui.editor.SyntaxHighlightingTransformation
import com.example.ui.viewmodel.EditorViewModel
import com.example.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

// Theme colors for Alpine Slate
val AlpineDarkBg = Color(0xFF181B20)
val AlpineEditorBg = Color(0xFF1E222B)
val AlpineMenuBg = Color(0xFF21252B)
val AlpineAccent = Color(0xFF61AFEF) // Ocean Blue
val AlpineGreenAccent = Color(0xFF98C379)
val AlpineAmberAccent = Color(0xFFD19A66)
val AlpineRedAccent = Color(0xFFE06C75)
val AlpinePurple = Color(0xFFC678DD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EditorViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var activeTab by remember { mutableStateOf(0) } // 0: Editor, 1: Terminal, 2: AI Agent, 3: Settings
    var auxTab by remember { mutableStateOf(1) } // For widescreen split: 1: Terminal, 2: AI Agent, 3: Settings

    val openTabsList by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabPath by viewModel.activeTabPath.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 760.dp

        if (isWideScreen) {
            // WIDESCREEN / TABLET MULTI-PANE LAYOUT
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AlpineDarkBg)
            ) {
                // 1. Structural permanent sidebar file explorer
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                ) {
                    SidebarExplorer(viewModel, onClose = {})
                }

                VerticalDivider(color = Color.DarkGray)

                // 2. Main Editor Pane (Middle)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) {
                    // Top Bar for Editor
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = AlpineDarkBg,
                            titleContentColor = Color.White
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.FilterHdr,
                                    contentDescription = "Alpine Logo",
                                    tint = AlpineAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Alpine Editor",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        },
                        actions = {
                            if (activeTabPath != null) {
                                // Run active script in split auxiliary terminal
                                IconButton(
                                    onClick = {
                                        val fileName = activeTabPath ?: ""
                                        val ext = fileName.substringAfterLast('.')
                                        scope.launch {
                                            viewModel.saveActiveTabContent()
                                            if (ext == "py") {
                                                viewModel.executeTerminalCommand("python3 $fileName")
                                                auxTab = 1
                                                Toast.makeText(context, "Executing Python script in Terminal...", Toast.LENGTH_SHORT).show()
                                            } else if (ext == "js") {
                                                viewModel.executeTerminalCommand("node $fileName")
                                                auxTab = 1
                                                Toast.makeText(context, "Executing Node script in Terminal...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.executeTerminalCommand("cat $fileName")
                                                auxTab = 1
                                                Toast.makeText(context, "Opening file in Terminal...", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("run_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run Code", tint = AlpineGreenAccent)
                                }

                                // Save file
                                IconButton(
                                    onClick = {
                                        viewModel.saveCurrentFile()
                                        Toast.makeText(context, "File saved successfully", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("save_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save file", tint = Color.White)
                                }
                            }
                        }
                    )

                    Divider(color = Color.DarkGray)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        EditorTab(viewModel)
                    }
                }

                VerticalDivider(color = Color.DarkGray)

                // 3. Auxiliary Pane (Right) - Terminal, AI, Settings
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(AlpineDarkBg)
                ) {
                    TabRow(
                        selectedTabIndex = when (auxTab) {
                            1 -> 0
                            2 -> 1
                            3 -> 2
                            else -> 0
                        },
                        containerColor = AlpineDarkBg,
                        contentColor = Color.White
                    ) {
                        Tab(
                            selected = auxTab == 1,
                            onClick = { auxTab = 1 },
                            text = { Text("Terminal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = auxTab == 2,
                            onClick = { auxTab = 2 },
                            text = { Text("AI Agent", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = auxTab == 3,
                            onClick = { auxTab = 3 },
                            text = { Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    Divider(color = Color.DarkGray)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        when (auxTab) {
                            1 -> TerminalTab(viewModel)
                            2 -> AgentTab(viewModel)
                            3 -> SettingsTab(viewModel)
                        }
                    }
                }
            }
        } else {
            // STANDARD MOBILE SINGLE-PANE LAYOUT
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = AlpineMenuBg,
                        modifier = Modifier.width(300.dp)
                    ) {
                        SidebarExplorer(viewModel, onClose = { scope.launch { drawerState.close() } })
                    }
                }
            ) {
                Scaffold(
                    containerColor = AlpineDarkBg,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = AlpineDarkBg,
                                titleContentColor = Color.White
                            ),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.FilterHdr,
                                        contentDescription = "Alpine Logo",
                                        tint = AlpineAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Alpine Editor",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("menu_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Files", tint = Color.White)
                                }
                            },
                            actions = {
                                if (activeTab == 0 && activeTabPath != null) {
                                    // Run script action
                                    IconButton(
                                        onClick = {
                                            val fileName = activeTabPath ?: ""
                                            val ext = fileName.substringAfterLast('.')
                                            scope.launch {
                                                viewModel.saveActiveTabContent()
                                                if (ext == "py") {
                                                    viewModel.executeTerminalCommand("python3 $fileName")
                                                    activeTab = 1
                                                    Toast.makeText(context, "Executing Python script in Terminal...", Toast.LENGTH_SHORT).show()
                                                } else if (ext == "js") {
                                                    viewModel.executeTerminalCommand("node $fileName")
                                                    activeTab = 1
                                                    Toast.makeText(context, "Executing Node script in Terminal...", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    viewModel.executeTerminalCommand("cat $fileName")
                                                    activeTab = 1
                                                    Toast.makeText(context, "Opening file in Terminal...", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("run_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Run Code", tint = AlpineGreenAccent)
                                    }

                                    // Save file action
                                    IconButton(
                                        onClick = {
                                            viewModel.saveCurrentFile()
                                            Toast.makeText(context, "File saved successfully", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.testTag("save_button")
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "Save file", tint = Color.White)
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = AlpineDarkBg,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                label = { Text("Editor", color = if (activeTab == 0) AlpineAccent else Color.Gray) },
                                icon = { Icon(Icons.Default.Code, contentDescription = "Code Editor") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AlpineAccent,
                                    indicatorColor = AlpineDarkBg.copy(alpha = 0.5f),
                                    unselectedIconColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_editor")
                            )
                            NavigationBarItem(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                label = { Text("Terminal", color = if (activeTab == 1) AlpineAccent else Color.Gray) },
                                icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AlpineAccent,
                                    indicatorColor = AlpineDarkBg.copy(alpha = 0.5f),
                                    unselectedIconColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_terminal")
                            )
                            NavigationBarItem(
                                selected = activeTab == 2,
                                onClick = { activeTab = 2 },
                                label = { Text("AI Agent", color = if (activeTab == 2) AlpineAccent else Color.Gray) },
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Agent") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AlpineAccent,
                                    indicatorColor = AlpineDarkBg.copy(alpha = 0.5f),
                                    unselectedIconColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_agent")
                            )
                            NavigationBarItem(
                                selected = activeTab == 3,
                                onClick = { activeTab = 3 },
                                label = { Text("Settings", color = if (activeTab == 3) AlpineAccent else Color.Gray) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AlpineAccent,
                                    indicatorColor = AlpineDarkBg.copy(alpha = 0.5f),
                                    unselectedIconColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_settings")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(AlpineDarkBg)
                    ) {
                        when (activeTab) {
                            0 -> EditorTab(viewModel)
                            1 -> TerminalTab(viewModel)
                            2 -> AgentTab(viewModel)
                            3 -> SettingsTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}

// --- 1. Sidebar File Explorer View ---
@Composable
fun SidebarExplorer(viewModel: EditorViewModel, onClose: () -> Unit) {
    val workspaceFiles by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlpineMenuBg)
            .padding(16.dp)
    ) {
        // Explorer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "WORKSPACE EXPLORER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.5.sp
            )
            Row {
                IconButton(onClick = { showNewFileDialog = true }) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { viewModel.refreshFiles() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

        // Files Tree
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val rootFolder = WorkspaceManager.getWorkspaceRoot(context)
            
            // Render directory list
            val sortedFiles = workspaceFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            items(sortedFiles) { file ->
                val relPath = WorkspaceManager.getRelativePath(context, file)
                if (relPath.isNotEmpty()) {
                    FileItemRow(
                        file = file,
                        relPath = relPath,
                        onClick = {
                            if (file.isFile) {
                                viewModel.openTab(relPath)
                                onClose()
                            }
                        },
                        onDelete = {
                            viewModel.deleteFile(relPath)
                            Toast.makeText(context, "Deleted: $relPath", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false; newItemName = "" },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("File Name (e.g. script.py)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_file_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemName.isNotBlank()) {
                            viewModel.createFile(newItemName)
                            newItemName = ""
                            showNewFileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_file")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false; newItemName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false; newItemName = "" },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("Folder Name (e.g. src)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemName.isNotBlank()) {
                            viewModel.createDirectory(newItemName)
                            newItemName = ""
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false; newItemName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FileItemRow(
    file: File,
    relPath: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val icon = if (file.isDirectory) {
        Icons.Default.Folder
    } else {
        when (file.extension.lowercase()) {
            "py" -> Icons.Default.Terminal
            "kt", "kts" -> Icons.Default.Code
            "js", "ts" -> Icons.Default.Javascript
            "md" -> Icons.Default.Description
            else -> Icons.Default.Article
        }
    }

    val tint = if (file.isDirectory) {
        AlpineAmberAccent
    } else {
        when (file.extension.lowercase()) {
            "py" -> AlpineAccent
            "kt", "kts" -> AlpinePurple
            "js", "ts" -> AlpineGreenAccent
            "md" -> Color.LightGray
            else -> Color.White
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = file.name,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.Red) },
                    onClick = {
                        onDelete()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
                )
            }
        }
    }
}

// --- 2. Editor Tab View ---
@Composable
fun EditorTab(viewModel: EditorViewModel) {
    val openTabsList by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabPath by viewModel.activeTabPath.collectAsStateWithLifecycle()

    if (openTabsList.isEmpty() || activeTabPath == null) {
        // Empty State
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Code,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No open files.",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Open files from the left sidebar explorer menu.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    } else {
        val lang = WorkspaceManager.getLanguageFromExtension(activeTabPath ?: "")
        Column(modifier = Modifier.fillMaxSize()) {
            // Horizontal open files tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AlpineDarkBg)
                    .horizontalScroll(rememberScrollState())
            ) {
                openTabsList.forEach { tab ->
                    val isActive = tab.filePath == activeTabPath
                    EditorTabItem(
                        tab = tab,
                        isActive = isActive,
                        onSelect = { viewModel.selectTab(tab.filePath) },
                        onClose = { viewModel.closeTab(tab.filePath) }
                    )
                }
            }

            Divider(color = Color.DarkGray)

            // Editing Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(AlpineEditorBg)
            ) {
                val lineScrollState = rememberScrollState()
                val editorScrollState = rememberScrollState()

                // Two column layout: Line numbers on left, Code editor text on right
                Row(modifier = Modifier.fillMaxSize()) {
                    // Line numbers column
                    val linesCount = viewModel.activeFileContent.lines().size
                    val lineNumbersText = buildString {
                        for (i in 1..maxOf(1, linesCount)) {
                            appendLine("$i")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(AlpineDarkBg.copy(alpha = 0.5f))
                            .verticalScroll(lineScrollState)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = lineNumbersText,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }

                    VerticalDivider(color = Color.DarkGray)

                    // Text input canvas
                    BasicTextField(
                        value = viewModel.activeFileContent,
                        onValueChange = { viewModel.updateActiveContent(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(editorScrollState)
                            .padding(12.dp)
                            .testTag("editor_canvas"),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(AlpineAccent),
                        visualTransformation = SyntaxHighlightingTransformation(lang)
                    )
                }
            }

            // Inline code completions row
            val lastWord = viewModel.activeFileContent.substringAfterLast(' ', "").substringAfterLast('\n', "").trim()
            val suggestions = remember(lastWord, lang) {
                if (lastWord.isEmpty()) {
                    emptyList()
                } else {
                    when (lang) {
                        "python" -> {
                            listOf(
                                Pair("def", "def greet(name):\n    print(f'Hello, {name}!')"),
                                Pair("pri", "print(f'Value: {value}')"),
                                Pair("for", "for i in range(10):\n    "),
                                Pair("if", "if __name__ == '__main__':\n    "),
                                Pair("imp", "import sys\nimport os\n")
                            ).filter { it.first.startsWith(lastWord) && it.first != lastWord }
                        }
                        "javascript" -> {
                            listOf(
                                Pair("con", "console.log('Value:', value);"),
                                Pair("fun", "function greet(name) {\n    return 'Hello ' + name;\n}"),
                                Pair("req", "const http = require('http');"),
                                Pair("con", "const PORT = 3000;")
                            ).filter { it.first.startsWith(lastWord) && it.first != lastWord }
                        }
                        "kotlin" -> {
                            listOf(
                                Pair("fun", "fun main() {\n    println(\"Hello, Alpine!\")\n}"),
                                Pair("val", "val items = listOf(1, 2, 3)"),
                                Pair("pri", "println(\"Value: \$value\")")
                            ).filter { it.first.startsWith(lastWord) && it.first != lastWord }
                        }
                        else -> emptyList()
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlpineMenuBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡 Suggestion:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    suggestions.forEach { pair ->
                        SuggestionChip(
                            onClick = {
                                val base = viewModel.activeFileContent.substring(0, viewModel.activeFileContent.length - lastWord.length)
                                viewModel.updateActiveContent(base + pair.second)
                            },
                            label = {
                                Text(
                                    text = pair.first + " ➔ " + pair.second.take(20).replace("\n", " "),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = AlpineAccent.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
                Divider(color = Color.DarkGray)
            }

            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AlpineDarkBg)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lines = viewModel.activeFileContent.lines().size
                val chars = viewModel.activeFileContent.length
                val lang = WorkspaceManager.getLanguageFromExtension(activeTabPath ?: "")
                Text(
                    text = "Lines: $lines • Chars: $chars",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = lang.uppercase(),
                    color = AlpineAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EditorTabItem(
    tab: OpenTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(if (isActive) AlpineEditorBg else AlpineDarkBg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = if (isActive) AlpineAccent else Color.Gray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = tab.title,
            color = if (isActive) Color.White else Color.Gray,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.Gray,
            modifier = Modifier
                .size(14.dp)
                .clickable { onClose() }
        )
    }
}

// --- 3. Terminal Tab View ---
@Composable
fun TerminalTab(viewModel: EditorViewModel) {
    val history by viewModel.terminalHistory.collectAsStateWithLifecycle()
    val prompt by viewModel.terminalPrompt.collectAsStateWithLifecycle()
    var inputCommand by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp)
    ) {
        // Output Console Buffer
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable { focusRequester.requestFocus() }
        ) {
            items(history) { line ->
                Text(
                    text = line,
                    color = if (line.startsWith("alpine-editor:")) Color.White else AlpineGreenAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Active Prompt Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = prompt,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            BasicTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("terminal_input"),
                textStyle = TextStyle(
                    color = AlpineGreenAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(AlpineGreenAccent),
                singleLine = true
            )
        }

        // Special Linux keys row for touch usability
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AlpineDarkBg)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TerminalSoftKey("TAB", onClick = {
                // Autocomplete simulated logic
                if (inputCommand.isBlank()) {
                    inputCommand = "help"
                } else if (inputCommand == "py") {
                    inputCommand = "python3 "
                } else {
                    inputCommand += "README.md"
                }
            })
            TerminalSoftKey("ESC", onClick = { inputCommand = "" })
            TerminalSoftKey("CTRL", onClick = { Toast.makeText(context, "CTRL command action active", Toast.LENGTH_SHORT).show() })
            TerminalSoftKey("|", onClick = { inputCommand += " | " })
            TerminalSoftKey("Up", onClick = { inputCommand = "python3 main.py" })
            TerminalSoftKey("Enter", onClick = {
                if (inputCommand.isNotBlank()) {
                    viewModel.executeTerminalCommand(inputCommand)
                    inputCommand = ""
                }
            })
        }
    }
}

@Composable
fun TerminalSoftKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

// --- 4. AI Chat & Coding Agent View ---
@Composable
fun AgentTab(viewModel: EditorViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isRunning by viewModel.isAgentRunning.collectAsStateWithLifecycle()
    val logs by viewModel.agentLogs.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    val scrollState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Clear chat header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AlpineDarkBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "AUTONOMOUS CODING AGENT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            TextButton(
                onClick = { viewModel.clearChat() },
                modifier = Modifier.testTag("clear_chat_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = AlpineRedAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear History", color = AlpineRedAccent, fontSize = 12.sp)
            }
        }

        Divider(color = Color.DarkGray)

        // Messages List View
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            items(messages) { msg ->
                ChatMessageBubble(msg)
            }

            if (isRunning) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AlpineAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Agent is writing, testing, or reviewing code...",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }

        // Suggestions bar
        if (messages.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChipItem("Explain python script", onClick = { textInput = "Explain the file main.py to me" })
                SuggestionChipItem("Fix bugs", onClick = { textInput = "Fix any potential bugs in Fibonacci.kt" })
                SuggestionChipItem("/agent Add factorial", onClick = { textInput = "/agent Create a new file fact.py that calculates factorials and execute it in terminal to verify!" })
            }
        }

        // Input bottom bar
        Surface(
            color = AlpineMenuBg,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask AI, or use /agent for autonomous mode...", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AlpineAccent,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    maxLines = 4,
                    textStyle = TextStyle(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.sendChatMessage(textInput)
                        textInput = ""
                    },
                    enabled = textInput.isNotBlank() && !isRunning,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (textInput.isNotBlank() && !isRunning) AlpineAccent else Color.DarkGray)
                        .testTag("send_message_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun SuggestionChipItem(label: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label, color = Color.White, fontSize = 11.sp) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = Color.DarkGray.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun ChatMessageBubble(msg: ChatMessageEntity) {
    val isUser = msg.role == "user"
    val isAgentAction = msg.role == "agent_action"
    val isAgentThinking = msg.role == "agent_thinking"
    val isAgentError = msg.role == "agent_error"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        val bubbleColor = when {
            isUser -> AlpineAccent
            isAgentAction -> AlpineAmberAccent.copy(alpha = 0.2f)
            isAgentThinking -> AlpineAccent.copy(alpha = 0.1f)
            isAgentError -> AlpineRedAccent.copy(alpha = 0.2f)
            else -> AlpineEditorBg
        }

        val textColor = when {
            isUser -> Color.Black
            isAgentError -> AlpineRedAccent
            isAgentAction -> AlpineAmberAccent
            else -> Color.White
        }

        val label = when {
            isUser -> "You"
            isAgentThinking -> "Agent Thinking"
            isAgentAction -> "Agent Action"
            isAgentError -> "Agent Error"
            else -> "Alpine Assistant"
        }

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = msg.content,
                color = textColor,
                fontSize = 13.sp,
                fontFamily = if (isUser) FontFamily.Default else FontFamily.Monospace,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// --- 5. Settings Tab View ---
@Composable
fun SettingsTab(viewModel: EditorViewModel) {
    val settings by viewModel.settingsMap.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var activeProvider by remember { mutableStateOf("gemini") }

    var geminiApiKey by remember { mutableStateOf("") }
    var geminiModel by remember { mutableStateOf("gemini-3.5-flash") }

    var nvidiaApiKey by remember { mutableStateOf("") }
    var nvidiaModel by remember { mutableStateOf("nvidia/llama-3.1-nemotron-70b-instruct") }
    var nvidiaBaseUrl by remember { mutableStateOf("https://integrate.api.nvidia.com/v1") }

    var openaiApiKey by remember { mutableStateOf("") }
    var openaiModel by remember { mutableStateOf("gpt-4o-mini") }

    var anthropicApiKey by remember { mutableStateOf("") }
    var anthropicModel by remember { mutableStateOf("claude-3-5-sonnet-20240620") }

    var customApiKey by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("custom-model") }
    var customBaseUrl by remember { mutableStateOf("http://10.0.2.2:11434/v1") }

    LaunchedEffect(settings) {
        activeProvider = settings["ai_provider"] ?: "gemini"
        geminiApiKey = settings["gemini_api_key"] ?: ""
        geminiModel = settings["gemini_model"] ?: "gemini-3.5-flash"

        nvidiaApiKey = settings["nvidia_api_key"] ?: ""
        nvidiaModel = settings["nvidia_model"] ?: "nvidia/llama-3.1-nemotron-70b-instruct"
        nvidiaBaseUrl = settings["nvidia_base_url"] ?: "https://integrate.api.nvidia.com/v1"

        openaiApiKey = settings["openai_api_key"] ?: ""
        openaiModel = settings["openai_model"] ?: "gpt-4o-mini"

        anthropicApiKey = settings["anthropic_api_key"] ?: ""
        anthropicModel = settings["anthropic_model"] ?: "claude-3-5-sonnet-20240620"

        customApiKey = settings["custom_api_key"] ?: ""
        customModel = settings["custom_model"] ?: "custom-model"
        customBaseUrl = settings["custom_base_url"] ?: "http://10.0.2.2:11434/v1"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "CONFIGURATION SETTINGS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "ACTIVE AI CLOUD PROVIDER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val providers = listOf(
                "gemini" to "Google Gemini",
                "nvidia" to "NVIDIA NIM",
                "openai" to "OpenAI",
                "anthropic" to "Anthropic",
                "custom" to "Custom API"
            )
            providers.forEach { (provId, label) ->
                val isSel = activeProvider == provId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) AlpineAccent else AlpineEditorBg)
                        .clickable {
                            activeProvider = provId
                            viewModel.saveSetting("ai_provider", provId)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show provider-specific configuration
        when (activeProvider) {
            "gemini" -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlpineEditorBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = "Gemini", tint = AlpineAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Gemini Configuration", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Enter your Gemini API Key from Google AI Studio. The default model is gemini-3.5-flash which is recommended for extremely fast response times and agent loops.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("API Key", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = {
                                geminiApiKey = it
                                viewModel.saveSetting("gemini_api_key", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("gemini_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Selected Model", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = {
                                geminiModel = it
                                viewModel.saveSetting("gemini_model", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            placeholder = { Text("gemini-3.5-flash", color = Color.DarkGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModelRadioOption(
                                modelName = "gemini-3.5-flash",
                                label = "3.5 Flash",
                                isSelected = geminiModel == "gemini-3.5-flash",
                                onSelect = {
                                    geminiModel = "gemini-3.5-flash"
                                    viewModel.saveSetting("gemini_model", "gemini-3.5-flash")
                                }
                            )
                            ModelRadioOption(
                                modelName = "gemini-1.5-pro",
                                label = "1.5 Pro (Heavy)",
                                isSelected = geminiModel == "gemini-1.5-pro",
                                onSelect = {
                                    geminiModel = "gemini-1.5-pro"
                                    viewModel.saveSetting("gemini_model", "gemini-1.5-pro")
                                }
                            )
                        }
                    }
                }
            }
            "nvidia" -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlpineEditorBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = "NVIDIA", tint = AlpineGreenAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("NVIDIA NIM Configuration", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Configure NVIDIA NIM Cloud AI. NVIDIA NIM offers blazing-fast inference speeds on modern foundation models like Llama-3.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("NVIDIA API Key", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = nvidiaApiKey,
                            onValueChange = {
                                nvidiaApiKey = it
                                viewModel.saveSetting("nvidia_api_key", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("nvidia_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineGreenAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("NVIDIA NIM Base URL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = nvidiaBaseUrl,
                            onValueChange = {
                                nvidiaBaseUrl = it
                                viewModel.saveSetting("nvidia_base_url", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineGreenAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("NVIDIA Model ID", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = nvidiaModel,
                            onValueChange = {
                                nvidiaModel = it
                                viewModel.saveSetting("nvidia_model", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineGreenAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Popular NVIDIA Models:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModelRadioOption(
                                modelName = "nvidia/llama-3.1-nemotron-70b-instruct",
                                label = "Nemotron-70B",
                                isSelected = nvidiaModel == "nvidia/llama-3.1-nemotron-70b-instruct",
                                onSelect = {
                                    nvidiaModel = "nvidia/llama-3.1-nemotron-70b-instruct"
                                    viewModel.saveSetting("nvidia_model", "nvidia/llama-3.1-nemotron-70b-instruct")
                                }
                            )
                            ModelRadioOption(
                                modelName = "meta/llama-3.1-405b-instruct",
                                label = "Llama-3.1 405B",
                                isSelected = nvidiaModel == "meta/llama-3.1-405b-instruct",
                                onSelect = {
                                    nvidiaModel = "meta/llama-3.1-405b-instruct"
                                    viewModel.saveSetting("nvidia_model", "meta/llama-3.1-405b-instruct")
                                }
                            )
                        }
                    }
                }
            }
            "openai" -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlpineEditorBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = "OpenAI", tint = AlpinePurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OpenAI Configuration", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Configure standard OpenAI cloud services. Enter your API key and choose an OpenAI model.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("OpenAI API Key", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = openaiApiKey,
                            onValueChange = {
                                openaiApiKey = it
                                viewModel.saveSetting("openai_api_key", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("openai_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpinePurple,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("OpenAI Model ID", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = openaiModel,
                            onValueChange = {
                                openaiModel = it
                                viewModel.saveSetting("openai_model", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpinePurple,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModelRadioOption(
                                modelName = "gpt-4o-mini",
                                label = "gpt-4o-mini",
                                isSelected = openaiModel == "gpt-4o-mini",
                                onSelect = {
                                    openaiModel = "gpt-4o-mini"
                                    viewModel.saveSetting("openai_model", "gpt-4o-mini")
                                }
                            )
                            ModelRadioOption(
                                modelName = "gpt-4o",
                                label = "gpt-4o",
                                isSelected = openaiModel == "gpt-4o",
                                onSelect = {
                                    openaiModel = "gpt-4o"
                                    viewModel.saveSetting("openai_model", "gpt-4o")
                                }
                            )
                        }
                    }
                }
            }
            "anthropic" -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlpineEditorBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = "Anthropic", tint = AlpineAmberAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Anthropic Claude Configuration", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Configure Anthropic cloud AI models. Ideal for deeply detailed analysis and pristine Kotlin code generation.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Anthropic API Key", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = anthropicApiKey,
                            onValueChange = {
                                anthropicApiKey = it
                                viewModel.saveSetting("anthropic_api_key", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("anthropic_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineAmberAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Claude Model ID", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = anthropicModel,
                            onValueChange = {
                                anthropicModel = it
                                viewModel.saveSetting("anthropic_model", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineAmberAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModelRadioOption(
                                modelName = "claude-3-5-sonnet-20240620",
                                label = "Claude 3.5 Sonnet",
                                isSelected = anthropicModel == "claude-3-5-sonnet-20240620",
                                onSelect = {
                                    anthropicModel = "claude-3-5-sonnet-20240620"
                                    viewModel.saveSetting("anthropic_model", "claude-3-5-sonnet-20240620")
                                }
                            )
                            ModelRadioOption(
                                modelName = "claude-3-haiku-20240307",
                                label = "Claude 3 Haiku",
                                isSelected = anthropicModel == "claude-3-haiku-20240307",
                                onSelect = {
                                    anthropicModel = "claude-3-haiku-20240307"
                                    viewModel.saveSetting("anthropic_model", "claude-3-haiku-20240307")
                                }
                            )
                        }
                    }
                }
            }
            "custom" -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlpineEditorBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = "Custom", tint = AlpineAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Custom OpenAI-Compatible API", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Connect to any self-hosted, local, or cloud OpenAI-compatible gateway (e.g. OpenRouter, Local LM Studio, Ollama).",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Custom Endpoint Base URL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = {
                                customBaseUrl = it
                                viewModel.saveSetting("custom_base_url", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("API Key (Optional)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = {
                                customApiKey = it
                                viewModel.saveSetting("custom_api_key", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Model Identifier Name", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = customModel,
                            onValueChange = {
                                customModel = it
                                viewModel.saveSetting("custom_model", it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AlpineAccent,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage & External Files Integration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AlpineEditorBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Storage & SD Card Integration",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "By default, files are safely kept inside the secure sandbox at:\n" +
                    "• /data/user/0/com.aistudio.weathertracker.../files/workspace\n\n" +
                    "Use the tools below to synchronize, export, or import your workspaces directly to and from your device's /sdcard or /storage shared directory.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // SD Card Permissions Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray.copy(alpha = 0.3f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = AlpineAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SD Card Status: Mounted & Readable",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Export Button
                Button(
                    onClick = {
                        val result = viewModel.exportWorkspace()
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlpineAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Workspace to /sdcard/Download", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Import Section
                var importPath by remember { mutableStateOf("/sdcard/Download/YourWorkspace") }
                Text(
                    "Import from Custom SD Card Directory",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = importPath,
                    onValueChange = { importPath = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AlpineAccent,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val result = viewModel.importWorkspace(importPath)
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Import",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Files from Path", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reset Options
        Button(
            onClick = {
                viewModel.createFile("README.md", """# Reset Welcome to Alpine Editor! 🏔️
Code was reset back to pristine status successfully.
""")
                viewModel.refreshFiles()
                Toast.makeText(context, "Workspace reset complete", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = AlpineRedAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Workspace to Templates", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ModelRadioOption(
    modelName: String,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AlpineAccent.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = AlpineAccent)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// Vertical divider helper
@Composable
fun VerticalDivider(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(color)
    )
}
