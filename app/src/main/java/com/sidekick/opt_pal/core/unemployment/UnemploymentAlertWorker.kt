package com.sidekick.opt_pal.core.unemployment

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sidekick.opt_pal.core.security.SecurityManager

class UnemploymentAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.success()
        val store = UnemploymentAlertStore(SecurityManager(applicationContext))
        val snapshot = store.getSnapshot(uid) ?: return Result.success()
        evaluateUnemploymentAlertUpdate(
            uid = uid,
            snapshot = snapshot,
            store = store,
            notifier = UnemploymentAlertNotifier(applicationContext),
            now = System.currentTimeMillis()
        )
        return Result.success()
    }
}
