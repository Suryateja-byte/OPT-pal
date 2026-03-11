package com.sidekick.opt_pal.core.h1b

import com.sidekick.opt_pal.data.model.CapGapAssessment
import com.sidekick.opt_pal.data.model.CapSeasonTimeline
import com.sidekick.opt_pal.data.model.EmployerVerificationSummary
import com.sidekick.opt_pal.data.model.H1bCapGapState
import com.sidekick.opt_pal.data.model.H1bCapSeasonMilestone
import com.sidekick.opt_pal.data.model.H1bCapTrack
import com.sidekick.opt_pal.data.model.H1bCaseTracking
import com.sidekick.opt_pal.data.model.H1bCaseTrackingState
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bEmployerVerification
import com.sidekick.opt_pal.data.model.H1bEvidence
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bReadinessLevel
import com.sidekick.opt_pal.data.model.H1bReadinessSummary
import com.sidekick.opt_pal.data.model.H1bTimelineState
import com.sidekick.opt_pal.data.model.H1bVerificationConfidence
import com.sidekick.opt_pal.data.model.H1bWorkflowStage
import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType

data class H1bDashboardComputedState(
    val readinessSummary: H1bReadinessSummary,
    val employerVerificationSummary: EmployerVerificationSummary,
    val capSeasonTimeline: CapSeasonTimeline,
    val capGapAssessment: CapGapAssessment,
    val caseTrackingState: H1bCaseTrackingState
)

class H1bDashboardEngine {

    fun build(
        userProfile: UserProfile?,
        h1bProfile: H1bProfile,
        employerVerification: H1bEmployerVerification,
        timelineState: H1bTimelineState,
        caseTracking: H1bCaseTracking,
        evidence: H1bEvidence,
        trackedCases: List<UscisCaseTracker>,
        bundle: H1bDashboardBundle,
        now: Long
    ): H1bDashboardComputedState {
        val linkedCase = resolveLinkedCase(caseTracking, timelineState, trackedCases)
        val capTrack = resolveCapTrack(h1bProfile, evidence)
        val verificationConfidence = resolveVerificationConfidence(
            capTrack = capTrack,
            h1bProfile = h1bProfile,
            employerVerification = employerVerification,
            evidence = evidence
        )
        val capGapAssessment = buildCapGapAssessment(
            userProfile = userProfile,
            timelineState = timelineState,
            evidence = evidence
        )
        return H1bDashboardComputedState(
            readinessSummary = buildReadinessSummary(
                h1bProfile = h1bProfile,
                timelineState = timelineState,
                capTrack = capTrack,
                verificationConfidence = verificationConfidence,
                capGapAssessment = capGapAssessment,
                linkedCase = linkedCase
            ),
            employerVerificationSummary = buildEmployerVerificationSummary(
                h1bProfile = h1bProfile,
                employerVerification = employerVerification,
                capTrack = capTrack,
                verificationConfidence = verificationConfidence
            ),
            capSeasonTimeline = buildTimeline(bundle, now),
            capGapAssessment = capGapAssessment,
            caseTrackingState = H1bCaseTrackingState(
                linkedCaseId = linkedCase?.id,
                linkedReceiptNumber = linkedCase?.receiptNumber?.ifBlank { null }
                    ?: caseTracking.linkedReceiptNumber.ifBlank { null },
                linkedCase = linkedCase
            )
        )
    }

    private fun resolveLinkedCase(
        caseTracking: H1bCaseTracking,
        timelineState: H1bTimelineState,
        trackedCases: List<UscisCaseTracker>
    ): UscisCaseTracker? {
        val i129Cases = trackedCases.filter(::isI129Case)
        return i129Cases.firstOrNull { it.id == caseTracking.linkedCaseId } ?:
            i129Cases.firstOrNull { it.receiptNumber == caseTracking.linkedReceiptNumber } ?:
            i129Cases.firstOrNull { it.receiptNumber == timelineState.receiptNumber } ?:
            i129Cases.firstOrNull()
    }

