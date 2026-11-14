package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraThemeStyle
import com.example.core.ConfirmationLevel
import com.example.core.LanguageMode
import com.example.core.OverlayAnimationStyle
import com.example.data.local.ActionLogDao
import com.example.data.local.ActionLogEntity
import com.example.data.local.BlockedAppDao
import com.example.data.local.BlockedAppEntity
import com.example.data.local.ExpenseDao
import com.example.data.local.ExpenseEntity
import com.example.data.local.MemoryDao
import com.example.data.local.MemoryEntity
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.local.RoutineDao
import com.example.data.local.RoutineEntity
import com.example.data.local.TodoDao
import com.example.data.local.TodoItemEntity
import com.example.data.remote.CloudSyncRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryDao,
    private val cloudSyncRepository: CloudSyncRepository
) {
    val memories: Flow<List<MemoryEntity>> = dao.observeAll()

    suspend fun getAllOnce(): List<MemoryEntity> = dao.getAllOnce()

    suspend fun saveFact(key: String, value: String) {
        val cleanKey = key.trim()
        val cleanVal = value.trim()
        val rowId = dao.insert(MemoryEntity(memoryKey = cleanKey, memoryValue = cleanVal))
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.createMemory(uid, cleanKey, cleanVal, docId = "mem_local_$rowId")
        }
    }

    suspend fun updateMemory(entity: MemoryEntity) {
        dao.update(entity)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.createMemory(uid, entity.memoryKey, entity.memoryValue, docId = "mem_local_${entity.id}")
        }
    }

    suspend fun deleteMemory(id: Long) {
        dao.deleteById(id)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.deleteMemory(uid, "mem_local_$id")
        }
    }

    suspend fun clearAll() = dao.clearAll()
}

@Singleton
class RoutineRepository @Inject constructor(
    private val dao: RoutineDao,
    private val json: Json,
    private val cloudSyncRepository: CloudSyncRepository
) {
    val routines: Flow<List<RoutineEntity>> = dao.observeAll()

    suspend fun ensureBuiltInRoutines() {
        if (dao.count() == 0) {
            val builtIns = listOf(
                RoutineEntity(
                    name = "Good Morning / صبح بخیر",
                    triggerType = "VOICE",
                    triggerValue = "good morning",
                    stepsJson = json.encodeToString(
                        listOf(
                            "Speak daily briefing (weather, prayer times, calendar)",
                            "Set media volume to 65%",
                            "Read unread notifications summary"
                        )
                    ),
                    isEnabled = true,
                    isBuiltIn = true
                ),
                RoutineEntity(
                    name = "Good Night / شب بخیر",
                    triggerType = "VOICE",
                    triggerValue = "good night",
                    stepsJson = json.encodeToString(
                        listOf(
                            "Turn on Do Not Disturb",
                            "Set screen brightness to 15%",
                            "Set morning alarm for 6:00 AM"
                        )
                    ),
                    isEnabled = true,
                    isBuiltIn = true
                ),
                RoutineEntity(
                    name = "Driving Mode / ڈرائیونگ موڈ",
                    triggerType = "VOICE",
                    triggerValue = "driving mode",
                    stepsJson = json.encodeToString(
                        listOf(
                            "Read latest unread notifications",
                            "Open Google Maps navigation",
                            "Speak incoming caller & SMS alerts"
                        )
                    ),
                    isEnabled = true,
                    isBuiltIn = true
                ),
                RoutineEntity(
                    name = "Study Mode / اسٹڈی موڈ",
                    triggerType = "VOICE",
                    triggerValue = "study mode",
                    stepsJson = json.encodeToString(
                        listOf(
                            "Enable Focus Mode (block social apps for 45 min)",
                            "Turn on Do Not Disturb",
                            "Open Study Helper"
                        )
                    ),
                    isEnabled = true,
                    isBuiltIn = true
                )
            )
            dao.insertAll(builtIns)
        }
    }

    suspend fun saveRoutine(routine: RoutineEntity): Long {
        val id = dao.insert(routine)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank() && !routine.isBuiltIn) {
            val steps = runCatching { json.decodeFromString<List<String>>(routine.stepsJson) }.getOrDefault(emptyList())
            if (steps.isNotEmpty()) {
                cloudSyncRepository.createRoutine(
                    userId = uid,
                    name = routine.name,
                    triggerType = routine.triggerType,
                    triggerValue = routine.triggerValue,
                    steps = steps,
                    isEnabled = routine.isEnabled,
                    docId = "routine_local_$id"
                )
            }
        }
        return id
    }

    suspend fun updateRoutine(routine: RoutineEntity) = dao.update(routine)
    suspend fun deleteRoutine(id: Long) {
        dao.deleteById(id)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.deleteRoutine(uid, "routine_local_$id")
        }
    }
    suspend fun getAllOnce(): List<RoutineEntity> = dao.getAllOnce()
}

