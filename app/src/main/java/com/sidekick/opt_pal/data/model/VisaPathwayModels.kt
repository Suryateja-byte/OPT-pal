package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class VisaPathwayId(val wireValue: String, val label: String, val trackType: VisaPathwayTrackType) {
    STEM_OPT("stem_opt", "STEM OPT", VisaPathwayTrackType.TEMPORARY),
    H1B_CAP_SUBJECT("h1b_cap_subject", "H-1B Cap-Subject", VisaPathwayTrackType.TEMPORARY),
    H1B_CAP_EXEMPT("h1b_cap_exempt", "H-1B Cap-Exempt", VisaPathwayTrackType.TEMPORARY),
    O1A("o1a", "O-1A", VisaPathwayTrackType.TEMPORARY),
    EB1("eb1", "EB-1", VisaPathwayTrackType.LONG_TERM),
    EB2_NIW("eb2_niw", "EB-2 NIW", VisaPathwayTrackType.LONG_TERM),
    EB2_EB3_EMPLOYER("eb2_eb3_employer", "EB-2 / EB-3 Employer-Sponsored", VisaPathwayTrackType.LONG_TERM);

    companion object {
        fun fromWireValue(value: String?): VisaPathwayId {
            return entries.firstOrNull { it.wireValue == value } ?: STEM_OPT
        }
    }
}

enum class VisaPathwayTrackType {
    TEMPORARY,
    LONG_TERM
}

enum class VisaPathwayRecommendation(val label: String) {
    STRONG_FIT("Strong fit"),
    POSSIBLE_WITH_GAPS("Possible with gaps"),
    EXPLORATORY("Exploratory"),
    NOT_A_CURRENT_FIT("Not a current fit"),
    CONSULT_DSO_OR_ATTORNEY("Consult DSO or attorney")
}

enum class VisaPathwayEntitlementSource {
    USER_FLAG,
    OPEN_BETA,
    LOCKED_PREVIEW
}

enum class VisaPathwayEmployerType(val wireValue: String, val label: String) {
    UNKNOWN("unknown", "Unknown"),
    PRIVATE_COMPANY("private_company", "Private company"),
    UNIVERSITY("university", "University"),
    NONPROFIT_RESEARCH("nonprofit_research", "Nonprofit research"),
    GOVERNMENT_RESEARCH("government_research", "Government research"),
    HOSPITAL("hospital", "Hospital"),
    AGENT("agent", "Agent / petitioner");

    companion object {
        fun fromWireValue(value: String?): VisaPathwayEmployerType {
            return entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
        }
    }
}

enum class VisaPathwayH1bRegistrationStatus(val wireValue: String, val label: String) {
    UNKNOWN("unknown", "Unknown"),
    NOT_STARTED("not_started", "Not started"),
    REGISTERED("registered", "Registered"),
    SELECTED("selected", "Selected"),
    NOT_SELECTED("not_selected", "Not selected");

    companion object {
        fun fromWireValue(value: String?): VisaPathwayH1bRegistrationStatus {
            return entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
        }
    }
}

enum class VisaPathwayO1EvidenceBucket(val wireValue: String, val label: String) {
    AWARDS("awards", "Awards"),
    PUBLICATIONS("publications", "Publications"),
    JUDGING("judging", "Judging"),
    ORIGINAL_CONTRIBUTIONS("original_contributions", "Original contributions"),
    CRITICAL_ROLE("critical_role", "Critical role"),
    MEMBERSHIPS("memberships", "Memberships"),
    PRESS("press", "Press"),
    PATENTS("patents", "Patents"),
    HIGH_COMPENSATION("high_compensation", "High compensation");