    private fun resolveCapTrack(
        h1bProfile: H1bProfile,
        evidence: H1bEvidence
    ): H1bCapTrack {
        return when (h1bProfile.parsedEmployerType) {
            VisaPathwayEmployerType.UNIVERSITY,
            VisaPathwayEmployerType.NONPROFIT_RESEARCH,
            VisaPathwayEmployerType.GOVERNMENT_RESEARCH -> H1bCapTrack.CAP_EXEMPT

            VisaPathwayEmployerType.PRIVATE_COMPANY,
            VisaPathwayEmployerType.AGENT -> H1bCapTrack.CAP_SUBJECT

            VisaPathwayEmployerType.HOSPITAL -> if (evidence.hasCapExemptSupport == true) {
                H1bCapTrack.CAP_EXEMPT
            } else {
                H1bCapTrack.UNKNOWN
            }

            VisaPathwayEmployerType.UNKNOWN -> H1bCapTrack.UNKNOWN
        }
    }

    private fun resolveVerificationConfidence(
        capTrack: H1bCapTrack,
        h1bProfile: H1bProfile,
        employerVerification: H1bEmployerVerification,
        evidence: H1bEvidence
    ): H1bVerificationConfidence {
        val hasConfirmedEVerify = employerVerification.eVerifyUserConfirmed &&
            employerVerification.parsedEVerifyStatus != com.sidekick.opt_pal.data.model.H1bEVerifyStatus.UNKNOWN
        val hasEmployerHistory = employerVerification.employerHistory.fiscalYearSummaries.isNotEmpty()
        val hasCapTrackEvidence = when (capTrack) {
            H1bCapTrack.CAP_EXEMPT -> evidence.hasCapExemptSupport == true ||
                h1bProfile.parsedEmployerType in setOf(
                    VisaPathwayEmployerType.UNIVERSITY,
                    VisaPathwayEmployerType.NONPROFIT_RESEARCH,
                    VisaPathwayEmployerType.GOVERNMENT_RESEARCH
                )

            H1bCapTrack.CAP_SUBJECT -> h1bProfile.parsedEmployerType in setOf(
                VisaPathwayEmployerType.PRIVATE_COMPANY,
                VisaPathwayEmployerType.AGENT
            )

            H1bCapTrack.UNKNOWN -> false
        }
        return when {
            hasConfirmedEVerify && hasEmployerHistory && hasCapTrackEvidence -> H1bVerificationConfidence.VERIFIED
            hasConfirmedEVerify || hasEmployerHistory || hasCapTrackEvidence -> H1bVerificationConfidence.PARTIALLY_VERIFIED
            else -> H1bVerificationConfidence.SELF_REPORTED_ONLY
        }
    }

