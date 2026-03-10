package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.Employment
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun getEmployments(uid: String): Flow<List<Employment>>
    suspend fun getEmploymentsSnapshot(uid: String): Result<List<Employment>>
    suspend fun addEmployment(uid: String, employment: Employment): Result<Unit>
    suspend fun deleteEmployment(uid: String, employmentId: String): Result<Unit>
    suspend fun getEmployment(uid: String, employmentId: String): Result<Employment?>
}