@Singleton
class ActionLogRepository @Inject constructor(
    private val dao: ActionLogDao,
    private val json: Json
) {
    val logs: Flow<List<ActionLogEntity>> = dao.observeAll()

    fun search(query: String): Flow<List<ActionLogEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query.trim())

    suspend fun logAction(
        command: String,
        intent: String,
        stepsSummary: String,
        resultText: String,
        success: Boolean
    ) {
        dao.insert(
            ActionLogEntity(
                commandText = command,
                intent = intent,
                stepsSummary = stepsSummary,
                resultText = resultText,
                success = success
            )
        )
    }

    suspend fun applyRetentionPolicy(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        dao.deleteOlderThan(cutoff)
    }

    suspend fun exportLogsJson(): String {
        val list = dao.getAllOnce()
        return json.encodeToString(list)
    }

    suspend fun clearAll() = dao.clearAll()
}

@Singleton
class ToolsRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val expenseDao: ExpenseDao,
    private val todoDao: TodoDao,
    private val cloudSyncRepository: CloudSyncRepository
) {
    val notes: Flow<List<NoteEntity>> = noteDao.observeAll()
    fun searchNotes(q: String): Flow<List<NoteEntity>> =
        if (q.isBlank()) noteDao.observeAll() else noteDao.search(q.trim())

    suspend fun saveNote(title: String, content: String): Long {
        val resolvedTitle = title.ifBlank { "Voice Note" }
        val id = noteDao.insert(NoteEntity(title = resolvedTitle, content = content))
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.createNote(uid, resolvedTitle, content, docId = "note_local_$id")
        }
        return id
    }

    suspend fun updateNote(note: NoteEntity) =
        noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteNote(id: Long) {
        noteDao.deleteById(id)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.deleteNote(uid, "note_local_$id")
        }
    }

    val expenses: Flow<List<ExpenseEntity>> = expenseDao.observeAll()
    fun observeCurrentMonthTotal(): Flow<Double> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return expenseDao.observeMonthlyTotal(cal.timeInMillis)
    }

    suspend fun addExpense(amount: Double, category: String, description: String) {
        val resolvedCat = category.ifBlank { "General" }
        val id = expenseDao.insert(
            ExpenseEntity(
                amount = amount,
                category = resolvedCat,
                description = description
            )
        )
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank() && amount > 0) {
            cloudSyncRepository.createExpense(uid, amount, resolvedCat, description, docId = "exp_local_$id")
        }
    }

    suspend fun deleteExpense(id: Long) {
        expenseDao.deleteById(id)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.deleteExpense(uid, "exp_local_$id")
        }
    }

    val todos: Flow<List<TodoItemEntity>> = todoDao.observeAll()
    suspend fun addTodo(title: String, listType: String = "TODO") {
        val id = todoDao.insert(TodoItemEntity(title = title, listType = listType))
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.createTodo(uid, title, listType, docId = "todo_local_$id")
        }
    }

    suspend fun toggleTodo(item: TodoItemEntity) {
        val nextDone = !item.isDone
        todoDao.update(item.copy(isDone = nextDone))
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.updateTodoDone(uid, "todo_local_${item.id}", nextDone)
        }
    }

    suspend fun deleteTodo(id: Long) {
        todoDao.deleteById(id)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            cloudSyncRepository.deleteTodo(uid, "todo_local_$id")
        }
    }
}

