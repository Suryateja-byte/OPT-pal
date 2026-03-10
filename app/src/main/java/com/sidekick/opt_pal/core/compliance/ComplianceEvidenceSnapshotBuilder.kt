package com.sidekick.opt_pal.core.compliance

import com.sidekick.opt_pal.core.calculations.UnemploymentDataQualityState
import com.sidekick.opt_pal.core.calculations.calculateUnemploymentForecast
import com.sidekick.opt_pal.core.calculations.utcStartOfDay
import com.sidekick.opt_pal.core.travel.buildTravelEvidenceSnapshot
import com.sidekick.opt_pal.data.model.ComplianceAction
import com.sidekick.opt_pal.data.model.ComplianceDocumentEvidence
import com.sidekick.opt_pal.data.model.ComplianceEvidenceSnapshot
import com.sidekick.opt_pal.data.model.ComplianceMilestone
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertSeverity
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.navigation.AppScreen
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

fun buildComplianceEvidenceSnapshot(
    profile: UserProfile?,
    employments: List<Employment>,
    reportingObligations: List<ReportingObligation>,
    documents: List<DocumentMetadata>,
    trackedCases: List<UscisCaseTracker>,
    policyAlerts: List<PolicyAlertCard>,
    policyStates: List<PolicyAlertState>,
    now: Long
): ComplianceEvidenceSnapshot {
    val forecast = calculateUnemploymentForecast(
        optType = profile?.optType,
        optStartDate = profile?.optStartDate,
        unemploymentTrackingStartDate = profile?.unemploymentTrackingStartDate,
        optEndDate = profile?.optEndDate,
        employments = employments,
        now = now
    )
    val travelEvidence = buildTravelEvidenceSnapshot(
        documents = documents,
        profile = profile,
        employments = employments,
        now = now
    )
    val relevantObligations = reportingObligations.filterNot { it.isCompleted }
    val overdueObligations = relevantObligations.filter { it.dueDate in 1 until now }
    val dueSoonObligations = relevantObligations.filter { obligation ->
        obligation.dueDate >= now && obligation.dueDate <= now + TEN_DAYS_MILLIS
    }
    val stemMilestones = buildStemMilestones(
        profile = profile,
        employments = employments,
        reportingObligations = reportingObligations,
        now = now
    )
    val latestTrackedCase = trackedCases
        .filterNot { it.isArchived }
        .maxByOrNull { maxOf(it.lastChangedAt, it.lastCheckedAt) }
    val criticalPolicyAlerts = policyAlerts.filter { alert ->
        !alert.isArchived &&
            !alert.isSuperseded &&
            alert.parsedSeverity == PolicyAlertSeverity.CRITICAL &&
            policyStates.none { state -> state.alertId == alert.id && state.openedAt != null }
    }
    val latestCriticalPolicyAlert = criticalPolicyAlerts.maxByOrNull { it.publishedAt }
    val missingHoursEmploymentId = employments.firstOrNull { it.hoursPerWeek == null }?.id

    return ComplianceEvidenceSnapshot(
        profile = profile,
        unemploymentForecast = forecast,
        missingHoursEmploymentId = if (forecast.dataQualityState == UnemploymentDataQualityState.NEEDS_HOURS_REVIEW) {
            missingHoursEmploymentId
        } else {
            null
        },
        documents = ComplianceDocumentEvidence(
            hasPassportDocument = hasDocumentType(documents, "passport") || travelEvidence.passportSourceLabel != null,
            hasEadDocument = hasDocumentType(documents, "ead", "employmentauthorization") || travelEvidence.eadSourceLabel != null,
            passportExpirationDate = travelEvidence.passportExpirationDate,
            eadExpirationDate = travelEvidence.eadExpirationDate ?: profile?.optEndDate,
            visaExpirationDate = travelEvidence.visaExpirationDate
        ),
        reportingObligations = reportingObligations,
        overdueReportingObligations = overdueObligations,
        dueSoonReportingObligations = dueSoonObligations,
        stemMilestones = stemMilestones,
        trackedCase = latestTrackedCase,
        latestCriticalPolicyAlertTitle = latestCriticalPolicyAlert?.title,
        latestCriticalPolicyAlertId = latestCriticalPolicyAlert?.id,
        unreadCriticalPolicyCount = criticalPolicyAlerts.size
    )
}

