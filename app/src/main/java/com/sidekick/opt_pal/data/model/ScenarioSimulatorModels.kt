package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class ScenarioTemplateId(
    val wireValue: String,
    val label: String,
    val summary: String
) {
    JOB_LOSS_OR_INTERRUPTION(
        wireValue = "job_loss_or_interruption",
        label = "Job loss or interruption",
        summary = "Forecast unemployment, compliance drag, and next continuity steps."
    ),
    ADD_OR_SWITCH_EMPLOYER(
        wireValue = "add_or_switch_employer",
        label = "Add or switch employer",
        summary = "Check STEM reporting, I-983 readiness, and employer-change timing."
    ),
    REPORTING_DEADLINE_MISSED(
        wireValue = "reporting_deadline_missed",
        label = "Reporting deadline missed",
        summary = "Stress-test overdue reporting and escalation paths."
    ),
    INTERNATIONAL_TRAVEL(
        wireValue = "international_travel",
        label = "International travel",
        summary = "Run the travel rules engine against a hypothetical trip."
    ),
    H1B_CAP_CONTINUITY(
        wireValue = "h1b_cap_continuity",
        label = "H-1B cap continuity",
        summary = "Project cap-gap continuity, petition stage, and travel sensitivity."
    ),
    PENDING_STEM_EXTENSION(
        wireValue = "pending_stem_extension",
        label = "Pending STEM extension",
        summary = "Model timely filing, the 180-day bridge, and STEM-specific edge cases."
    );

    companion object {
        fun fromWireValue(value: String?): ScenarioTemplateId {
            return entries.firstOrNull { it.wireValue == value } ?: JOB_LOSS_OR_INTERRUPTION
        }
    }
}

enum class ScenarioOutcome(val label: String) {
    ON_TRACK("On track"),
    ACTION_REQUIRED("Action required"),
    HIGH_RISK("High risk"),
    CONSULT_DSO_OR_ATTORNEY("Consult DSO or attorney")
}

enum class ScenarioConfidence(val label: String) {
    VERIFIED("Verified"),
    PARTIAL("Partial"),
    HYPOTHETICAL("Hypothetical")
}

enum class ScenarioEntitlementSource {
    USER_FLAG,
    OPEN_BETA,
    LOCKED_PREVIEW
}

enum class ScenarioImpactGroup(val label: String) {
    EMPLOYMENT("Employment"),
    REPORTING("Reporting"),
    DOCUMENTS("Documents"),
    TRAVEL("Travel"),
    H1B_CONTINUITY("H-1B continuity")
}

data class ScenarioSimulatorEntitlementState(
    val isEnabled: Boolean = false,
    val source: ScenarioEntitlementSource = ScenarioEntitlementSource.LOCKED_PREVIEW,
    val message: String = "Scenario Simulator is not enabled for this account yet."
)

data class ScenarioCitation(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val effectiveDate: String? = null,
    val lastReviewedDate: String? = null,
    val summary: String = ""
)

data class ScenarioDefinition(
    val templateId: String = ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION.wireValue,
    val title: String = "",
    val summary: String = "",
    val editorHint: String = "",
    val resultCaveat: String = ""
) {
    val parsedTemplateId: ScenarioTemplateId
        get() = ScenarioTemplateId.fromWireValue(templateId)
}

data class ScenarioRuleCard(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val confidence: String = "",
    val whatThisDoesNotMean: String = "",
    val citationIds: List<String> = emptyList()
)

data class ScenarioChangelogEntry(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val effectiveDate: String = "",
    val citationId: String = ""
)

data class ScenarioSimulatorBundle(
    val version: String = "",
    val generatedAt: Long = 0L,
    val lastReviewedAt: Long = 0L,
    val staleAfterDays: Int = 30,
    val sources: List<ScenarioCitation> = emptyList(),
    val scenarioDefinitions: List<ScenarioDefinition> = emptyList(),
    val ruleCards: List<ScenarioRuleCard> = emptyList(),
    val changelog: List<ScenarioChangelogEntry> = emptyList()
) {
    fun isStale(now: Long): Boolean {
        if (lastReviewedAt <= 0L) return true
        return now - lastReviewedAt > staleAfterDays * ONE_DAY_MILLIS
    }

    fun citationById(id: String): ScenarioCitation? = sources.firstOrNull { it.id == id }

    fun definitionFor(templateId: ScenarioTemplateId): ScenarioDefinition? {
        return scenarioDefinitions.firstOrNull { it.parsedTemplateId == templateId }
    }
}

