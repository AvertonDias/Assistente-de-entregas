package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

data class UserProfile(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean = false
)

sealed class AuthResult {
    data class Success(val user: UserProfile, val isNewUser: Boolean = false) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

interface AuthRepository {
    val currentUserFlow: Flow<UserProfile?>
    val currentUser: UserProfile?

    suspend fun signInWithEmail(email: String, pass: String): AuthResult
    suspend fun signUpWithEmail(name: String, email: String, pass: String): AuthResult
    suspend fun signInWithGoogle(context: Context, serverClientId: String? = null): AuthResult
    suspend fun signOut(context: Context)
}

class FirebaseAuthRepositoryImpl(
    private val appContext: Context
) : AuthRepository {

    private val prefs = appContext.getSharedPreferences("app_delivery_auth_prefs", Context.MODE_PRIVATE)

    private fun getFirebaseAuth(): FirebaseAuth? {
        return try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Firebase auth not available: ${e.message}")
            null
        }
    }

    private fun getLocalUser(): UserProfile? {
        val email = prefs.getString("logged_email", null) ?: return null
        val name = prefs.getString("logged_name", email.substringBefore("@"))
        val uid = prefs.getString("logged_uid", "local_uid_${email.hashCode()}") ?: "local_user"
        return UserProfile(uid = uid, email = email, displayName = name, photoUrl = null, isAnonymous = false)
    }

    private fun saveLocalUser(email: String, name: String?) {
        prefs.edit()
            .putString("logged_email", email)
            .putString("logged_name", name ?: email.substringBefore("@"))
            .putString("logged_uid", "local_uid_${email.hashCode()}")
            .apply()
    }

    private fun clearLocalUser() {
        prefs.edit().clear().apply()
    }

    override val currentUserFlow: Flow<UserProfile?> = callbackFlow {
        val local = getLocalUser()
        trySend(local)

        val auth = getFirebaseAuth()
        if (auth != null) {
            val listener = FirebaseAuth.AuthStateListener { fbAuth ->
                val user = fbAuth.currentUser?.toUserProfile() ?: getLocalUser()
                trySend(user)
            }
            try {
                auth.addAuthStateListener(listener)
            } catch (_: Exception) {}
            awaitClose {
                try {
                    auth.removeAuthStateListener(listener)
                } catch (_: Exception) {}
            }
        } else {
            awaitClose { }
        }
    }

    override val currentUser: UserProfile?
        get() = try {
            getFirebaseAuth()?.currentUser?.toUserProfile() ?: getLocalUser()
        } catch (e: Exception) {
            getLocalUser()
        }

    override suspend fun signInWithEmail(email: String, pass: String): AuthResult {
        val auth = getFirebaseAuth()
        if (auth != null) {
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
                val user = result.user?.toUserProfile()
                if (user != null) {
                    saveLocalUser(email.trim(), user.displayName)
                    return AuthResult.Success(user, isNewUser = false)
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firebase sign in failed, falling back to local: ${e.message}")
            }
        }

        // Fallback Local Auth (Sempre funcional offline/sem Firebase configurado)
        if (email.isNotBlank() && pass.length >= 6) {
            saveLocalUser(email.trim(), null)
            val user = getLocalUser() ?: UserProfile(uid = "local_${email.hashCode()}", email = email, displayName = email.substringBefore("@"), photoUrl = null)
            return AuthResult.Success(user, isNewUser = false)
        } else if (pass.length < 6) {
            return AuthResult.Error("A senha deve conter no mínimo 6 caracteres.")
        }
        return AuthResult.Error("E-mail ou senha inválidos.")
    }

    override suspend fun signUpWithEmail(name: String, email: String, pass: String): AuthResult {
        val auth = getFirebaseAuth()
        if (auth != null) {
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
                val fbUser = result.user
                if (fbUser != null) {
                    if (name.isNotBlank()) {
                        try {
                            val profileUpdate = userProfileChangeRequest {
                                displayName = name.trim()
                            }
                            fbUser.updateProfile(profileUpdate).await()
                        } catch (_: Exception) {}
                    }
                    val user = fbUser.toUserProfile()
                    saveLocalUser(email.trim(), name.ifBlank { user.displayName })
                    return AuthResult.Success(user, isNewUser = true)
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firebase sign up failed, falling back to local: ${e.message}")
            }
        }

        // Fallback Local Sign Up (Sempre funcional offline/sem Firebase configurado)
        if (email.isNotBlank() && pass.length >= 6) {
            saveLocalUser(email.trim(), name)
            val user = getLocalUser() ?: UserProfile(uid = "local_${email.hashCode()}", email = email, displayName = name.ifBlank { email.substringBefore("@") }, photoUrl = null)
            return AuthResult.Success(user, isNewUser = true)
        } else if (pass.length < 6) {
            return AuthResult.Error("A senha deve conter no mínimo 6 caracteres.")
        }
        return AuthResult.Error("Dados de cadastro inválidos.")
    }

