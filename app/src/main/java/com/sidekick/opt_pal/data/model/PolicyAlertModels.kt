package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class PolicyAlertSeverity(val wireValue: String) {
    CRITICAL("critical"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromWireValue(value: String?): PolicyAlertSeverity {
            return entries.firstOrNull { it.wireValue == value } ?: LOW
        }
    }
}

enum class PolicyAlertConfidence(val wireValue: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromWireValue(value: String?): PolicyAlertConfidence {
            return entries.firstOrNull { it.wireValue == value } ?: LOW
        }
    }
}

enum class PolicyAlertTopic(val wireValue: String) {
    TRAVEL("travel"),
    EMPLOYMENT("employment"),
    REPORTING("reporting"),
    APPLICATIONS("applications");

    companion object {
        fun fromWireValue(value: String?): PolicyAlertTopic? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

enum class PolicyAlertFinality(val wireValue: String) {
    FINAL("final"),
    GUIDANCE("guidance"),
    OPERATIONS("operations"),
    PROPOSAL("proposal"),
    EXECUTIVE_ACTION("executive_action"),
    LITIGATION("litigation");

    companion object {
        fun fromWireValue(value: String?): PolicyAlertFinality {
            return entries.firstOrNull { it.wireValue == value } ?: GUIDANCE
        }
    }
}

enum class PolicyAlertAudience(val wireValue: String) {
    ALL("all"),
    INITIAL_OPT("initial_opt"),
    STEM_OPT("stem_opt"),
    ALL_OPT_USERS("all_opt_users");

    companion object {
        fun fromWireValue(value: String?): PolicyAlertAudience {
            return entries.firstOrNull { it.wireValue == value } ?: ALL_OPT_USERS
        }
    }
}

data class PolicyAlertSource(
    val label: String = "",
    val url: String = "",
    val publishedAt: Long? = null
)

data class PolicyAlertCard(
    @DocumentId val id: String = "",
    val candidateId: String = "",
    val title: String = "",
    val whatChanged: String = "",
    val effectiveDate: Long? = null,
    val effectiveDateText: String? = null,
    val whoIsAffected: String = "",
    val whyItMatters: String = "",
    val recommendedAction: String = "",
    val source: PolicyAlertSource = PolicyAlertSource(),
    val lastReviewedAt: Long? = null,
    val severity: String = PolicyAlertSeverity.LOW.wireValue,
    val confidence: String = PolicyAlertConfidence.LOW.wireValue,
    val finality: String = PolicyAlertFinality.GUIDANCE.wireValue,
    val topics: List<String> = emptyList(),
    val audience: String = PolicyAlertAudience.ALL_OPT_USERS.wireValue,
    val affectedOptTypes: List<String> = emptyList(),
    val supersedesAlertId: String? = null,
    val supersededByAlertId: String? = null,
    val isArchived: Boolean = false,
    val isSuperseded: Boolean = false,
    val sendPush: Boolean = false,
    val urgentPush: Boolean = false,
    val publishedAt: Long = 0L,
    val publishedBy: String = "",
    val callToActionRoute: String? = null,
    val callToActionLabel: String? = null,
    val updatedAt: Long = 0L
) {
    val parsedSeverity: PolicyAlertSeverity
        get() = PolicyAlertSeverity.fromWireValue(severity)

    val parsedConfidence: PolicyAlertConfidence
        get() = PolicyAlertConfidence.fromWireValue(confidence)

    val parsedFinality: PolicyAlertFinality
        get() = PolicyAlertFinality.fromWireValue(finality)

    val parsedAudience: PolicyAlertAudience
        get() = PolicyAlertAudience.fromWireValue(audience)

    val parsedTopics: List<PolicyAlertTopic>
        get() = topics.mapNotNull(PolicyAlertTopic::fromWireValue)

    val isCritical: Boolean
        get() = parsedSeverity == PolicyAlertSeverity.CRITICAL
}

data class PolicyAlertState(
    @DocumentId val id: String = "",
    val alertId: String = "",
    val openedAt: Long? = null,
    val lastSeenAt: Long? = null
)

data class PolicyAlertAvailability(
    val isEnabled: Boolean = false,
    val message: String = "Policy Alert Feed is not enabled for this account yet."
)
