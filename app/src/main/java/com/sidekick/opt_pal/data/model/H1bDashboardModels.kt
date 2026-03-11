package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

const val H1B_EVERIFY_SEARCH_URL = "https://www.e-verify.gov/e-verify-employer-search"

enum class H1bCapTrack(val wireValue: String, val label: String) {
    CAP_SUBJECT("cap_subject", "Cap-subject"),
    CAP_EXEMPT("cap_exempt", "Cap-exempt"),
    UNKNOWN("unknown", "Unknown");

    companion object {
        fun fromWireValue(value: String?): H1bCapTrack {
            return entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
        }
    }
}

enum class H1bReadinessLevel(val wireValue: String, val label: String) {
    READY("ready", "Ready"),
    NEEDS_INPUTS("needs_inputs", "Needs inputs"),
    WAITING_ON_EMPLOYER("waiting_on_employer", "Waiting on employer"),
    IN_PROGRESS("in_progress", "In progress"),
    ATTENTION_NEEDED("attention_needed", "Attention needed");

    companion object {
        fun fromWireValue(value: String?): H1bReadinessLevel {
            return entries.firstOrNull { it.wireValue == value } ?: NEEDS_INPUTS
        }
    }
}

enum class H1bWorkflowStage(val wireValue: String, val label: String) {
    NOT_STARTED("not_started", "Not started"),
    REGISTRATION_PLANNED("registration_planned", "Registration planned"),
    REGISTRATION_SUBMITTED("registration_submitted", "Registration submitted"),
    SELECTED("selected", "Selected"),
    PETITION_FILED("petition_filed", "Petition filed"),
    RECEIPT_RECEIVED("receipt_received", "Receipt received"),
    APPROVED("approved", "Approved"),
    DENIED("denied", "Denied"),
    WITHDRAWN("withdrawn", "Withdrawn");

    companion object {
        fun fromWireValue(value: String?): H1bWorkflowStage {
            return entries.firstOrNull { it.wireValue == value } ?: NOT_STARTED
        }
    }
}

enum class H1bCapGapState(val wireValue: String, val label: String) {
    NOT_APPLICABLE("not_applicable", "Not applicable"),
    ELIGIBLE("eligible", "Eligible"),
    LIKELY_ELIGIBLE_NEEDS_REVIEW("likely_eligible_needs_review", "Likely eligible, needs review"),
    NOT_ELIGIBLE("not_eligible", "Not eligible"),
    HARD_STOP("hard_stop", "Hard stop");

    companion object {
        fun fromWireValue(value: String?): H1bCapGapState {
            return entries.firstOrNull { it.wireValue == value } ?: NOT_APPLICABLE
        }
    }
}

enum class H1bVerificationConfidence(val wireValue: String, val label: String) {
    VERIFIED("verified", "Verified"),
    PARTIALLY_VERIFIED("partially_verified", "Partially verified"),
    SELF_REPORTED_ONLY("self_reported_only", "Self-reported only");

    companion object {
        fun fromWireValue(value: String?): H1bVerificationConfidence {
            return entries.firstOrNull { it.wireValue == value } ?: SELF_REPORTED_ONLY
        }
    }
}

enum class H1bEVerifyStatus(val wireValue: String, val label: String) {
    UNKNOWN("unknown", "Unknown"),
    ACTIVE("active", "Active"),
    NOT_FOUND("not_found", "No match found"),
    INACTIVE("inactive", "Inactive or terminated");

    companion object {
        fun fromWireValue(value: String?): H1bEVerifyStatus {
            return entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
        }
    }
}

data class H1bProfile(
    @DocumentId val id: String = "profile",
    val employerName: String = "",
    val employerCity: String = "",
    val employerState: String = "",
    val feinLastFour: String = "",
    val employerType: String = VisaPathwayEmployerType.UNKNOWN.wireValue,
    val selfReportedSponsorIntent: Boolean? = null,
    val roleMatchesSpecialtyOccupation: Boolean? = null,
    val updatedAt: Long = 0L
) {
    val parsedEmployerType: VisaPathwayEmployerType
        get() = VisaPathwayEmployerType.fromWireValue(employerType)
}

data class H1bEmployerFiscalYearRecord(
    val fiscalYear: Int = 0,
    val totalWorkers: Int = 0,
    val initialApprovals: Int = 0,
    val initialDenials: Int = 0,
    val continuingApprovals: Int = 0,
    val continuingDenials: Int = 0,
    val changeOfEmployerApprovals: Int = 0,
    val changeOfEmployerDenials: Int = 0
)

data class H1bEmployerHistorySummary(
    val employerName: String = "",
    val matchedEmployerName: String = "",
    val city: String = "",
    val state: String = "",
    val taxIdLastFour: String = "",
    val sourceFile: String = "",
    val lastIngestedAt: Long = 0L,
    val dataLimitations: String = "",
    val fiscalYearSummaries: List<H1bEmployerFiscalYearRecord> = emptyList()
) {
    val totalInitialApprovals: Int
        get() = fiscalYearSummaries.sumOf { it.initialApprovals }

    val totalChangeOfEmployerApprovals: Int
        get() = fiscalYearSummaries.sumOf { it.changeOfEmployerApprovals }
}

