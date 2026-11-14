package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppPreferences
import com.example.data.local.ActionLogEntity
import com.example.data.local.MemoryEntity
import com.example.data.repository.ActionLogRepository
import com.example.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val actionLogRepository: ActionLogRepository,
    private val memoryRepository: MemoryRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val logs: StateFlow<List<ActionLogEntity>> = _searchQuery
        .flatMapLatest { q -> actionLogRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = memoryRepository.memories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val days = appPreferences.settingsFlow.first().logRetentionDays
            actionLogRepository.applyRetentionPolicy(days)
        }
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    suspend fun exportLogsJson(): String = actionLogRepository.exportLogsJson()

    fun clearLogs() = viewModelScope.launch { actionLogRepository.clearAll() }

    fun addMemory(key: String, value: String) = viewModelScope.launch {
        if (key.isNotBlank() && value.isNotBlank()) {
            memoryRepository.saveFact(key, value)
        }
    }

    fun updateMemory(entity: MemoryEntity) = viewModelScope.launch {
        memoryRepository.updateMemory(entity)
    }

    fun deleteMemory(id: Long) = viewModelScope.launch {
        memoryRepository.deleteMemory(id)
    }
}
