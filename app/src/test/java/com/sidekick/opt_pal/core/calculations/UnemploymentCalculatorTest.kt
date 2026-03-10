package com.sidekick.opt_pal.core.calculations

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UnemploymentCalculatorTest {

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun noJobsCountsAllDays() {
        val start = date(2024, 1, 1)
        val today = date(2024, 1, 11)
        val result = calculateUnemploymentDays(start, emptyList(), today)
        assertEquals(10, result)
    }

    @Test
    fun jobStartingOnOptDateProducesZeroGaps() {
        val start = date(2024, 1, 1)
        val today = date(2024, 2, 1)
        val employment = listOf(EmploymentPeriod(start, date(2024, 2, 1)))
        val result = calculateUnemploymentDays(start, employment, today)
        assertEquals(0, result)
    }

    @Test
    fun gapBeforeFirstJobIsCounted() {
        val start = date(2024, 1, 1)
        val firstJobStart = date(2024, 1, 6)
        val today = date(2024, 2, 1)
        val employment = listOf(EmploymentPeriod(firstJobStart, date(2024, 2, 1)))
        val result = calculateUnemploymentDays(start, employment, today)
        assertEquals(5, result)
    }

    @Test
    fun gapsBetweenJobsAreCountedOnce() {
        val start = date(2024, 1, 1)
        val jobOne = EmploymentPeriod(date(2024, 1, 5), date(2024, 1, 20))
        val jobTwo = EmploymentPeriod(date(2024, 1, 25), date(2024, 2, 5))
        val today = date(2024, 2, 20)
        val result = calculateUnemploymentDays(start, listOf(jobOne, jobTwo), today)
        val expected = 4 /* before job one */ + 5 /* gap between jobs */ + 15 /* after job two */
        assertEquals(expected, result)
    }

    @Test
    fun ongoingJobStopsClock() {
        val start = date(2024, 1, 1)
        val job = EmploymentPeriod(date(2024, 1, 5), null)
        val today = date(2024, 2, 1)
        val result = calculateUnemploymentDays(start, listOf(job), today)
        assertEquals(4, result)
    }

    @Test
    fun overlappingJobsDoNotDoubleCount() {
        val start = date(2024, 1, 1)
        val jobOne = EmploymentPeriod(date(2024, 1, 10), date(2024, 2, 10))
        val jobTwo = EmploymentPeriod(date(2024, 1, 20), date(2024, 3, 1))
        val today = date(2024, 3, 15)
        val result = calculateUnemploymentDays(start, listOf(jobOne, jobTwo), today)
        val expected = 9 /* before job one */ + 14 /* after second job until today */
        assertEquals(expected, result)
    }

    @Test
    fun whenTodayEqualsEndDateNoExtraDaysAdded() {
        val start = date(2024, 1, 1)
        val today = date(2024, 1, 31)
        val job = EmploymentPeriod(date(2024, 1, 10), date(2024, 1, 31))
        val result = calculateUnemploymentDays(start, listOf(job), today)
        assertEquals(9, result)
    }

    @Test
    fun editingJobToCloseGapReducesUnemployment() {
        val start = date(2024, 1, 1)
        val jobOne = EmploymentPeriod(date(2024, 1, 5), date(2024, 1, 20))
        val jobTwo = EmploymentPeriod(date(2024, 1, 20), date(2024, 2, 5))
        val today = date(2024, 2, 20)
        val result = calculateUnemploymentDays(start, listOf(jobOne, jobTwo), today)
        val expected = 4 /* before first job */ + 15 /* after job two */
        assertEquals(expected, result)
    }

    @Test
    fun editingJobToSetEndDateAddsUnemploymentAfterwards() {
        val start = date(2024, 1, 1)
        val job = EmploymentPeriod(date(2024, 1, 5), date(2024, 2, 1))
        val today = date(2024, 2, 20)
        val result = calculateUnemploymentDays(start, listOf(job), today)
        val expected = 4 /* before job */ + 19 /* after new end date */
        assertEquals(expected, result)
    }
}