    private fun buildReadinessSummary(
        h1bProfile: H1bProfile,
        timelineState: H1bTimelineState,
        capTrack: H1bCapTrack,
        verificationConfidence: H1bVerificationConfidence,
        capGapAssessment: CapGapAssessment,
        linkedCase: UscisCaseTracker?
    ): H1bReadinessSummary {
        val escalationFlags = mutableListOf<String>()
        if (capGapAssessment.legalReviewRequired) {
            escalationFlags += "legalReviewRequired"
        }
        if (linkedCase?.parsedStage in setOf(
                UscisCaseStage.RFE_OR_NOID,
                UscisCaseStage.DENIED,
                UscisCaseStage.REJECTED,
                UscisCaseStage.WITHDRAWN
            )
        ) {
            escalationFlags += "caseStatusRisk"
        }
        if (h1bProfile.selfReportedSponsorIntent == false) {
            escalationFlags += "waitingOnEmployer"
        }

        val why = mutableListOf<String>()
        if (h1bProfile.employerName.isBlank()) why += "Employer name is still missing."
        if (h1bProfile.selfReportedSponsorIntent == null) why += "Employer sponsorship intent is not confirmed."
        if (h1bProfile.roleMatchesSpecialtyOccupation == null) why += "Role-to-degree fit is not confirmed."
        if (capTrack == H1bCapTrack.UNKNOWN) why += "Cap track is still unverified."
        if (verificationConfidence == H1bVerificationConfidence.SELF_REPORTED_ONLY) {
            why += "Employer verification is still mostly self-reported."
        }
        linkedCase?.let {
            why += "Linked USCIS case stage: ${it.parsedStage.name.replace('_', ' ').lowercase()}."
        }

        val stage = timelineState.parsedWorkflowStage
        val level = when {
            escalationFlags.isNotEmpty() -> H1bReadinessLevel.ATTENTION_NEEDED
            stage in setOf(
                H1bWorkflowStage.REGISTRATION_SUBMITTED,
                H1bWorkflowStage.SELECTED,
                H1bWorkflowStage.PETITION_FILED,
                H1bWorkflowStage.RECEIPT_RECEIVED,
                H1bWorkflowStage.APPROVED
            ) -> H1bReadinessLevel.IN_PROGRESS

            h1bProfile.selfReportedSponsorIntent == false -> H1bReadinessLevel.WAITING_ON_EMPLOYER
            h1bProfile.employerName.isBlank() ||
                h1bProfile.roleMatchesSpecialtyOccupation != true -> H1bReadinessLevel.NEEDS_INPUTS

            else -> H1bReadinessLevel.READY
        }

        val nextAction = when (level) {
            H1bReadinessLevel.READY ->
                "Use the official FY timeline, confirm employer verification signals, and prepare registration or filing evidence."

            H1bReadinessLevel.NEEDS_INPUTS ->
                "Fill in employer details, role fit, and current H-1B workflow status before relying on this dashboard."

            H1bReadinessLevel.WAITING_ON_EMPLOYER ->
                "Confirm whether your employer will sponsor H-1B and which cap track applies."

            H1bReadinessLevel.IN_PROGRESS -> when (stage) {
                H1bWorkflowStage.SELECTED ->
                    "Coordinate the petition filing package and watch for receipt issuance."

                H1bWorkflowStage.PETITION_FILED,
                H1bWorkflowStage.RECEIPT_RECEIVED ->
                    "Keep the filing and receipt notices together and monitor USCIS for the next update."

                H1bWorkflowStage.APPROVED ->
                    "Confirm the approval notice, effective date, and any travel implications before changing plans."

                else ->
                    "Keep monitoring the current H-1B step and update the dashboard as the case advances."
            }

            H1bReadinessLevel.ATTENTION_NEEDED ->
                "Use the hard-stop guidance on this screen and coordinate with your DSO or immigration counsel before taking action."
        }

        val title = when (level) {
            H1bReadinessLevel.READY -> "H-1B path looks structured"
            H1bReadinessLevel.NEEDS_INPUTS -> "More H-1B inputs are needed"
            H1bReadinessLevel.WAITING_ON_EMPLOYER -> "Employer confirmation is the blocker"
            H1bReadinessLevel.IN_PROGRESS -> "H-1B workflow is underway"
            H1bReadinessLevel.ATTENTION_NEEDED -> "H-1B path needs human review"
        }

        val summary = when (level) {
            H1bReadinessLevel.READY ->
                "The dashboard can see a plausible H-1B path, but it still avoids lottery or approval predictions."

            H1bReadinessLevel.NEEDS_INPUTS ->
                "Important fields are still missing, so the dashboard is intentionally conservative."

            H1bReadinessLevel.WAITING_ON_EMPLOYER ->
                "The next real step depends on employer sponsorship intent or cap-track confirmation."

            H1bReadinessLevel.IN_PROGRESS ->
                "The case has moved past planning and is now in the registration or petition pipeline."

            H1bReadinessLevel.ATTENTION_NEEDED ->
                "The dashboard found a cap-gap, travel, or USCIS-risk condition that should not be handled as a routine self-service step."
        }

        return H1bReadinessSummary(
            level = level,
            title = title,
            summary = summary,
            nextAction = nextAction,
            whyThisStatus = why.ifEmpty {
                listOf("The dashboard is using your saved employer, timeline, and USCIS case inputs.")
            },
            capTrack = capTrack,
            verificationConfidence = verificationConfidence,
            escalationFlags = escalationFlags
        )
    }