data class H1bEmployerVerification(
    @DocumentId val id: String = "employerVerification",
    val eVerifyStatus: String = H1bEVerifyStatus.UNKNOWN.wireValue,
    val eVerifyLookedUpAt: Long? = null,
    val eVerifySourceUrl: String = H1B_EVERIFY_SEARCH_URL,
    val eVerifyUserConfirmed: Boolean = false,
    val employerHistory: H1bEmployerHistorySummary = H1bEmployerHistorySummary(),
    val updatedAt: Long = 0L
) {
    val parsedEVerifyStatus: H1bEVerifyStatus
        get() = H1bEVerifyStatus.fromWireValue(eVerifyStatus)
}

data class H1bTimelineState(
    @DocumentId val id: String = "timelineState",
    val workflowStage: String = H1bWorkflowStage.NOT_STARTED.wireValue,
    val selectedRegistration: Boolean? = null,
    val filedPetition: Boolean? = null,
    val requestedChangeOfStatus: Boolean? = null,
    val requestedConsularProcessing: Boolean? = null,
    val receiptNumber: String = "",
    val updatedAt: Long = 0L
) {
    val parsedWorkflowStage: H1bWorkflowStage
        get() = H1bWorkflowStage.fromWireValue(workflowStage)
}

data class H1bCaseTracking(
    @DocumentId val id: String = "caseTracking",
    val linkedCaseId: String = "",
    val linkedReceiptNumber: String = "",
    val updatedAt: Long = 0L
)

data class H1bEvidence(
    @DocumentId val id: String = "evidence",
    val hasEmployerLetter: Boolean? = null,
    val hasWageInfo: Boolean? = null,
    val hasDegreeMatchEvidence: Boolean? = null,
    val hasRegistrationConfirmation: Boolean? = null,
    val hasReceiptNotice: Boolean? = null,
    val hasCapExemptSupport: Boolean? = null,
    val capGapTravelPlanned: Boolean? = null,
    val hasRfeOrNoid: Boolean? = null,
    val updatedAt: Long = 0L
)

data class PolicyCitation(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val effectiveDate: String? = null,
    val lastReviewedAt: String? = null,
    val summary: String = ""
)

data class PolicyChangelogEntry(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val effectiveDate: String = "",
    val citationId: String = ""
)

data class H1bRuleCard(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val confidence: String = "",
    val whatThisDoesNotMean: String = "",
    val citationIds: List<String> = emptyList()
)

data class H1bCapSeasonMilestone(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val timestamp: Long? = null
)

data class H1bCapSeason(
    val fiscalYear: Int = 0,
    val registrationOpenAt: Long? = null,
    val registrationCloseAt: Long? = null,
    val petitionFilingOpensAt: Long? = null,
    val capGapEndAt: Long? = null,
    val weightedSelectionRuleEffectiveAt: Long? = null,
    val notes: String = ""
)

data class H1bDashboardBundle(
    val version: String = "",
    val generatedAt: Long = 0L,
    val lastReviewedAt: Long = 0L,
    val staleAfterDays: Int = 30,
    val citations: List<PolicyCitation> = emptyList(),
    val changelog: List<PolicyChangelogEntry> = emptyList(),
    val ruleCards: List<H1bRuleCard> = emptyList(),
    val capSeason: H1bCapSeason = H1bCapSeason(),
    val eVerifySearchUrl: String = H1B_EVERIFY_SEARCH_URL,
    val employerDataHubUrl: String = "",
    val capExemptCategories: List<String> = emptyList()
) {
    fun citationById(id: String): PolicyCitation? = citations.firstOrNull { it.id == id }

    fun isStale(now: Long): Boolean {
        if (lastReviewedAt <= 0L) return true
        return now - lastReviewedAt > staleAfterDays * ONE_DAY_MILLIS
    }
}

data class H1bReadinessSummary(
    val level: H1bReadinessLevel = H1bReadinessLevel.NEEDS_INPUTS,
    val title: String = "",
    val summary: String = "",
    val nextAction: String = "",
    val whyThisStatus: List<String> = emptyList(),
    val capTrack: H1bCapTrack = H1bCapTrack.UNKNOWN,
    val verificationConfidence: H1bVerificationConfidence = H1bVerificationConfidence.SELF_REPORTED_ONLY,
    val escalationFlags: List<String> = emptyList()
)

data class EmployerVerificationSummary(
    val capTrack: H1bCapTrack = H1bCapTrack.UNKNOWN,
    val verificationConfidence: H1bVerificationConfidence = H1bVerificationConfidence.SELF_REPORTED_ONLY,
    val eVerifyStatusLabel: String = "",
    val employerHistoryHeadline: String = "",
    val employerHistoryDetail: String = "",
    val capTrackDetail: String = "",
    val caveats: List<String> = emptyList(),
    val lastVerifiedAt: Long? = null,
    val lastIngestedAt: Long? = null
)

data class CapSeasonTimeline(
    val fiscalYear: Int = 0,
    val phaseLabel: String = "",
    val nextDeadlineLabel: String = "",
    val nextDeadlineAt: Long? = null,
    val milestones: List<H1bCapSeasonMilestone> = emptyList()
)

data class H1bCaseTrackingState(
    val linkedCaseId: String? = null,
    val linkedReceiptNumber: String? = null,
    val linkedCase: UscisCaseTracker? = null
)

data class CapGapAssessment(
    val state: H1bCapGapState = H1bCapGapState.NOT_APPLICABLE,
    val title: String = "",
    val summary: String = "",
    val workAuthorizationText: String = "",
    val travelWarning: String? = null,
    val legalReviewRequired: Boolean = false
)

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
