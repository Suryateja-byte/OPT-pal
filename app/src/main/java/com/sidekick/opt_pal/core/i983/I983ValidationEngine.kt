package com.sidekick.opt_pal.core.i983

import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.I983Assessment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983PolicyBundle
import com.sidekick.opt_pal.data.model.I983Readiness
import com.sidekick.opt_pal.data.model.I983ValidationIssue
import com.sidekick.opt_pal.data.model.I983ValidationSeverity
import com.sidekick.opt_pal.data.model.I983WorkflowType
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UserProfile
import kotlin.math.absoluteValue

class I983ValidationEngine(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    fun assess(
        draft: I983Draft,
        policyBundle: I983PolicyBundle?,
        profile: UserProfile?,
        linkedEmployment: Employment?,
        linkedObligation: ReportingObligation?,
        autofillIssues: List<I983ValidationIssue> = emptyList(),
        now: Long = timeProvider()
    ): I983Assessment {
        val issues = mutableListOf<I983ValidationIssue>()
        issues += autofillIssues

        if (!profile?.optType.equals("stem", ignoreCase = true)) {
            issues += escalationIssue(
                id = "non_stem_scope",
                fieldKey = "workflowType",
                message = "The I-983 assistant only supports STEM OPT workflows."
            )
        }

        if (policyBundle == null) {
            issues += blockerIssue(
                id = "missing_policy_bundle",
                fieldKey = "policyVersion",
                message = "The I-983 policy bundle is still loading. Refresh and try again."
            )
        } else if (policyBundle.isStale(now)) {
            issues += warningIssue(
                id = "stale_policy_bundle",
                fieldKey = "policyVersion",
                message = "This policy bundle is older than the freshness target. Refresh before you export."
            )
        }

        val workflowType = draft.parsedWorkflowType
        validateStudentSection(draft, issues)
        validateEmployerSection(draft, issues)
        validateTrainingPlanSection(draft, issues)
        validateWorkflowSpecificRules(draft, workflowType, linkedEmployment, linkedObligation, issues, now)
        validateNarrativeClassification(draft, issues)
        validateEmployerConsistency(draft, linkedEmployment, issues)

        val readiness = when {
            issues.any { it.severity == I983ValidationSeverity.ESCALATE } ->
                I983Readiness.CONTACT_DSO_OR_ATTORNEY
            issues.any { it.severity == I983ValidationSeverity.BLOCKER || it.severity == I983ValidationSeverity.CONFLICT } ->
                I983Readiness.NEEDS_INPUT
            else -> I983Readiness.READY_TO_EXPORT
        }

        return I983Assessment(
            readiness = readiness,
            headline = when (readiness) {
                I983Readiness.NEEDS_INPUT -> "Needs input before export"
                I983Readiness.READY_TO_EXPORT -> "Ready to export the official form"
                I983Readiness.CONTACT_DSO_OR_ATTORNEY -> "Contact your DSO or attorney before using this draft"
            },
            summary = when (readiness) {
                I983Readiness.NEEDS_INPUT -> "Finish the missing required fields and resolve any conflicts before exporting the official I-983."
                I983Readiness.READY_TO_EXPORT -> "The tracked required fields are present and no blockers are currently detected."
                I983Readiness.CONTACT_DSO_OR_ATTORNEY -> "This draft includes a risk flag or out-of-scope condition that should not be handled as a normal self-serve export."
            },
            issues = issues.sortedByDescending { severityRank(it.severity) },
            citations = policyBundle?.citationsFor(workflowType).orEmpty(),
            requiresConflictReview = issues.any { it.severity == I983ValidationSeverity.CONFLICT }
        )
    }

    private fun validateStudentSection(
        draft: I983Draft,
        issues: MutableList<I983ValidationIssue>
    ) {
        val student = draft.studentSection
        requireText(student.studentName, "studentName", "Add the student's legal name exactly as it appears on the form.", issues)
        requireText(student.studentEmailAddress, "studentEmailAddress", "Add the student's email address.", issues)
        requireText(student.schoolRecommendingStemOpt, "schoolRecommendingStemOpt", "Add the school recommending STEM OPT.", issues)
        requireText(student.schoolWhereDegreeWasEarned, "schoolWhereDegreeWasEarned", "Add the school where the qualifying STEM degree was earned.", issues)
        requireText(student.sevisSchoolCode, "sevisSchoolCode", "Add the SEVIS school code.", issues)
        requireText(student.dsoNameAndContact, "dsoNameAndContact", "Add the DSO name and contact details.", issues)
        requireText(student.studentSevisId, "studentSevisId", "Add the SEVIS ID.", issues)
        requireDate(student.requestedStartDate, "requestedStartDate", "Add the training plan start date.", issues)
        requireDate(student.requestedEndDate, "requestedEndDate", "Add the training plan end date.", issues)
        requireText(student.qualifyingMajorAndCipCode, "qualifyingMajorAndCipCode", "Add the qualifying major and CIP code.", issues)
        requireText(student.degreeLevel, "degreeLevel", "Add the qualifying STEM degree level.", issues)
        requireDate(student.degreeAwardedDate, "degreeAwardedDate", "Add the degree-awarded date.", issues)
        requireText(student.employmentAuthorizationNumber, "employmentAuthorizationNumber", "Add the employment authorization number from the EAD.", issues)
    }

    private fun validateEmployerSection(
        draft: I983Draft,
        issues: MutableList<I983ValidationIssue>
    ) {
        val employer = draft.employerSection
        requireText(employer.employerName, "employerName", "Add the employer name.", issues)
        requireText(employer.streetAddress, "streetAddress", "Add the employer street address.", issues)
        requireText(employer.employerWebsiteUrl, "employerWebsiteUrl", "Add the employer website.", issues)
        requireText(employer.city, "city", "Add the employer city.", issues)
        requireText(employer.state, "state", "Add the employer state.", issues)
        requireText(employer.zipCode, "zipCode", "Add the employer ZIP code.", issues)
        requireText(employer.employerEin, "employerEin", "Add the employer EIN.", issues)
        requireText(employer.naicsCode, "naicsCode", "Add the employer NAICS code.", issues)
        requireText(employer.salaryAmountAndFrequency, "salaryAmountAndFrequency", "Add the compensation description.", issues)
        requireDate(employer.employmentStartDate, "employmentStartDate", "Add the employment start date.", issues)
        requireText(employer.employerOfficialNameAndTitle, "employerOfficialNameAndTitle", "Add the employer official with signatory authority.", issues)
        requireText(employer.employingOrganizationName, "employingOrganizationName", "Add the employing organization name.", issues)

        val hours = employer.hoursPerWeek
        if (hours == null) {
            issues += blockerIssue(
                id = "hours_per_week_missing",
                fieldKey = "hoursPerWeek",
                message = "Add the weekly hours. STEM OPT employment must be at least 20 hours per week."
            )
        } else if (hours < 20) {
            issues += blockerIssue(
                id = "hours_per_week_under_minimum",
                fieldKey = "hoursPerWeek",
                message = "This workflow is blocked because the recorded weekly hours are below 20."
            )
        }
    }

    private fun validateTrainingPlanSection(
        draft: I983Draft,
        issues: MutableList<I983ValidationIssue>
    ) {
        val training = draft.trainingPlanSection
        requireText(training.siteName, "siteName", "Add the worksite name.", issues)
        requireText(training.siteAddress, "siteAddress", "Add the worksite address.", issues)
        requireText(training.officialName, "officialName", "Add the employer official's name.", issues)
        requireText(training.officialTitle, "officialTitle", "Add the employer official's title.", issues)
        requireText(training.officialEmail, "officialEmail", "Add the employer official's email.", issues)
        requireText(training.officialPhoneNumber, "officialPhoneNumber", "Add the employer official's phone number.", issues)
        requireText(training.studentRole, "studentRole", "Describe the student's role and responsibilities.", issues)
        requireText(training.goalsAndObjectives, "goalsAndObjectives", "Describe the goals and objectives for the training plan.", issues)
        requireText(training.employerOversight, "employerOversight", "Describe employer oversight for the training plan.", issues)
        requireText(training.measuresAndAssessments, "measuresAndAssessments", "Describe how progress will be measured and assessed.", issues)
    }

    private fun validateWorkflowSpecificRules(
        draft: I983Draft,
        workflowType: I983WorkflowType,
        linkedEmployment: Employment?,
        linkedObligation: ReportingObligation?,
        issues: MutableList<I983ValidationIssue>,
        now: Long
    ) {
        when (workflowType) {
            I983WorkflowType.MATERIAL_CHANGE -> {
                if (draft.revisionOfDraftId.isBlank()) {
                    issues += blockerIssue(
                        id = "material_change_requires_revision",
                        fieldKey = "revisionOfDraftId",
                        message = "Material-change workflows must start from an existing I-983 draft for the same employer."
                    )
                }
            }

            I983WorkflowType.ANNUAL_EVALUATION -> {
                requireDate(draft.evaluationSection.annualEvaluationFromDate, "annualEvaluationFromDate", "Add the annual evaluation start date.", issues)
                requireDate(draft.evaluationSection.annualEvaluationToDate, "annualEvaluationToDate", "Add the annual evaluation end date.", issues)
                requireText(draft.evaluationSection.annualEvaluationText, "annualEvaluationText", "Add the annual self-evaluation text.", issues)
            }

            I983WorkflowType.FINAL_EVALUATION -> {
                requireDate(draft.evaluationSection.finalEvaluationFromDate, "finalEvaluationFromDate", "Add the final evaluation start date.", issues)
                requireDate(draft.evaluationSection.finalEvaluationToDate, "finalEvaluationToDate", "Add the final evaluation end date.", issues)
                requireText(draft.evaluationSection.finalEvaluationText, "finalEvaluationText", "Add the final self-evaluation text.", issues)
                if (linkedEmployment?.endDate == null && linkedObligation?.eventDate == null) {
                    issues += warningIssue(
                        id = "final_evaluation_missing_end_event",
                        fieldKey = "finalEvaluationToDate",
                        message = "Final evaluations normally follow an employer-end event. Confirm the evaluation window manually."
                    )
                }
            }

            else -> Unit
        }

        linkedObligation?.dueDate?.let { dueDate ->
            if (dueDate < now) {
                issues += warningIssue(
                    id = "workflow_overdue",
                    fieldKey = "dueDate",
                    message = "This I-983 workflow appears overdue based on the linked reporting obligation."
                )
            } else if (dueDate - now <= TEN_DAYS_MILLIS) {
                issues += warningIssue(
                    id = "workflow_due_soon",
                    fieldKey = "dueDate",
                    message = "This workflow is due within 10 days. Review and export promptly."
                )
            }
        }
    }

    private fun validateNarrativeClassification(
        draft: I983Draft,
        issues: MutableList<I983ValidationIssue>
    ) {
        val narrative = draft.generatedNarrative ?: return
        if (narrative.parsedClassification == com.sidekick.opt_pal.data.model.I983NarrativeClassification.CONSULT_DSO_ATTORNEY) {
            issues += escalationIssue(
                id = "consult_dso_attorney",
                fieldKey = "generatedNarrative",
                message = "The narrative generator flagged this draft for DSO or attorney review."
            )
        }
        if (narrative.missingInputs.isNotEmpty()) {
            issues += warningIssue(
                id = "narrative_missing_inputs",
                fieldKey = "generatedNarrative",
                message = "The AI draft still lists missing inputs: ${narrative.missingInputs.joinToString(", ")}."
            )
        }
        narrative.warnings.forEachIndexed { index, warning ->
            issues += warningIssue(
                id = "narrative_warning_$index",
                fieldKey = "generatedNarrative",
                message = warning
            )
        }
    }

    private fun validateEmployerConsistency(
        draft: I983Draft,
        linkedEmployment: Employment?,
        issues: MutableList<I983ValidationIssue>
    ) {
        val employer = draft.employerSection
        val organizationName = employer.employingOrganizationName
        if (employer.employerName.isNotBlank() &&
            organizationName.isNotBlank() &&
            normalizeLooseValue(employer.employerName) != normalizeLooseValue(organizationName)
        ) {
            issues += warningIssue(
                id = "employer_name_mismatch",
                fieldKey = "employingOrganizationName",
                message = "The employer name and employing organization name do not match exactly. Confirm the entity details."
            )
        }
        if (linkedEmployment != null &&
            draft.linkedEmploymentId.isNotBlank() &&
            draft.linkedEmploymentId == linkedEmployment.id &&
            normalizeLooseValue(linkedEmployment.employerName) != normalizeLooseValue(employer.employerName)
        ) {
            issues += warningIssue(
                id = "employment_record_mismatch",
                fieldKey = "employerName",
                message = "The draft employer name does not match the linked employment record."
            )
        }
    }

    private fun requireText(
        value: String,
        fieldKey: String,
        message: String,
        issues: MutableList<I983ValidationIssue>
    ) {
        if (value.isBlank()) {
            issues += blockerIssue(
                id = "${fieldKey}_required",
                fieldKey = fieldKey,
                message = message
            )
        }
    }

    private fun requireDate(
        value: Long?,
        fieldKey: String,
        message: String,
        issues: MutableList<I983ValidationIssue>
    ) {
        if (value == null || value <= 0L) {
            issues += blockerIssue(
                id = "${fieldKey}_required",
                fieldKey = fieldKey,
                message = message
            )
        }
    }

    private fun blockerIssue(id: String, fieldKey: String, message: String): I983ValidationIssue {
        return I983ValidationIssue(
            id = id,
            fieldKey = fieldKey,
            message = message,
            severity = I983ValidationSeverity.BLOCKER
        )
    }

    private fun warningIssue(id: String, fieldKey: String, message: String): I983ValidationIssue {
        return I983ValidationIssue(
            id = id,
            fieldKey = fieldKey,
            message = message,
            severity = I983ValidationSeverity.WARNING
        )
    }

    private fun escalationIssue(id: String, fieldKey: String, message: String): I983ValidationIssue {
        return I983ValidationIssue(
            id = id,
            fieldKey = fieldKey,
            message = message,
            severity = I983ValidationSeverity.ESCALATE
        )
    }

    private fun severityRank(severity: I983ValidationSeverity): Int {
        return when (severity) {
            I983ValidationSeverity.ESCALATE -> 4
            I983ValidationSeverity.BLOCKER -> 3
            I983ValidationSeverity.CONFLICT -> 2
            I983ValidationSeverity.WARNING -> 1
        }
    }

    private fun normalizeLooseValue(value: String): String {
        return value.lowercase().filter { it.isLetterOrDigit() }
    }

    private companion object {
        const val TEN_DAYS_MILLIS = 10L * 24L * 60L * 60L * 1000L
    }
}
