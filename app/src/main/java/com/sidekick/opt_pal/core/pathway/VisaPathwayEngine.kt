package com.sidekick.opt_pal.core.pathway

import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.data.model.VisaPathwayAction
import com.sidekick.opt_pal.data.model.VisaPathwayAssessment
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType
import com.sidekick.opt_pal.data.model.VisaPathwayEvidenceSnapshot
import com.sidekick.opt_pal.data.model.VisaPathwayGap
import com.sidekick.opt_pal.data.model.VisaPathwayH1bRegistrationStatus
import com.sidekick.opt_pal.data.model.VisaPathwayId
import com.sidekick.opt_pal.data.model.VisaPathwayMilestone
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerSummary
import com.sidekick.opt_pal.data.model.VisaPathwayRecommendation
import com.sidekick.opt_pal.data.model.VisaPathwayTrackType
import com.sidekick.opt_pal.navigation.AppScreen
import java.util.Locale

class VisaPathwayEngine {

    fun assess(
        evidence: VisaPathwayEvidenceSnapshot,
        bundle: VisaPathwayPlannerBundle,
        now: Long
    ): List<VisaPathwayAssessment> {
        val temporary = listOf(
            assessStemOpt(evidence, bundle, now),
            assessH1bCapSubject(evidence, bundle, now),
            assessH1bCapExempt(evidence, bundle, now),
            assessO1a(evidence, bundle, now)
        ).sortedWith(
            compareByDescending<VisaPathwayAssessment> { it.recommendation.priority() }
                .thenByDescending { it.rankScore }
                .thenBy { it.pathwayId.label }
        )
        val longTerm = listOf(
            buildEducationalTrack(VisaPathwayId.EB1, bundle),
            buildEducationalTrack(VisaPathwayId.EB2_NIW, bundle),
            buildEducationalTrack(VisaPathwayId.EB2_EB3_EMPLOYER, bundle)
        )
        return temporary + longTerm
    }

    fun buildSummary(
        assessments: List<VisaPathwayAssessment>,
        preferredPathwayId: VisaPathwayId?
    ): VisaPathwayPlannerSummary {
        val temporary = assessments.filter { it.trackType == VisaPathwayTrackType.TEMPORARY }
        val preferred = preferredPathwayId?.let { pathwayId ->
            temporary.firstOrNull { it.pathwayId == pathwayId }
        }
        return VisaPathwayPlannerSummary(
            topAssessment = preferred ?: temporary.firstOrNull(),
            preferredPathwayId = preferredPathwayId
        )
    }

