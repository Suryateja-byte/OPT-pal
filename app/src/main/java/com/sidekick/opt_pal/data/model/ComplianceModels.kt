package com.sidekick.opt_pal.data.model

enum class ComplianceFactorId {
    UNEMPLOYMENT,
    REPORTING,
    DOCUMENTS,
    USCIS_CASE
}

enum class ComplianceScoreBand {
    STABLE,
    WATCH,
    ACTION_NEEDED,
    CRITICAL
}

enum class ComplianceScoreQuality {
    VERIFIED,
    PROVISIONAL
}

data class ComplianceHealthAvailability(
    val isEnabled: Boolean = false,
    val message: String = "Compliance Health Score is not enabled for this account yet."
)

data class ComplianceReference(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val effectiveDate: String? = null,
    val lastReviewedDate: String? = null,
    val summary: String = ""
)

data class ComplianceAction(
    val id: String = "",
    val label: String = "",
    val route: String? = null,
    val externalUrl: String? = null
)

data class ComplianceBlocker(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val scoreCap: Int = 100,
    val action: ComplianceAction? = null
)

data class ComplianceFactorAssessment(
    val id: ComplianceFactorId = ComplianceFactorId.UNEMPLOYMENT,
    val title: String = "",
    val score: Int = 0,
    val maxScore: Int = 0,
    val summary: String = "",
    val detail: String = "",
    val isVerified: Boolean = true,
    val actions: List<ComplianceAction> = emptyList(),
    val references: List<ComplianceReference> = emptyList()
) {
    val isPerfect: Boolean
        get() = score >= maxScore
}

data class ComplianceMilestone(
    val id: String = "",
    val title: String = "",
    val dueDate: Long = 0L,
    val isOverdue: Boolean = false,
    val inferredOnly: Boolean = false,
    val action: ComplianceAction? = null
)

data class ComplianceDocumentEvidence(
    val hasPassportDocument: Boolean = false,
    val hasEadDocument: Boolean = false,
    val passportExpirationDate: Long? = null,
    val eadExpirationDate: Long? = null,
    val visaExpirationDate: Long? = null
)

data class ComplianceEvidenceSnapshot(
    val profile: UserProfile? = null,
    val unemploymentForecast: com.sidekick.opt_pal.core.calculations.UnemploymentForecast,
    val missingHoursEmploymentId: String? = null,
    val documents: ComplianceDocumentEvidence = ComplianceDocumentEvidence(),
    val reportingObligations: List<ReportingObligation> = emptyList(),
    val overdueReportingObligations: List<ReportingObligation> = emptyList(),
    val dueSoonReportingObligations: List<ReportingObligation> = emptyList(),
    val stemMilestones: List<ComplianceMilestone> = emptyList(),
    val trackedCase: UscisCaseTracker? = null,
    val latestCriticalPolicyAlertTitle: String? = null,
    val latestCriticalPolicyAlertId: String? = null,
    val unreadCriticalPolicyCount: Int = 0
)

data class ComplianceScoreSnapshot(
    val score: Int = 0,
    val computedAt: Long = 0L
)

data class ComplianceScoreSnapshotState(
    val current: ComplianceScoreSnapshot? = null,
    val previous: ComplianceScoreSnapshot? = null
) {
    val delta: Int?
        get() = if (current != null && previous != null) current.score - previous.score else null
}

data class ComplianceHealthScore(
    val score: Int = 0,
    val band: ComplianceScoreBand = ComplianceScoreBand.CRITICAL,
    val quality: ComplianceScoreQuality = ComplianceScoreQuality.PROVISIONAL,
    val headline: String = "",
    val summary: String = "",
    val delta: Int? = null,
    val computedAt: Long = 0L,
    val topReasons: List<String> = emptyList(),
    val blockers: List<ComplianceBlocker> = emptyList(),
    val factors: List<ComplianceFactorAssessment> = emptyList(),
    val latestCriticalPolicyAlertTitle: String? = null,
    val latestCriticalPolicyAlertId: String? = null,
    val unreadCriticalPolicyCount: Int = 0
)
