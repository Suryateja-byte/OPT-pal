package com.sidekick.opt_pal.core.compliance

import com.sidekick.opt_pal.core.calculations.UnemploymentAlertThreshold
import com.sidekick.opt_pal.core.calculations.UnemploymentDataQualityState
import com.sidekick.opt_pal.data.model.ComplianceAction
import com.sidekick.opt_pal.data.model.ComplianceBlocker
import com.sidekick.opt_pal.data.model.ComplianceEvidenceSnapshot
import com.sidekick.opt_pal.data.model.ComplianceFactorAssessment
import com.sidekick.opt_pal.data.model.ComplianceFactorId
import com.sidekick.opt_pal.data.model.ComplianceHealthScore
import com.sidekick.opt_pal.data.model.ComplianceReference
import com.sidekick.opt_pal.data.model.ComplianceScoreBand
import com.sidekick.opt_pal.data.model.ComplianceScoreQuality
import com.sidekick.opt_pal.data.model.UscisCaseClassification
import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.navigation.AppScreen
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class ComplianceScoreEngine(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    fun score(
        evidence: ComplianceEvidenceSnapshot,
        delta: Int? = null,
        now: Long = timeProvider()
    ): ComplianceHealthScore {
        val factors = listOf(
            buildUnemploymentFactor(evidence),
            buildReportingFactor(evidence, now),
            buildDocumentsFactor(evidence, now),
            buildUscisFactor(evidence, now)
        )
        val blockers = buildBlockers(evidence, now)
        val quality = if (factors.all { it.isVerified }) {
            ComplianceScoreQuality.VERIFIED
        } else {
            ComplianceScoreQuality.PROVISIONAL
        }
        val rawScore = factors.sumOf { it.score }
        val provisionalCap = if (quality == ComplianceScoreQuality.PROVISIONAL) 89 else 100
        val blockerCap = blockers.minOfOrNull { it.scoreCap } ?: 100
        val finalScore = rawScore.coerceAtMost(minOf(provisionalCap, blockerCap)).coerceIn(0, 100)
        val band = bandForScore(finalScore)
        val topReasons = blockers.map { it.title }.ifEmpty {
            factors.filterNot(ComplianceFactorAssessment::isPerfect)
                .sortedByDescending { it.maxScore - it.score }
                .take(3)
                .map { it.summary }
        }

        return ComplianceHealthScore(
            score = finalScore,
            band = band,
            quality = quality,
            headline = buildHeadline(band, blockers.isNotEmpty()),
            summary = buildSummary(band, quality, blockers),
            delta = delta,
            computedAt = now,
            topReasons = topReasons,
            blockers = blockers,
            factors = factors,
            latestCriticalPolicyAlertTitle = evidence.latestCriticalPolicyAlertTitle,
            latestCriticalPolicyAlertId = evidence.latestCriticalPolicyAlertId,
            unreadCriticalPolicyCount = evidence.unreadCriticalPolicyCount
        )
    }

    private fun buildUnemploymentFactor(evidence: ComplianceEvidenceSnapshot): ComplianceFactorAssessment {
        val forecast = evidence.unemploymentForecast
        val action = when (forecast.dataQualityState) {
            UnemploymentDataQualityState.NEEDS_HOURS_REVIEW -> ComplianceAction(
                id = "review_hours",
                label = "Review employment hours",
                route = evidence.missingHoursEmploymentId?.let(AppScreen.EditEmployment::createRoute)
            )
            UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START -> ComplianceAction(
                id = "open_dashboard",
                label = "Review dashboard setup",
                route = AppScreen.Dashboard.route
            )
            UnemploymentDataQualityState.READY -> ComplianceAction(
                id = "open_dashboard",
                label = "Review unemployment status",
                route = AppScreen.Dashboard.route
            )
        }
        val score = when (forecast.dataQualityState) {
            UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START -> 20
            UnemploymentDataQualityState.NEEDS_HOURS_REVIEW -> 24
            UnemploymentDataQualityState.READY -> when {
                forecast.usedDays > forecast.allowedDays -> 0
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_88 && forecast.clockRunningNow -> 4
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_88 -> 8
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_85 && forecast.clockRunningNow -> 8
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_85 -> 14
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_80 && forecast.clockRunningNow -> 16
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_80 -> 22
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_75 && forecast.clockRunningNow -> 22
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_75 -> 28
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_60 && forecast.clockRunningNow -> 28
                forecast.currentThreshold == UnemploymentAlertThreshold.DAY_60 -> 34
                forecast.clockRunningNow -> 36
                else -> 40
            }
        }
        val detail = when (forecast.dataQualityState) {
            UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START ->
                "Your STEM score is provisional because the app still needs the original post-completion OPT start date to verify the full 150-day unemployment window."
            UnemploymentDataQualityState.NEEDS_HOURS_REVIEW ->
                "Your unemployment score is provisional because at least one employment record is missing hours per week."
            UnemploymentDataQualityState.READY -> when {
                forecast.usedDays > forecast.allowedDays ->
                    "You are over the recorded unemployment limit. This is a critical status risk."
                forecast.clockRunningNow ->
                    "Your unemployment clock is currently running with ${forecast.usedDays} of ${forecast.allowedDays} days used."
                else ->
                    "Your unemployment clock is currently paused with ${forecast.usedDays} of ${forecast.allowedDays} days used."
            }
        }
        return ComplianceFactorAssessment(
            id = ComplianceFactorId.UNEMPLOYMENT,
            title = "Unemployment compliance",
            score = score,
            maxScore = 40,
            summary = when {
                forecast.usedDays > forecast.allowedDays -> "Recorded unemployment exceeds the allowed OPT limit."
                forecast.dataQualityState != UnemploymentDataQualityState.READY -> "Unemployment scoring is provisional until the missing data is fixed."
                forecast.clockRunningNow -> "Your unemployment clock is running."
                else -> "Your unemployment clock is paused."
            },
            detail = detail,
            isVerified = forecast.dataQualityState == UnemploymentDataQualityState.READY,
            actions = listOfNotNull(action),
            references = unemploymentReferences()
        )
    }

    private fun buildReportingFactor(evidence: ComplianceEvidenceSnapshot, now: Long): ComplianceFactorAssessment {
        val actualOverdue = evidence.overdueReportingObligations
        val actualDueSoon = evidence.dueSoonReportingObligations
        val inferredStemIssues = evidence.stemMilestones.filter { milestone ->
            milestone.inferredOnly && milestone.dueDate <= now + THIRTY_DAYS_MILLIS
        }
        val score = when {
            actualOverdue.any { now - it.dueDate > TEN_DAYS_MILLIS } -> 0
            actualOverdue.isNotEmpty() -> 10
            inferredStemIssues.any { it.isOverdue } -> 14
            actualDueSoon.isNotEmpty() || inferredStemIssues.isNotEmpty() -> 24
            else -> 30
        }
        val summary = when {
            actualOverdue.any { now - it.dueDate > TEN_DAYS_MILLIS } ->
                "At least one reporting task is overdue past the normal reporting window."
            actualOverdue.isNotEmpty() ->
                "At least one reporting task is overdue."
            inferredStemIssues.any { it.isOverdue } ->
                "A STEM reporting milestone appears overdue, but the app cannot verify whether you completed it outside OPTPal."
            actualDueSoon.isNotEmpty() || inferredStemIssues.isNotEmpty() ->
                "You have reporting work coming due soon."
            else ->
                "No upcoming or overdue reporting risk was found in tracked obligations."
        }
        val detail = buildString {
            append(summary)
            if (actualDueSoon.isNotEmpty()) {
                append(" Next tracked deadline: ${formatUtcDate(actualDueSoon.minOf { it.dueDate })}.")
            }
            if (inferredStemIssues.isNotEmpty()) {
                append(" STEM milestones are shown conservatively because OPTPal cannot confirm off-app submissions.")
            }
        }
        return ComplianceFactorAssessment(
            id = ComplianceFactorId.REPORTING,
            title = "Reporting status",
            score = score,
            maxScore = 30,
            summary = summary,
            detail = detail,
            isVerified = inferredStemIssues.isEmpty(),
            actions = listOf(
                ComplianceAction(
                    id = "open_reporting",
                    label = "Open Reporting",
                    route = AppScreen.Reporting.route
                )
            ),
            references = reportingReferences()
        )
    }

    private fun buildDocumentsFactor(evidence: ComplianceEvidenceSnapshot, now: Long): ComplianceFactorAssessment {
        val documents = evidence.documents
        val trackedCase = evidence.trackedCase
        val eadScore = scoreEadReadiness(
            eadExpirationDate = documents.eadExpirationDate,
            trackedCase = trackedCase,
            optType = evidence.profile?.optType,
            now = now
        )
        val passportScore = scorePassportReadiness(documents.passportExpirationDate, now)
        val completenessScore = when {
            documents.hasPassportDocument && documents.hasEadDocument -> 2
            documents.hasPassportDocument || documents.hasEadDocument || documents.eadExpirationDate != null -> 1
            else -> 0
        }
        val summary = when {
            documents.eadExpirationDate == null -> "Core OPT document data is incomplete."
            documents.eadExpirationDate < now -> "Your current work authorization document appears expired."
            documents.passportExpirationDate != null && documents.passportExpirationDate < now ->
                "Your passport appears expired."
            else -> "Your core OPT documents are currently usable, but expiration timing still affects the score."
        }
        return ComplianceFactorAssessment(
            id = ComplianceFactorId.DOCUMENTS,
            title = "Document readiness",
            score = (eadScore + passportScore + completenessScore).coerceIn(0, 20),
            maxScore = 20,
            summary = summary,
            detail = buildString {
                append(summary)
                documents.eadExpirationDate?.let {
                    append(" EAD/OPT end date: ${formatUtcDate(it)}.")
                }
                documents.passportExpirationDate?.let {
                    append(" Passport expiration: ${formatUtcDate(it)}.")
                }
                append(" Visa expiration is intentionally excluded from the base compliance score while you remain in the United States.")
            },
            isVerified = documents.passportExpirationDate != null && documents.eadExpirationDate != null,
            actions = listOf(
                ComplianceAction(
                    id = "upload_document",
                    label = "Upload document",
                    route = AppScreen.DocumentSelection.route
                )
            ),
            references = documentReferences()
        )
    }

    private fun buildUscisFactor(evidence: ComplianceEvidenceSnapshot, now: Long): ComplianceFactorAssessment {
        val trackedCase = evidence.trackedCase
        if (trackedCase == null) {
            return ComplianceFactorAssessment(
                id = ComplianceFactorId.USCIS_CASE,
                title = "USCIS case progress",
                score = 10,
                maxScore = 10,
                summary = "No active USCIS case is linked, so this factor stays neutral.",
                detail = "This factor only reacts to tracked Form I-765 case states. It does not penalize you for not linking the tracker.",
                isVerified = true,
                actions = listOf(
                    ComplianceAction(
                        id = "open_case_tracker",
                        label = "Open USCIS tracker",
                        route = AppScreen.CaseStatus.createRoute()
                    )
                ),
                references = uscisReferences()
            )
        }

        val score = when {
            trackedCase.parsedStage in setOf(UscisCaseStage.DENIED, UscisCaseStage.REJECTED, UscisCaseStage.WITHDRAWN) -> 0
            trackedCase.parsedStage == UscisCaseStage.RFE_OR_NOID -> 3
            trackedCase.parsedClassification == UscisCaseClassification.CONSULT_DSO_ATTORNEY -> 2
            trackedCase.parsedStage in setOf(
                UscisCaseStage.APPROVED,
                UscisCaseStage.CARD_PRODUCED,
                UscisCaseStage.CARD_PICKED_UP,
                UscisCaseStage.CARD_DELIVERED
            ) -> 10
            trackedCase.lastError.isNotBlank() -> 6
            trackedCase.parsedStage in setOf(UscisCaseStage.RECEIVED, UscisCaseStage.ACTIVE_REVIEW) -> 8
            else -> 7
        }
        val summary = when {
            trackedCase.parsedStage in setOf(UscisCaseStage.DENIED, UscisCaseStage.REJECTED, UscisCaseStage.WITHDRAWN) ->
                "Your tracked I-765 case is in a terminal negative state."
            trackedCase.parsedStage == UscisCaseStage.RFE_OR_NOID ->
                "Your tracked I-765 case is waiting on an RFE or NOID response."
            trackedCase.parsedClassification == UscisCaseClassification.CONSULT_DSO_ATTORNEY ->
                "Your tracked I-765 case includes a consult-attorney classification."
            trackedCase.parsedStage in setOf(UscisCaseStage.APPROVED, UscisCaseStage.CARD_PRODUCED, UscisCaseStage.CARD_DELIVERED) ->
                "Your tracked I-765 case is in a favorable post-approval stage."
            else ->
                "Your tracked I-765 case is active and should still be monitored."
        }
        return ComplianceFactorAssessment(
            id = ComplianceFactorId.USCIS_CASE,
            title = "USCIS case progress",
            score = score,
            maxScore = 10,
            summary = summary,
            detail = buildString {
                append(summary)
                if (trackedCase.lastCheckedAt > 0L) {
                    append(" Last checked ${formatUtcDate(trackedCase.lastCheckedAt)}.")
                }
                append(" Use the official case-status and processing-times tools before escalating a delay.")
            },
            isVerified = true,
            actions = buildUscisActions(trackedCase),
            references = uscisReferences()
        )
    }

    private fun buildBlockers(evidence: ComplianceEvidenceSnapshot, now: Long): List<ComplianceBlocker> {
        val blockers = mutableListOf<ComplianceBlocker>()
        if (evidence.unemploymentForecast.usedDays > evidence.unemploymentForecast.allowedDays) {
            blockers += ComplianceBlocker(
                id = "over_unemployment_limit",
                title = "Unemployment limit exceeded",
                detail = "Your recorded unemployment total is above the allowed OPT limit.",
                scoreCap = 19,
                action = ComplianceAction(
                    id = "open_reporting",
                    label = "Open Reporting",
                    route = AppScreen.Reporting.route
                )
            )
        }
        if (evidence.overdueReportingObligations.any { now - it.dueDate > TEN_DAYS_MILLIS } ||
            evidence.stemMilestones.any { it.inferredOnly && it.isOverdue && now - it.dueDate > TEN_DAYS_MILLIS }
        ) {
            blockers += ComplianceBlocker(
                id = "reporting_overdue",
                title = "Reporting appears overdue",
                detail = "At least one tracked or inferred reporting item is overdue beyond the normal reporting window.",
                scoreCap = 29,
                action = ComplianceAction(
                    id = "open_reporting",
                    label = "Open Reporting",
                    route = AppScreen.Reporting.route
                )
            )
        }
        val trackedCase = evidence.trackedCase
        val eadExpirationDate = evidence.documents.eadExpirationDate
        if (eadExpirationDate != null && eadExpirationDate < now && !hasSafeStemPendingPath(trackedCase, evidence.profile?.optType, eadExpirationDate, now)) {
            blockers += ComplianceBlocker(
                id = "ead_expired",
                title = "Current work authorization appears expired",
                detail = "The current EAD/OPT end date is in the past and there is no tracked safe pending STEM-extension path.",
                scoreCap = 24,
                action = ComplianceAction(
                    id = "open_case_tracker",
                    label = "Open USCIS tracker",
                    route = AppScreen.CaseStatus.createRoute(trackedCase?.id)
                )
            )
        }
        if (trackedCase?.parsedStage in setOf(UscisCaseStage.DENIED, UscisCaseStage.REJECTED, UscisCaseStage.WITHDRAWN)) {
            blockers += ComplianceBlocker(
                id = "uscis_negative_terminal",
                title = "Tracked USCIS case needs escalation",
                detail = "Your tracked I-765 case is in a negative terminal state.",
                scoreCap = 19,
                action = ComplianceAction(
                    id = "open_case_tracker",
                    label = "Open USCIS tracker",
                    route = AppScreen.CaseStatus.createRoute(trackedCase?.id)
                )
            )
        }
        return blockers
    }

    private fun buildUscisActions(
        trackedCase: com.sidekick.opt_pal.data.model.UscisCaseTracker
    ): List<ComplianceAction> {
        val actions = mutableListOf(
            ComplianceAction(
                id = "open_case_tracker",
                label = "Open USCIS tracker",
                route = AppScreen.CaseStatus.createRoute(trackedCase.id.ifBlank { null })
            )
        )
        if (trackedCase.parsedStage in setOf(UscisCaseStage.RECEIVED, UscisCaseStage.ACTIVE_REVIEW)) {
            actions += ComplianceAction(
                id = "open_processing_times",
                label = "Open processing times",
                externalUrl = "https://egov.uscis.gov/processing-times/"
            )
        }
        return actions
    }

    private fun scoreEadReadiness(
        eadExpirationDate: Long?,
        trackedCase: com.sidekick.opt_pal.data.model.UscisCaseTracker?,
        optType: String?,
        now: Long
    ): Int {
        if (eadExpirationDate == null) return 6
        if (eadExpirationDate < now) {
            return if (hasSafeStemPendingPath(trackedCase, optType, eadExpirationDate, now)) 8 else 0
        }
        val daysUntilExpiration = ((eadExpirationDate - now) / ONE_DAY_MILLIS).toInt()
        return when {
            daysUntilExpiration <= 14 -> 3
            daysUntilExpiration <= 30 -> 6
            daysUntilExpiration <= 60 -> 8
            daysUntilExpiration <= 90 -> 10
            else -> 12
        }
    }

    private fun scorePassportReadiness(passportExpirationDate: Long?, now: Long): Int {
        if (passportExpirationDate == null) return 4
        if (passportExpirationDate < now) return 0
        val daysUntilExpiration = ((passportExpirationDate - now) / ONE_DAY_MILLIS).toInt()
        return when {
            daysUntilExpiration <= 30 -> 2
            daysUntilExpiration <= 180 -> 4
            else -> 6
        }
    }

    private fun hasSafeStemPendingPath(
        trackedCase: com.sidekick.opt_pal.data.model.UscisCaseTracker?,
        optType: String?,
        eadExpirationDate: Long,
        now: Long
    ): Boolean {
        if (!optType.equals("stem", ignoreCase = true)) return false
        if (trackedCase == null) return false
        if (trackedCase.parsedStage in setOf(UscisCaseStage.DENIED, UscisCaseStage.REJECTED, UscisCaseStage.WITHDRAWN)) {
            return false
        }
        return now <= eadExpirationDate + 180L * ONE_DAY_MILLIS
    }

    private fun bandForScore(score: Int): ComplianceScoreBand {
        return when {
            score >= 90 -> ComplianceScoreBand.STABLE
            score >= 75 -> ComplianceScoreBand.WATCH
            score >= 50 -> ComplianceScoreBand.ACTION_NEEDED
            else -> ComplianceScoreBand.CRITICAL
        }
    }

    private fun buildHeadline(band: ComplianceScoreBand, hasBlockers: Boolean): String {
        return when {
            hasBlockers -> "Critical compliance issues need attention."
            band == ComplianceScoreBand.STABLE -> "Your tracked compliance signals look stable."
            band == ComplianceScoreBand.WATCH -> "Your tracked compliance signals need monitoring."
            band == ComplianceScoreBand.ACTION_NEEDED -> "You have tracked compliance items to fix soon."
            else -> "Your tracked compliance score is in a critical range."
        }
    }

    private fun buildSummary(
        band: ComplianceScoreBand,
        quality: ComplianceScoreQuality,
        blockers: List<ComplianceBlocker>
    ): String {
        val base = when (band) {
            ComplianceScoreBand.STABLE -> "Your current unemployment, reporting, document, and USCIS signals are generally healthy."
            ComplianceScoreBand.WATCH -> "One or more tracked factors need attention before they become higher-risk problems."
            ComplianceScoreBand.ACTION_NEEDED -> "Several tracked factors are pulling your score down and should be addressed soon."
            ComplianceScoreBand.CRITICAL -> "Your tracked factors show a serious compliance risk."
        }
        val qualifier = if (quality == ComplianceScoreQuality.PROVISIONAL) {
            " This score is provisional because the app is missing some evidence or is relying on inferred milestones."
        } else {
            ""
        }
        val blockerText = if (blockers.isNotEmpty()) {
            " Hard blockers cap the score until the underlying issue is resolved."
        } else {
            ""
        }
        return base + qualifier + blockerText
    }

    private fun unemploymentReferences(): List<ComplianceReference> {
        return listOf(
            ComplianceReference(
                id = "opt_unemployment",
                label = "SEVIS Help Hub: F-1 Optional Practical Training",
                url = "https://studyinthestates.dhs.gov/sevis-help-hub/student-records/fm-student-employment/f-1-optional-practical-training-opt",
                lastReviewedDate = "Mar 10, 2026",
                summary = "Official OPT unemployment and reporting guidance."
            ),
            ComplianceReference(
                id = "stem_opt_extension",
                label = "SEVIS Help Hub: F-1 STEM OPT Extension",
                url = "https://studyinthestates.dhs.gov/sevis-help-hub/student-records/fm-student-employment/f-1-stem-optional-practical-training-opt-extension",
                lastReviewedDate = "Mar 10, 2026",
                summary = "Official STEM OPT unemployment extension guidance."
            )
        )
    }

    private fun reportingReferences(): List<ComplianceReference> {
        return listOf(
            ComplianceReference(
                id = "opt_reporting",
                label = "Study in the States: OPT Student Reporting Requirements",
                url = "https://studyinthestates.dhs.gov/opt-student-reporting-requirements",
                lastReviewedDate = "Mar 10, 2026",
                summary = "Official OPT reporting windows and required updates."
            ),
            ComplianceReference(
                id = "stem_reporting",
                label = "Study in the States: STEM OPT Reporting Requirements",
                url = "https://studyinthestates.dhs.gov/students-stem-opt-reporting-requirements",
                lastReviewedDate = "Mar 10, 2026",
                summary = "Official STEM validation and self-evaluation requirements."
            )
        )
    }

    private fun documentReferences(): List<ComplianceReference> {
        return listOf(
            ComplianceReference(
                id = "ice_travel",
                label = "ICE SEVIS Travel",
                url = "https://www.ice.gov/sevis/travel",
                lastReviewedDate = "Mar 10, 2026",
                summary = "Official OPT document readiness and travel-document guidance."
            ),
            ComplianceReference(
                id = "duration_of_status",
                label = "Study in the States: What is My Duration of Status?",
                url = "https://studyinthestates.dhs.gov/2015/03/what-my-duration-status",
                lastReviewedDate = "Mar 10, 2026",
                summary = "Visa expiration does not determine how long a student may remain in the United States."
            )
        )
    }

    private fun uscisReferences(): List<ComplianceReference> {
        return listOf(
            ComplianceReference(
                id = "uscis_case_status",
                label = "USCIS: Checking Your Case Status Online",
                url = "https://www.uscis.gov/tools/checking-your-case-status-online",
                lastReviewedDate = "Mar 10, 2026",
                summary = "Official USCIS case-status monitoring guidance."
            ),
            ComplianceReference(
                id = "uscis_myprogress",
                label = "USCIS: myProgress for Form I-765",
                url = "https://www.uscis.gov/newsroom/alerts/uscis-expands-myprogress-to-form-i-765-and-form-i-131",
                lastReviewedDate = "Mar 10, 2026",
                summary = "USCIS myProgress helps track processing estimates for I-765."
            )
        )
    }

    private fun formatUtcDate(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    }

    private companion object {
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val TEN_DAYS_MILLIS = 10L * ONE_DAY_MILLIS
        const val THIRTY_DAYS_MILLIS = 30L * ONE_DAY_MILLIS
    }
}