    private fun assessStemOpt(
        evidence: VisaPathwayEvidenceSnapshot,
        bundle: VisaPathwayPlannerBundle,
        now: Long
    ): VisaPathwayAssessment {
        val reasons = mutableListOf<String>()
        val gaps = mutableListOf<VisaPathwayGap>()
        var score = 20
        if (requiresConsult(evidence, bundle, now)) {
            return consultAssessment(
                pathwayId = VisaPathwayId.STEM_OPT,
                summary = "Planner evidence shows a status or review issue that should be handled outside the app.",
                bundle = bundle,
                evidence = evidence
            )
        }
        if (evidence.optType.equals("stem", ignoreCase = true)) {
            score += 10
            reasons += "You are already on STEM OPT, so this path is your current work-authorization track."
        } else if (evidence.optType.equals("initial", ignoreCase = true)) {
            score += 12
            reasons += "You are on post-completion OPT, which is the normal starting point for a STEM OPT extension."
        } else {
            gaps += blockingGap(
                id = "missing_opt_state",
                title = "OPT status is not clear",
                detail = "The planner needs a current post-completion OPT record before it can rate STEM OPT strongly."
            )
        }
        if (evidence.isStemCipEligible == true || evidence.hasPriorUsStemDegree == true) {
            score += 22
            reasons += if (evidence.hasPriorUsStemDegree == true && evidence.isStemCipEligible != true) {
                "You reported a prior U.S. STEM degree that may support this route."
            } else {
                "Your degree evidence matches the STEM-designated CIP list used for OPT extension planning."
            }
        } else {
            gaps += blockingGap(
                id = "stem_cip_missing",
                title = "STEM degree match is not confirmed",
                detail = "The current CIP or prior-degree evidence does not confirm STEM OPT eligibility."
            )
        }
        if (evidence.employerUsesEVerify == true) {
            score += 14
            reasons += "Your employer is marked as E-Verify participating."
        } else {
            gaps += blockingGap(
                id = "e_verify_required",
                title = "E-Verify confirmation is missing",
                detail = "STEM OPT requires an E-Verify employer."
            )
        }
        if ((evidence.hoursPerWeek ?: 0) >= 20) {
            score += 12
            reasons += "Your current hours meet the 20-hour STEM OPT minimum."
        } else {
            gaps += blockingGap(
                id = "hours_under_20",
                title = "Hours are below the STEM OPT minimum",
                detail = "STEM OPT planning assumes at least 20 hours per week with a qualifying employer."
            )
        }
        if (evidence.isRoleRelatedToDegree == true) {
            score += 10
            reasons += "You marked the role as directly related to your degree."
        } else {
            gaps += VisaPathwayGap(
                id = "degree_relation_unconfirmed",
                title = "Degree relationship is not confirmed",
                detail = "You should confirm that the job is directly related to the STEM degree used for the extension.",
                isBlocking = false,
                action = openReportingAction()
            )
        }
        if (evidence.i983Ready) {
            score += 12
            reasons += "An I-983 draft is already in a ready or exported state."
        } else {
            gaps += VisaPathwayGap(
                id = "i983_not_ready",
                title = "I-983 is not ready yet",
                detail = "You need a workable I-983 before this becomes a clean STEM OPT path.",
                isBlocking = false,
                action = openI983Action()
            )
        }
        val filingRunwayDays = daysUntil(evidence.optEndDate, now)
        if (filingRunwayDays != null && filingRunwayDays > 0) {
            score += if (filingRunwayDays >= 60) 10 else 4
            reasons += "Your current OPT end date still leaves filing runway for a STEM OPT packet."
        } else {
            gaps += blockingGap(
                id = "filing_runway_missing",
                title = "The filing runway is not safe",
                detail = "The recorded OPT end date leaves no safe runway for this extension path."
            )
        }
        val capped = capForBundleAndAlerts(score, evidence, bundle, now)
        return buildAssessment(
            pathwayId = VisaPathwayId.STEM_OPT,
            rankScore = capped,
            summary = summaryFor(VisaPathwayId.STEM_OPT, recommendationFor(capped, gaps), evidence),
            whyItFits = reasons,
            gaps = gaps,
            milestones = listOfNotNull(
                evidence.optEndDate?.let {
                    VisaPathwayMilestone(
                        id = "stem_file_before_opt_end",
                        title = "Finish the STEM OPT packet before your current OPT end date",
                        detail = "Use the I-983 workflow and reporting checklist to prepare the filing set.",
                        dueDate = it,
                        action = openI983Action()
                    )
                }
            ),
            actions = listOf(openI983Action(), openReportingAction(), openPolicyAlertsAction("applications")),
            bundle = bundle,
            evidence = evidence
        )
    }