sealed interface ScenarioAssumptions {
    val templateId: ScenarioTemplateId
}

data class JobLossScenarioAssumptions(
    val employmentId: String? = null,
    val interruptionStartDate: Long? = null,
    val replacementStartDate: Long? = null,
    val replacementEmployerName: String = "",
    val replacementHoursPerWeek: Int? = null,
    val hasUnauthorizedEmployment: Boolean? = null,
    val hasStatusViolation: Boolean? = null
) : ScenarioAssumptions {
    override val templateId: ScenarioTemplateId = ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION
}

data class EmployerChangeScenarioAssumptions(
    val changeDate: Long? = null,
    val newEmployerName: String = "",
    val newEmployerHoursPerWeek: Int? = null,
    val newEmployerUsesEVerify: Boolean? = null,
    val roleRelatedToDegree: Boolean? = null,
    val hasNewI983: Boolean? = null,
    val hasNewI20: Boolean? = null
) : ScenarioAssumptions {
    override val templateId: ScenarioTemplateId = ScenarioTemplateId.ADD_OR_SWITCH_EMPLOYER
}

data class ReportingDeadlineScenarioAssumptions(
    val obligationId: String? = null,
    val reportingLabel: String = "",
    val dueDate: Long? = null,
    val submittedLate: Boolean? = null,
    val isStemValidation: Boolean? = null,
    val isFinalEvaluation: Boolean? = null
) : ScenarioAssumptions {
    override val templateId: ScenarioTemplateId = ScenarioTemplateId.REPORTING_DEADLINE_MISSED
}

data class TravelScenarioAssumptions(
    val tripInput: TravelTripInput = TravelTripInput()
) : ScenarioAssumptions {
    override val templateId: ScenarioTemplateId = ScenarioTemplateId.INTERNATIONAL_TRAVEL
}

data class H1bContinuityScenarioAssumptions(
    val workflowStage: String = H1bWorkflowStage.NOT_STARTED.wireValue,
    val selectedRegistration: Boolean? = null,
    val filedPetition: Boolean? = null,
    val requestedChangeOfStatus: Boolean? = null,
    val requestedConsularProcessing: Boolean? = null,
    val travelPlanned: Boolean? = null,
    val hasReceiptNotice: Boolean? = null
) : ScenarioAssumptions {
    override val templateId: ScenarioTemplateId = ScenarioTemplateId.H1B_CAP_CONTINUITY

    val parsedWorkflowStage: H1bWorkflowStage
        get() = H1bWorkflowStage.fromWireValue(workflowStage)
}

data class PendingStemExtensionScenarioAssumptions(
    val filingDate: Long? = null,
    val optEndDate: Long? = null,
    val hasReceiptNotice: Boolean? = null,
    val travelPlanned: Boolean? = null,
    val employerChangePlanned: Boolean? = null,
    val hasNewI983: Boolean? = null,
    val hasNewI20: Boolean? = null
) : ScenarioAssumptions {
    override val templateId: ScenarioTemplateId = ScenarioTemplateId.PENDING_STEM_EXTENSION
}

data class ScenarioBaselineFingerprint(
    val value: String = ""
)

data class ScenarioDraftOutcomeSummary(
    val outcome: String = ScenarioOutcome.ACTION_REQUIRED.name,
    val headline: String = "",
    val confidence: String = ScenarioConfidence.HYPOTHETICAL.name,
    val computedAt: Long = 0L
) {
    val parsedOutcome: ScenarioOutcome
        get() = ScenarioOutcome.entries.firstOrNull { it.name == outcome } ?: ScenarioOutcome.ACTION_REQUIRED

    val parsedConfidence: ScenarioConfidence
        get() = ScenarioConfidence.entries.firstOrNull { it.name == confidence } ?: ScenarioConfidence.HYPOTHETICAL
}