@Singleton
class BlockedAppsRepository @Inject constructor(
    private val dao: BlockedAppDao
) {
    val blockedApps: Flow<List<BlockedAppEntity>> = dao.observeAll()

    suspend fun ensureDefaults() {
        if (dao.count() == 0) {
            val defaults = AxtraConstants.DEFAULT_BLOCKED_PACKAGES.map { pkg ->
                BlockedAppEntity(
                    packageName = pkg,
                    appLabel = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                )
            }
            dao.insertAll(defaults)
        }
    }

    suspend fun addBlockedApp(packageName: String, label: String) {
        dao.insert(BlockedAppEntity(packageName = packageName, appLabel = label))
    }

    suspend fun removeBlockedApp(entity: BlockedAppEntity) {
        dao.delete(entity)
    }

    suspend fun isPackageBlocked(packageName: String): Boolean {
        return dao.getAllOnce().any { it.packageName.equals(packageName, ignoreCase = true) }
    }
}

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cachedApps: List<InstalledAppInfo> = emptyList()

    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            if (cachedApps.isNotEmpty() && !forceRefresh) return@withContext cachedApps
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            val list = resolveInfos.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) null else InstalledAppInfo(pkg, label)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            cachedApps = list
            list
        }
}

@Serializable
data class AxtraBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val geminiModel: String,
    val temperature: Float,
    val languageMode: String,
    val themeStyle: String,
    val overlayStyle: String,
    val confirmationLevel: String,
    val memories: List<MemoryEntity>,
    val routines: List<RoutineEntity>,
    val notes: List<NoteEntity>,
    val expenses: List<ExpenseEntity>,
    val todos: List<TodoItemEntity>,
    val blockedApps: List<BlockedAppEntity>
)

@Singleton
class BackupRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val memoryDao: MemoryDao,
    private val routineDao: RoutineDao,
    private val noteDao: NoteDao,
    private val expenseDao: ExpenseDao,
    private val todoDao: TodoDao,
    private val blockedAppDao: BlockedAppDao,
    private val actionLogDao: ActionLogDao,
    private val json: Json
) {
    suspend fun exportBackupJson(): String {
        val settings = appPreferences.settingsFlow.first()
        val payload = AxtraBackupPayload(
            geminiModel = settings.geminiModel,
            temperature = settings.temperature,
            languageMode = settings.languageMode.name,
            themeStyle = settings.themeStyle.name,
            overlayStyle = settings.overlayAnimationStyle.name,
            confirmationLevel = settings.confirmationLevel.name,
            memories = memoryDao.getAllOnce(),
            routines = routineDao.getAllOnce(),
            notes = noteDao.getAllOnce(),
            expenses = expenseDao.getAllOnce(),
            todos = todoDao.getAllOnce(),
            blockedApps = blockedAppDao.getAllOnce()
        )
        return json.encodeToString(payload)
    }

    suspend fun restoreFromBackupJson(rawJson: String): Result<Int> = runCatching {
        val payload = json.decodeFromString<AxtraBackupPayload>(rawJson)
        appPreferences.updateGeminiModel(payload.geminiModel)
        appPreferences.updateTemperature(payload.temperature)
        runCatching { LanguageMode.valueOf(payload.languageMode) }
            .getOrNull()?.let { appPreferences.updateLanguageMode(it) }
        runCatching { AxtraThemeStyle.valueOf(payload.themeStyle) }
            .getOrNull()?.let { appPreferences.updateThemeStyle(it) }
        runCatching { OverlayAnimationStyle.valueOf(payload.overlayStyle) }
            .getOrNull()?.let { appPreferences.updateOverlayStyle(it) }
        runCatching { ConfirmationLevel.valueOf(payload.confirmationLevel) }
            .getOrNull()?.let { appPreferences.updateConfirmationLevel(it) }

        payload.memories.forEach { memoryDao.insert(it.copy(id = 0)) }
        payload.routines.filter { !it.isBuiltIn }.forEach { routineDao.insert(it.copy(id = 0)) }
        payload.notes.forEach { noteDao.insert(it.copy(id = 0)) }
        payload.expenses.forEach { expenseDao.insert(it.copy(id = 0)) }
        payload.todos.forEach { todoDao.insert(it.copy(id = 0)) }
        blockedAppDao.insertAll(payload.blockedApps)

        payload.memories.size + payload.routines.size + payload.notes.size + payload.expenses.size + payload.todos.size
    }

    suspend fun deleteAllUserData() {
        memoryDao.clearAll()
        routineDao.clearAll()
        actionLogDao.clearAll()
        noteDao.clearAll()
        expenseDao.clearAll()
        todoDao.clearAll()
        blockedAppDao.clearAll()
        appPreferences.clearAll()
    }
}
