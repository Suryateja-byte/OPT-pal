package com.sidekick.opt_pal.core.calculations

import com.sidekick.opt_pal.data.model.Employment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MILLIS_IN_DAY = 86_400_000L
private const val MIN_QUALIFYING_HOURS = 20

data class EmploymentPeriod(
    val start: Long,
    val end: Long?
)

enum class UnemploymentDataQualityState {
    READY,
    NEEDS_HOURS_REVIEW,
    NEEDS_STEM_CYCLE_START
}

enum class UnemploymentAlertThreshold(val triggerDays: Int?) {
    NONE(null),
    DAY_60(60),
    DAY_75(75),
    DAY_80(80),
    DAY_85(85),
    DAY_88(88),
    OVER_LIMIT(null);

    companion object {
        fun reachedThresholds(usedDays: Int, allowedDays: Int): Set<UnemploymentAlertThreshold> {
            val thresholds = mutableSetOf<UnemploymentAlertThreshold>()
            if (usedDays >= 60) thresholds += DAY_60
            if (usedDays >= 75) thresholds += DAY_75
            if (usedDays >= 80) thresholds += DAY_80
            if (usedDays >= 85) thresholds += DAY_85
            if (usedDays >= 88) thresholds += DAY_88
            if (usedDays > allowedDays) thresholds += OVER_LIMIT
            return thresholds
        }

        fun current(usedDays: Int, allowedDays: Int): UnemploymentAlertThreshold {
            return when {
                usedDays > allowedDays -> OVER_LIMIT
                usedDays >= 88 -> DAY_88
                usedDays >= 85 -> DAY_85
                usedDays >= 80 -> DAY_80
                usedDays >= 75 -> DAY_75
                usedDays >= 60 -> DAY_60
                else -> NONE
            }
        }
    }
}

data class UnemploymentForecast(
    val usedDays: Int,
    val remainingDays: Int,
    val allowedDays: Int,
    val clockRunningNow: Boolean,
    val currentGapStartDate: Long?,
    val projectedExceedDate: Long?,
    val currentThreshold: UnemploymentAlertThreshold,
    val dataQualityState: UnemploymentDataQualityState,
    val isEstimate: Boolean
)

fun List<Employment>.toEmploymentPeriods(): List<EmploymentPeriod> {
    return map { EmploymentPeriod(it.startDate, it.endDate) }
}

fun calculateUnemploymentDays(
    optStartDate: Long,
    employmentPeriods: List<EmploymentPeriod>,
    today: Long
): Int {
    val normalizedToday = utcStartOfDay(today)
    val normalizedStart = utcStartOfDay(optStartDate)
    if (normalizedToday <= normalizedStart) return 0
    val sortedPeriods = employmentPeriods.sortedBy { it.start }
    var unemploymentDays = 0
    var cursor = normalizedStart
    sortedPeriods.forEach { period ->
        val jobStart = utcStartOfDay(period.start)
        if (jobStart > cursor) {
            unemploymentDays += millisToDays(jobStart - cursor)
        }
        val jobEnd = (period.end?.let(::utcStartOfDay) ?: normalizedToday).coerceAtLeast(jobStart)
        if (jobEnd > cursor) {
            cursor = jobEnd
        }
        if (cursor >= normalizedToday) {
            return unemploymentDays
        }
    }
    if (cursor < normalizedToday) {
        unemploymentDays += millisToDays(normalizedToday - cursor)
    }
    return unemploymentDays
}

fun allowedUnemploymentDays(optType: String?): Int {
    return when (optType?.lowercase()) {
        "stem" -> 150
        else -> 90
    }
}

