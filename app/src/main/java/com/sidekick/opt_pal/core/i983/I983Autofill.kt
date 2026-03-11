package com.sidekick.opt_pal.core.i983

import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983EmployerSection
import com.sidekick.opt_pal.data.model.I983EvaluationSection
import com.sidekick.opt_pal.data.model.I983StudentSection
import com.sidekick.opt_pal.data.model.I983TrainingPlanSection
import com.sidekick.opt_pal.data.model.I983ValidationIssue
import com.sidekick.opt_pal.data.model.I983ValidationSeverity
import com.sidekick.opt_pal.data.model.I983WorkflowType
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UserProfile
import java.time.LocalDate
import java.time.ZoneOffset

data class I983AutofillResult(
    val draft: I983Draft,
    val issues: List<I983ValidationIssue> = emptyList(),
    val sourceLabels: List<String> = emptyList()
)

fun buildI983AutofillResult(
    workflowType: I983WorkflowType,
    templateVersion: String,
    policyVersion: String,
    linkedEmploymentId: String,
    linkedObligationId: String,
    profile: UserProfile?,
    employments: List<Employment>,
    obligations: List<ReportingObligation>,
    existingDrafts: List<I983Draft>,
    documents: List<DocumentMetadata>,
    now: Long
): I983AutofillResult {
    val linkedEmployment = employments.firstOrNull { it.id == linkedEmploymentId }
        ?: employments.firstOrNull { it.endDate == null }
        ?: employments.maxByOrNull { it.startDate }
    val linkedObligation = obligations.firstOrNull { it.id == linkedObligationId }
    val supportingDraft = findSupportingDraft(
        workflowType = workflowType,
        linkedEmployment = linkedEmployment,
        existingDrafts = existingDrafts
    )
    val extracted = documents.mapNotNull(::toI983Extraction).sortedByDescending { it.document.uploadedAt }

    val studentConflicts = mutableListOf<I983ValidationIssue>()
    val employerConflicts = mutableListOf<I983ValidationIssue>()

    val studentSection = buildStudentSection(
        base = supportingDraft?.studentSection,
        profile = profile,
        extracted = extracted,
        linkedEmployment = linkedEmployment,
        conflicts = studentConflicts
    )
    val employerSection = buildEmployerSection(
        base = supportingDraft?.employerSection,
        linkedEmployment = linkedEmployment,
        extracted = extracted,
        conflicts = employerConflicts
    )
    val trainingPlanSection = buildTrainingPlanSection(
        base = supportingDraft?.trainingPlanSection,
        linkedEmployment = linkedEmployment,
        extracted = extracted,
        generatedNarrative = supportingDraft?.generatedNarrative
    )
    val evaluationSection = buildEvaluationSection(
        workflowType = workflowType,
        base = supportingDraft?.evaluationSection,
        linkedEmployment = linkedEmployment,
        linkedObligation = linkedObligation,
        extracted = extracted,
        now = now
    )

    val draft = I983Draft(
        workflowType = workflowType.wireValue,
        linkedEmploymentId = linkedEmployment?.id.orEmpty(),
        linkedObligationId = linkedObligation?.id.orEmpty(),
        revisionOfDraftId = if (workflowType == I983WorkflowType.MATERIAL_CHANGE) {
            supportingDraft?.id.orEmpty()
        } else {
            ""
        },
        templateVersion = templateVersion,
        policyVersion = policyVersion,
        selectedDocumentIds = supportingDraft?.selectedDocumentIds ?: emptyList(),
        latestExportDocumentId = supportingDraft?.latestExportDocumentId.orEmpty(),
        signedDocumentId = supportingDraft?.signedDocumentId.orEmpty(),
        studentSection = studentSection,
        employerSection = employerSection,
        trainingPlanSection = trainingPlanSection,
        evaluationSection = evaluationSection,
        generatedNarrative = supportingDraft?.generatedNarrative
    )

    return I983AutofillResult(
        draft = draft,
        issues = studentConflicts + employerConflicts,
        sourceLabels = extracted.map { it.document.userTag.ifBlank { it.document.fileName } }.distinct()
    )
}

