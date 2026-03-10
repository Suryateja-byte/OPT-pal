package com.sidekick.opt_pal.data.repository

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.CompleteSetupRequest
import com.sidekick.opt_pal.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getAuthState(): Flow<FirebaseUser?>
    fun getUserProfile(uid: String): Flow<UserProfile?>
    suspend fun getUserProfileSnapshot(uid: String): Result<UserProfile?>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun register(email: String, password: String): Result<Unit>
    suspend fun completeSetup(request: CompleteSetupRequest): Result<Unit>
    suspend fun updateUnemploymentTrackingStartDate(dateMillis: Long): Result<Unit>
    suspend fun updateMajorName(majorName: String): Result<Unit>
    suspend fun updateFirstUsStudentTaxYear(taxYear: Int): Result<Unit>
    suspend fun signOut()
}