data class ScenarioDraft(
    @DocumentId val id: String = "",
    val templateId: String = ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION.wireValue,
    val name: String = "",
    val assumptions: ScenarioAssumptions = JobLossScenarioAssumptions(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastRunAt: Long? = null,
    val baselineFingerprint: ScenarioBaselineFingerprint = ScenarioBaselineFingerprint(),
    val lastOutcome: ScenarioDraftOutcomeSummary? = null,
    val archivedAt: Long? = null,
    val pinned: Boolean = false
) {
    val parsedTemplateId: ScenarioTemplateId
        get() = ScenarioTemplateId.fromWireValue(templateId)

    val isArchived: Boolean
        get() = archivedAt != null
}

data class ScenarioAction(
    val id: String = "",
    val label: String = "",
    val route: String? = null,
    val externalUrl: String? = null
)

data class ScenarioImpactCard(
    val id: String = "",
    val group: ScenarioImpactGroup = ScenarioImpactGroup.EMPLOYMENT,
    val title: String = "",
    val summary: String = "",
    val outcome: ScenarioOutcome = ScenarioOutcome.ACTION_REQUIRED,
    val dueDate: Long? = null,
    val action: ScenarioAction? = null
)

data class ScenarioTimelineEvent(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val timestamp: Long? = null
)

data class ScenarioSimulationResult(
    val templateId: ScenarioTemplateId = ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION,
    val outcome: ScenarioOutcome = ScenarioOutcome.ACTION_REQUIRED,
    val confidence: ScenarioConfidence = ScenarioConfidence.HYPOTHETICAL,
    val headline: String = "",
    val summary: String = "",
    val timeline: List<ScenarioTimelineEvent> = emptyList(),
    val impactCards: List<ScenarioImpactCard> = emptyList(),
    val nextActions: List<ScenarioAction> = emptyList(),
    val citations: List<ScenarioCitation> = emptyList(),
    val whatThisDoesNotMean: String = "",
    val dependencyWarnings: List<String> = emptyList(),
    val computedAt: Long = 0L
)

fun ScenarioTemplateId.defaultDraftName(): String = when (this) {
    ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION -> "Job interruption draft"
    ScenarioTemplateId.ADD_OR_SWITCH_EMPLOYER -> "Employer change draft"
    ScenarioTemplateId.REPORTING_DEADLINE_MISSED -> "Reporting deadline draft"
    ScenarioTemplateId.INTERNATIONAL_TRAVEL -> "Travel draft"
    ScenarioTemplateId.H1B_CAP_CONTINUITY -> "H-1B continuity draft"
    ScenarioTemplateId.PENDING_STEM_EXTENSION -> "Pending STEM draft"
}

fun ScenarioAssumptions.toStorageMap(): Map<String, Any> {
    return when (this) {
        is JobLossScenarioAssumptions -> buildMap {
            put("employmentId", employmentId.orEmpty())
            interruptionStartDate?.let { put("interruptionStartDate", it) }
            replacementStartDate?.let { put("replacementStartDate", it) }
            put("replacementEmployerName", replacementEmployerName)
            replacementHoursPerWeek?.let { put("replacementHoursPerWeek", it) }
            hasUnauthorizedEmployment?.let { put("hasUnauthorizedEmployment", it) }
            hasStatusViolation?.let { put("hasStatusViolation", it) }
        }

        is EmployerChangeScenarioAssumptions -> buildMap {
            changeDate?.let { put("changeDate", it) }
            put("newEmployerName", newEmployerName)
            newEmployerHoursPerWeek?.let { put("newEmployerHoursPerWeek", it) }
            newEmployerUsesEVerify?.let { put("newEmployerUsesEVerify", it) }
            roleRelatedToDegree?.let { put("roleRelatedToDegree", it) }
            hasNewI983?.let { put("hasNewI983", it) }
            hasNewI20?.let { put("hasNewI20", it) }
        }

        is ReportingDeadlineScenarioAssumptions -> buildMap {
            put("obligationId", obligationId.orEmpty())
            put("reportingLabel", reportingLabel)
            dueDate?.let { put("dueDate", it) }
            submittedLate?.let { put("submittedLate", it) }
            isStemValidation?.let { put("isStemValidation", it) }
            isFinalEvaluation?.let { put("isFinalEvaluation", it) }
        }

        is TravelScenarioAssumptions -> buildMap {
            put("tripInput", tripInput.toStorageMap())
        }

        is H1bContinuityScenarioAssumptions -> buildMap {
            put("workflowStage", workflowStage)
            selectedRegistration?.let { put("selectedRegistration", it) }
            filedPetition?.let { put("filedPetition", it) }
            requestedChangeOfStatus?.let { put("requestedChangeOfStatus", it) }
            requestedConsularProcessing?.let { put("requestedConsularProcessing", it) }
            travelPlanned?.let { put("travelPlanned", it) }
            hasReceiptNotice?.let { put("hasReceiptNotice", it) }
        }

        is PendingStemExtensionScenarioAssumptions -> buildMap {
            filingDate?.let { put("filingDate", it) }
            optEndDate?.let { put("optEndDate", it) }
            hasReceiptNotice?.let { put("hasReceiptNotice", it) }
            travelPlanned?.let { put("travelPlanned", it) }
            employerChangePlanned?.let { put("employerChangePlanned", it) }
            hasNewI983?.let { put("hasNewI983", it) }
            hasNewI20?.let { put("hasNewI20", it) }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun scenarioAssumptionsFromStorage(
    templateId: ScenarioTemplateId,
    raw: Map<String, Any>?
): ScenarioAssumptions {
    val values = raw.orEmpty()
    return when (templateId) {
        ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION -> JobLossScenarioAssumptions(
            employmentId = values.stringValue("employmentId"),
            interruptionStartDate = values.longValue("interruptionStartDate"),
            replacementStartDate = values.longValue("replacementStartDate"),
            replacementEmployerName = values.stringValue("replacementEmployerName").orEmpty(),
            replacementHoursPerWeek = values.intValue("replacementHoursPerWeek"),
            hasUnauthorizedEmployment = values.booleanValue("hasUnauthorizedEmployment"),
            hasStatusViolation = values.booleanValue("hasStatusViolation")
        )

        ScenarioTemplateId.ADD_OR_SWITCH_EMPLOYER -> EmployerChangeScenarioAssumptions(
            changeDate = values.longValue("changeDate"),
            newEmployerName = values.stringValue("newEmployerName").orEmpty(),
            newEmployerHoursPerWeek = values.intValue("newEmployerHoursPerWeek"),
            newEmployerUsesEVerify = values.booleanValue("newEmployerUsesEVerify"),
            roleRelatedToDegree = values.booleanValue("roleRelatedToDegree"),
            hasNewI983 = values.booleanValue("hasNewI983"),
            hasNewI20 = values.booleanValue("hasNewI20")
        )

        ScenarioTemplateId.REPORTING_DEADLINE_MISSED -> ReportingDeadlineScenarioAssumptions(
            obligationId = values.stringValue("obligationId"),
            reportingLabel = values.stringValue("reportingLabel").orEmpty(),
            dueDate = values.longValue("dueDate"),
            submittedLate = values.booleanValue("submittedLate"),
            isStemValidation = values.booleanValue("isStemValidation"),
            isFinalEvaluation = values.booleanValue("isFinalEvaluation")
        )

        ScenarioTemplateId.INTERNATIONAL_TRAVEL -> TravelScenarioAssumptions(
            tripInput = travelTripInputFromStorage(values["tripInput"] as? Map<String, Any>)
        )

        ScenarioTemplateId.H1B_CAP_CONTINUITY -> H1bContinuityScenarioAssumptions(
            workflowStage = values.stringValue("workflowStage") ?: H1bWorkflowStage.NOT_STARTED.wireValue,
            selectedRegistration = values.booleanValue("selectedRegistration"),
            filedPetition = values.booleanValue("filedPetition"),
            requestedChangeOfStatus = values.booleanValue("requestedChangeOfStatus"),
            requestedConsularProcessing = values.booleanValue("requestedConsularProcessing"),
            travelPlanned = values.booleanValue("travelPlanned"),
            hasReceiptNotice = values.booleanValue("hasReceiptNotice")
        )

        ScenarioTemplateId.PENDING_STEM_EXTENSION -> PendingStemExtensionScenarioAssumptions(
            filingDate = values.longValue("filingDate"),
            optEndDate = values.longValue("optEndDate"),
            hasReceiptNotice = values.booleanValue("hasReceiptNotice"),
            travelPlanned = values.booleanValue("travelPlanned"),
            employerChangePlanned = values.booleanValue("employerChangePlanned"),
            hasNewI983 = values.booleanValue("hasNewI983"),
            hasNewI20 = values.booleanValue("hasNewI20")
        )
    }
}

private fun TravelTripInput.toStorageMap(): Map<String, Any> = buildMap {
    departureDate?.let { put("departureDate", it) }
    plannedReturnDate?.let { put("plannedReturnDate", it) }
    put("destinationCountry", destinationCountry)
    onlyCanadaMexicoAdjacentIslands?.let { put("onlyCanadaMexicoAdjacentIslands", it) }
    needsNewVisa?.let { put("needsNewVisa", it) }
    visaRenewalOutsideResidence?.let { put("visaRenewalOutsideResidence", it) }
    hasEmploymentOrOfferProof?.let { put("hasEmploymentOrOfferProof", it) }
    capGapActive?.let { put("capGapActive", it) }
    hasRfeStatusIssueOrArrestHistory?.let { put("hasRfeStatusIssueOrArrestHistory", it) }
    travelScenario?.let { put("travelScenario", it.name) }
    put("passportIssuingCountry", passportIssuingCountry)
    passportExpirationDate?.let { put("passportExpirationDate", it) }
    put("visaClass", visaClass)
    visaExpirationDate?.let { put("visaExpirationDate", it) }
    i20TravelSignatureDate?.let { put("i20TravelSignatureDate", it) }
    eadExpirationDate?.let { put("eadExpirationDate", it) }
    hasOriginalEadInHand?.let { put("hasOriginalEadInHand", it) }
}

private fun travelTripInputFromStorage(raw: Map<String, Any>?): TravelTripInput {
    val values = raw.orEmpty()
    return TravelTripInput(
        departureDate = values.longValue("departureDate"),
        plannedReturnDate = values.longValue("plannedReturnDate"),
        destinationCountry = values.stringValue("destinationCountry").orEmpty(),
        onlyCanadaMexicoAdjacentIslands = values.booleanValue("onlyCanadaMexicoAdjacentIslands"),
        needsNewVisa = values.booleanValue("needsNewVisa"),
        visaRenewalOutsideResidence = values.booleanValue("visaRenewalOutsideResidence"),
        hasEmploymentOrOfferProof = values.booleanValue("hasEmploymentOrOfferProof"),
        capGapActive = values.booleanValue("capGapActive"),
        hasRfeStatusIssueOrArrestHistory = values.booleanValue("hasRfeStatusIssueOrArrestHistory"),
        travelScenario = values.stringValue("travelScenario")?.let {
            TravelScenario.entries.firstOrNull { scenario -> scenario.name == it }
        },
        passportIssuingCountry = values.stringValue("passportIssuingCountry").orEmpty(),
        passportExpirationDate = values.longValue("passportExpirationDate"),
        visaClass = values.stringValue("visaClass") ?: "F-1",
        visaExpirationDate = values.longValue("visaExpirationDate"),
        i20TravelSignatureDate = values.longValue("i20TravelSignatureDate"),
        eadExpirationDate = values.longValue("eadExpirationDate"),
        hasOriginalEadInHand = values.booleanValue("hasOriginalEadInHand")
    )
}

private fun Map<String, Any>.stringValue(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

private fun Map<String, Any>.longValue(key: String): Long? = when (val value = this[key]) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> value.toLong()
    is Float -> value.toLong()
    is Number -> value.toLong()
    else -> null
}

private fun Map<String, Any>.intValue(key: String): Int? = when (val value = this[key]) {
    is Int -> value
    is Long -> value.toInt()
    is Double -> value.toInt()
    is Float -> value.toInt()
    is Number -> value.toInt()
    else -> null
}

private fun Map<String, Any>.booleanValue(key: String): Boolean? = this[key] as? Boolean

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
