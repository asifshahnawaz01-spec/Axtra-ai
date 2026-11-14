package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.CloudSyncRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertProfileAndCreateMemory_authenticatedOwner_succeedsAndStreams() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(
                userId = uid,
                displayName = "Asif",
                email = ALICE_EMAIL,
                photoUrl = null,
                preferredLanguage = "AUTO"
            )
        }
        assertTrue(profileRes.isSuccess)

        val memId = "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val memRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createMemory(uid, "City", "Multan", memId)
        }
        assertTrue(memRes.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMemories(uid).first { list -> list.any { it.id == memId } }
        }
        assertTrue(observed.any { it.id == memId && it.memoryValue == "Multan" })
    }

    @Test
    fun createNoteExpenseTodoRoutine_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore)
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)

        val noteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createNote(uid, "Reminder", "Buy books", "note_$suffix")
        }
        assertTrue(noteRes.isSuccess)

        val expRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createExpense(uid, 450.0, "Food", "Dinner", "PKR", "exp_$suffix")
        }
        assertTrue(expRes.isSuccess)

        val todoId = "todo_$suffix"
        val todoRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createTodo(uid, "Pay bill", "TODO", todoId)
        }
        assertTrue(todoRes.isSuccess)

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateTodoDone(uid, todoId, true)
        }
        assertTrue(updateRes.isSuccess)

        val routineRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createRoutine(uid, "Focus", "VOICE", "focus mode", listOf("Turn on DND"), true, "routine_$suffix")
        }
        assertTrue(routineRes.isSuccess)
    }

    @Test
    fun observeMemories_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore)
        val memId = "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createMemory(aliceUid, "Secret", "Data", memId)
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore)
        var capturedError: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeMemories(aliceUid).first()
            }
        } catch (e: Exception) {
            capturedError = e
        }
        val firestoreEx = checkNotNull(capturedError?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for cross-user read, got $capturedError"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun observeNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore)
        var capturedError: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeNotes("some_user_id").first()
            }
        } catch (e: Exception) {
            capturedError = e
        }
        val firestoreEx = checkNotNull(capturedError?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for unauthenticated read, got $capturedError"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@axtra.test"
        const val BOB_EMAIL = "bob@axtra.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
