package com.sidekick.opt_pal.core.unemployment

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sidekick.opt_pal.R
import com.sidekick.opt_pal.core.calculations.UnemploymentAlertThreshold
import com.sidekick.opt_pal.core.calculations.UnemploymentDataQualityState
import com.sidekick.opt_pal.core.calculations.UnemploymentForecast
import com.sidekick.opt_pal.core.calculations.allowedUnemploymentDays
import com.sidekick.opt_pal.core.calculations.calculateUnemploymentForecast
import com.sidekick.opt_pal.core.calculations.clearThresholdsAboveCurrent
import com.sidekick.opt_pal.core.calculations.highestNewThresholdToNotify
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.core.security.SecurityManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager

private const val SNAPSHOT_PREFIX = "unemployment_snapshot_"
private const val FIRED_PREFIX = "unemployment_fired_"
private const val CHANNEL_ID = "opt_status_alerts"
private const val CHANNEL_NAME = "OPT status alerts"
private const val PERIODIC_WORK_PREFIX = "opt_unemployment_periodic_"
private const val IMMEDIATE_WORK_PREFIX = "opt_unemployment_immediate_"

data class UnemploymentAlertSnapshot(
    val uid: String,
    val optType: String?,
    val optStartDate: Long?,
    val unemploymentTrackingStartDate: Long?,
    val optEndDate: Long?,
    val employments: List<Employment>
)

class UnemploymentAlertStore(
    securityManager: SecurityManager,
    private val gson: Gson = Gson()
) {
    private val prefs = securityManager.encryptedPrefs
    private val thresholdListType = object : TypeToken<List<String>>() {}.type

    fun saveSnapshot(snapshot: UnemploymentAlertSnapshot) {
        prefs.edit()
            .putString(snapshotKey(snapshot.uid), gson.toJson(snapshot))
            .apply()
    }

    fun getSnapshot(uid: String): UnemploymentAlertSnapshot? {
        val raw = prefs.getString(snapshotKey(uid), null) ?: return null
        return runCatching { gson.fromJson(raw, UnemploymentAlertSnapshot::class.java) }.getOrNull()
    }

    fun clearSnapshot(uid: String) {
        prefs.edit().remove(snapshotKey(uid)).apply()
    }

    fun getFiredThresholds(cycleKey: String): Set<UnemploymentAlertThreshold> {
        val raw = prefs.getString(firedKey(cycleKey), null) ?: return emptySet()
        val stored = runCatching { gson.fromJson<List<String>>(raw, thresholdListType) }.getOrNull().orEmpty()
        return stored.mapNotNull { value ->
            runCatching { UnemploymentAlertThreshold.valueOf(value) }.getOrNull()
        }.toSet()
    }

    fun saveFiredThresholds(cycleKey: String, thresholds: Set<UnemploymentAlertThreshold>) {
        prefs.edit()
            .putString(firedKey(cycleKey), gson.toJson(thresholds.map { it.name }))
            .apply()
    }

    fun clearFiredThresholdsForUser(uid: String) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("$FIRED_PREFIX$uid") }
            .forEach(editor::remove)
        editor.apply()
    }

    fun cycleKey(uid: String, trackingStartDate: Long, allowedDays: Int): String {
        return "${uid}_${trackingStartDate}_$allowedDays"
    }

    private fun snapshotKey(uid: String) = "$SNAPSHOT_PREFIX$uid"
    private fun firedKey(cycleKey: String) = "$FIRED_PREFIX$cycleKey"
}

