package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class UscisTrackerMode(val wireValue: String) {
    DISABLED("disabled"),
    SANDBOX("sandbox"),
    PRODUCTION("production");

    companion object {
        fun fromWireValue(value: String?): UscisTrackerMode {
            return entries.firstOrNull { it.wireValue == value } ?: DISABLED
        }
    }
}

enum class UscisCaseStage {
    RECEIVED,
    ACTIVE_REVIEW,
    BIOMETRICS,
    RFE_OR_NOID,
    CORRESPONDENCE_RECEIVED,
    TRANSFERRED,
    APPROVED,
    CARD_PRODUCED,
    CARD_PICKED_UP,
    CARD_DELIVERED,
    DENIED,
    REJECTED,
    WITHDRAWN,
    UNKNOWN;

    companion object {
        fun fromValue(value: String?): UscisCaseStage {
            return entries.firstOrNull { it.name == value } ?: UNKNOWN
        }
    }
}

enum class UscisCaseClassification(val wireValue: String) {
    INFORMATIONAL("informational"),
    CONSULT_DSO_ATTORNEY("consult_dso_attorney");

    companion object {
        fun fromWireValue(value: String?): UscisCaseClassification {
            return entries.firstOrNull { it.wireValue == value } ?: INFORMATIONAL
        }
    }
}

enum class UscisCaseConfidence(val wireValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun fromWireValue(value: String?): UscisCaseConfidence {
            return entries.firstOrNull { it.wireValue == value } ?: LOW
        }
    }
}

data class UscisTrackerAvailability(
    val mode: String = UscisTrackerMode.DISABLED.wireValue,
    val reason: String = "",
    val maxTrackedCases: Int = 3
) {
    val parsedMode: UscisTrackerMode
        get() = UscisTrackerMode.fromWireValue(mode)

    val isEnabled: Boolean
        get() = parsedMode != UscisTrackerMode.DISABLED
}

data class UscisCaseHistoryEntry(
    val statusText: String = "",
    val statusDescription: String = "",
    val statusDate: Long? = null
)

data class UscisCaseTracker(
    @DocumentId val id: String = "",
    val receiptNumber: String = "",
    val formType: String = "",
    val normalizedStage: String = UscisCaseStage.UNKNOWN.name,
    val officialStatusText: String = "",
    val officialStatusDescription: String = "",
    val officialHistory: List<UscisCaseHistoryEntry> = emptyList(),
    val plainEnglishSummary: String = "",
    val recommendedAction: String = "",
    val classification: String = UscisCaseClassification.INFORMATIONAL.wireValue,
    val confidence: String = UscisCaseConfidence.LOW.wireValue,
    val watchFor: List<String> = emptyList(),
    val statusHash: String = "",
    val lastCheckedAt: Long = 0L,
    val lastChangedAt: Long = 0L,
    val nextPollAt: Long = 0L,
    val lastError: String = "",
    val consecutiveFailureCount: Int = 0,
    val isTerminal: Boolean = false,
    val isArchived: Boolean = false
) {
    val parsedStage: UscisCaseStage
        get() = UscisCaseStage.fromValue(normalizedStage)

    val parsedClassification: UscisCaseClassification
        get() = UscisCaseClassification.fromWireValue(classification)

    val parsedConfidence: UscisCaseConfidence
        get() = UscisCaseConfidence.fromWireValue(confidence)
}

data class UscisCaseSummary(
    val caseId: String = "",
    val receiptNumber: String = "",
    val stage: UscisCaseStage = UscisCaseStage.UNKNOWN,
    val statusText: String = "",
    val lastCheckedAt: Long = 0L,
    val lastChangedAt: Long = 0L,
    val hasRecentChange: Boolean = false
)

data class UscisCaseRefreshResult(
    val refreshed: Boolean = false,
    val statusChanged: Boolean = false,
    val cooldownRemainingMinutes: Int = 0,
    val caseId: String = "",
    val tracker: UscisCaseTracker? = null
)
