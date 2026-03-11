package com.sidekick.opt_pal.core.scenario

import com.sidekick.opt_pal.core.calculations.calculateUnemploymentForecast
import com.sidekick.opt_pal.core.compliance.ComplianceScoreEngine
import com.sidekick.opt_pal.core.compliance.buildComplianceEvidenceSnapshot
import com.sidekick.opt_pal.core.h1b.H1bDashboardEngine
import com.sidekick.opt_pal.core.pathway.VisaPathwayEngine
import com.sidekick.opt_pal.core.pathway.buildVisaPathwayEvidenceSnapshot
import com.sidekick.opt_pal.core.travel.TravelRulesEngine
import com.sidekick.opt_pal.core.travel.buildTravelEvidenceSnapshot
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.EmployerChangeScenarioAssumptions
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bCaseTracking
import com.sidekick.opt_pal.data.model.H1bEmployerVerification
import com.sidekick.opt_pal.data.model.H1bEvidence
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bReadinessLevel
import com.sidekick.opt_pal.data.model.H1bTimelineState
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.JobLossScenarioAssumptions
import com.sidekick.opt_pal.data.model.PendingStemExtensionScenarioAssumptions
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.data.model.ReportingDeadlineScenarioAssumptions
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ScenarioAction
import com.sidekick.opt_pal.data.model.ScenarioAssumptions
import com.sidekick.opt_pal.data.model.ScenarioBaselineFingerprint
import com.sidekick.opt_pal.data.model.ScenarioCitation
import com.sidekick.opt_pal.data.model.ScenarioConfidence
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioImpactCard
import com.sidekick.opt_pal.data.model.ScenarioImpactGroup
import com.sidekick.opt_pal.data.model.ScenarioOutcome
import com.sidekick.opt_pal.data.model.ScenarioSimulationResult
import com.sidekick.opt_pal.data.model.ScenarioSimulatorBundle
import com.sidekick.opt_pal.data.model.ScenarioTemplateId
import com.sidekick.opt_pal.data.model.ScenarioTimelineEvent
import com.sidekick.opt_pal.data.model.TravelOutcome
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.TravelRuleId
import com.sidekick.opt_pal.data.model.TravelScenarioAssumptions
import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayAssessment
import com.sidekick.opt_pal.data.model.VisaPathwayCitation
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import com.sidekick.opt_pal.data.model.VisaPathwayRecommendation
import com.sidekick.opt_pal.data.model.TravelSourceCitation
import com.sidekick.opt_pal.navigation.AppScreen
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScenarioBaselineSnapshot(
    val userProfile: UserProfile? = null,
    val employments: List<Employment> = emptyList(),
    val reportingObligations: List<ReportingObligation> = emptyList(),
    val documents: List<DocumentMetadata> = emptyList(),
    val i983Drafts: List<I983Draft> = emptyList(),
    val trackedCases: List<UscisCaseTracker> = emptyList(),
    val plannerProfile: VisaPathwayProfile? = null,
    val h1bProfile: H1bProfile? = null,
    val h1bEmployerVerification: H1bEmployerVerification? = null,
    val h1bTimelineState: H1bTimelineState? = null,
    val h1bCaseTracking: H1bCaseTracking? = null,
    val h1bEvidence: H1bEvidence? = null,
    val policyAlerts: List<PolicyAlertCard> = emptyList(),
    val policyStates: List<PolicyAlertState> = emptyList(),
    val travelBundle: TravelPolicyBundle? = null,
    val pathwayBundle: VisaPathwayPlannerBundle? = null,
    val h1bBundle: H1bDashboardBundle? = null,
    val scenarioBundle: ScenarioSimulatorBundle? = null
) {
    fun fingerprint(): ScenarioBaselineFingerprint {
        val payload = buildString {
            append(userProfile?.optType)
            append('|').append(userProfile?.optStartDate)
            append('|').append(userProfile?.optEndDate)
            append('|').append(travelBundle?.version)
            append('|').append(pathwayBundle?.version)
            append('|').append(h1bBundle?.version)
            append('|').append(scenarioBundle?.version)
            employments.sortedBy { it.id }.forEach { append('|').append(it.id).append(':').append(it.startDate).append(':').append(it.endDate).append(':').append(it.hoursPerWeek) }
            reportingObligations.sortedBy { it.id }.forEach { append('|').append(it.id).append(':').append(it.dueDate).append(':').append(it.isCompleted) }
            trackedCases.sortedBy { it.id }.forEach { append('|').append(it.id).append(':').append(it.normalizedStage).append(':').append(it.lastChangedAt) }
        }
        return ScenarioBaselineFingerprint(md5(payload))
    }

    fun latestTrackedI765(): UscisCaseTracker? {
        return trackedCases
            .filterNot { it.isArchived }
            .filter { it.parsedFormType == com.sidekick.opt_pal.data.model.UscisTrackedFormType.I765 }
            .maxByOrNull { maxOf(it.lastCheckedAt, it.lastChangedAt) }
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}

class ScenarioSimulatorEngine(
    private val complianceScoreEngine: ComplianceScoreEngine = ComplianceScoreEngine(),
    private val visaPathwayEngine: VisaPathwayEngine = VisaPathwayEngine(),
    private val travelRulesEngine: TravelRulesEngine = TravelRulesEngine(),
    private val h1bDashboardEngine: H1bDashboardEngine = H1bDashboardEngine()
) {

    fun simulate(
        draft: ScenarioDraft,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): ScenarioSimulationResult {
        val warnings = dependencyWarnings(draft.parsedTemplateId, baseline, now)
        val computed = when (val assumptions = draft.assumptions) {
            is JobLossScenarioAssumptions -> simulateJobLoss(assumptions, baseline, now)
            is EmployerChangeScenarioAssumptions -> simulateEmployerChange(assumptions, baseline, now)
            is ReportingDeadlineScenarioAssumptions -> simulateReportingMiss(assumptions, baseline, now)
            is TravelScenarioAssumptions -> simulateTravel(assumptions, baseline, now)
            is com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions -> simulateH1bContinuity(assumptions, baseline, now)
            is PendingStemExtensionScenarioAssumptions -> simulatePendingStem(assumptions, baseline, now)
        }
        val confidence = when {
            warnings.isNotEmpty() -> ScenarioConfidence.HYPOTHETICAL
            draft.assumptions is ReportingDeadlineScenarioAssumptions &&
                (draft.assumptions as ReportingDeadlineScenarioAssumptions).obligationId != null -> ScenarioConfidence.VERIFIED
            draft.assumptions is com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions -> ScenarioConfidence.VERIFIED
            else -> ScenarioConfidence.PARTIAL
        }
        return computed.copy(
            outcome = if (warnings.isNotEmpty() && computed.outcome == ScenarioOutcome.ON_TRACK) {
                ScenarioOutcome.ACTION_REQUIRED
            } else {
                computed.outcome
            },
            confidence = mostConservativeConfidence(confidence, computed.confidence),
            dependencyWarnings = warnings,
            citations = dedupeCitations(computed.citations),
            whatThisDoesNotMean = baseline.scenarioBundle
                ?.definitionFor(draft.parsedTemplateId)
                ?.resultCaveat
                ?: "This result is advisory. It does not change your saved records or predict approvals.",
            computedAt = now
        )
    }

    private fun simulateJobLoss(
        assumptions: JobLossScenarioAssumptions,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): ScenarioSimulationResult {
        val interruptionStart = assumptions.interruptionStartDate ?: now
        val updatedEmployments = baseline.employments.map { employment ->
            if (employment.id == assumptions.employmentId || (assumptions.employmentId == null && employment.endDate == null)) {
                employment.copy(endDate = interruptionStart)
            } else {
                employment
            }
        }.let { current ->
            if (assumptions.replacementStartDate != null || assumptions.replacementEmployerName.isNotBlank()) {
                current + Employment(
                    id = "scenario_replacement",
                    employerName = assumptions.replacementEmployerName.ifBlank { "Scenario employer" },
                    startDate = assumptions.replacementStartDate ?: interruptionStart,
                    endDate = null,
                    jobTitle = "Scenario role",
                    hoursPerWeek = assumptions.replacementHoursPerWeek
                )
            } else {
                current
            }
        }
        val forecast = calculateUnemploymentForecast(
            optType = baseline.userProfile?.optType,
            optStartDate = baseline.userProfile?.optStartDate,
            unemploymentTrackingStartDate = baseline.userProfile?.unemploymentTrackingStartDate,
            optEndDate = baseline.userProfile?.optEndDate,
            employments = updatedEmployments,
            now = now
        )
        val compliance = complianceScoreEngine.score(
            evidence = buildComplianceEvidenceSnapshot(
                profile = baseline.userProfile,
                employments = updatedEmployments,
                reportingObligations = baseline.reportingObligations,
                documents = baseline.documents,
                trackedCases = baseline.trackedCases,
                policyAlerts = baseline.policyAlerts,
                policyStates = baseline.policyStates,
                now = now
            ),
            now = now
        )
        val pathwayAssessment = pathwayAssessment(
            baseline = baseline,
            now = now,
            employments = updatedEmployments,
            plannerProfile = baseline.plannerProfile
        )
        val outcome = when {
            assumptions.hasStatusViolation == true || assumptions.hasUnauthorizedEmployment == true ->
                ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY

            forecast.usedDays > forecast.allowedDays -> ScenarioOutcome.HIGH_RISK
            compliance.blockers.isNotEmpty() -> ScenarioOutcome.HIGH_RISK
            forecast.clockRunningNow -> ScenarioOutcome.ACTION_REQUIRED
            pathwayAssessment?.recommendation == VisaPathwayRecommendation.NOT_A_CURRENT_FIT -> ScenarioOutcome.ACTION_REQUIRED
            else -> ScenarioOutcome.ON_TRACK
        }
        return ScenarioSimulationResult(
            templateId = ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION,
            outcome = outcome,
            confidence = ScenarioConfidence.PARTIAL,
            headline = when (outcome) {
                ScenarioOutcome.ON_TRACK -> "The interruption still fits within the current unemployment runway."
                ScenarioOutcome.ACTION_REQUIRED -> "This interruption needs a quick status-continuity plan."
                ScenarioOutcome.HIGH_RISK -> "This interruption creates a real OPT compliance risk."
                ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY -> "This interruption needs human review."
            },
            summary = compliance.summary,
            timeline = listOfNotNull(
                ScenarioTimelineEvent("job_loss", "Interruption starts", "Scenario job end date.", interruptionStart),
                assumptions.replacementStartDate?.let {
                    ScenarioTimelineEvent("replacement", "Replacement starts", "Scenario replacement start date.", it)
                },
                forecast.projectedExceedDate?.let {
                    ScenarioTimelineEvent("limit", "Projected limit", "If the gap continues, this is when the recorded unemployment limit is reached.", it)
                }
            ),
            impactCards = listOf(
                ScenarioImpactCard(
                    id = "job_loss_unemployment",
                    group = ScenarioImpactGroup.EMPLOYMENT,
                    title = "Unemployment forecast",
                    summary = "${forecast.usedDays} of ${forecast.allowedDays} unemployment days used.",
                    outcome = if (forecast.clockRunningNow) ScenarioOutcome.ACTION_REQUIRED else ScenarioOutcome.ON_TRACK,
                    dueDate = forecast.projectedExceedDate,
                    action = ScenarioAction("open_dashboard", "Open dashboard", AppScreen.Dashboard.route)
                ),
                ScenarioImpactCard(
                    id = "job_loss_reporting",
                    group = ScenarioImpactGroup.REPORTING,
                    title = "Reporting follow-up",
                    summary = "Employer changes still need timely reporting to keep the record clean.",
                    outcome = ScenarioOutcome.ACTION_REQUIRED,
                    dueDate = interruptionStart + TEN_DAYS_MILLIS,
                    action = ScenarioAction("open_reporting", "Open Reporting", AppScreen.Reporting.route)
                ),
                ScenarioImpactCard(
                    id = "job_loss_pathway",
                    group = ScenarioImpactGroup.H1B_CONTINUITY,
                    title = "Pathway pressure",
                    summary = pathwayAssessment?.summary ?: "Visa Pathway bundle unavailable for this simulation.",
                    outcome = assessmentOutcome(pathwayAssessment),
                    action = ScenarioAction("open_planner", "Open Visa Planner", AppScreen.VisaPathwayPlanner.createRoute())
                )
            ),
            nextActions = listOf(
                ScenarioAction("open_reporting", "Open Reporting", AppScreen.Reporting.route),
                ScenarioAction("add_employment", "Add employment", AppScreen.AddEmployment.route),
                ScenarioAction("open_planner", "Open Visa Planner", AppScreen.VisaPathwayPlanner.createRoute())
            ),
            citations = pathwayAssessment?.citations?.map(::pathwayCitation).orEmpty()
        )
    }

    private fun simulateEmployerChange(
        assumptions: EmployerChangeScenarioAssumptions,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): ScenarioSimulationResult {
        val changeDate = assumptions.changeDate ?: now
        val currentEmployment = baseline.employments.firstOrNull { it.endDate == null }
        val updatedEmployments = baseline.employments.map { employment ->
            if (employment.id == currentEmployment?.id) employment.copy(endDate = changeDate) else employment
        } + Employment(
            id = "scenario_new_employer",
            employerName = assumptions.newEmployerName.ifBlank { "Scenario employer" },
            startDate = changeDate,
            endDate = null,
            jobTitle = currentEmployment?.jobTitle ?: "Scenario role",
            hoursPerWeek = assumptions.newEmployerHoursPerWeek
        )
        val updatedPlannerProfile = (baseline.plannerProfile ?: VisaPathwayProfile()).copy(
            employerUsesEVerify = assumptions.newEmployerUsesEVerify ?: baseline.plannerProfile?.employerUsesEVerify,
            roleDirectlyRelatedToDegree = assumptions.roleRelatedToDegree ?: baseline.plannerProfile?.roleDirectlyRelatedToDegree
        )
        val pathwayAssessment = pathwayAssessment(baseline, now, updatedEmployments, updatedPlannerProfile)
        val isStem = baseline.userProfile?.optType.equals("stem", ignoreCase = true)
        val missingStemDocs = isStem && (assumptions.hasNewI983 != true || assumptions.hasNewI20 != true)
        val lowHours = (assumptions.newEmployerHoursPerWeek ?: 0) in 1..19
        val outcome = when {
            missingStemDocs || lowHours -> ScenarioOutcome.HIGH_RISK
            pathwayAssessment?.recommendation == VisaPathwayRecommendation.CONSULT_DSO_OR_ATTORNEY ->
                ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY

            else -> ScenarioOutcome.ACTION_REQUIRED
        }
        return ScenarioSimulationResult(
            templateId = ScenarioTemplateId.ADD_OR_SWITCH_EMPLOYER,
            outcome = outcome,
            confidence = ScenarioConfidence.HYPOTHETICAL,
            headline = if (missingStemDocs) {
                "A STEM employer change needs the new I-983 and updated I-20 first."
            } else {
                "Employer changes stay safe only if reporting and work-hour rules remain clean."
            },
            summary = pathwayAssessment?.summary ?: "The simulator applied a hypothetical employer switch to the copied baseline.",
            timeline = listOf(
                ScenarioTimelineEvent("change", "Employer change", "Scenario change date.", changeDate),
                ScenarioTimelineEvent("report_due", "Reporting due", "Employer-change reporting should be handled quickly after the change.", changeDate + TEN_DAYS_MILLIS)
            ),
            impactCards = listOf(
                ScenarioImpactCard(
                    id = "employer_change_docs",
                    group = ScenarioImpactGroup.DOCUMENTS,
                    title = "Change-of-employer paperwork",
                    summary = when {
                        !isStem -> "This scenario does not depend on STEM-specific employer-change paperwork."
                        missingStemDocs -> "A STEM employer change is not clean until the new I-983 and updated I-20 exist."
                        else -> "You marked the new I-983 and updated I-20 as ready."
                    },
                    outcome = if (missingStemDocs) ScenarioOutcome.HIGH_RISK else ScenarioOutcome.ACTION_REQUIRED,
                    action = ScenarioAction("open_i983", "Open I-983", AppScreen.I983Assistant.createRoute())
                ),
                ScenarioImpactCard(
                    id = "employer_change_reporting",
                    group = ScenarioImpactGroup.REPORTING,
                    title = "Reporting timing",
                    summary = "Treat the employer switch as a reporting event and update the record promptly.",
                    outcome = ScenarioOutcome.ACTION_REQUIRED,
                    dueDate = changeDate + TEN_DAYS_MILLIS,
                    action = ScenarioAction("open_reporting", "Open Reporting", AppScreen.Reporting.route)
                ),
                ScenarioImpactCard(
                    id = "employer_change_pathway",
                    group = ScenarioImpactGroup.EMPLOYMENT,
                    title = "Pathway effect",
                    summary = pathwayAssessment?.summary ?: "Pathway bundle unavailable for this simulation.",
                    outcome = assessmentOutcome(pathwayAssessment),
                    action = ScenarioAction("open_planner", "Open Visa Planner", AppScreen.VisaPathwayPlanner.createRoute())
                )
            ),
            nextActions = listOf(
                ScenarioAction("open_reporting", "Open Reporting", AppScreen.Reporting.route),
                ScenarioAction("open_i983", "Open I-983", AppScreen.I983Assistant.createRoute()),
                ScenarioAction("open_planner", "Open Visa Planner", AppScreen.VisaPathwayPlanner.createRoute())
            ),
            citations = pathwayAssessment?.citations?.map(::pathwayCitation).orEmpty()
        )
    }

    private fun simulateReportingMiss(
        assumptions: ReportingDeadlineScenarioAssumptions,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): ScenarioSimulationResult {
        val dueDate = assumptions.dueDate ?: now - FIVE_DAYS_MILLIS
        val overdue = now - dueDate
        val outcome = when {
            assumptions.submittedLate == true -> ScenarioOutcome.ACTION_REQUIRED
            overdue > TEN_DAYS_MILLIS -> ScenarioOutcome.HIGH_RISK
            else -> ScenarioOutcome.ACTION_REQUIRED
        }
        return ScenarioSimulationResult(
            templateId = ScenarioTemplateId.REPORTING_DEADLINE_MISSED,
            outcome = outcome,
            confidence = if (assumptions.obligationId != null) ScenarioConfidence.VERIFIED else ScenarioConfidence.PARTIAL,
            headline = if (outcome == ScenarioOutcome.HIGH_RISK) {
                "The reporting miss is now a compliance problem."
            } else {
                "The reporting miss still needs cleanup and documentation."
            },
            summary = "This scenario does not change your real obligations. It only projects the compliance effect of an overdue reporting item.",
            timeline = listOf(
                ScenarioTimelineEvent("report_due", "Reporting due date", assumptions.reportingLabel.ifBlank { "Scenario reporting deadline." }, dueDate)
            ),
            impactCards = listOf(
                ScenarioImpactCard(
                    id = "reporting_deadline",
                    group = ScenarioImpactGroup.REPORTING,
                    title = "Overdue reporting",
                    summary = assumptions.reportingLabel.ifBlank { "Missed reporting deadline." },
                    outcome = outcome,
                    dueDate = dueDate,
                    action = ScenarioAction("open_reporting", "Open Reporting", AppScreen.Reporting.route)
                )
            ),
            nextActions = listOf(
                ScenarioAction("open_reporting", "Open Reporting", AppScreen.Reporting.route),
                ScenarioAction("open_i983", "Open I-983", AppScreen.I983Assistant.createRoute())
            )
        )
    }

    private fun simulateTravel(
        assumptions: TravelScenarioAssumptions,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): ScenarioSimulationResult {
        val bundle = baseline.travelBundle ?: return missingBundleResult(
            ScenarioTemplateId.INTERNATIONAL_TRAVEL,
            "Travel guidance is unavailable.",
            "The simulator cannot run the travel rules without the current travel bundle.",
            ScenarioAction("open_travel", "Open Travel Advisor", AppScreen.TravelAdvisor.route)
        )
        val evidence = buildTravelEvidenceSnapshot(
            documents = baseline.documents,
            profile = baseline.userProfile,
            employments = baseline.employments,
            now = now
        )
        val mergedInput = com.sidekick.opt_pal.feature.travel.TravelAdvisorViewModel.mergeTripInputWithEvidence(
            current = assumptions.tripInput,
            evidence = evidence,
            profile = baseline.userProfile,
            now = now
        )
        val assessment = travelRulesEngine.assess(mergedInput, evidence, bundle, now)
        return ScenarioSimulationResult(
            templateId = ScenarioTemplateId.INTERNATIONAL_TRAVEL,
            outcome = when (assessment.outcome) {
                TravelOutcome.GO -> ScenarioOutcome.ON_TRACK
                TravelOutcome.CAUTION -> ScenarioOutcome.ACTION_REQUIRED
                TravelOutcome.NO_GO -> ScenarioOutcome.HIGH_RISK
                TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY -> ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY
            },
            confidence = if (bundle.isStale(now)) ScenarioConfidence.HYPOTHETICAL else ScenarioConfidence.PARTIAL,
            headline = assessment.headline,
            summary = assessment.summary,
            timeline = listOfNotNull(
                mergedInput.departureDate?.let { ScenarioTimelineEvent("departure", "Departure", "Scenario departure date.", it) },
                mergedInput.plannedReturnDate?.let { ScenarioTimelineEvent("return", "Planned return", "Scenario return date.", it) }
            ),
            impactCards = assessment.checklistItems
                .filterNot { it.status == com.sidekick.opt_pal.data.model.TravelChecklistStatus.PASS }
                .map(::travelImpact),
            nextActions = listOf(
                ScenarioAction("open_travel", "Open Travel Advisor", AppScreen.TravelAdvisor.route),
                ScenarioAction("upload_document", "Upload document", AppScreen.DocumentSelection.route)
            ),
            citations = assessment.checklistItems.flatMap { item -> item.citations.map(::travelCitation) }
        )
    }

    private fun simulateH1bContinuity(
        assumptions: com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): ScenarioSimulationResult {
        val bundle = baseline.h1bBundle ?: return missingBundleResult(
            ScenarioTemplateId.H1B_CAP_CONTINUITY,
            "The H-1B bundle is unavailable.",
            "The simulator cannot evaluate cap-gap continuity without the reviewed H-1B bundle.",
            ScenarioAction("open_h1b", "Open H-1B Dashboard", AppScreen.H1bDashboard.route)
        )
        val timeline = (baseline.h1bTimelineState ?: H1bTimelineState()).copy(
            workflowStage = assumptions.workflowStage,
            selectedRegistration = assumptions.selectedRegistration,
            filedPetition = assumptions.filedPetition,
            requestedChangeOfStatus = assumptions.requestedChangeOfStatus,
            requestedConsularProcessing = assumptions.requestedConsularProcessing
        )
        val evidence = (baseline.h1bEvidence ?: H1bEvidence()).copy(
            capGapTravelPlanned = assumptions.travelPlanned,
            hasReceiptNotice = assumptions.hasReceiptNotice
        )
        val computed = h1bDashboardEngine.build(
            userProfile = baseline.userProfile,
            h1bProfile = baseline.h1bProfile ?: H1bProfile(),
            employerVerification = baseline.h1bEmployerVerification ?: H1bEmployerVerification(),
            timelineState = timeline,
            caseTracking = baseline.h1bCaseTracking ?: H1bCaseTracking(),
            evidence = evidence,
            trackedCases = baseline.trackedCases,
            bundle = bundle,
            now = now
        )
        val outcome = when {
            computed.capGapAssessment.legalReviewRequired -> ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY
            computed.readinessSummary.level == H1bReadinessLevel.ATTENTION_NEEDED -> ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY
            computed.capGapAssessment.state == com.sidekick.opt_pal.data.model.H1bCapGapState.ELIGIBLE -> ScenarioOutcome.ON_TRACK
            computed.capGapAssessment.state == com.sidekick.opt_pal.data.model.H1bCapGapState.LIKELY_ELIGIBLE_NEEDS_REVIEW -> ScenarioOutcome.ACTION_REQUIRED
            else -> ScenarioOutcome.HIGH_RISK
        }
        return ScenarioSimulationResult(
            templateId = ScenarioTemplateId.H1B_CAP_CONTINUITY,
            outcome = outcome,
            confidence = if (bundle.isStale(now)) ScenarioConfidence.HYPOTHETICAL else ScenarioConfidence.VERIFIED,
            headline = computed.capGapAssessment.title.ifBlank { computed.readinessSummary.title },
            summary = computed.capGapAssessment.summary.ifBlank { computed.readinessSummary.summary },
            timeline = computed.capSeasonTimeline.milestones.map {
                ScenarioTimelineEvent(it.id, it.title, it.detail, it.timestamp)
            },
            impactCards = listOf(
                ScenarioImpactCard(
                    id = "h1b_gap",
                    group = ScenarioImpactGroup.H1B_CONTINUITY,
                    title = computed.capGapAssessment.title,
                    summary = computed.capGapAssessment.workAuthorizationText,
                    outcome = outcome,
                    action = ScenarioAction("open_h1b", "Open H-1B Dashboard", AppScreen.H1bDashboard.route)
                ),
                ScenarioImpactCard(
                    id = "h1b_case",
                    group = ScenarioImpactGroup.DOCUMENTS,
                    title = computed.readinessSummary.title,
                    summary = computed.readinessSummary.nextAction,
                    outcome = when (computed.readinessSummary.level) {
                        H1bReadinessLevel.READY,
                        H1bReadinessLevel.IN_PROGRESS -> ScenarioOutcome.ON_TRACK
                        H1bReadinessLevel.NEEDS_INPUTS,
                        H1bReadinessLevel.WAITING_ON_EMPLOYER -> ScenarioOutcome.ACTION_REQUIRED
                        H1bReadinessLevel.ATTENTION_NEEDED -> ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY
                    },
                    action = ScenarioAction("open_case_tracker", "Open USCIS tracker", AppScreen.CaseStatus.createRoute())
                )
            ),
            nextActions = listOf(
                ScenarioAction("open_h1b", "Open H-1B Dashboard", AppScreen.H1bDashboard.route),
                ScenarioAction("open_case_tracker", "Open USCIS tracker", AppScreen.CaseStatus.createRoute())
            ),
            citations = bundle.ruleCards.flatMap { rule ->
                rule.citationIds.mapNotNull(bundle::citationById).map { citation ->
                    ScenarioCitation(citation.id, citation.label, citation.url, citation.effectiveDate, citation.lastReviewedAt, citation.summary)
                }
            }
        )
    }

    private fun simulatePendingStem(
        assumptions: PendingStemExtensionScenarioAssumptions,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): ScenarioSimulationResult {
        val optEndDate = assumptions.optEndDate ?: baseline.userProfile?.optEndDate
        val filingDate = assumptions.filingDate
        val timelyFiled = filingDate != null && optEndDate != null && filingDate <= optEndDate
        val bridgeEnd = optEndDate?.plus(ONE_HUNDRED_EIGHTY_DAYS_MILLIS)
        val trackedCase = baseline.latestTrackedI765()
        val denied = trackedCase?.parsedStage in setOf(UscisCaseStage.DENIED, UscisCaseStage.REJECTED, UscisCaseStage.WITHDRAWN)
        val employerChangeRisk = assumptions.employerChangePlanned == true &&
            (assumptions.hasNewI983 != true || assumptions.hasNewI20 != true)
        val outcome = when {
            assumptions.travelPlanned == true -> ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY
            denied || !timelyFiled || employerChangeRisk -> ScenarioOutcome.HIGH_RISK
            assumptions.hasReceiptNotice != true -> ScenarioOutcome.ACTION_REQUIRED
            else -> ScenarioOutcome.ON_TRACK
        }
        val pathwayAssessment = pathwayAssessment(
            baseline = baseline,
            now = now,
            employments = baseline.employments,
            plannerProfile = baseline.plannerProfile
        )
        return ScenarioSimulationResult(
            templateId = ScenarioTemplateId.PENDING_STEM_EXTENSION,
            outcome = outcome,
            confidence = if (filingDate != null && optEndDate != null) ScenarioConfidence.PARTIAL else ScenarioConfidence.HYPOTHETICAL,
            headline = when (outcome) {
                ScenarioOutcome.ON_TRACK -> "A timely STEM filing can still preserve the 180-day bridge."
                ScenarioOutcome.ACTION_REQUIRED -> "The STEM bridge is plausible, but it is not fully documented."
                ScenarioOutcome.HIGH_RISK -> "The STEM bridge does not look clean under this scenario."
                ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY -> "Travel during a pending STEM bridge needs human review."
            },
            summary = "This simulator uses the F-1-specific 180-day STEM auto-extension rule, not the general 540-day EAD extension.",
            timeline = listOfNotNull(
                filingDate?.let { ScenarioTimelineEvent("filing", "Filing date", "Scenario STEM filing date.", it) },
                optEndDate?.let { ScenarioTimelineEvent("opt_end", "OPT end", "Recorded OPT end date used for the bridge calculation.", it) },
                bridgeEnd?.let { ScenarioTimelineEvent("bridge_end", "180-day bridge end", "Timely STEM filings can extend work authorization for up to 180 days while pending.", it) }
            ),
            impactCards = listOf(
                ScenarioImpactCard(
                    id = "stem_bridge",
                    group = ScenarioImpactGroup.EMPLOYMENT,
                    title = "180-day bridge",
                    summary = when {
                        !timelyFiled -> "The filing misses the current OPT end date, so the STEM bridge does not line up."
                        bridgeEnd == null -> "Add the current OPT end date to verify the bridge window."
                        else -> "A timely STEM filing may preserve work authorization through ${formatUtcDate(bridgeEnd)} while pending."
                    },
                    outcome = if (timelyFiled) ScenarioOutcome.ACTION_REQUIRED else ScenarioOutcome.HIGH_RISK,
                    dueDate = bridgeEnd,
                    action = ScenarioAction("open_case_tracker", "Open USCIS tracker", AppScreen.CaseStatus.createRoute())
                ),
                ScenarioImpactCard(
                    id = "stem_docs",
                    group = ScenarioImpactGroup.DOCUMENTS,
                    title = "Bridge evidence",
                    summary = if (assumptions.hasReceiptNotice == true) {
                        "You marked the STEM receipt notice as available."
                    } else {
                        "Keep the STEM receipt notice, updated STEM I-20, and original EAD together before relying on the bridge."
                    },
                    outcome = if (assumptions.hasReceiptNotice == true) ScenarioOutcome.ON_TRACK else ScenarioOutcome.ACTION_REQUIRED,
                    action = ScenarioAction("open_i983", "Open I-983", AppScreen.I983Assistant.createRoute())
                ),
                ScenarioImpactCard(
                    id = "stem_pathway",
                    group = ScenarioImpactGroup.H1B_CONTINUITY,
                    title = "Pathway pressure",
                    summary = pathwayAssessment?.summary ?: "Pathway bundle unavailable for this simulation.",
                    outcome = assessmentOutcome(pathwayAssessment),
                    action = ScenarioAction("open_planner", "Open Visa Planner", AppScreen.VisaPathwayPlanner.createRoute())
                )
            ),
            nextActions = listOf(
                ScenarioAction("open_case_tracker", "Open USCIS tracker", AppScreen.CaseStatus.createRoute()),
                ScenarioAction("open_i983", "Open I-983", AppScreen.I983Assistant.createRoute()),
                ScenarioAction("open_reporting", "Open Reporting", AppScreen.Reporting.route)
            ),
            citations = pathwayAssessment?.citations?.map(::pathwayCitation).orEmpty()
        )
    }

    private fun pathwayAssessment(
        baseline: ScenarioBaselineSnapshot,
        now: Long,
        employments: List<Employment>,
        plannerProfile: VisaPathwayProfile?
    ): VisaPathwayAssessment? {
        val bundle = baseline.pathwayBundle ?: return null
        val snapshot = buildVisaPathwayEvidenceSnapshot(
            profile = baseline.userProfile,
            plannerProfile = plannerProfile,
            employments = employments,
            reportingObligations = baseline.reportingObligations,
            documents = baseline.documents,
            i983Drafts = baseline.i983Drafts,
            trackedCases = baseline.trackedCases,
            policyAlerts = baseline.policyAlerts,
            policyStates = baseline.policyStates,
            bundle = bundle
        )
        return visaPathwayEngine.buildSummary(
            assessments = visaPathwayEngine.assess(snapshot, bundle, now),
            preferredPathwayId = plannerProfile?.parsedPreferredPathwayId
        ).topAssessment
    }

    private fun assessmentOutcome(assessment: VisaPathwayAssessment?): ScenarioOutcome {
        return when (assessment?.recommendation) {
            VisaPathwayRecommendation.STRONG_FIT -> ScenarioOutcome.ON_TRACK
            VisaPathwayRecommendation.POSSIBLE_WITH_GAPS,
            VisaPathwayRecommendation.EXPLORATORY -> ScenarioOutcome.ACTION_REQUIRED

            VisaPathwayRecommendation.NOT_A_CURRENT_FIT -> ScenarioOutcome.HIGH_RISK
            VisaPathwayRecommendation.CONSULT_DSO_OR_ATTORNEY -> ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY
            null -> ScenarioOutcome.ACTION_REQUIRED
        }
    }

    private fun travelImpact(item: com.sidekick.opt_pal.data.model.TravelChecklistItem): ScenarioImpactCard {
        val group = when (item.ruleId) {
            TravelRuleId.EMPLOYMENT_EVIDENCE,
            TravelRuleId.UNEMPLOYMENT_LIMIT -> ScenarioImpactGroup.EMPLOYMENT

            TravelRuleId.ESCALATION,
            TravelRuleId.GRACE_PERIOD,
            TravelRuleId.COUNTRY_RESTRICTIONS -> ScenarioImpactGroup.TRAVEL

            else -> ScenarioImpactGroup.DOCUMENTS
        }
        return ScenarioImpactCard(
            id = item.ruleId.name.lowercase(Locale.US),
            group = group,
            title = item.title,
            summary = item.detail,
            outcome = when (item.status) {
                com.sidekick.opt_pal.data.model.TravelChecklistStatus.PASS -> ScenarioOutcome.ON_TRACK
                com.sidekick.opt_pal.data.model.TravelChecklistStatus.CAUTION -> ScenarioOutcome.ACTION_REQUIRED
                com.sidekick.opt_pal.data.model.TravelChecklistStatus.BLOCK -> ScenarioOutcome.HIGH_RISK
                com.sidekick.opt_pal.data.model.TravelChecklistStatus.ESCALATE -> ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY
            }
        )
    }

    private fun travelCitation(citation: TravelSourceCitation): ScenarioCitation {
        return ScenarioCitation(
            id = citation.id,
            label = citation.label,
            url = citation.url,
            effectiveDate = citation.effectiveDate,
            lastReviewedDate = citation.lastReviewedDate,
            summary = citation.summary
        )
    }

    private fun pathwayCitation(citation: VisaPathwayCitation): ScenarioCitation {
        return ScenarioCitation(
            id = citation.id,
            label = citation.label,
            url = citation.url,
            effectiveDate = citation.effectiveDate,
            lastReviewedDate = citation.lastReviewedDate,
            summary = citation.summary
        )
    }

    private fun dependencyWarnings(
        templateId: ScenarioTemplateId,
        baseline: ScenarioBaselineSnapshot,
        now: Long
    ): List<String> {
        val warnings = mutableListOf<String>()
        if (baseline.scenarioBundle == null || baseline.scenarioBundle.isStale(now)) warnings += "Scenario bundle is stale."
        if (templateId == ScenarioTemplateId.INTERNATIONAL_TRAVEL &&
            (baseline.travelBundle == null || baseline.travelBundle.isStale(now))
        ) warnings += "Travel bundle is stale."
        if (templateId == ScenarioTemplateId.H1B_CAP_CONTINUITY &&
            (baseline.h1bBundle == null || baseline.h1bBundle.isStale(now))
        ) warnings += "H-1B bundle is stale."
        if (templateId in setOf(
                ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION,
                ScenarioTemplateId.ADD_OR_SWITCH_EMPLOYER,
                ScenarioTemplateId.PENDING_STEM_EXTENSION
            ) && (baseline.pathwayBundle == null || baseline.pathwayBundle.isStale(now))
        ) warnings += "Visa Pathway bundle is stale."
        return warnings
    }

    private fun missingBundleResult(
        templateId: ScenarioTemplateId,
        headline: String,
        summary: String,
        action: ScenarioAction
    ): ScenarioSimulationResult {
        return ScenarioSimulationResult(
            templateId = templateId,
            outcome = ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY,
            confidence = ScenarioConfidence.HYPOTHETICAL,
            headline = headline,
            summary = summary,
            impactCards = listOf(
                ScenarioImpactCard(
                    id = "missing_bundle",
                    group = ScenarioImpactGroup.DOCUMENTS,
                    title = "Policy dependency unavailable",
                    summary = summary,
                    outcome = ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY,
                    action = action
                )
            ),
            nextActions = listOf(action)
        )
    }

    private fun formatUtcDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestamp))
    }

    private fun dedupeCitations(citations: List<ScenarioCitation>): List<ScenarioCitation> {
        return citations.associateBy { it.id.ifBlank { it.url } }.values.toList()
    }

    private fun mostConservativeConfidence(
        left: ScenarioConfidence,
        right: ScenarioConfidence
    ): ScenarioConfidence {
        return if (left.severity() >= right.severity()) left else right
    }

    private fun ScenarioConfidence.severity(): Int {
        return when (this) {
            ScenarioConfidence.VERIFIED -> 0
            ScenarioConfidence.PARTIAL -> 1
            ScenarioConfidence.HYPOTHETICAL -> 2
        }
    }

    private companion object {
        const val TEN_DAYS_MILLIS = 10L * 24L * 60L * 60L * 1000L
        const val FIVE_DAYS_MILLIS = 5L * 24L * 60L * 60L * 1000L
        const val ONE_HUNDRED_EIGHTY_DAYS_MILLIS = 180L * 24L * 60L * 60L * 1000L
    }
}
