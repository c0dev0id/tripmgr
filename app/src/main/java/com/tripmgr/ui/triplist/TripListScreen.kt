package com.tripmgr.ui.triplist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripmgr.data.model.TripListItem
import com.tripmgr.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TripListScreen(
    viewModel: TripListViewModel,
    onTripClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showNewTripDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<RenameTarget?>(null) }
    var itemToDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    var itemToCopy by remember { mutableStateOf<CopyTarget?>(null) }
    var itemToMove by remember { mutableStateOf<MoveTarget?>(null) }
    var contextMenuItem by remember { mutableStateOf<ContextMenuTarget?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadItems()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.folderStack.isEmpty()) {
                        Text("Trip Manager")
                    } else {
                        Text(
                            uiState.folderStack.lastOrNull()?.name ?: "Trips",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.folderStack.isNotEmpty()) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Create new")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Breadcrumb bar
            if (uiState.folderStack.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        TextButton(onClick = { viewModel.navigateToBreadcrumb(0) }) {
                            Text("Home")
                        }
                    }
                    itemsIndexed(uiState.folderStack.drop(1)) { index, crumb ->
                        Text(" / ", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { viewModel.navigateToBreadcrumb(index + 1) }) {
                            Text(crumb.name)
                        }
                    }
                }
            }

            // Error display
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Loading
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Content
            if (uiState.items.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Luggage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No trips yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap + to create a trip or folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.items, key = {
                        when (it) {
                            is TripListItem.FolderItem -> it.folder.folderId
                            is TripListItem.TripItem -> it.trip.folderId
                        }
                    }) { item ->
                        when (item) {
                            is TripListItem.FolderItem -> FolderRow(
                                folder = item,
                                onClick = { viewModel.navigateToFolder(item.folder) },
                                onLongClick = {
                                    contextMenuItem = ContextMenuTarget(
                                        id = item.folder.folderId,
                                        name = item.folder.name,
                                        isTrip = false
                                    )
                                }
                            )
                            is TripListItem.TripItem -> TripRow(
                                trip = item,
                                onClick = { onTripClick(item.trip.folderId) },
                                onLongClick = {
                                    contextMenuItem = ContextMenuTarget(
                                        id = item.trip.folderId,
                                        name = item.trip.name,
                                        isTrip = true
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──

    if (showCreateDialog) {
        CreateNewDialog(
            onDismiss = { showCreateDialog = false },
            onCreateTrip = {
                showCreateDialog = false
                showNewTripDialog = true
            },
            onCreateFolder = {
                showCreateDialog = false
                showNewFolderDialog = true
            }
        )
    }

    if (showNewTripDialog) {
        TextInputDialog(
            title = "New Trip",
            label = "Trip name",
            confirmText = "Create",
            onDismiss = { showNewTripDialog = false },
            onConfirm = { name ->
                showNewTripDialog = false
                viewModel.createTrip(name)
            }
        )
    }

    if (showNewFolderDialog) {
        TextInputDialog(
            title = "New Folder",
            label = "Folder name",
            confirmText = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name)
            }
        )
    }

    itemToRename?.let { target ->
        TextInputDialog(
            title = "Rename",
            label = "New name",
            initialValue = target.name,
            confirmText = "Rename",
            onDismiss = { itemToRename = null },
            onConfirm = { newName ->
                viewModel.renameItem(target.id, newName, target.isTrip)
                itemToRename = null
            }
        )
    }

    itemToDelete?.let { target ->
        ConfirmDeleteDialog(
            itemName = target.name,
            onDismiss = { itemToDelete = null },
            onConfirm = {
                viewModel.deleteItem(target.id)
                itemToDelete = null
            }
        )
    }

    itemToCopy?.let { target ->
        TextInputDialog(
            title = "Copy Trip",
            label = "New name",
            initialValue = "${target.name} (copy)",
            confirmText = "Copy",
            onDismiss = { itemToCopy = null },
            onConfirm = { newName ->
                viewModel.copyTrip(target.id, newName)
                itemToCopy = null
            }
        )
    }

    itemToMove?.let { target ->
        LaunchedEffect(target) {
            viewModel.loadFoldersForPicker()
        }
        FolderPickerDialog(
            folders = uiState.foldersForPicker,
            onDismiss = { itemToMove = null },
            onSelectFolder = { folder ->
                viewModel.moveItem(target.id, folder.folderId)
                itemToMove = null
            },
            onNavigateInto = { folder ->
                viewModel.loadFoldersForPicker(folder.driveFolderId)
            }
        )
    }

    // Context menu (shown as bottom sheet style dialog)
    contextMenuItem?.let { target ->
        ContextMenuDialog(
            target = target,
            onDismiss = { contextMenuItem = null },
            onRename = {
                contextMenuItem = null
                itemToRename = RenameTarget(target.id, target.name, target.isTrip)
            },
            onDelete = {
                contextMenuItem = null
                itemToDelete = DeleteTarget(target.id, target.name)
            },
            onCopy = if (target.isTrip) ({
                contextMenuItem = null
                itemToCopy = CopyTarget(target.id, target.name)
            }) else null,
            onMove = {
                contextMenuItem = null
                itemToMove = MoveTarget(target.id, target.name)
            }
        )
    }
}

// ── Row composables ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: TripListItem.FolderItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        headlineContent = { Text(folder.folder.name) },
        leadingContent = {
            Icon(
                Icons.Default.Folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(
    trip: TripListItem.TripItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        headlineContent = { Text(trip.trip.name) },
        supportingContent = {
            if (trip.trip.sectionCount > 0) {
                Text("${trip.trip.sectionCount} section${if (trip.trip.sectionCount != 1) "s" else ""}")
            }
        },
        leadingContent = {
            Icon(
                Icons.Default.Map,
                contentDescription = "Trip",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    )
}

// ── Context Menu Dialog ──

@Composable
private fun ContextMenuDialog(
    target: ContextMenuTarget,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (() -> Unit)?,
    onMove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rename")
                    Spacer(Modifier.weight(1f))
                }
                onCopy?.let {
                    TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy")
                        Spacer(Modifier.weight(1f))
                    }
                }
                TextButton(onClick = onMove, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Move")
                    Spacer(Modifier.weight(1f))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                    Spacer(Modifier.weight(1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Helper data classes ──

private data class ContextMenuTarget(val id: String, val name: String, val isTrip: Boolean)
private data class RenameTarget(val id: String, val name: String, val isTrip: Boolean)
private data class DeleteTarget(val id: String, val name: String)
private data class CopyTarget(val id: String, val name: String)
private data class MoveTarget(val id: String, val name: String)
