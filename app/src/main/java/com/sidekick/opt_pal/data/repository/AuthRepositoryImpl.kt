package com.sidekick.opt_pal.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.CompleteSetupRequest
import com.sidekick.opt_pal.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class AuthRepositoryImpl : AuthRepository {
    private val auth: FirebaseAuth = Firebase.auth
    private val firestore = Firebase.firestore
    private val usersCollection = firestore.collection("users")

    override fun getAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun getUserProfile(uid: String): Flow<UserProfile?> =
        usersCollection.document(uid).snapshots().map { snapshot ->
            snapshot.toUserProfile()
        }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        val sanitizedEmail = email.trim()
        val sanitizedPassword = password.trim()
        return runCatching {
            val result = auth.signInWithEmailAndPassword(sanitizedEmail, sanitizedPassword).await()
            val firebaseUser = result.user ?: error("User not available after sign-in")
            ensureUserDocument(firebaseUser)
        }
    }

    override suspend fun register(email: String, password: String): Result<Unit> {
        val sanitizedEmail = email.trim()
        val sanitizedPassword = password.trim()
        return runCatching {
            val result = auth.createUserWithEmailAndPassword(sanitizedEmail, sanitizedPassword).await()
            val firebaseUser = result.user ?: error("User not available after registration")
            val profile = hashMapOf<String, Any>(
                "uid" to firebaseUser.uid,
                "email" to sanitizedEmail
            )
            usersCollection.document(firebaseUser.uid).set(profile).await()
            ensureUserDocument(firebaseUser)
        }
    }

    override suspend fun completeSetup(request: CompleteSetupRequest): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("No authenticated user"))
        val uid = currentUser.uid
        val payload = mutableMapOf<String, Any>(
            "uid" to uid,
            "optType" to request.optType,
            "optStartDate" to Timestamp(Date(request.optStartDate)),
            "onboardingSource" to request.onboardingSource,
            "onboardingDocumentIds" to request.onboardingDocumentIds,
            "onboardingConfirmedAt" to request.onboardingConfirmedAt
        )
        request.optEndDate?.let { payload["optEndDate"] = Timestamp(Date(it)) }
        request.sevisId?.takeIf { it.isNotBlank() }?.let { payload["sevisId"] = it }
        request.schoolName?.takeIf { it.isNotBlank() }?.let { payload["schoolName"] = it }
        request.cipCode?.takeIf { it.isNotBlank() }?.let { payload["cipCode"] = it }
        currentUser.email?.let { payload["email"] = it }

        return runCatching {
            usersCollection.document(uid).set(payload, SetOptions.merge()).await()
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private suspend fun ensureUserDocument(user: FirebaseUser) {
        val docRef = usersCollection.document(user.uid)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            val profile = hashMapOf<String, Any>(
                "uid" to user.uid,
                "email" to user.email.orEmpty()
            )
            docRef.set(profile).await()
            return
        }
        val updates = mutableMapOf<String, Any>()
        val existing = snapshot.toUserProfile()
        if (existing?.uid.isNullOrBlank()) {
            updates["uid"] = user.uid
        }
        val email = user.email
        if (!email.isNullOrBlank() && existing?.email.isNullOrBlank()) {
            updates["email"] = email
        }
        if (updates.isNotEmpty()) {
            docRef.set(updates, SetOptions.merge()).await()
        }
    }
}

private fun DocumentSnapshot.toUserProfile(): UserProfile? {
    if (!exists()) return null
    val onboardingDocumentIds = (get("onboardingDocumentIds") as? List<*>)
        ?.mapNotNull { it as? String }
        .orEmpty()
    return UserProfile(
        uid = getString("uid").orEmpty(),
        email = getString("email").orEmpty(),
        optType = getString("optType"),
        optStartDate = getDateField("optStartDate"),
        optEndDate = getDateField("optEndDate"),
        sevisId = getString("sevisId"),
        schoolName = getString("schoolName"),
        cipCode = getString("cipCode"),
        onboardingSource = getString("onboardingSource"),
        onboardingDocumentIds = onboardingDocumentIds,
        onboardingConfirmedAt = getDateField("onboardingConfirmedAt")
    )
}

private fun DocumentSnapshot.getDateField(fieldName: String): Long? {
    val rawValue = get(fieldName)
    return when (rawValue) {
        is Timestamp -> rawValue.toDate().time
        is Number -> rawValue.toLong()
        else -> null
    }
}