class UnemploymentAlertScheduler(
    private val context: Context,
    private val store: UnemploymentAlertStore
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun initialize() {
        createNotificationChannel()
    }

    fun syncSnapshot(snapshot: UnemploymentAlertSnapshot) {
        store.saveSnapshot(snapshot)
        schedulePeriodic(snapshot.uid)
        enqueueImmediate(snapshot.uid)
    }

    fun enqueueImmediate(uid: String) {
        val request = OneTimeWorkRequestBuilder<UnemploymentAlertWorker>()
            .setInputData(workDataOf("uid" to uid))
            .build()
        workManager.enqueueUniqueWork(
            immediateWorkName(uid),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelForUser(uid: String) {
        workManager.cancelUniqueWork(periodicWorkName(uid))
        workManager.cancelUniqueWork(immediateWorkName(uid))
        store.clearSnapshot(uid)
        store.clearFiredThresholdsForUser(uid)
    }

    private fun schedulePeriodic(uid: String) {
        val request = PeriodicWorkRequestBuilder<UnemploymentAlertWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf("uid" to uid))
            .build()
        workManager.enqueueUniquePeriodicWork(
            periodicWorkName(uid),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Milestone alerts for unemployment-day risk on OPT."
        }
        manager.createNotificationChannel(channel)
    }

    private fun periodicWorkName(uid: String) = "$PERIODIC_WORK_PREFIX$uid"
    private fun immediateWorkName(uid: String) = "$IMMEDIATE_WORK_PREFIX$uid"
}

class UnemploymentAlertCoordinator(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val userSessionProvider: UserSessionProvider,
    private val scheduler: UnemploymentAlertScheduler
) {
    private var lastSyncedUid: String? = null

    suspend fun syncWithSession(isLoggedIn: Boolean, userProfile: UserProfile?) {
        if (!isLoggedIn) {
            lastSyncedUid?.let(scheduler::cancelForUser)
            lastSyncedUid = null
            return
        }

        val uid = userSessionProvider.currentUserId ?: return
        if (lastSyncedUid != null && lastSyncedUid != uid) {
            scheduler.cancelForUser(lastSyncedUid.orEmpty())
        }
        lastSyncedUid = uid
        syncForUser(uid, userProfile)
    }

    suspend fun syncForCurrentUser() {
        val uid = userSessionProvider.currentUserId ?: return
        lastSyncedUid = uid
        syncForUser(uid, null)
    }

    private suspend fun syncForUser(uid: String, hydratedProfile: UserProfile?) {
        val profile = if (hydratedProfile?.uid == uid) {
            hydratedProfile
        } else {
            authRepository.getUserProfileSnapshot(uid).getOrNull()
        }
        if (profile?.optType == null || profile.optStartDate == null) {
            scheduler.cancelForUser(uid)
            return
        }

        val employments = dashboardRepository.getEmploymentsSnapshot(uid).getOrElse { return }
        scheduler.syncSnapshot(
            UnemploymentAlertSnapshot(
                uid = uid,
                optType = profile.optType,
                optStartDate = profile.optStartDate,
                unemploymentTrackingStartDate = profile.unemploymentTrackingStartDate,
                optEndDate = profile.optEndDate,
                employments = employments
            )
        )
    }
}

class UnemploymentAlertNotifier(private val context: Context) {
    fun canPostNotifications(): Boolean {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun post(uid: String, threshold: UnemploymentAlertThreshold, forecast: UnemploymentForecast) {
        val title = when (threshold) {
            UnemploymentAlertThreshold.DAY_60 -> "60 unemployment days used"
            UnemploymentAlertThreshold.DAY_75 -> "75 unemployment days used"
            UnemploymentAlertThreshold.DAY_80 -> "80 unemployment days used"
            UnemploymentAlertThreshold.DAY_85 -> "85 unemployment days used"
            UnemploymentAlertThreshold.DAY_88 -> "88 unemployment days used"
            UnemploymentAlertThreshold.OVER_LIMIT -> "Unemployment limit exceeded"
            UnemploymentAlertThreshold.NONE -> return
        }
        val body = buildBody(forecast, threshold)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(uid.hashCode(), notification)
    }

    private fun buildBody(forecast: UnemploymentForecast, threshold: UnemploymentAlertThreshold): String {
        if (threshold == UnemploymentAlertThreshold.OVER_LIMIT) {
            return "Your recorded unemployment days now exceed the allowed limit. Review your records and contact your DSO immediately."
        }
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val projection = if (forecast.clockRunningNow && forecast.projectedExceedDate != null) {
            " If this gap continues, you exceed the limit on ${formatter.format(forecast.projectedExceedDate)}."
        } else {
            ""
        }
        return "You have used ${forecast.usedDays} of ${forecast.allowedDays} unemployment days.$projection"
    }
}

fun evaluateUnemploymentAlertUpdate(
    uid: String,
    snapshot: UnemploymentAlertSnapshot,
    store: UnemploymentAlertStore,
    notifier: UnemploymentAlertNotifier,
    now: Long
) {
    val forecast = calculateUnemploymentForecast(
        optType = snapshot.optType,
        optStartDate = snapshot.optStartDate,
        unemploymentTrackingStartDate = snapshot.unemploymentTrackingStartDate,
        optEndDate = snapshot.optEndDate,
        employments = snapshot.employments,
        now = now
    )
    if (forecast.dataQualityState != UnemploymentDataQualityState.READY) {
        return
    }
    val trackingStart = snapshot.unemploymentTrackingStartDate
        ?: snapshot.optStartDate
        ?: return
    val cycleKey = store.cycleKey(uid, trackingStart, allowedUnemploymentDays(snapshot.optType))
    val pruned = clearThresholdsAboveCurrent(
        firedThresholds = store.getFiredThresholds(cycleKey),
        currentThreshold = forecast.currentThreshold
    )
    store.saveFiredThresholds(cycleKey, pruned)
    val thresholdToNotify = highestNewThresholdToNotify(forecast, pruned) ?: return
    if (!notifier.canPostNotifications()) {
        return
    }
    notifier.post(uid, thresholdToNotify, forecast)
    store.saveFiredThresholds(cycleKey, pruned + thresholdToNotify)
}
