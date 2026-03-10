package com.sidekick.opt_pal.core.calculations

import com.sidekick.opt_pal.data.model.Employment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UnemploymentForecastTest {

    @Test
    fun initialOptForecastProjectsLimitDateDuringCurrentGap() {
        val forecast = calculateUnemploymentForecast(
            optType = "initial",
            optStartDate = date(2024, 1, 1),
            unemploymentTrackingStartDate = date(2024, 1, 1),
            optEndDate = null,
            employments = emptyList(),
            now = date(2024, 3, 1)
        )

        assertEquals(60, forecast.usedDays)
        assertTrue(forecast.clockRunningNow)
        assertEquals(date(2024, 4, 1), forecast.projectedExceedDate)
        assertEquals(UnemploymentAlertThreshold.DAY_60, forecast.currentThreshold)
    }

    @Test
    fun initialOptAggregatesConcurrentPartTimeJobs() {
        val forecast = calculateUnemploymentForecast(
            optType = "initial",
            optStartDate = date(2024, 1, 1),
            unemploymentTrackingStartDate = date(2024, 1, 1),
            optEndDate = null,
            employments = listOf(
                job("a", date(2024, 1, 10), null, 12),
                job("b", date(2024, 1, 10), null, 10)
            ),
            now = date(2024, 2, 1)
        )

        assertEquals(9, forecast.usedDays)
        assertFalse(forecast.clockRunningNow)
        assertNull(forecast.projectedExceedDate)
    }

    @Test
    fun initialOptSingleSubTwentyHourJobDoesNotStopClock() {
        val forecast = calculateUnemploymentForecast(
            optType = "initial",
            optStartDate = date(2024, 1, 1),
            unemploymentTrackingStartDate = date(2024, 1, 1),
            optEndDate = null,
            employments = listOf(job("a", date(2024, 1, 10), null, 15)),
            now = date(2024, 2, 1)
        )

        assertEquals(31, forecast.usedDays)
        assertTrue(forecast.clockRunningNow)
    }

    @Test
    fun stemRequiresSingleEmployerAtTwentyHours() {
        val nineteenHourEmployer = calculateUnemploymentForecast(
            optType = "stem",
            optStartDate = date(2024, 1, 1),
            unemploymentTrackingStartDate = date(2024, 1, 1),
            optEndDate = null,
            employments = listOf(job("a", date(2024, 1, 10), null, 19)),
            now = date(2024, 2, 1)
        )
        val twoPartTimeEmployers = calculateUnemploymentForecast(
            optType = "stem",
            optStartDate = date(2024, 1, 1),
            unemploymentTrackingStartDate = date(2024, 1, 1),
            optEndDate = null,
            employments = listOf(
                job("a", date(2024, 1, 10), null, 15),
                job("b", date(2024, 1, 10), null, 15)
            ),
            now = date(2024, 2, 1)
        )

        assertEquals(31, nineteenHourEmployer.usedDays)
        assertEquals(31, twoPartTimeEmployers.usedDays)
        assertTrue(nineteenHourEmployer.clockRunningNow)
        assertTrue(twoPartTimeEmployers.clockRunningNow)
    }

    @Test
    fun optEndDateStillAllowsProjectionBeyondAuthorizationBoundary() {
        val forecast = calculateUnemploymentForecast(
            optType = "initial",
            optStartDate = date(2024, 1, 1),
            unemploymentTrackingStartDate = date(2024, 1, 1),
            optEndDate = date(2024, 3, 20),
            employments = emptyList(),
            now = date(2024, 3, 1)
        )

        assertTrue(forecast.clockRunningNow)
        assertEquals(date(2024, 4, 1), forecast.projectedExceedDate)
    }

    @Test
    fun missingHoursRequiresReviewState() {
        val forecast = calculateUnemploymentForecast(
            optType = "initial",
            optStartDate = date(2024, 1, 1),
            unemploymentTrackingStartDate = date(2024, 1, 1),
            optEndDate = null,
            employments = listOf(
                Employment(
                    id = "a",
                    employerName = "Acme",
                    startDate = date(2024, 1, 10),
                    endDate = null,
                    jobTitle = "Engineer",
                    hoursPerWeek = null
                )
            ),
            now = date(2024, 2, 1)
        )

        assertEquals(UnemploymentDataQualityState.NEEDS_HOURS_REVIEW, forecast.dataQualityState)
        assertTrue(forecast.isEstimate)
        assertNull(forecast.projectedExceedDate)
    }

    @Test
    fun missingStemTrackingStartBlocksPredictiveMode() {
        val forecast = calculateUnemploymentForecast(
            optType = "stem",
            optStartDate = date(2025, 1, 1),
            unemploymentTrackingStartDate = null,
            optEndDate = null,
            employments = emptyList(),
            now = date(2025, 3, 1)
        )

        assertEquals(UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START, forecast.dataQualityState)
        assertTrue(forecast.isEstimate)
        assertNull(forecast.projectedExceedDate)
    }

    private fun job(id: String, startDate: Long, endDate: Long?, hoursPerWeek: Int): Employment {
        return Employment(
            id = id,
            employerName = "Employer $id",
            startDate = startDate,
            endDate = endDate,
            jobTitle = "Engineer",
            hoursPerWeek = hoursPerWeek
        )
    }

    private fun date(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
}