fun calculateUnemploymentForecast(
    optType: String?,
    optStartDate: Long?,
    unemploymentTrackingStartDate: Long?,
    optEndDate: Long?,
    employments: List<Employment>,
    now: Long
): UnemploymentForecast {
    val allowedDays = allowedUnemploymentDays(optType)
    val normalizedNow = utcStartOfDay(now)
    val normalizedOptStart = optStartDate?.let(::utcStartOfDay)
    val normalizedTrackingStart = unemploymentTrackingStartDate?.let(::utcStartOfDay)
    val normalizedOptEndExclusive = optEndDate?.let { utcStartOfDay(it) + MILLIS_IN_DAY }
    val isStem = optType.equals("stem", ignoreCase = true)
    val hasMissingHours = employments.any { it.hoursPerWeek == null }

    if (normalizedOptStart == null) {
        return UnemploymentForecast(
            usedDays = 0,
            remainingDays = allowedDays,
            allowedDays = allowedDays,
            clockRunningNow = false,
            currentGapStartDate = null,
            projectedExceedDate = null,
            currentThreshold = UnemploymentAlertThreshold.NONE,
            dataQualityState = UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START,
            isEstimate = false
        )
    }

    val estimateStart = normalizedTrackingStart ?: normalizedOptStart
    val estimateEnd = normalizedOptEndExclusive?.coerceAtMost(normalizedNow) ?: normalizedNow
    val estimateUsed = calculateUnemploymentDays(
        estimateStart,
        employments.toEmploymentPeriods(),
        estimateEnd
    )

    if (isStem && normalizedTrackingStart == null) {
        return UnemploymentForecast(
            usedDays = estimateUsed,
            remainingDays = (allowedDays - estimateUsed).coerceAtLeast(0),
            allowedDays = allowedDays,
            clockRunningNow = false,
            currentGapStartDate = null,
            projectedExceedDate = null,
            currentThreshold = UnemploymentAlertThreshold.current(estimateUsed, allowedDays),
            dataQualityState = UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START,
            isEstimate = true
        )
    }

    if (hasMissingHours) {
        return UnemploymentForecast(
            usedDays = estimateUsed,
            remainingDays = (allowedDays - estimateUsed).coerceAtLeast(0),
            allowedDays = allowedDays,
            clockRunningNow = false,
            currentGapStartDate = null,
            projectedExceedDate = null,
            currentThreshold = UnemploymentAlertThreshold.current(estimateUsed, allowedDays),
            dataQualityState = UnemploymentDataQualityState.NEEDS_HOURS_REVIEW,
            isEstimate = true
        )
    }

    val trackingStart = normalizedTrackingStart ?: normalizedOptStart
    val evaluationEnd = normalizedOptEndExclusive?.coerceAtMost(normalizedNow) ?: normalizedNow
    var usedDays = 0
    var openGapStart: Long? = null
    var cursor = trackingStart

    while (cursor < evaluationEnd) {
        val qualifies = hasQualifyingEmploymentOnDay(
            dayStart = cursor,
            employments = employments,
            isStem = isStem
        )
        if (qualifies) {
            openGapStart = null
        } else {
            usedDays += 1
            if (openGapStart == null) {
                openGapStart = cursor
            }
        }
        cursor += MILLIS_IN_DAY
    }

    val optActiveToday = normalizedOptEndExclusive == null || normalizedNow < normalizedOptEndExclusive
    val qualifiesToday = optActiveToday && hasQualifyingEmploymentOnDay(
        dayStart = normalizedNow,
        employments = employments,
        isStem = isStem
    )
    val clockRunningNow = optActiveToday && !qualifiesToday
    val currentGapStart = when {
        !clockRunningNow -> null
        openGapStart != null -> openGapStart
        else -> normalizedNow
    }

    val projectedExceedDate = if (!clockRunningNow) {
        null
    } else if (usedDays > allowedDays) {
        normalizedNow
    } else {
        normalizedNow + ((allowedDays - usedDays + 1).toLong() * MILLIS_IN_DAY)
    }

    return UnemploymentForecast(
        usedDays = usedDays,
        remainingDays = (allowedDays - usedDays).coerceAtLeast(0),
        allowedDays = allowedDays,
        clockRunningNow = clockRunningNow,
        currentGapStartDate = currentGapStart,
        projectedExceedDate = projectedExceedDate,
        currentThreshold = UnemploymentAlertThreshold.current(usedDays, allowedDays),
        dataQualityState = UnemploymentDataQualityState.READY,
        isEstimate = false
    )
}

fun clearThresholdsAboveCurrent(
    firedThresholds: Set<UnemploymentAlertThreshold>,
    currentThreshold: UnemploymentAlertThreshold
): Set<UnemploymentAlertThreshold> {
    val currentRank = currentThreshold.rank()
    return firedThresholds.filterTo(linkedSetOf()) { it.rank() <= currentRank }
}

fun highestNewThresholdToNotify(
    forecast: UnemploymentForecast,
    firedThresholds: Set<UnemploymentAlertThreshold>
): UnemploymentAlertThreshold? {
    if (forecast.dataQualityState != UnemploymentDataQualityState.READY) return null
    val reached = UnemploymentAlertThreshold.reachedThresholds(forecast.usedDays, forecast.allowedDays)
    return reached
        .filterNot { it in firedThresholds }
        .maxByOrNull { it.rank() }
}

private fun UnemploymentAlertThreshold.rank(): Int {
    return when (this) {
        UnemploymentAlertThreshold.NONE -> 0
        UnemploymentAlertThreshold.DAY_60 -> 1
        UnemploymentAlertThreshold.DAY_75 -> 2
        UnemploymentAlertThreshold.DAY_80 -> 3
        UnemploymentAlertThreshold.DAY_85 -> 4
        UnemploymentAlertThreshold.DAY_88 -> 5
        UnemploymentAlertThreshold.OVER_LIMIT -> 6
    }
}

private fun hasQualifyingEmploymentOnDay(
    dayStart: Long,
    employments: List<Employment>,
    isStem: Boolean
): Boolean {
    val activeJobs = employments.filter { employment ->
        val start = utcStartOfDay(employment.startDate)
        val end = employment.endDate?.let(::utcStartOfDay)
        dayStart >= start && (end == null || dayStart < end)
    }
    if (activeJobs.isEmpty()) return false
    return if (isStem) {
        activeJobs.any { (it.hoursPerWeek ?: 0) >= MIN_QUALIFYING_HOURS }
    } else {
        activeJobs.sumOf { it.hoursPerWeek ?: 0 } >= MIN_QUALIFYING_HOURS
    }
}

private fun millisToDays(millis: Long): Int {
    if (millis <= 0L) return 0
    return ((millis + MILLIS_IN_DAY - 1) / MILLIS_IN_DAY).toInt()
}

fun utcStartOfDay(millis: Long): Long {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
}

fun formatUtcDate(millis: Long): LocalDate {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
}
