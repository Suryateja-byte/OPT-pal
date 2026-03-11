package com.sidekick.opt_pal.core.pathway

import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983DraftStatus
import com.sidekick.opt_pal.data.model.PolicyAlertAudience
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertSeverity
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayEvidenceSnapshot
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import java.util.Locale

fun buildVisaPathwayEvidenceSnapshot(
    profile: UserProfile?,
    plannerProfile: VisaPathwayProfile?,
    employments: List<Employment>,
    reportingObligations: List<ReportingObligation>,
    documents: List<DocumentMetadata>,
    i983Drafts: List<I983Draft>,
    trackedCases: List<UscisCaseTracker>,
    policyAlerts: List<PolicyAlertCard>,
    policyStates: List<PolicyAlertState>,
    bundle: VisaPathwayPlannerBundle
): VisaPathwayEvidenceSnapshot {
    val normalizedPlannerProfile = plannerProfile ?: VisaPathwayProfile()
    val currentEmployment = employments
        .filter { it.endDate == null }
        .maxByOrNull { it.startDate }
        ?: employments.maxByOrNull { it.startDate }
    val relevantDrafts = i983Drafts.sortedByDescending { it.updatedAt }
    val matchingEmploymentDraft = currentEmployment?.let { employment ->
        relevantDrafts.firstOrNull { draft -> draft.linkedEmploymentId == employment.id }
    }
    val latestDraft = matchingEmploymentDraft ?: relevantDrafts.firstOrNull()
    val cipCode = parseCipCode(
        latestDraft?.studentSection?.qualifyingMajorAndCipCode,
        latestExtractedValue(documents, "cip_code"),
        profile?.cipCode
    )
    val degreeLevel = firstNonBlank(
        latestDraft?.studentSection?.degreeLevel,
        latestExtractedValue(documents, "degree_level"),
        profile?.majorName,
        normalizedPlannerProfile.degreeLevel
    )
    val hoursPerWeek = latestDraft?.employerSection?.hoursPerWeek
        ?: latestIntValue(documents, "hours_per_week")
        ?: currentEmployment?.hoursPerWeek
    val visibleCriticalAlerts = policyAlerts.filter { alert ->
        !alert.isArchived &&
            !alert.isSuperseded &&
            alert.parsedSeverity == PolicyAlertSeverity.CRITICAL &&
            matchesAudience(alert.parsedAudience, profile) &&
            policyStates.none { state -> state.alertId == alert.id && state.openedAt != null }
    }
    val latestCriticalAlert = visibleCriticalAlerts.maxByOrNull { it.publishedAt }
    val latestTrackedCase = trackedCases
        .filterNot { it.isArchived }
        .maxByOrNull { maxOf(it.lastCheckedAt, it.lastChangedAt) }

    return VisaPathwayEvidenceSnapshot(
        profile = profile,
        plannerProfile = normalizedPlannerProfile,
        employments = employments,
        currentEmployment = currentEmployment,
        reportingObligations = reportingObligations,
        documents = documents,
        i983Drafts = i983Drafts,
        trackedCase = latestTrackedCase,
        latestCriticalPolicyAlertTitle = latestCriticalAlert?.title,
        latestCriticalPolicyAlertId = latestCriticalAlert?.id,
        cipCode = cipCode,
        degreeLevel = degreeLevel,
        optType = firstNonBlank(latestExtractedValue(documents, "opt_type"), profile?.optType),
        optStartDate = latestDateValue(documents, "opt_start_date") ?: profile?.optStartDate,
        optEndDate = latestDateValue(documents, "opt_end_date") ?: profile?.optEndDate,
        hoursPerWeek = hoursPerWeek,
        isStemCipEligible = cipCode?.let { matchesStemCip(it, bundle.stemEligibleCipPrefixes) },
        isRoleRelatedToDegree = normalizedPlannerProfile.roleDirectlyRelatedToDegree,
        hasPriorUsStemDegree = normalizedPlannerProfile.hasPriorUsStemDegree,
        hasPetitioningEmployerOrAgent = normalizedPlannerProfile.hasPetitioningEmployerOrAgent,
        employerType = normalizedPlannerProfile.parsedEmployerType,
        employerUsesEVerify = normalizedPlannerProfile.employerUsesEVerify,
        employerWillSponsorH1b = normalizedPlannerProfile.employerWillSponsorH1b,
        h1bRegistrationStatus = normalizedPlannerProfile.parsedH1bRegistrationStatus,
        o1EvidenceSignals = normalizedPlannerProfile.parsedO1EvidenceSignals,
        i983Ready = latestDraft?.parsedStatus in setOf(
            I983DraftStatus.READY_TO_EXPORT,
            I983DraftStatus.EXPORTED,
            I983DraftStatus.SIGNED
        ),
        hasStatusEscalationIssue = normalizedPlannerProfile.hasStatusViolation == true ||
            normalizedPlannerProfile.hasArrestHistory == true ||
            normalizedPlannerProfile.hasUnauthorizedEmployment == true ||
            normalizedPlannerProfile.hasRfeOrNoid == true ||
            latestTrackedCase?.parsedStage in setOf(
                UscisCaseStage.RFE_OR_NOID,
                UscisCaseStage.DENIED,
                UscisCaseStage.REJECTED
            ),
        desiredContinuityDate = normalizedPlannerProfile.desiredContinuityDate ?: profile?.optEndDate
    )
}

