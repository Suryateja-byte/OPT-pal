package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class I983WorkflowType(val wireValue: String) {
    INITIAL_STEM_EXTENSION("initial_stem_extension"),
    NEW_EMPLOYER("new_employer"),
    MATERIAL_CHANGE("material_change"),
    ANNUAL_EVALUATION("annual_evaluation"),
    FINAL_EVALUATION("final_evaluation");

    companion object {
        fun fromWireValue(value: String?): I983WorkflowType {
            return entries.firstOrNull { it.wireValue == value } ?: INITIAL_STEM_EXTENSION
        }
    }
}

enum class I983DraftStatus(val wireValue: String) {
    DRAFTING("drafting"),
    READY_TO_EXPORT("ready_to_export"),
    EXPORTED("exported"),
    SIGNED("signed"),
    ESCALATED("escalated"),
    CANCELLED("cancelled");

    companion object {
        fun fromWireValue(value: String?): I983DraftStatus {
            return entries.firstOrNull { it.wireValue == value } ?: DRAFTING
        }
    }
}

enum class I983Readiness {
    NEEDS_INPUT,
    READY_TO_EXPORT,
    CONTACT_DSO_OR_ATTORNEY
}

enum class I983EntitlementSource {
    USER_FLAG,
    OPEN_BETA,
    LOCKED_PREVIEW
}

data class I983EntitlementState(
    val isEnabled: Boolean = false,
    val source: I983EntitlementSource = I983EntitlementSource.LOCKED_PREVIEW,
    val message: String = "I-983 AI Assistant is not enabled for this account yet."
)

data class I983StudentSection(
    val studentName: String = "",
    val studentEmailAddress: String = "",
    val schoolRecommendingStemOpt: String = "",
    val schoolWhereDegreeWasEarned: String = "",
    val sevisSchoolCode: String = "",
    val dsoNameAndContact: String = "",
    val studentSevisId: String = "",
    val requestedStartDate: Long? = null,
    val requestedEndDate: Long? = null,
    val qualifyingMajorAndCipCode: String = "",
    val degreeLevel: String = "",
    val degreeAwardedDate: Long? = null,
    val basedOnPriorDegree: Boolean? = null,
    val employmentAuthorizationNumber: String = ""
)

data class I983EmployerSection(
    val employerName: String = "",
    val streetAddress: String = "",
    val suite: String = "",
    val employerWebsiteUrl: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val employerEin: String = "",
    val fullTimeEmployeesInUs: String = "",
    val naicsCode: String = "",
    val hoursPerWeek: Int? = null,
    val salaryAmountAndFrequency: String = "",
    val otherCompensationLine1: String = "",
    val otherCompensationLine2: String = "",
    val otherCompensationLine3: String = "",
    val otherCompensationLine4: String = "",
    val employmentStartDate: Long? = null,
    val employerOfficialNameAndTitle: String = "",
    val employingOrganizationName: String = ""
)

data class I983TrainingPlanSection(
    val siteName: String = "",
    val siteAddress: String = "",
    val officialName: String = "",
    val officialTitle: String = "",
    val officialEmail: String = "",
    val officialPhoneNumber: String = "",
    val studentRole: String = "",
    val goalsAndObjectives: String = "",
    val employerOversight: String = "",
    val measuresAndAssessments: String = "",
    val additionalRemarks: String = ""
)

data class I983EvaluationSection(
    val annualEvaluationFromDate: Long? = null,
    val annualEvaluationToDate: Long? = null,
    val annualEvaluationText: String = "",
    val finalEvaluationFromDate: Long? = null,
    val finalEvaluationToDate: Long? = null,
    val finalEvaluationText: String = ""
)

enum class I983NarrativeClassification(val wireValue: String) {
    DRAFT_ASSISTANCE("draft_assistance"),
    CONSULT_DSO_ATTORNEY("consult_dso_attorney");

    companion object {
        fun fromWireValue(value: String?): I983NarrativeClassification {
            return entries.firstOrNull { it.wireValue == value } ?: DRAFT_ASSISTANCE
        }
    }
}

enum class I983NarrativeConfidence(val wireValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun fromWireValue(value: String?): I983NarrativeConfidence {
            return entries.firstOrNull { it.wireValue == value } ?: LOW
        }
    }
}