    private fun assessH1bCapSubject(
        evidence: VisaPathwayEvidenceSnapshot,
        bundle: VisaPathwayPlannerBundle,
        now: Long
    ): VisaPathwayAssessment {
        val reasons = mutableListOf<String>()
        val gaps = mutableListOf<VisaPathwayGap>()
        var score = 18
        if (requiresConsult(evidence, bundle, now)) {
            return consultAssessment(
                pathwayId = VisaPathwayId.H1B_CAP_SUBJECT,
                summary = "Planner evidence shows a status or review issue that should be handled outside the app.",
                bundle = bundle,
                evidence = evidence
            )
        }
        if (evidence.isRoleRelatedToDegree == true) {
            score += 18
            reasons += "You marked the role as directly related to the degree, which is central to H-1B specialty-occupation planning."
        } else {
            gaps += blockingGap(
                id = "specialty_role_unconfirmed",
                title = "Degree-to-role fit is not confirmed",
                detail = "The planner cannot rate H-1B strongly without confirming the specialty-occupation fit."
            )
        }
        if (evidence.employerWillSponsorH1b == true) {
            score += 18
            reasons += "You marked the employer as willing to sponsor an H-1B filing."
        } else {
            gaps += blockingGap(
                id = "sponsor_intent_missing",
                title = "Sponsor intent is not confirmed",
                detail = "Cap-subject H-1B planning depends on an employer that is actually willing to register and file."
            )
        }
        if (evidence.employerType !in setOf(
                VisaPathwayEmployerType.UNIVERSITY,
                VisaPathwayEmployerType.NONPROFIT_RESEARCH,
                VisaPathwayEmployerType.GOVERNMENT_RESEARCH
            )
        ) {
            score += 10
            reasons += "Your employer type is not marked as cap-exempt, so the regular cap-subject path still makes sense."
        } else {
            gaps += VisaPathwayGap(
                id = "employer_looks_cap_exempt",
                title = "Employer may be cap-exempt instead",
                detail = "The employer type you chose looks closer to the cap-exempt route than the cap-subject lottery path.",
                isBlocking = false,
                action = selectPathAction(VisaPathwayId.H1B_CAP_EXEMPT)
            )
        }
        if (isUsAdvancedDegreePath(evidence.degreeLevel, evidence)) {
            score += 8
            reasons += "Your degree evidence suggests a possible U.S. advanced-degree exemption angle."
        }
        when (evidence.h1bRegistrationStatus) {
            VisaPathwayH1bRegistrationStatus.SELECTED -> {
                score += 18
                reasons += "You already marked the H-1B registration as selected."
            }
            VisaPathwayH1bRegistrationStatus.REGISTERED -> {
                score += 12
                reasons += "You already marked the registration as submitted."
            }
            VisaPathwayH1bRegistrationStatus.NOT_SELECTED -> {
                gaps += blockingGap(
                    id = "not_selected",
                    title = "Current registration was not selected",
                    detail = "This season does not provide a clean cap-subject filing path without a future cycle or different route."
                )
            }
            else -> Unit
        }
        if (bundle.h1bSeason.isPublished &&
            bundle.h1bSeason.registrationCloseDate != null &&
            evidence.h1bRegistrationStatus == VisaPathwayH1bRegistrationStatus.NOT_STARTED &&
            now > bundle.h1bSeason.registrationCloseDate
        ) {
            gaps += blockingGap(
                id = "season_closed",
                title = "The published H-1B registration window has already closed",
                detail = "The current season timing does not leave a registration path in this cycle."
            )
        } else if (!bundle.h1bSeason.isPublished) {
            gaps += VisaPathwayGap(
                id = "season_unpublished",
                title = "The next H-1B season dates are not published",
                detail = "The planner does not guess cap-season dates before USCIS publishes them.",
                isBlocking = false,
                action = openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_SUBJECT)
            )
        } else {
            score += 8
            reasons += "The current reviewed bundle includes a published H-1B season timeline."
        }
        val runwayDays = daysUntil(evidence.desiredContinuityDate ?: evidence.optEndDate, now)
        if (runwayDays != null && runwayDays > 200) {
            score += 8
            reasons += "Your recorded continuity window leaves status runway through a normal cap-season cycle."
        } else if (runwayDays != null && runwayDays > 0) {
            gaps += VisaPathwayGap(
                id = "tight_runway",
                title = "Status runway is tight",
                detail = "The continuity date may be too close for a comfortable cap-subject cycle.",
                isBlocking = false,
                action = openPolicyAlertsAction("applications")
            )
        } else {
            gaps += blockingGap(
                id = "no_runway",
                title = "Status runway is not safe",
                detail = "The recorded continuity date does not leave a safe cap-subject runway."
            )
        }
        val capped = capForBundleAndAlerts(score, evidence, bundle, now)
        return buildAssessment(
            pathwayId = VisaPathwayId.H1B_CAP_SUBJECT,
            rankScore = capped,
            summary = summaryFor(VisaPathwayId.H1B_CAP_SUBJECT, recommendationFor(capped, gaps), evidence),
            whyItFits = reasons,
            gaps = gaps,
            milestones = buildH1bMilestones(bundle),
            actions = listOf(openPolicyAlertsAction("applications"), openTravelAction(), openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_SUBJECT)),
            bundle = bundle,
            evidence = evidence
        )
    }

    private fun assessH1bCapExempt(
        evidence: VisaPathwayEvidenceSnapshot,
        bundle: VisaPathwayPlannerBundle,
        now: Long
    ): VisaPathwayAssessment {
        val reasons = mutableListOf<String>()
        val gaps = mutableListOf<VisaPathwayGap>()
        var score = 16
        if (requiresConsult(evidence, bundle, now)) {
            return consultAssessment(
                pathwayId = VisaPathwayId.H1B_CAP_EXEMPT,
                summary = "Planner evidence shows a status or review issue that should be handled outside the app.",
                bundle = bundle,
                evidence = evidence
            )
        }
        if (evidence.employerType in setOf(
                VisaPathwayEmployerType.UNIVERSITY,
                VisaPathwayEmployerType.NONPROFIT_RESEARCH,
                VisaPathwayEmployerType.GOVERNMENT_RESEARCH
            )
        ) {
            score += 26
            reasons += "Your employer type lines up with the common cap-exempt categories used for H-1B planning."
        } else {
            gaps += blockingGap(
                id = "cap_exempt_employer_unconfirmed",
                title = "Cap-exempt employer type is not confirmed",
                detail = "The employer type in your planner profile does not currently look cap-exempt."
            )
        }
        if (evidence.employerWillSponsorH1b == true) {
            score += 18
            reasons += "You marked the employer as willing to sponsor an H-1B petition."
        } else {
            gaps += blockingGap(
                id = "cap_exempt_sponsor_intent",
                title = "Sponsor intent is not confirmed",
                detail = "This route only works if the cap-exempt employer is actually willing to file."
            )
        }
        if (evidence.isRoleRelatedToDegree == true) {
            score += 12
            reasons += "You marked the role as degree-related."
        } else {
            gaps += VisaPathwayGap(
                id = "cap_exempt_role_relation",
                title = "Degree relationship is not confirmed",
                detail = "You still need a clean specialty-occupation fit even in the cap-exempt path.",
                isBlocking = false
            )
        }
        if (evidence.desiredContinuityDate != null || evidence.optEndDate != null) {
            score += 8
            reasons += "You provided a continuity date the planner can use for petition timing."
        }
        gaps += VisaPathwayGap(
            id = "employer_type_unverified",
            title = "Employer type is unverified",
            detail = "The app stores your employer-type input, but it does not verify cap-exempt status on your behalf yet.",
            isBlocking = false,
            action = openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_EXEMPT)
        )
        val capped = capForBundleAndAlerts(score, evidence, bundle, now)
        return buildAssessment(
            pathwayId = VisaPathwayId.H1B_CAP_EXEMPT,
            rankScore = capped,
            summary = summaryFor(VisaPathwayId.H1B_CAP_EXEMPT, recommendationFor(capped, gaps), evidence),
            whyItFits = reasons,
            gaps = gaps,
            milestones = listOf(
                VisaPathwayMilestone(
                    id = "confirm_cap_exempt_basis",
                    title = "Confirm the employer's cap-exempt basis",
                    detail = "This path stays provisional until the employer's exempt status is confirmed.",
                    action = openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_EXEMPT)
                )
            ),
            actions = listOf(openPolicyAlertsAction("employment"), openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_EXEMPT)),
            bundle = bundle,
            evidence = evidence
        )
    }

    private fun assessO1a(
        evidence: VisaPathwayEvidenceSnapshot,
        bundle: VisaPathwayPlannerBundle,
        now: Long
    ): VisaPathwayAssessment {
        val reasons = mutableListOf<String>()
        val gaps = mutableListOf<VisaPathwayGap>()
        var score = 12
        if (requiresConsult(evidence, bundle, now)) {
            return consultAssessment(
                pathwayId = VisaPathwayId.O1A,
                summary = "Planner evidence shows a status or review issue that should be handled outside the app.",
                bundle = bundle,
                evidence = evidence
            )
        }
        if (evidence.hasPetitioningEmployerOrAgent == true) {
            score += 16
            reasons += "You indicated that a petitioning employer or agent is available."
        } else {
            gaps += blockingGap(
                id = "o1_petitioner_missing",
                title = "A petitioning employer or agent is not confirmed",
                detail = "O-1A planning needs a petitioner or agent."
            )
        }
        if (evidence.isRoleRelatedToDegree == true) {
            score += 8
            reasons += "Your current field still lines up with your degree and work history."
        }
        val evidenceCount = evidence.o1EvidenceSignals.size
        score += evidenceCount * 10
        if (evidenceCount >= 3) {
            reasons += "You marked at least three O-1A evidence buckets, which gives this route real traction."
        } else {
            gaps += blockingGap(
                id = "o1_evidence_too_thin",
                title = "O-1A evidence is still thin",
                detail = "This route should stay exploratory until you can point to at least three credible evidence buckets."
            )
        }
        if (evidenceCount == 0) {
            gaps += VisaPathwayGap(
                id = "o1_no_signal_data",
                title = "No O-1A evidence signals recorded",
                detail = "You have not filled in awards, publications, judging, press, patents, or similar evidence yet.",
                isBlocking = false,
                action = uploadDocumentAction()
            )
        }
        val capped = capForBundleAndAlerts(score, evidence, bundle, now)
        return buildAssessment(
            pathwayId = VisaPathwayId.O1A,
            rankScore = capped,
            summary = summaryFor(VisaPathwayId.O1A, recommendationFor(capped, gaps), evidence),
            whyItFits = reasons,
            gaps = gaps,
            milestones = listOf(
                VisaPathwayMilestone(
                    id = "o1_evidence_packet",
                    title = "Build an O-1A evidence packet",
                    detail = "Collect exhibits for the evidence buckets you marked in the planner.",
                    action = uploadDocumentAction()
                )
            ),
            actions = listOf(uploadDocumentAction(), openOfficialCitationAction(bundle, VisaPathwayId.O1A)),
            bundle = bundle,
            evidence = evidence
        )
    }

    private fun buildEducationalTrack(pathwayId: VisaPathwayId, bundle: VisaPathwayPlannerBundle): VisaPathwayAssessment {
        val definition = bundle.definitionFor(pathwayId)
        val steps = definition?.milestoneTemplates?.map { template ->
            VisaPathwayMilestone(
                id = template.id,
                title = template.title,
                detail = template.detail,
                action = VisaPathwayAction(
                    id = template.id,
                    label = when {
                        !template.route.isNullOrBlank() -> "Open"
                        !template.externalUrl.isNullOrBlank() -> "Open source"
                        else -> "Review"
                    },
                    route = template.route,
                    externalUrl = template.externalUrl
                )
            )
        }.orEmpty()
        return VisaPathwayAssessment(
            pathwayId = pathwayId,
            title = pathwayId.label,
            trackType = VisaPathwayTrackType.LONG_TERM,
            recommendation = VisaPathwayRecommendation.EXPLORATORY,
            rankScore = 0,
            summary = definition?.summary ?: "Educational track only in v1.",
            whyItFits = emptyList(),
            gaps = emptyList(),
            milestones = steps,
            actions = listOf(
                VisaPathwayAction(
                    id = "${pathwayId.wireValue}_visa_bulletin",
                    label = "Visa Bulletin",
                    externalUrl = bundle.visaBulletinUrl
                )
            ),
            citations = bundle.citationsFor(pathwayId),
            isEducationalOnly = true
        )
    }

    private fun buildAssessment(
        pathwayId: VisaPathwayId,
        rankScore: Int,
        summary: String,
        whyItFits: List<String>,
        gaps: List<VisaPathwayGap>,
        milestones: List<VisaPathwayMilestone>,
        actions: List<VisaPathwayAction>,
        bundle: VisaPathwayPlannerBundle,
        evidence: VisaPathwayEvidenceSnapshot
    ): VisaPathwayAssessment {
        return VisaPathwayAssessment(
            pathwayId = pathwayId,
            title = pathwayId.label,
            trackType = VisaPathwayTrackType.TEMPORARY,
            recommendation = recommendationFor(rankScore, gaps),
            rankScore = rankScore,
            summary = summary,
            whyItFits = whyItFits,
            gaps = gaps,
            milestones = milestones,
            actions = actions,
            citations = bundle.citationsFor(pathwayId),
            isEducationalOnly = false,
            policyOverlayTitle = evidence.latestCriticalPolicyAlertTitle
        )
    }

    private fun consultAssessment(
        pathwayId: VisaPathwayId,
        summary: String,
        bundle: VisaPathwayPlannerBundle,
        evidence: VisaPathwayEvidenceSnapshot
    ): VisaPathwayAssessment {
        return VisaPathwayAssessment(
            pathwayId = pathwayId,
            title = pathwayId.label,
            trackType = VisaPathwayTrackType.TEMPORARY,
            recommendation = VisaPathwayRecommendation.CONSULT_DSO_OR_ATTORNEY,
            rankScore = 0,
            summary = summary,
            whyItFits = emptyList(),
            gaps = listOf(
                blockingGap(
                    id = "consult_required",
                    title = "Manual review is required",
                    detail = "The planner found a status, case, or policy issue that should not be auto-scored."
                )
            ),
            milestones = emptyList(),
            actions = listOf(openTravelAction(), openPolicyAlertsAction("applications")),
            citations = bundle.citationsFor(pathwayId),
            isEducationalOnly = false,
            policyOverlayTitle = evidence.latestCriticalPolicyAlertTitle
        )
    }

    private fun summaryFor(
        pathwayId: VisaPathwayId,
        recommendation: VisaPathwayRecommendation,
        evidence: VisaPathwayEvidenceSnapshot
    ): String {
        return when (recommendation) {
            VisaPathwayRecommendation.STRONG_FIT -> "${pathwayId.label} looks like the strongest near-term path from the evidence the app can see."
            VisaPathwayRecommendation.POSSIBLE_WITH_GAPS -> "${pathwayId.label} could work, but the planner still sees gaps you need to close."
            VisaPathwayRecommendation.EXPLORATORY -> "${pathwayId.label} is worth tracking, but the evidence is still early."
            VisaPathwayRecommendation.NOT_A_CURRENT_FIT -> "${pathwayId.label} is not lining up with the current OPT/STEM facts."
            VisaPathwayRecommendation.CONSULT_DSO_OR_ATTORNEY -> {
                val title = evidence.latestCriticalPolicyAlertTitle
                if (title.isNullOrBlank()) {
                    "${pathwayId.label} needs manual review before the app should guide next steps."
                } else {
                    "${pathwayId.label} needs manual review because there is also an active policy overlay: $title"
                }
            }
        }
    }

    private fun recommendationFor(rankScore: Int, gaps: List<VisaPathwayGap>): VisaPathwayRecommendation {
        val blockingCount = gaps.count { it.isBlocking }
        return when {
            gaps.any { it.id in setOf("season_closed", "not_selected", "no_runway") } -> {
                VisaPathwayRecommendation.NOT_A_CURRENT_FIT
            }
            blockingCount >= 3 -> VisaPathwayRecommendation.NOT_A_CURRENT_FIT
            rankScore >= 80 && blockingCount == 0 -> VisaPathwayRecommendation.STRONG_FIT
            rankScore >= 55 -> VisaPathwayRecommendation.POSSIBLE_WITH_GAPS
            rankScore >= 35 -> VisaPathwayRecommendation.EXPLORATORY
            else -> VisaPathwayRecommendation.NOT_A_CURRENT_FIT
        }
    }

    private fun requiresConsult(
        evidence: VisaPathwayEvidenceSnapshot,
        bundle: VisaPathwayPlannerBundle,
        now: Long
    ): Boolean {
        return evidence.hasStatusEscalationIssue ||
            evidence.trackedCase?.parsedStage in setOf(
                UscisCaseStage.RFE_OR_NOID,
                UscisCaseStage.DENIED,
                UscisCaseStage.REJECTED
            )
    }

    private fun capForBundleAndAlerts(
        score: Int,
        evidence: VisaPathwayEvidenceSnapshot,
        bundle: VisaPathwayPlannerBundle,
        now: Long
    ): Int {
        var adjusted = score.coerceAtLeast(0)
        if (bundle.isStale(now)) {
            adjusted = minOf(adjusted, 69)
        }
        if (!evidence.latestCriticalPolicyAlertTitle.isNullOrBlank()) {
            adjusted = minOf(adjusted, 69)
        }
        return adjusted
    }

    private fun buildH1bMilestones(bundle: VisaPathwayPlannerBundle): List<VisaPathwayMilestone> {
        val milestones = mutableListOf<VisaPathwayMilestone>()
        bundle.h1bSeason.registrationOpenDate?.let { date ->
            milestones += VisaPathwayMilestone(
                id = "h1b_registration_open",
                title = "Registration window opens",
                detail = "USCIS opens the reviewed H-1B electronic registration window.",
                dueDate = date,
                action = openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_SUBJECT)
            )
        }
        bundle.h1bSeason.registrationCloseDate?.let { date ->
            milestones += VisaPathwayMilestone(
                id = "h1b_registration_close",
                title = "Registration window closes",
                detail = "Registrations need to be submitted before the reviewed close date.",
                dueDate = date,
                action = openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_SUBJECT)
            )
        }
        if (!bundle.h1bSeason.isPublished) {
            milestones += VisaPathwayMilestone(
                id = "h1b_dates_pending",
                title = "Wait for USCIS to publish the next H-1B season dates",
                detail = bundle.h1bSeason.notes.ifBlank {
                    "The planner leaves future registration dates unknown until USCIS publishes them."
                },
                action = openOfficialCitationAction(bundle, VisaPathwayId.H1B_CAP_SUBJECT)
            )
        }
        return milestones
    }

    private fun blockingGap(id: String, title: String, detail: String): VisaPathwayGap {
        return VisaPathwayGap(
            id = id,
            title = title,
            detail = detail,
            isBlocking = true
        )
    }

    private fun openI983Action(): VisaPathwayAction {
        return VisaPathwayAction(
            id = "open_i983",
            label = "Open I-983",
            route = AppScreen.I983Assistant.createRoute()
        )
    }

    private fun openReportingAction(): VisaPathwayAction {
        return VisaPathwayAction(
            id = "open_reporting",
            label = "Open Reporting",
            route = AppScreen.Reporting.route
        )
    }

    private fun openTravelAction(): VisaPathwayAction {
        return VisaPathwayAction(
            id = "open_travel_advisor",
            label = "Open Travel Advisor",
            route = AppScreen.TravelAdvisor.route
        )
    }

    private fun uploadDocumentAction(): VisaPathwayAction {
        return VisaPathwayAction(
            id = "upload_document",
            label = "Upload document",
            route = AppScreen.DocumentSelection.route
        )
    }

    private fun openPolicyAlertsAction(filter: String): VisaPathwayAction {
        return VisaPathwayAction(
            id = "open_policy_alerts_$filter",
            label = "Open Policy Alerts",
            route = AppScreen.PolicyAlerts.createRoute(filter = filter)
        )
    }

    private fun selectPathAction(pathwayId: VisaPathwayId): VisaPathwayAction {
        return VisaPathwayAction(
            id = "select_${pathwayId.wireValue}",
            label = "Open ${pathwayId.label}",
            route = AppScreen.VisaPathwayPlanner.createRoute(pathwayId = pathwayId.wireValue)
        )
    }

    private fun openOfficialCitationAction(bundle: VisaPathwayPlannerBundle, pathwayId: VisaPathwayId): VisaPathwayAction {
        val url = bundle.citationsFor(pathwayId).firstOrNull()?.url
        return VisaPathwayAction(
            id = "open_official_${pathwayId.wireValue}",
            label = "Open official source",
            externalUrl = url
        )
    }

    private fun isUsAdvancedDegreePath(degreeLevel: String?, evidence: VisaPathwayEvidenceSnapshot): Boolean {
        val normalized = degreeLevel.orEmpty().lowercase(Locale.US)
        return (normalized.contains("master") || normalized.contains("phd") || normalized.contains("doctor")) &&
            (
                !evidence.profile?.schoolName.isNullOrBlank() ||
                    evidence.hasPriorUsStemDegree == true
                )
    }

    private fun daysUntil(timestamp: Long?, now: Long): Int? {
        val date = timestamp ?: return null
        return ((date - now) / ONE_DAY_MILLIS).toInt()
    }

    private fun VisaPathwayRecommendation.priority(): Int {
        return when (this) {
            VisaPathwayRecommendation.STRONG_FIT -> 5
            VisaPathwayRecommendation.POSSIBLE_WITH_GAPS -> 4
            VisaPathwayRecommendation.EXPLORATORY -> 3
            VisaPathwayRecommendation.NOT_A_CURRENT_FIT -> 2
            VisaPathwayRecommendation.CONSULT_DSO_OR_ATTORNEY -> 1
        }
    }

    private companion object {
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
