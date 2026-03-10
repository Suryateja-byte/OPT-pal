package com.sidekick.opt_pal.core.compliance

import com.sidekick.opt_pal.core.calculations.utcStartOfDay
import com.sidekick.opt_pal.core.security.SecurityManager
import com.sidekick.opt_pal.data.model.ComplianceScoreSnapshot
import com.sidekick.opt_pal.data.model.ComplianceScoreSnapshotState
import org.json.JSONObject

class ComplianceScoreSnapshotStore(
    securityManager: SecurityManager
) {
    private val prefs = securityManager.encryptedPrefs

    fun sync(uid: String, score: Int, computedAt: Long): ComplianceScoreSnapshotState {
        val currentKey = currentKey(uid)
        val previousKey = previousKey(uid)
        val current = prefs.getString(currentKey, null)?.let(::decodeSnapshot)
        val previous = prefs.getString(previousKey, null)?.let(::decodeSnapshot)
        val nextCurrent = ComplianceScoreSnapshot(score = score, computedAt = computedAt)

        if (current == null) {
            prefs.edit().putString(currentKey, encodeSnapshot(nextCurrent)).apply()
            return ComplianceScoreSnapshotState(current = nextCurrent, previous = null)
        }

        return if (utcStartOfDay(current.computedAt) == utcStartOfDay(computedAt)) {
            val finalCurrent = if (current.score == score) current else nextCurrent
            prefs.edit().putString(currentKey, encodeSnapshot(finalCurrent)).apply()
            ComplianceScoreSnapshotState(current = finalCurrent, previous = previous)
        } else {
            prefs.edit()
                .putString(previousKey, encodeSnapshot(current))
                .putString(currentKey, encodeSnapshot(nextCurrent))
                .apply()
            ComplianceScoreSnapshotState(current = nextCurrent, previous = current)
        }
    }

    private fun encodeSnapshot(snapshot: ComplianceScoreSnapshot): String {
        return JSONObject()
            .put("score", snapshot.score)
            .put("computedAt", snapshot.computedAt)
            .toString()
    }

    private fun decodeSnapshot(raw: String): ComplianceScoreSnapshot {
        return runCatching {
            val payload = JSONObject(raw)
            ComplianceScoreSnapshot(
                score = payload.optInt("score", 0),
                computedAt = payload.optLong("computedAt", 0L)
            )
        }.getOrDefault(ComplianceScoreSnapshot())
    }

    private fun currentKey(uid: String) = "compliance_score_current_$uid"

    private fun previousKey(uid: String) = "compliance_score_previous_$uid"
}