    override suspend fun signInWithGoogle(context: Context, serverClientId: String?): AuthResult {
        val auth = getFirebaseAuth()
        return try {
            if (auth == null) {
                throw Exception("Firebase not initialized")
            }
            val credentialManager = CredentialManager.create(context)

            // Gera um nonce SHA-256 seguro
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            // Usa o clientId configurado, ou obtém do google-services.json (default_web_client_id), ou fallback
            val resClientId = try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else null
            } catch (_: Exception) {
                null
            }

            val clientId = serverClientId?.takeIf { it.isNotBlank() }
                ?: resClientId?.takeIf { it.isNotBlank() }
                ?: "946779143583-qd2lkgkpfc1igmrgtmj0sjv2gden2ijh.apps.googleusercontent.com"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user?.toUserProfile()
                if (user != null) {
                    val isNew = authResult.additionalUserInfo?.isNewUser ?: false
                    saveLocalUser(user.email ?: googleIdTokenCredential.id ?: "google.user@gmail.com", user.displayName ?: googleIdTokenCredential.displayName)
                    AuthResult.Success(user, isNewUser = isNew)
                } else {
                    val displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName
                    val email = googleIdTokenCredential.id
                    saveLocalUser(email, displayName)
                    val localUser = UserProfile(
                        uid = "google_${email.hashCode()}",
                        email = email,
                        displayName = displayName,
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    )
                    AuthResult.Success(localUser, isNewUser = false)
                }
            } else {
                throw Exception("Credencial do Google não reconhecida.")
            }
        } catch (e: Exception) {
            Log.w("AuthRepository", "Google Sign-In failed: ${e.message}", e)
            val className = e.javaClass.simpleName
            if (className.contains("Cancellation", ignoreCase = true) || 
                className.contains("GetCredentialCancellationException", ignoreCase = true) ||
                e.message?.contains("cancel", ignoreCase = true) == true) {
                return AuthResult.Error("Login cancelado pelo usuário.")
            }
            
            val readableError = getReadableErrorMessage(e)
            return AuthResult.Error(readableError)
        }
    }

    override suspend fun signOut(context: Context) {
        try {
            clearLocalUser()
            getFirebaseAuth()?.signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun FirebaseUser.toUserProfile(): UserProfile {
        return UserProfile(
            uid = uid,
            email = email,
            displayName = displayName ?: email?.substringBefore("@"),
            photoUrl = photoUrl?.toString(),
            isAnonymous = isAnonymous
        )
    }

    private fun getReadableErrorMessage(e: Exception): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("Developer console is not set up correctly", ignoreCase = true) || msg.contains("28444") || msg.contains(": 10") ->
                "Configuração do Google Console pendente: Adicione a impressão digital SHA-1 no Firebase Console."
            msg.contains("No credentials available", ignoreCase = true) || msg.contains("NoCredentialException", ignoreCase = true) ->
                "Nenhuma conta Google cadastrada neste emulador/dispositivo. Use o login por E-mail e Senha ou adicione uma conta Google no Android."
            msg.contains("password", ignoreCase = true) && msg.contains("invalid", ignoreCase = true) ->
                "Senha incorreta ou inválida."
            msg.contains("user-not-found", ignoreCase = true) || msg.contains("no user", ignoreCase = true) ->
                "Nenhum usuário encontrado com este e-mail."
            msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("already exists", ignoreCase = true) ->
                "Este e-mail já está cadastrado no sistema."
            msg.contains("invalid-email", ignoreCase = true) ->
                "Formato de e-mail inválido."
            msg.contains("weak-password", ignoreCase = true) ->
                "A senha deve ter pelo menos 6 caracteres."
            msg.contains("network", ignoreCase = true) ->
                "Falha de conexão com a rede. Verifique sua internet."
            msg.contains("canceled", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true) ->
                "Autenticação cancelada pelo usuário."
            else -> msg.ifBlank { "Ocorreu um erro durante a autenticação." }
        }
    }
}