    companion object {
        fun fromWireValue(value: String?): VisaPathwayO1EvidenceBucket? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class VisaPathwayEntitlementState(
    val isEnabled: Boolean = false,
    val source: VisaPathwayEntitlementSource = VisaPathwayEntitlementSource.LOCKED_PREVIEW,
    val message: String = "Visa Pathway Planner is not enabled for this account yet."
)

data class VisaPathwayProfile(
    @DocumentId val id: String = "profile",
    val desiredContinuityDate: Long? = null,
    val employerType: String = VisaPathwayEmployerType.UNKNOWN.wireValue,
    val employerUsesEVerify: Boolean? = null,
    val employerWillSponsorH1b: Boolean? = null,
    val h1bRegistrationStatus: String = VisaPathwayH1bRegistrationStatus.UNKNOWN.wireValue,
    val degreeLevel: String = "",
    val hasPriorUsStemDegree: Boolean? = null,
    val roleDirectlyRelatedToDegree: Boolean? = null,
    val hasPetitioningEmployerOrAgent: Boolean? = null,
    val o1EvidenceSignals: List<String> = emptyList(),
    val preferredPathwayId: String? = null,
    val hasStatusViolation: Boolean? = null,
    val hasArrestHistory: Boolean? = null,
    val hasUnauthorizedEmployment: Boolean? = null,
    val hasRfeOrNoid: Boolean? = null,
    val updatedAt: Long = 0L
) {
    val parsedEmployerType: VisaPathwayEmployerType
        get() = VisaPathwayEmployerType.fromWireValue(employerType)

    val parsedH1bRegistrationStatus: VisaPathwayH1bRegistrationStatus
        get() = VisaPathwayH1bRegistrationStatus.fromWireValue(h1bRegistrationStatus)

    val parsedPreferredPathwayId: VisaPathwayId?
        get() = preferredPathwayId?.let(VisaPathwayId::fromWireValue)

    val parsedO1EvidenceSignals: Set<VisaPathwayO1EvidenceBucket>
        get() = o1EvidenceSignals.mapNotNull(VisaPathwayO1EvidenceBucket::fromWireValue).toSet()
}

data class VisaPathwayCitation(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val effectiveDate: String? = null,
    val lastReviewedDate: String? = null,
    val summary: String = ""
)

data class VisaPathwayMilestoneTemplate(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val route: String? = null,
    val externalUrl: String? = null
)

data class VisaPathwayDefinition(
    val pathwayId: String = VisaPathwayId.STEM_OPT.wireValue,
    val title: String = "",
    val summary: String = "",
    val citationIds: List<String> = emptyList(),
    val milestoneTemplates: List<VisaPathwayMilestoneTemplate> = emptyList()
) {
    val parsedPathwayId: VisaPathwayId
        get() = VisaPathwayId.fromWireValue(pathwayId)
}

data class VisaPathwayH1bSeason(
    val fiscalYear: Int? = null,
    val registrationOpenDate: Long? = null,
    val registrationCloseDate: Long? = null,
    val selectionNoticeDate: Long? = null,
    val petitionFilingEarliestDate: Long? = null,
    val isPublished: Boolean = false,
    val notes: String = ""
)

data class VisaPathwayPlannerBundle(
    val version: String = "",
    val generatedAt: Long = 0L,
    val lastReviewedAt: Long = 0L,
    val staleAfterDays: Int = 30,
    val sources: List<VisaPathwayCitation> = emptyList(),
    val pathwayDefinitions: List<VisaPathwayDefinition> = emptyList(),
    val stemEligibleCipPrefixes: List<String> = emptyList(),
    val h1bSeason: VisaPathwayH1bSeason = VisaPathwayH1bSeason(),
    val capGapSummary: String = "",
    val visaBulletinUrl: String = "",
    val policyOverlaySummary: String = ""
) {
    fun citationsFor(pathwayId: VisaPathwayId): List<VisaPathwayCitation> {
        val definition = pathwayDefinitions.firstOrNull { it.parsedPathwayId == pathwayId } ?: return emptyList()
        return definition.citationIds.mapNotNull { citationId ->
            sources.firstOrNull { it.id == citationId }
        }
    }

    fun definitionFor(pathwayId: VisaPathwayId): VisaPathwayDefinition? {
        return pathwayDefinitions.firstOrNull { it.parsedPathwayId == pathwayId }
    }

    fun isStale(now: Long): Boolean {
        if (lastReviewedAt <= 0L) return true
        return now - lastReviewedAt > staleAfterDays * ONE_DAY_MILLIS
    }
}

data class VisaPathwayAction(
    val id: String = "",
    val label: String = "",
    val route: String? = null,
    val externalUrl: String? = null
)

data class VisaPathwayGap(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val isBlocking: Boolean = false,
    val action: VisaPathwayAction? = null
)

data class VisaPathwayMilestone(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val dueDate: Long? = null,
    val action: VisaPathwayAction? = null
)

data class VisaPathwayAssessment(
    val pathwayId: VisaPathwayId = VisaPathwayId.STEM_OPT,
    val title: String = "",
    val trackType: VisaPathwayTrackType = VisaPathwayTrackType.TEMPORARY,
    val recommendation: VisaPathwayRecommendation = VisaPathwayRecommendation.EXPLORATORY,
    val rankScore: Int = 0,
    val summary: String = "",
    val whyItFits: List<String> = emptyList(),
    val gaps: List<VisaPathwayGap> = emptyList(),
    val milestones: List<VisaPathwayMilestone> = emptyList(),
    val actions: List<VisaPathwayAction> = emptyList(),
    val citations: List<VisaPathwayCitation> = emptyList(),
    val isEducationalOnly: Boolean = false,
    val policyOverlayTitle: String? = null
) {
    val nextMilestone: VisaPathwayMilestone?
        get() = milestones.firstOrNull { it.dueDate != null } ?: milestones.firstOrNull()
}

data class VisaPathwayEvidenceSnapshot(
    val profile: UserProfile? = null,
    val plannerProfile: VisaPathwayProfile = VisaPathwayProfile(),
    val employments: List<Employment> = emptyList(),
    val currentEmployment: Employment? = null,
    val reportingObligations: List<ReportingObligation> = emptyList(),
    val documents: List<DocumentMetadata> = emptyList(),
    val i983Drafts: List<I983Draft> = emptyList(),
    val trackedCase: UscisCaseTracker? = null,
    val latestCriticalPolicyAlertTitle: String? = null,
    val latestCriticalPolicyAlertId: String? = null,
    val cipCode: String? = null,
    val degreeLevel: String? = null,
    val optType: String? = null,
    val optStartDate: Long? = null,
    val optEndDate: Long? = null,
    val hoursPerWeek: Int? = null,
    val isStemCipEligible: Boolean? = null,
    val isRoleRelatedToDegree: Boolean? = null,
    val hasPriorUsStemDegree: Boolean? = null,
    val hasPetitioningEmployerOrAgent: Boolean? = null,
    val employerType: VisaPathwayEmployerType = VisaPathwayEmployerType.UNKNOWN,
    val employerUsesEVerify: Boolean? = null,
    val employerWillSponsorH1b: Boolean? = null,
    val h1bRegistrationStatus: VisaPathwayH1bRegistrationStatus = VisaPathwayH1bRegistrationStatus.UNKNOWN,
    val o1EvidenceSignals: Set<VisaPathwayO1EvidenceBucket> = emptySet(),
    val i983Ready: Boolean = false,
    val hasStatusEscalationIssue: Boolean = false,
    val desiredContinuityDate: Long? = null
)

data class VisaPathwayPlannerSummary(
    val topAssessment: VisaPathwayAssessment? = null,
    val preferredPathwayId: VisaPathwayId? = null
)

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
