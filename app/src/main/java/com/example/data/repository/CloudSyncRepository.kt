package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)

    suspend fun upsertUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        preferredLanguage: String = "AUTO"
    ): Result<Unit> {
        val docRef = userDoc(userId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to displayName.ifBlank { "Axtra Owner" }.take(120),
                    "preferredLanguage" to preferredLanguage.take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) updates["email"] = email.take(256)
                if (!photoUrl.isNullOrBlank()) updates["photoUrl"] = photoUrl.take(600)
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "uid" to userId,
                    "displayName" to displayName.ifBlank { "Axtra Owner" }.take(120),
                    "preferredLanguage" to preferredLanguage.take(32),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) payload["email"] = email.take(256)
                if (!photoUrl.isNullOrBlank()) payload["photoUrl"] = photoUrl.take(600)
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Memories ---
    fun observeMemories(userId: String): Flow<List<CloudMemoryItem>> =
        userDoc(userId).collection("memories")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudMemoryItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun createMemory(userId: String, key: String, value: String, docId: String = newValidId("mem")): Result<String> {
        val docRef = userDoc(userId).collection("memories").document(docId)
        return try {
            val item = CloudMemoryItem(memoryKey = key, memoryValue = value)
            docRef.set(item.toCreateMap()).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, docId: String): Result<Unit> =
        deleteSubDoc(userId, "memories", docId)

    // --- Notes ---
    fun observeNotes(userId: String): Flow<List<CloudNoteItem>> =
        userDoc(userId).collection("notes")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudNoteItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun createNote(userId: String, title: String, content: String, docId: String = newValidId("note")): Result<String> {
        val docRef = userDoc(userId).collection("notes").document(docId)
        return try {
            val item = CloudNoteItem(title = title, content = content)
            docRef.set(item.toCreateMap()).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, docId: String): Result<Unit> =
        deleteSubDoc(userId, "notes", docId)

    // --- Expenses ---
    fun observeExpenses(userId: String): Flow<List<CloudExpenseItem>> =
        userDoc(userId).collection("expenses")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudExpenseItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun createExpense(
        userId: String,
        amount: Double,
        category: String,
        description: String,
        currency: String = "PKR",
        docId: String = newValidId("exp")
    ): Result<String> {
        val docRef = userDoc(userId).collection("expenses").document(docId)
        return try {
            val item = CloudExpenseItem(
                amount = amount,
                currency = currency,
                category = category,
                description = description
            )
            docRef.set(item.toCreateMap()).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(userId: String, docId: String): Result<Unit> =
        deleteSubDoc(userId, "expenses", docId)

    // --- Todos ---
    fun observeTodos(userId: String): Flow<List<CloudTodoItem>> =
        userDoc(userId).collection("todos")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudTodoItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun createTodo(
        userId: String,
        title: String,
        listType: String = "TODO",
        docId: String = newValidId("todo")
    ): Result<String> {
        val docRef = userDoc(userId).collection("todos").document(docId)
        return try {
            val item = CloudTodoItem(title = title, listType = listType, isDone = false)
            docRef.set(item.toCreateMap()).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTodoDone(userId: String, docId: String, isDone: Boolean): Result<Unit> {
        val docRef = userDoc(userId).collection("todos").document(docId)
        return try {
            docRef.update(
                mapOf(
                    "isDone" to isDone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTodo(userId: String, docId: String): Result<Unit> =
        deleteSubDoc(userId, "todos", docId)

    // --- Routines ---
    fun observeRoutines(userId: String): Flow<List<CloudRoutineItem>> =
        userDoc(userId).collection("routines")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudRoutineItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun createRoutine(
        userId: String,
        name: String,
        triggerType: String,
        triggerValue: String,
        steps: List<String>,
        isEnabled: Boolean = true,
        docId: String = newValidId("routine")
    ): Result<String> {
        val docRef = userDoc(userId).collection("routines").document(docId)
        return try {
            val item = CloudRoutineItem(
                name = name,
                triggerType = triggerType,
                triggerValue = triggerValue,
                steps = steps,
                isEnabled = isEnabled
            )
            docRef.set(item.toCreateMap()).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteRoutine(userId: String, docId: String): Result<Unit> =
        deleteSubDoc(userId, "routines", docId)

    private suspend fun deleteSubDoc(userId: String, collection: String, docId: String): Result<Unit> {
        val docRef = userDoc(userId).collection(collection).document(docId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    private fun newValidId(prefix: String): String {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(16)
        return "${prefix}_$suffix"
    }
}