    private fun buildEmployerVerificationSummary(
        h1bProfile: H1bProfile,
        employerVerification: H1bEmployerVerification,
        capTrack: H1bCapTrack,
        verificationConfidence: H1bVerificationConfidence
    ): EmployerVerificationSummary {
        val history = employerVerification.employerHistory
        val employerHistoryHeadline = if (history.fiscalYearSummaries.isEmpty()) {
            "No USCIS H-1B employer-history snapshot is saved yet."
        } else {
            "USCIS employer data shows ${history.fiscalYearSummaries.size} fiscal-year record(s) for ${history.matchedEmployerName.ifBlank { history.employerName }}."
        }
        val employerHistoryDetail = if (history.fiscalYearSummaries.isEmpty()) {
            "Run an employer-history search after you add the employer name and location."
        } else {
            "${history.totalInitialApprovals} initial approvals and ${history.totalChangeOfEmployerApprovals} change-of-employer approvals are in the saved data snapshot."
        }
        val capTrackDetail = when (capTrack) {
            H1bCapTrack.CAP_EXEMPT -> when (h1bProfile.parsedEmployerType) {
                VisaPathwayEmployerType.UNIVERSITY ->
                    "Employer is marked as a university, which is commonly cap-exempt."

                VisaPathwayEmployerType.NONPROFIT_RESEARCH ->
                    "Employer is marked as a nonprofit research organization, which may support a cap-exempt route."

                VisaPathwayEmployerType.GOVERNMENT_RESEARCH ->
                    "Employer is marked as a governmental research organization, which may support a cap-exempt route."

                else ->
                    "Cap-exempt classification should be backed by employer-type evidence before you rely on it."
            }

            H1bCapTrack.CAP_SUBJECT ->
                "Current saved employer type points to the regular H-1B cap track."

            H1bCapTrack.UNKNOWN ->
                "Current saved employer information is not enough to classify the cap track confidently."
        }
        return EmployerVerificationSummary(
            capTrack = capTrack,
            verificationConfidence = verificationConfidence,
            eVerifyStatusLabel = employerVerification.parsedEVerifyStatus.label,
            employerHistoryHeadline = employerHistoryHeadline,
            employerHistoryDetail = employerHistoryDetail,
            capTrackDetail = capTrackDetail,
            caveats = listOf(
                "The public E-Verify search is self-reported by employers and may not cover every hiring site.",
                "USCIS Employer Data Hub records reflect first decisions and exclude later appeals or revocations."
            ),
            lastVerifiedAt = employerVerification.eVerifyLookedUpAt,
            lastIngestedAt = history.lastIngestedAt.takeIf { it > 0L }
        )
    }

    private fun buildTimeline(
        bundle: H1bDashboardBundle,
        now: Long
    ): CapSeasonTimeline {
        val capSeason = bundle.capSeason
        val milestones = listOfNotNull(
            capSeason.weightedSelectionRuleEffectiveAt?.let {
                H1bCapSeasonMilestone(
                    id = "weighted_selection_effective",
                    title = "Weighted selection rule effective",
                    detail = "USCIS applies the wage-weighted selection rule for the current season.",
                    timestamp = it
                )
            },
            capSeason.registrationOpenAt?.let {
                H1bCapSeasonMilestone(
                    id = "registration_open",
                    title = "Registration opens",
                    detail = "Electronic registration opens on the official USCIS portal.",
                    timestamp = it
                )
            },
            capSeason.registrationCloseAt?.let {
                H1bCapSeasonMilestone(
                    id = "registration_close",
                    title = "Registration closes",
                    detail = "Registrations must be submitted before the official USCIS deadline.",
                    timestamp = it
                )
            },
            capSeason.petitionFilingOpensAt?.let {
                H1bCapSeasonMilestone(
                    id = "petition_filing_opens",
                    title = "Petition filing opens",
                    detail = "Selected cap-subject petitions can begin filing.",
                    timestamp = it
                )
            },
            capSeason.capGapEndAt?.let {
                H1bCapSeasonMilestone(
                    id = "cap_gap_end",
                    title = "Cap-gap end date",
                    detail = "Cap-gap coverage ends on the earlier of the approved validity start date or the published cap-gap end date.",
                    timestamp = it
                )
            }
        ).sortedBy { it.timestamp ?: Long.MAX_VALUE }

        val phaseLabel = when {
            capSeason.registrationOpenAt == null || capSeason.registrationCloseAt == null ->
                "Official registration dates are missing"

            now < capSeason.registrationOpenAt ->
                "Registration opens soon"

            now in capSeason.registrationOpenAt..capSeason.registrationCloseAt ->
                "Registration window is open"

            capSeason.petitionFilingOpensAt != null && now < capSeason.petitionFilingOpensAt ->
                "Waiting for selected petitions to open for filing"

            capSeason.petitionFilingOpensAt != null && now >= capSeason.petitionFilingOpensAt ->
                "Petition filing is open"

            else ->
                "H-1B season is in progress"
        }
        val nextMilestone = milestones.firstOrNull { (it.timestamp ?: Long.MAX_VALUE) > now }
        return CapSeasonTimeline(
            fiscalYear = capSeason.fiscalYear,
            phaseLabel = phaseLabel,
            nextDeadlineLabel = nextMilestone?.title ?: "No upcoming published milestone",
            nextDeadlineAt = nextMilestone?.timestamp,
            milestones = milestones
        )
    }