private fun matchesAudience(audience: PolicyAlertAudience, profile: UserProfile?): Boolean {
    return when (audience) {
        PolicyAlertAudience.INITIAL_OPT -> profile?.optType?.lowercase(Locale.US) != "stem"
        PolicyAlertAudience.STEM_OPT -> profile?.optType?.lowercase(Locale.US) == "stem"
        else -> true
    }
}

private fun latestExtractedValue(documents: List<DocumentMetadata>, key: String): String? {
    return documents.sortedByDescending { it.uploadedAt }
        .mapNotNull { document -> document.extractedData?.get(key)?.let(::normalizeLooseValue) }
        .firstOrNull()
}

private fun latestIntValue(documents: List<DocumentMetadata>, key: String): Int? {
    return latestExtractedValue(documents, key)?.filter { it.isDigit() }?.toIntOrNull()
}

private fun latestDateValue(documents: List<DocumentMetadata>, key: String): Long? {
    return documents.sortedByDescending { it.uploadedAt }
        .mapNotNull { document -> document.extractedData?.get(key)?.let(::parseDateValue) }
        .firstOrNull()
}

private fun parseCipCode(vararg values: String?): String? {
    val regex = Regex("""\d{2}\.\d{2,4}""")
    return values.firstNotNullOfOrNull { value ->
        value?.let { regex.find(it)?.value ?: it.takeIf(regex::matches) }
    }
}

private fun firstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim()
}

private fun matchesStemCip(cipCode: String, allowedPrefixes: List<String>): Boolean {
    val normalized = cipCode.trim()
    return allowedPrefixes.any { prefix -> normalized.startsWith(prefix.trim()) }
}

private fun normalizeLooseValue(value: Any): String {
    return when (value) {
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> value.toString().trim()
    }
}

private fun parseDateValue(value: Any): Long? {
    val raw = normalizeLooseValue(value)
    if (raw.isBlank()) return null
    return runCatching {
        when {
            raw.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                java.time.LocalDate.parse(raw).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            }
            raw.matches(Regex("""\d{1,2}/\d{1,2}/\d{4}""")) -> {
                val parts = raw.split("/")
                java.time.LocalDate.of(parts[2].toInt(), parts[0].toInt(), parts[1].toInt())
                    .atStartOfDay()
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli()
            }
            raw.toLongOrNull() != null -> {
                val numeric = raw.toLong()
                if (numeric > 9_999_999_999L) numeric else numeric * 1000L
            }
            else -> null
        }
    }.getOrNull()
}