data class I983NarrativeDraft(
    val classification: String = I983NarrativeClassification.DRAFT_ASSISTANCE.wireValue,
    val confidence: String = I983NarrativeConfidence.LOW.wireValue,
    val studentRole: String = "",
    val goalsAndObjectives: String = "",
    val employerOversight: String = "",
    val measuresAndAssessments: String = "",
    val annualEvaluation: String = "",
    val finalEvaluation: String = "",
    val missingInputs: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val parsedClassification: I983NarrativeClassification
        get() = I983NarrativeClassification.fromWireValue(classification)

    val parsedConfidence: I983NarrativeConfidence
        get() = I983NarrativeConfidence.fromWireValue(confidence)
}

enum class I983ValidationSeverity {
    BLOCKER,
    WARNING,
    CONFLICT,
    ESCALATE
}

data class I983ValidationIssue(
    val id: String = "",
    val fieldKey: String = "",
    val message: String = "",
    val severity: I983ValidationSeverity = I983ValidationSeverity.BLOCKER,
    val sourceLabel: String? = null,
    val sourceUrl: String? = null
)

data class I983SourceCitation(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val effectiveDate: String? = null,
    val lastReviewedDate: String? = null,
    val summary: String = ""
)

data class I983PolicyRequirement(
    val workflowType: String = I983WorkflowType.INITIAL_STEM_EXTENSION.wireValue,
    val title: String = "",
    val summary: String = "",
    val citationIds: List<String> = emptyList()
)

data class I983PolicyBundle(
    val version: String = "",
    val generatedAt: Long = 0L,
    val lastReviewedAt: Long = 0L,
    val templateVersion: String = "",
    val templateSha256: String = "",
    val signatureGuidance: String = "",
    val staleAfterDays: Int = 30,
    val sources: List<I983SourceCitation> = emptyList(),
    val requirements: List<I983PolicyRequirement> = emptyList()
) {
    fun citationsFor(workflowType: I983WorkflowType): List<I983SourceCitation> {
        val requirement = requirements.firstOrNull { it.workflowType == workflowType.wireValue } ?: return emptyList()
        return requirement.citationIds.mapNotNull { citationId ->
            sources.firstOrNull { it.id == citationId }
        }
    }

    fun isStale(now: Long): Boolean {
        if (lastReviewedAt <= 0L) return true
        return now - lastReviewedAt > staleAfterDays * ONE_DAY_MILLIS
    }
}

data class I983ExportResult(
    val documentId: String = "",
    val fileName: String = "",
    val generatedAt: Long = 0L,
    val templateVersion: String = ""
)

data class I983Assessment(
    val readiness: I983Readiness = I983Readiness.NEEDS_INPUT,
    val headline: String = "",
    val summary: String = "",
    val issues: List<I983ValidationIssue> = emptyList(),
    val citations: List<I983SourceCitation> = emptyList(),
    val requiresConflictReview: Boolean = false
)

data class I983Draft(
    @DocumentId val id: String = "",
    val workflowType: String = I983WorkflowType.INITIAL_STEM_EXTENSION.wireValue,
    val status: String = I983DraftStatus.DRAFTING.wireValue,
    val linkedEmploymentId: String = "",
    val linkedObligationId: String = "",
    val revisionOfDraftId: String = "",
    val templateVersion: String = "",
    val policyVersion: String = "",
    val selectedDocumentIds: List<String> = emptyList(),
    val latestExportDocumentId: String = "",
    val signedDocumentId: String = "",
    val studentSection: I983StudentSection = I983StudentSection(),
    val employerSection: I983EmployerSection = I983EmployerSection(),
    val trainingPlanSection: I983TrainingPlanSection = I983TrainingPlanSection(),
    val evaluationSection: I983EvaluationSection = I983EvaluationSection(),
    val generatedNarrative: I983NarrativeDraft? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val exportedAt: Long? = null,
    val signedLinkedAt: Long? = null
) {
    val parsedWorkflowType: I983WorkflowType
        get() = I983WorkflowType.fromWireValue(workflowType)

    val parsedStatus: I983DraftStatus
        get() = I983DraftStatus.fromWireValue(status)
}

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