    private fun buildCapGapAssessment(
        userProfile: UserProfile?,
        timelineState: H1bTimelineState,
        evidence: H1bEvidence
    ): CapGapAssessment {
        val optEndDate = userProfile?.optEndDate
        val stage = timelineState.parsedWorkflowStage
        val selectedOrLater = stage in setOf(
            H1bWorkflowStage.SELECTED,
            H1bWorkflowStage.PETITION_FILED,
            H1bWorkflowStage.RECEIPT_RECEIVED,
            H1bWorkflowStage.APPROVED
        ) || timelineState.selectedRegistration == true
        val filedOrLater = stage in setOf(
            H1bWorkflowStage.PETITION_FILED,
            H1bWorkflowStage.RECEIPT_RECEIVED,
            H1bWorkflowStage.APPROVED
        ) || timelineState.filedPetition == true
        val requestedCos = timelineState.requestedChangeOfStatus == true
        val requestedConsularProcessing = timelineState.requestedConsularProcessing == true
        val travelPlanned = evidence.capGapTravelPlanned == true

        return when {
            optEndDate == null -> CapGapAssessment(
                state = H1bCapGapState.NOT_APPLICABLE,
                title = "Cap-gap needs OPT dates first",
                summary = "Add your recorded OPT end date before relying on cap-gap guidance.",
                workAuthorizationText = "OPT end date missing."
            )

            requestedConsularProcessing -> CapGapAssessment(
                state = H1bCapGapState.NOT_ELIGIBLE,
                title = "Cap-gap does not line up with consular processing",
                summary = "Saved timeline says the case is using consular processing, so automatic cap-gap continuity should not be assumed.",
                workAuthorizationText = "Do not assume continued work authorization from cap-gap on consular processing alone."
            )

            travelPlanned && requestedCos && selectedOrLater -> CapGapAssessment(
                state = H1bCapGapState.HARD_STOP,
                title = "Cap-gap travel is a hard-stop scenario",
                summary = "Travel plans during a pending or recently filed change-of-status path can break assumptions the dashboard cannot safely validate.",
                workAuthorizationText = "Do not rely on the app for cap-gap travel clearance.",
                travelWarning = "Use DSO or attorney review before traveling during a cap-gap change-of-status path.",
                legalReviewRequired = true
            )

            filedOrLater && requestedCos -> CapGapAssessment(
                state = H1bCapGapState.ELIGIBLE,
                title = "Cap-gap may bridge status continuity",
                summary = "Saved timeline shows a filed or receipted H-1B change-of-status path that can support cap-gap continuity.",
                workAuthorizationText = "Keep the receipt notice and school guidance together while the bridge is active."
            )

            selectedOrLater && requestedCos -> CapGapAssessment(
                state = H1bCapGapState.LIKELY_ELIGIBLE_NEEDS_REVIEW,
                title = "Cap-gap looks plausible but needs review",
                summary = "Registration or selection is saved, but the petition-filing stage is not yet strong enough to treat the bridge as fully confirmed.",
                workAuthorizationText = "Treat work authorization continuity as review-sensitive until the petition stage is documented."
            )

            else -> CapGapAssessment(
                state = H1bCapGapState.NOT_ELIGIBLE,
                title = "Cap-gap is not established",
                summary = "The saved timeline does not show a qualifying change-of-status bridge yet.",
                workAuthorizationText = "Do not assume cap-gap protection from current inputs."
            )
        }
    }

    private fun isI129Case(tracker: UscisCaseTracker): Boolean {
        return tracker.formType.replace(" ", "").uppercase() == "I-129"
    }
}
