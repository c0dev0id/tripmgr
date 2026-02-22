package com.tripmgr.ui.tripdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmgr.data.model.Section
import com.tripmgr.data.model.TripFileRef
import com.tripmgr.data.model.TripMetadata
import com.tripmgr.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

data class TripDetailUiState(
    val tripFolderId: String = "",
    val metadata: TripMetadata? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val expandedSections: Set<String> = emptySet()
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val repository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    fun loadTrip(tripFolderId: String) {
        _uiState.value = _uiState.value.copy(tripFolderId = tripFolderId, isLoading = true)
        viewModelScope.launch {
            try {
                val metadata = repository.getTripDetail(tripFolderId)
                _uiState.value = _uiState.value.copy(
                    metadata = metadata,
                    isLoading = false,
                    expandedSections = metadata?.sections?.map { it.id }?.toSet() ?: emptySet()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun addSection(label: String) {
        viewModelScope.launch {
            try {
                val section = repository.addSection(_uiState.value.tripFolderId, label)
                _uiState.value = _uiState.value.copy(
                    expandedSections = _uiState.value.expandedSections + section.id
                )
                reload()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun renameSection(sectionId: String, newLabel: String) {
        viewModelScope.launch {
            try {
                repository.renameSection(_uiState.value.tripFolderId, sectionId, newLabel)
                reload()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteSection(sectionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteSection(_uiState.value.tripFolderId, sectionId)
                reload()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun addFile(sectionId: String, fileName: String, mimeType: String, inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.addFileToSection(
                    _uiState.value.tripFolderId,
                    sectionId,
                    fileName,
                    mimeType,
                    inputStream
                )
                reload()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun removeFile(sectionId: String, driveFileId: String) {
        viewModelScope.launch {
            try {
                repository.removeFileFromSection(
                    _uiState.value.tripFolderId,
                    sectionId,
                    driveFileId
                )
                reload()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun toggleSectionExpanded(sectionId: String) {
        val expanded = _uiState.value.expandedSections.toMutableSet()
        if (sectionId in expanded) expanded.remove(sectionId) else expanded.add(sectionId)
        _uiState.value = _uiState.value.copy(expandedSections = expanded)
    }

    fun updateDescription(description: String) {
        viewModelScope.launch {
            val metadata = _uiState.value.metadata ?: return@launch
            try {
                repository.updateTripMetadata(
                    _uiState.value.tripFolderId,
                    metadata.copy(description = description)
                )
                reload()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun reload() {
        loadTrip(_uiState.value.tripFolderId)
    }
}