private fun buildStemMilestones(
    profile: UserProfile?,
    employments: List<Employment>,
    reportingObligations: List<ReportingObligation>,
    now: Long
): List<ComplianceMilestone> {
    if (!profile?.optType.equals("stem", ignoreCase = true)) {
        return emptyList()
    }
    val stemStart = profile?.optStartDate ?: return emptyList()
    val stemEnd = profile.optEndDate
    val milestones = mutableListOf<ComplianceMilestone>()
    val validationMonths = listOf(6L, 12L, 18L)
    validationMonths.forEach { month ->
        val dueDate = plusUtcMonths(stemStart, month)
        milestones += ComplianceMilestone(
            id = "stem_validation_$month",
            title = "STEM $month-month validation",
            dueDate = dueDate,
            isOverdue = dueDate < now,
            inferredOnly = !hasMatchingReportingEvidence(
                reportingObligations = reportingObligations,
                dueDate = dueDate,
                keywords = listOf("validation", "stem")
            ),
            action = ComplianceAction(
                id = "open_reporting",
                label = "Open Reporting",
                route = AppScreen.Reporting.route
            )
        )
    }
    listOf(12L, 24L).forEach { month ->
        val dueDate = if (month == 24L && stemEnd != null) stemEnd else plusUtcMonths(stemStart, month)
        milestones += ComplianceMilestone(
            id = "stem_evaluation_$month",
            title = if (month == 24L) "STEM final self-evaluation" else "STEM annual self-evaluation",
            dueDate = dueDate,
            isOverdue = dueDate < now,
            inferredOnly = !hasMatchingReportingEvidence(
                reportingObligations = reportingObligations,
                dueDate = dueDate,
                keywords = listOf("evaluation", "stem", "i-983")
            ),
            action = ComplianceAction(
                id = "open_reporting",
                label = "Open Reporting",
                route = AppScreen.Reporting.route
            )
        )
    }
    employments
        .filter { employment -> employment.endDate != null && employment.endDate > stemStart }
        .forEachIndexed { index, employment ->
            val dueDate = utcStartOfDay((employment.endDate ?: return@forEachIndexed) + TEN_DAYS_MILLIS)
            milestones += ComplianceMilestone(
                id = "stem_departure_eval_$index",
                title = "Final self-evaluation after employer end",
                dueDate = dueDate,
                isOverdue = dueDate < now,
                inferredOnly = !hasMatchingReportingEvidence(
                    reportingObligations = reportingObligations,
                    dueDate = dueDate,
                    keywords = listOf("evaluation", "final", "employment", "stem")
                ),
                action = ComplianceAction(
                    id = "open_reporting",
                    label = "Open Reporting",
                    route = AppScreen.Reporting.route
                )
            )
        }
    return milestones.sortedBy { it.dueDate }
}

private fun hasMatchingReportingEvidence(
    reportingObligations: List<ReportingObligation>,
    dueDate: Long,
    keywords: List<String>
): Boolean {
    return reportingObligations.any { obligation ->
        val description = obligation.description.lowercase(Locale.US)
        val eventType = obligation.eventType.lowercase(Locale.US)
        val dueDateMatches = kotlin.math.abs(utcStartOfDay(obligation.dueDate) - utcStartOfDay(dueDate)) <= THIRTY_DAYS_MILLIS
        val keywordMatch = keywords.any { keyword ->
            description.contains(keyword) || eventType.contains(keyword)
        }
        dueDateMatches && keywordMatch
    }
}

private fun hasDocumentType(documents: List<DocumentMetadata>, vararg keywords: String): Boolean {
    val normalizedKeywords = keywords.map { it.lowercase(Locale.US) }
    return documents.any { document ->
        val haystack = listOf(document.documentType, document.userTag, document.fileName)
            .joinToString(" ")
            .lowercase(Locale.US)
        normalizedKeywords.any { keyword -> haystack.contains(keyword) }
    }
}

private fun plusUtcMonths(startMillis: Long, months: Long): Long {
    return Instant.ofEpochMilli(utcStartOfDay(startMillis))
        .atZone(ZoneOffset.UTC)
        .plus(months, ChronoUnit.MONTHS)
        .toLocalDate()
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
}

private const val TEN_DAYS_MILLIS = 10L * 24L * 60L * 60L * 1000L
private const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L