private fun findSupportingDraft(
    workflowType: I983WorkflowType,
    linkedEmployment: Employment?,
    existingDrafts: List<I983Draft>
): I983Draft? {
    val matchingDrafts = existingDrafts
        .filter { draft ->
            linkedEmployment == null ||
                draft.linkedEmploymentId == linkedEmployment.id ||
                normalizeLooseValue(draft.employerSection.employerName) == normalizeLooseValue(linkedEmployment.employerName)
        }
        .sortedByDescending { it.updatedAt }
    return when (workflowType) {
        I983WorkflowType.ANNUAL_EVALUATION,
        I983WorkflowType.FINAL_EVALUATION,
        I983WorkflowType.MATERIAL_CHANGE -> matchingDrafts.firstOrNull()
        else -> matchingDrafts.firstOrNull()
    }
}

private fun buildStudentSection(
    base: I983StudentSection?,
    profile: UserProfile?,
    extracted: List<I983Extraction>,
    linkedEmployment: Employment?,
    conflicts: MutableList<I983ValidationIssue>
): I983StudentSection {
    val email = chooseString(
        fieldKey = "student_email_address",
        preferred = base?.studentEmailAddress,
        alternatives = listOfNotNull(
            profile?.email?.takeIf { it.isNotBlank() }?.let { value -> value to "Profile" },
            extracted.firstNotNullOfOrNull { it.valueOf("student_email", "email_address", "student_email_address")?.let { value -> value to it.sourceLabel } }
        ),
        conflicts = conflicts
    )
    val schoolName = chooseString(
        fieldKey = "school_recommending_stem_opt",
        preferred = base?.schoolRecommendingStemOpt,
        alternatives = listOfNotNull(
            profile?.schoolName?.takeIf { it.isNotBlank() }?.let { value -> value to "Profile" },
            extracted.firstNotNullOfOrNull { it.valueOf("school_name")?.let { value -> value to it.sourceLabel } }
        ),
        conflicts = conflicts
    )
    val degreeSchool = chooseString(
        fieldKey = "school_where_degree_was_earned",
        preferred = base?.schoolWhereDegreeWasEarned,
        alternatives = listOfNotNull(
            profile?.schoolName?.takeIf { it.isNotBlank() }?.let { value -> value to "Profile" },
            extracted.firstNotNullOfOrNull { it.valueOf("school_name", "degree_school_name")?.let { value -> value to it.sourceLabel } }
        ),
        conflicts = conflicts
    )
    val sevisId = chooseString(
        fieldKey = "student_sevis_id",
        preferred = base?.studentSevisId,
        alternatives = listOfNotNull(
            profile?.sevisId?.takeIf { it.isNotBlank() }?.let { value -> value to "Profile" },
            extracted.firstNotNullOfOrNull { it.valueOf("sevis_id", "student_sevis_id")?.let { value -> value to it.sourceLabel } }
        ),
        conflicts = conflicts
    )
    val cipCode = chooseString(
        fieldKey = "qualifying_major_cip",
        preferred = base?.qualifyingMajorAndCipCode,
        alternatives = listOfNotNull(
            profile?.cipCode?.takeIf { it.isNotBlank() }?.let { value -> value to "Profile" },
            extracted.firstNotNullOfOrNull {
                val cip = it.valueOf("cip_code")
                val major = it.valueOf("major_name")
                when {
                    !major.isNullOrBlank() && !cip.isNullOrBlank() -> "$major ($cip)" to it.sourceLabel
                    !cip.isNullOrBlank() -> cip to it.sourceLabel
                    !major.isNullOrBlank() -> major to it.sourceLabel
                    else -> null
                }
            }
        ),
        conflicts = conflicts
    )
    val degreeLevel = chooseString(
        fieldKey = "degree_level",
        preferred = base?.degreeLevel,
        alternatives = extracted.mapNotNull { it.valueOf("degree_level")?.let { value -> value to it.sourceLabel } },
        conflicts = conflicts
    )
    val degreeAwardedDate = chooseDate(
        preferred = base?.degreeAwardedDate,
        alternatives = extracted.mapNotNull { it.dateValueOf("degree_awarded_date")?.let { value -> value to it.sourceLabel } },
        conflicts = conflicts,
        fieldKey = "degree_awarded_date"
    )
    val optStartDate = chooseDate(
        preferred = base?.requestedStartDate,
        alternatives = buildList {
            extracted.firstNotNullOfOrNull { it.dateValueOf("opt_start_date")?.let { value -> value to it.sourceLabel } }?.let(::add)
            linkedEmployment?.startDate?.let { add(it to "Employment history") }
        },
        conflicts = conflicts,
        fieldKey = "requested_start_date"
    )
    val optEndDate = chooseDate(
        preferred = base?.requestedEndDate,
        alternatives = extracted.mapNotNull { it.dateValueOf("opt_end_date")?.let { value -> value to it.sourceLabel } },
        conflicts = conflicts,
        fieldKey = "requested_end_date"
    )
    val eadNumber = chooseString(
        fieldKey = "employment_authorization_number",
        preferred = base?.employmentAuthorizationNumber,
        alternatives = extracted.mapNotNull {
            it.valueOf("uscis_number", "employment_authorization_number")?.let { value -> value to it.sourceLabel }
        },
        conflicts = conflicts
    )

    return I983StudentSection(
        studentName = base?.studentName.orEmpty(),
        studentEmailAddress = email.orEmpty(),
        schoolRecommendingStemOpt = schoolName.orEmpty(),
        schoolWhereDegreeWasEarned = degreeSchool.orEmpty(),
        sevisSchoolCode = chooseString(
            fieldKey = "sevis_school_code",
            preferred = base?.sevisSchoolCode,
            alternatives = extracted.mapNotNull { it.valueOf("sevis_school_code", "school_code")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        dsoNameAndContact = chooseString(
            fieldKey = "dso_name_and_contact",
            preferred = base?.dsoNameAndContact,
            alternatives = extracted.mapNotNull { it.valueOf("dso_name_and_contact", "dso_contact")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        studentSevisId = sevisId.orEmpty(),
        requestedStartDate = optStartDate,
        requestedEndDate = optEndDate,
        qualifyingMajorAndCipCode = cipCode.orEmpty(),
        degreeLevel = degreeLevel.orEmpty(),
        degreeAwardedDate = degreeAwardedDate,
        basedOnPriorDegree = base?.basedOnPriorDegree,
        employmentAuthorizationNumber = eadNumber.orEmpty()
    )
}

private fun buildEmployerSection(
    base: I983EmployerSection?,
    linkedEmployment: Employment?,
    extracted: List<I983Extraction>,
    conflicts: MutableList<I983ValidationIssue>
): I983EmployerSection {
    val employerName = chooseString(
        fieldKey = "employer_name",
        preferred = base?.employerName,
        alternatives = buildList {
            linkedEmployment?.employerName?.takeIf { it.isNotBlank() }?.let { add(it to "Employment history") }
            addAll(extracted.mapNotNull { it.valueOf("employer_name")?.let { value -> value to it.sourceLabel } })
        },
        conflicts = conflicts
    )
    val salary = chooseString(
        fieldKey = "salary_amount_and_frequency",
        preferred = base?.salaryAmountAndFrequency,
        alternatives = extracted.mapNotNull { it.valueOf("compensation_text", "salary_amount_and_frequency")?.let { value -> value to it.sourceLabel } },
        conflicts = conflicts
    )

    return I983EmployerSection(
        employerName = employerName.orEmpty(),
        streetAddress = chooseString(
            fieldKey = "street_address",
            preferred = base?.streetAddress,
            alternatives = extracted.mapNotNull { it.valueOf("street_address", "employer_street_address", "address")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        suite = chooseString(
            fieldKey = "suite",
            preferred = base?.suite,
            alternatives = extracted.mapNotNull { it.valueOf("suite")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        employerWebsiteUrl = chooseString(
            fieldKey = "employer_website_url",
            preferred = base?.employerWebsiteUrl,
            alternatives = extracted.mapNotNull { it.valueOf("employer_website_url", "website", "employer_website")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        city = chooseString(
            fieldKey = "city",
            preferred = base?.city,
            alternatives = extracted.mapNotNull { it.valueOf("city")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        state = chooseString(
            fieldKey = "state",
            preferred = base?.state,
            alternatives = extracted.mapNotNull { it.valueOf("state")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        zipCode = chooseString(
            fieldKey = "zip_code",
            preferred = base?.zipCode,
            alternatives = extracted.mapNotNull { it.valueOf("zip_code", "postal_code")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        employerEin = chooseString(
            fieldKey = "employer_ein",
            preferred = base?.employerEin,
            alternatives = extracted.mapNotNull { it.valueOf("employer_ein", "ein")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        fullTimeEmployeesInUs = chooseString(
            fieldKey = "full_time_employees_us",
            preferred = base?.fullTimeEmployeesInUs,
            alternatives = extracted.mapNotNull { it.valueOf("full_time_employees_in_us", "number_of_full_time_employees")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        naicsCode = chooseString(
            fieldKey = "employer_naics",
            preferred = base?.naicsCode,
            alternatives = extracted.mapNotNull { it.valueOf("employer_naics", "naics_code")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty(),
        hoursPerWeek = chooseInt(
            preferred = base?.hoursPerWeek,
            alternatives = buildList {
                linkedEmployment?.hoursPerWeek?.let { add(it to "Employment history") }
                addAll(extracted.mapNotNull { it.intValueOf("hours_per_week")?.let { value -> value to it.sourceLabel } })
            },
            conflicts = conflicts,
            fieldKey = "hours_per_week"
        ),
        salaryAmountAndFrequency = salary.orEmpty(),
        otherCompensationLine1 = base?.otherCompensationLine1.orEmpty(),
        otherCompensationLine2 = base?.otherCompensationLine2.orEmpty(),
        otherCompensationLine3 = base?.otherCompensationLine3.orEmpty(),
        otherCompensationLine4 = base?.otherCompensationLine4.orEmpty(),
        employmentStartDate = chooseDate(
            preferred = base?.employmentStartDate ?: linkedEmployment?.startDate,
            alternatives = extracted.mapNotNull { it.dateValueOf("employment_start_date", "start_date")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts,
            fieldKey = "employment_start_date"
        ),
        employerOfficialNameAndTitle = chooseString(
            fieldKey = "employer_official_name_and_title",
            preferred = base?.employerOfficialNameAndTitle,
            alternatives = extracted.mapNotNull {
                val name = it.valueOf("employer_official_name")
                val title = it.valueOf("employer_official_title")
                when {
                    !name.isNullOrBlank() && !title.isNullOrBlank() -> "$name, $title" to it.sourceLabel
                    !name.isNullOrBlank() -> name to it.sourceLabel
                    else -> null
                }
            },
            conflicts = conflicts
        ).orEmpty(),
        employingOrganizationName = chooseString(
            fieldKey = "employing_organization_name",
            preferred = base?.employingOrganizationName,
            alternatives = extracted.mapNotNull { it.valueOf("employer_name", "employing_organization_name")?.let { value -> value to it.sourceLabel } },
            conflicts = conflicts
        ).orEmpty()
    )
}

private fun buildTrainingPlanSection(
    base: I983TrainingPlanSection?,
    linkedEmployment: Employment?,
    extracted: List<I983Extraction>,
    generatedNarrative: com.sidekick.opt_pal.data.model.I983NarrativeDraft?
): I983TrainingPlanSection {
    val currentSiteName = chooseFirstNonBlank(
        base?.siteName,
        extracted.firstNotNullOfOrNull { it.valueOf("site_name") }
    )
    val currentSiteAddress = chooseFirstNonBlank(
        base?.siteAddress,
        extracted.firstNotNullOfOrNull { it.valueOf("site_address", "worksite_address", "address") }
    )
    return I983TrainingPlanSection(
        siteName = currentSiteName.orEmpty(),
        siteAddress = currentSiteAddress.orEmpty(),
        officialName = chooseFirstNonBlank(
            base?.officialName,
            extracted.firstNotNullOfOrNull { it.valueOf("employer_official_name", "official_name") }
        ).orEmpty(),
        officialTitle = chooseFirstNonBlank(
            base?.officialTitle,
            extracted.firstNotNullOfOrNull { it.valueOf("employer_official_title", "official_title") }
        ).orEmpty(),
        officialEmail = chooseFirstNonBlank(
            base?.officialEmail,
            extracted.firstNotNullOfOrNull { it.valueOf("employer_official_email", "official_email") }
        ).orEmpty(),
        officialPhoneNumber = chooseFirstNonBlank(
            base?.officialPhoneNumber,
            extracted.firstNotNullOfOrNull { it.valueOf("employer_official_phone", "official_phone") }
        ).orEmpty(),
        studentRole = chooseFirstNonBlank(
            base?.studentRole,
            generatedNarrative?.studentRole,
            extracted.firstNotNullOfOrNull { it.valueOf("i983_role_description", "job_description") },
            linkedEmployment?.jobTitle
        ).orEmpty(),
        goalsAndObjectives = chooseFirstNonBlank(
            base?.goalsAndObjectives,
            generatedNarrative?.goalsAndObjectives,
            extracted.firstNotNullOfOrNull { it.valueOf("i983_goals_objectives") }
        ).orEmpty(),
        employerOversight = chooseFirstNonBlank(
            base?.employerOversight,
            generatedNarrative?.employerOversight,
            extracted.firstNotNullOfOrNull { it.valueOf("i983_employer_oversight") }
        ).orEmpty(),
        measuresAndAssessments = chooseFirstNonBlank(
            base?.measuresAndAssessments,
            generatedNarrative?.measuresAndAssessments,
            extracted.firstNotNullOfOrNull { it.valueOf("i983_measures_assessments") }
        ).orEmpty(),
        additionalRemarks = chooseFirstNonBlank(
            base?.additionalRemarks,
            extracted.firstNotNullOfOrNull { it.valueOf("i983_additional_remarks") }
        ).orEmpty()
    )
}

private fun buildEvaluationSection(
    workflowType: I983WorkflowType,
    base: I983EvaluationSection?,
    linkedEmployment: Employment?,
    linkedObligation: ReportingObligation?,
    extracted: List<I983Extraction>,
    now: Long
): I983EvaluationSection {
    val baseline = base ?: I983EvaluationSection()
    val employmentStart = linkedEmployment?.startDate
    val obligationDue = linkedObligation?.dueDate
    val annualTo = obligationDue ?: now
    val annualFrom = baseline.annualEvaluationFromDate ?: employmentStart
    val finalTo = baseline.finalEvaluationToDate ?: linkedEmployment?.endDate ?: linkedObligation?.eventDate ?: obligationDue
    val finalFrom = baseline.finalEvaluationFromDate ?: employmentStart

    return when (workflowType) {
        I983WorkflowType.ANNUAL_EVALUATION -> baseline.copy(
            annualEvaluationFromDate = annualFrom,
            annualEvaluationToDate = baseline.annualEvaluationToDate ?: annualTo,
            annualEvaluationText = chooseFirstNonBlank(
                baseline.annualEvaluationText,
                extracted.firstNotNullOfOrNull { it.valueOf("annual_evaluation", "i983_annual_evaluation") }
            ).orEmpty()
        )
        I983WorkflowType.FINAL_EVALUATION -> baseline.copy(
            finalEvaluationFromDate = finalFrom,
            finalEvaluationToDate = finalTo,
            finalEvaluationText = chooseFirstNonBlank(
                baseline.finalEvaluationText,
                extracted.firstNotNullOfOrNull { it.valueOf("final_evaluation", "i983_final_evaluation") }
            ).orEmpty()
        )
        else -> baseline
    }
}

private data class I983Extraction(
    val document: DocumentMetadata,
    val values: Map<String, Any>
) {
    val sourceLabel: String
        get() = document.userTag.ifBlank { document.fileName }

    fun valueOf(vararg keys: String): String? {
        val index = values.mapKeys { normalizeLooseValue(it.key) }
        return keys.firstNotNullOfOrNull { key ->
            index[normalizeLooseValue(key)]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun dateValueOf(vararg keys: String): Long? {
        return valueOf(*keys)?.let(::parseIsoDateMillis)
    }

    fun intValueOf(vararg keys: String): Int? {
        return valueOf(*keys)?.filter { it.isDigit() }?.toIntOrNull()
    }
}

private fun toI983Extraction(document: DocumentMetadata): I983Extraction? {
    val values = document.extractedData ?: return null
    val label = listOf(document.documentType, document.userTag, document.fileName)
        .joinToString(" ")
        .lowercase()
    val looksRelevant = listOf(
        "i-983",
        "i983",
        "offer",
        "job description",
        "passport",
        "visa",
        "ead",
        "i-20",
        "i20"
    ).any { label.contains(it) } || values.keys.any { key ->
        normalizeLooseValue(key) in setOf(
            "degreelevel",
            "degreeawardeddate",
            "employerein",
            "employernaics",
            "hoursperweek",
            "compensationtext",
            "siteaddress",
            "employerofficialname",
            "i983roledescription"
        )
    }
    return if (looksRelevant) I983Extraction(document = document, values = values) else null
}

private fun chooseString(
    fieldKey: String,
    preferred: String?,
    alternatives: List<Pair<String, String>>,
    conflicts: MutableList<I983ValidationIssue>
): String? {
    preferred?.takeIf { it.isNotBlank() }?.let { return it }
    val distinct = alternatives
        .filter { it.first.isNotBlank() }
        .distinctBy { normalizeLooseValue(it.first) }
    if (distinct.size > 1) {
        conflicts += I983ValidationIssue(
            id = "${fieldKey}_conflict",
            fieldKey = fieldKey,
            message = "Conflicting autofill values were found for this field. Review the chosen value before export.",
            severity = I983ValidationSeverity.CONFLICT
        )
    }
    return distinct.firstOrNull()?.first
}

private fun chooseDate(
    preferred: Long?,
    alternatives: List<Pair<Long, String>>,
    conflicts: MutableList<I983ValidationIssue>,
    fieldKey: String
): Long? {
    preferred?.let { return it }
    val distinct = alternatives.distinctBy { it.first }
    if (distinct.size > 1) {
        conflicts += I983ValidationIssue(
            id = "${fieldKey}_conflict",
            fieldKey = fieldKey,
            message = "Conflicting autofill dates were found for this field. Review the selected date before export.",
            severity = I983ValidationSeverity.CONFLICT
        )
    }
    return distinct.firstOrNull()?.first
}

private fun chooseInt(
    preferred: Int?,
    alternatives: List<Pair<Int, String>>,
    conflicts: MutableList<I983ValidationIssue>,
    fieldKey: String
): Int? {
    preferred?.let { return it }
    val distinct = alternatives.distinctBy { it.first }
    if (distinct.size > 1) {
        conflicts += I983ValidationIssue(
            id = "${fieldKey}_conflict",
            fieldKey = fieldKey,
            message = "Conflicting autofill values were found for this field. Review the selected value before export.",
            severity = I983ValidationSeverity.CONFLICT
        )
    }
    return distinct.firstOrNull()?.first
}

private fun chooseFirstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim()
}

private fun normalizeLooseValue(value: String): String {
    return value.lowercase().replace(Regex("[^a-z0-9]"), "")
}

private fun parseIsoDateMillis(value: String): Long? {
    return runCatching {
        LocalDate.parse(value.trim()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
}
