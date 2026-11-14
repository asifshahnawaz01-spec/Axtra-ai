package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed class GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome()
    data class Cancelled(val message: String) : GoogleSignInOutcome()
    data class NoSavedAccounts(val message: String) : GoogleSignInOutcome()
    data class Error(val message: String) : GoogleSignInOutcome()
}

@Singleton
class GoogleAuthHelper @Inject constructor() {

    /**
     * Explicit interactive "Sign in with Google" flow using Jetpack Credential Manager.
     */
    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInOutcome {
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleSignInOutcome.Error("Missing default_web_client_id in Firebase config.")
        }

        val credentialManager = CredentialManager.create(activityContext)
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            handleCredential(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in cancelled by user", e)
            GoogleSignInOutcome.Cancelled("سائن ان منسوخ کر دیا گیا۔")
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available", e)
            GoogleSignInOutcome.NoSavedAccounts("فون میں کوئی Google اکاؤنٹ دستیاب نہیں ہے۔")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Credential Manager sign-in failed", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }

    /**
     * Optional one-tap silent sign-in for previously authorized accounts.
     */
    suspend fun tryAutoSignIn(activityContext: Context): GoogleSignInOutcome {
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) return GoogleSignInOutcome.NoSavedAccounts("Missing web client ID")

        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            handleCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GoogleSignInOutcome.NoSavedAccounts(e.localizedMessage ?: "No authorized account")
        }
    }

    private suspend fun handleCredential(credential: androidx.credentials.Credential): GoogleSignInOutcome {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdCred.idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
            val user = authResult.user
            return if (user != null) {
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Error("Firebase returned a null user.")
            }
        }
        return GoogleSignInOutcome.Error("Unsupported credential type returned.")
    }

    suspend fun signOut(context: Context) {
        Firebase.auth.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private companion object {
        const val TAG = "GoogleAuthHelper"
    }
}
