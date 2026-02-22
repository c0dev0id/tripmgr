package com.tripmgr.ui.triplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmgr.data.model.TripFolder
import com.tripmgr.data.model.TripListItem
import com.tripmgr.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripListUiState(
    val items: List<TripListItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFolderId: String? = null,
    val folderStack: List<FolderBreadcrumb> = emptyList(),
    val foldersForPicker: List<TripFolder> = emptyList()
)

data class FolderBreadcrumb(
    val folderId: String?,
    val name: String
)

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val repository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripListUiState())
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    fun loadItems(folderId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val items = repository.listItems(folderId)
                _uiState.value = _uiState.value.copy(
                    items = items,
                    isLoading = false,
                    currentFolderId = folderId
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load items"
                )
            }
        }
    }

    fun navigateToFolder(folder: TripFolder) {
        val stack = _uiState.value.folderStack + FolderBreadcrumb(
            folderId = _uiState.value.currentFolderId,
            name = if (_uiState.value.folderStack.isEmpty()) "Home" else folder.name
        )
        _uiState.value = _uiState.value.copy(folderStack = stack)
        loadItems(folder.driveFolderId)
    }

    fun navigateBack(): Boolean {
        val stack = _uiState.value.folderStack
        if (stack.isEmpty()) return false
        val parent = stack.last()
        _uiState.value = _uiState.value.copy(folderStack = stack.dropLast(1))
        loadItems(parent.folderId)
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        val stack = _uiState.value.folderStack
        if (index >= stack.size) return
        val target = stack[index]
        _uiState.value = _uiState.value.copy(folderStack = stack.take(index))
        loadItems(target.folderId)
    }

    fun createTrip(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.createTrip(name, _uiState.value.currentFolderId)
                loadItems(_uiState.value.currentFolderId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.createFolder(name, _uiState.value.currentFolderId)
                loadItems(_uiState.value.currentFolderId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun renameItem(itemId: String, newName: String, isTrip: Boolean) {
        viewModelScope.launch {
            try {
                repository.renameItem(itemId, newName, isTrip)
                loadItems(_uiState.value.currentFolderId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {
                repository.deleteItem(itemId)
                loadItems(_uiState.value.currentFolderId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun copyTrip(tripFolderId: String, newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.copyTrip(tripFolderId, newName, _uiState.value.currentFolderId)
                loadItems(_uiState.value.currentFolderId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun moveItem(itemId: String, destinationFolderId: String) {
        viewModelScope.launch {
            try {
                repository.moveItem(itemId, destinationFolderId)
                loadItems(_uiState.value.currentFolderId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun loadFoldersForPicker(parentId: String? = null) {
        viewModelScope.launch {
            try {
                val folders = repository.listFoldersOnly(parentId)
                _uiState.value = _uiState.value.copy(foldersForPicker = folders)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
