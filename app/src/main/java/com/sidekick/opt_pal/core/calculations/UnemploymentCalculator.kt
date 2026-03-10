package com.sidekick.opt_pal.core.calculations

import com.sidekick.opt_pal.data.model.Employment

private const val MILLIS_IN_DAY = 86_400_000L

data class EmploymentPeriod(
    val start: Long,
    val end: Long?
)

fun List<Employment>.toEmploymentPeriods(): List<EmploymentPeriod> {
    return map { EmploymentPeriod(it.startDate, it.endDate) }
}

fun calculateUnemploymentDays(
    optStartDate: Long,
    employmentPeriods: List<EmploymentPeriod>,
    today: Long
): Int {
    if (today <= optStartDate) return 0
    val sortedPeriods = employmentPeriods.sortedBy { it.start }
    var unemploymentDays = 0
    var cursor = optStartDate
    sortedPeriods.forEach { period ->
        val jobStart = period.start
        if (jobStart > cursor) {
            unemploymentDays += millisToDays(jobStart - cursor)
        }
        val jobEnd = (period.end ?: today).coerceAtLeast(jobStart)
        if (jobEnd > cursor) {
            cursor = jobEnd
        }
        if (cursor >= today) {
            return unemploymentDays
        }
    }
    if (cursor < today) {
        unemploymentDays += millisToDays(today - cursor)
    }
    return unemploymentDays
}

private fun millisToDays(millis: Long): Int {
    if (millis <= 0L) return 0
    return ((millis + MILLIS_IN_DAY - 1) / MILLIS_IN_DAY).toInt()
}
