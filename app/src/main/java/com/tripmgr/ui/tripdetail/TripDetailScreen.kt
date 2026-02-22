package com.tripmgr.ui.tripdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripmgr.data.model.FileType
import com.tripmgr.data.model.Section
import com.tripmgr.data.model.TripFileRef
import com.tripmgr.ui.components.ConfirmDeleteDialog
import com.tripmgr.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripFolderId: String,
    viewModel: TripDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddSectionDialog by remember { mutableStateOf(false) }
    var sectionToRename by remember { mutableStateOf<Section?>(null) }
    var sectionToDelete by remember { mutableStateOf<Section?>(null) }
    var fileToDelete by remember { mutableStateOf<Pair<String, TripFileRef>?>(null) }
    var sectionForFileAdd by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val sectionId = sectionForFileAdd ?: return@rememberLauncherForActivityResult
        uri?.let {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(it) ?: "application/octet-stream"
            val fileName = getFileName(context, it) ?: "file"
            val inputStream = contentResolver.openInputStream(it) ?: return@let
            viewModel.addFile(sectionId, fileName, mimeType, inputStream)
        }
        sectionForFileAdd = null
    }

    LaunchedEffect(tripFolderId) {
        viewModel.loadTrip(tripFolderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.metadata?.name ?: "Trip",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSectionDialog = true }) {
                Icon(Icons.Default.Add, "Add section")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Error
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) { Text(error) }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val metadata = uiState.metadata
            if (metadata != null) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Description
                    item {
                        TripDescriptionCard(
                            description = metadata.description,
                            onUpdateDescription = { viewModel.updateDescription(it) }
                        )
                    }

                    // Sections
                    if (metadata.sections.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ViewDay,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No sections yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Tap + to add a section like \"Day 1\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    items(
                        metadata.sections.sortedBy { it.order },
                        key = { it.id }
                    ) { section ->
                        SectionCard(
                            section = section,
                            isExpanded = section.id in uiState.expandedSections,
                            onToggleExpand = { viewModel.toggleSectionExpanded(section.id) },
                            onRename = { sectionToRename = section },
                            onDelete = { sectionToDelete = section },
                            onAddFile = {
                                sectionForFileAdd = section.id
                                filePickerLauncher.launch(arrayOf(
                                    "application/gpx+xml",
                                    "application/xml",
                                    "application/pdf",
                                    "image/*",
                                    "video/*"
                                ))
                            },
                            onRemoveFile = { fileRef ->
                                fileToDelete = section.id to fileRef
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showAddSectionDialog) {
        val sectionCount = uiState.metadata?.sections?.size ?: 0
        TextInputDialog(
            title = "Add Section",
            label = "Section label",
            initialValue = "Day ${sectionCount + 1}",
            confirmText = "Add",
            onDismiss = { showAddSectionDialog = false },
            onConfirm = { label ->
                showAddSectionDialog = false
                viewModel.addSection(label)
            }
        )
    }

    sectionToRename?.let { section ->
        TextInputDialog(
            title = "Rename Section",
            label = "New label",
            initialValue = section.label,
            confirmText = "Rename",
            onDismiss = { sectionToRename = null },
            onConfirm = { newLabel ->
                viewModel.renameSection(section.id, newLabel)
                sectionToRename = null
            }
        )
    }

    sectionToDelete?.let { section ->
        ConfirmDeleteDialog(
            itemName = section.label,
            onDismiss = { sectionToDelete = null },
            onConfirm = {
                viewModel.deleteSection(section.id)
                sectionToDelete = null
            }
        )
    }

    fileToDelete?.let { (sectionId, fileRef) ->
        ConfirmDeleteDialog(
            itemName = fileRef.name,
            onDismiss = { fileToDelete = null },
            onConfirm = {
                viewModel.removeFile(sectionId, fileRef.fileId)
                fileToDelete = null
            }
        )
    }
}

@Composable
private fun TripDescriptionCard(
    description: String,
    onUpdateDescription: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(description) { mutableStateOf(description) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Description", style = MaterialTheme.typography.titleSmall)
                IconButton(
                    onClick = {
                        if (isEditing) {
                            onUpdateDescription(editText)
                        }
                        isEditing = !isEditing
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isEditing) "Save" else "Edit",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (isEditing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
            } else {
                Text(
                    text = description.ifEmpty { "No description" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (description.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: Section,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddFile: () -> Unit,
    onRemoveFile: (TripFileRef) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    section.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${section.files.size} file${if (section.files.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Section actions
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Section options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add file") },
                        onClick = { showMenu = false; onAddFile() },
                        leadingIcon = { Icon(Icons.Default.AttachFile, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete section") },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }

            // Expanded content: file list
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    if (section.files.isEmpty()) {
                        Text(
                            "No files in this section",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    section.files.forEach { fileRef ->
                        FileRow(fileRef = fileRef, onRemove = { onRemoveFile(fileRef) })
                    }

                    // Add file button
                    TextButton(
                        onClick = onAddFile,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add file")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    fileRef: TripFileRef,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (fileRef.type) {
                FileType.GPX -> Icons.Default.Route
                FileType.PDF -> Icons.Default.PictureAsPdf
                FileType.IMAGE -> Icons.Default.Image
                FileType.VIDEO -> Icons.Default.Videocam
            },
            contentDescription = fileRef.type.name,
            tint = when (fileRef.type) {
                FileType.GPX -> MaterialTheme.colorScheme.primary
                FileType.PDF -> MaterialTheme.colorScheme.error
                FileType.IMAGE -> MaterialTheme.colorScheme.tertiary
                FileType.VIDEO -> MaterialTheme.colorScheme.secondary
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            fileRef.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close, "Remove",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) it.getString(nameIndex) else null
        } else null
    }
}
