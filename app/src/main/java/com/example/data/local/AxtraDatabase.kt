package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryEntity::class,
        RoutineEntity::class,
        ActionLogEntity::class,
        NoteEntity::class,
        ExpenseEntity::class,
        TodoItemEntity::class,
        BlockedAppEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AxtraDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun routineDao(): RoutineDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun noteDao(): NoteDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun todoDao(): TodoDao
    abstract fun blockedAppDao(): BlockedAppDao
}
