package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.actions.ActionExecutor
import com.example.ai.AiBrainRepository
import com.example.data.local.RoutineEntity
import com.example.data.repository.RoutineRepository
import com.example.voice.AxtraTtsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val aiBrainRepository: AiBrainRepository,
    private val actionExecutor: ActionExecutor,
    private val ttsEngine: AxtraTtsEngine,
    private val json: Json
) : ViewModel() {

    val routines: StateFlow<List<RoutineEntity>> = routineRepository.routines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    init {
        viewModelScope.launch { routineRepository.ensureBuiltInRoutines() }
    }

    fun decodeSteps(stepsJson: String): List<String> {
        return runCatching { json.decodeFromString<List<String>>(stepsJson) }.getOrDefault(emptyList())
    }

    fun saveCustomRoutine(
        existingId: Long = 0,
        name: String,
        triggerType: String,
        triggerValue: String,
        steps: List<String>
    ) {
        if (name.isBlank() || steps.isEmpty()) return
        viewModelScope.launch {
            routineRepository.saveRoutine(
                RoutineEntity(
                    id = existingId,
                    name = name.trim(),
                    triggerType = triggerType,
                    triggerValue = triggerValue.trim(),
                    stepsJson = json.encodeToString(steps),
                    isEnabled = true,
                    isBuiltIn = false
                )
            )
        }
    }

    fun toggleRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            routineRepository.updateRoutine(routine.copy(isEnabled = !routine.isEnabled))
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch {
            routineRepository.deleteRoutine(id)
        }
    }

    fun runRoutine(routine: RoutineEntity) {
        val steps = decodeSteps(routine.stepsJson)
        if (steps.isEmpty()) return
        viewModelScope.launch {
            _testStatus.value = "Running routine: ${routine.name}..."
            for (step in steps) {
                val action = aiBrainRepository.processCommand(step)
                actionExecutor.executeAction(step, action) { reply, _ ->
                    _testStatus.value = "${routine.name}: $reply"
                }
            }
            ttsEngine.speak("${routine.name} روٹین مکمل ہو گئی ہے۔")
        }
    }

    fun clearTestStatus() {
        _testStatus.value = null
    }
}
