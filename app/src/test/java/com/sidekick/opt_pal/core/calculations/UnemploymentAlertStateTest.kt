package com.sidekick.opt_pal.core.calculations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnemploymentAlertStateTest {

    @Test
    fun highestNewThresholdReturnsLatestUnfiredMilestone() {
        val forecast = UnemploymentForecast(
            usedDays = 80,
            remainingDays = 10,
            allowedDays = 90,
            clockRunningNow = true,
            currentGapStartDate = 0L,
            projectedExceedDate = 0L,
            currentThreshold = UnemploymentAlertThreshold.DAY_80,
            dataQualityState = UnemploymentDataQualityState.READY,
            isEstimate = false
        )

        val next = highestNewThresholdToNotify(
            forecast = forecast,
            firedThresholds = setOf(UnemploymentAlertThreshold.DAY_60)
        )

        assertEquals(UnemploymentAlertThreshold.DAY_80, next)
    }

    @Test
    fun clearThresholdsAboveCurrentAllowsRefireAfterEdits() {
        val remaining = clearThresholdsAboveCurrent(
            firedThresholds = setOf(
                UnemploymentAlertThreshold.DAY_60,
                UnemploymentAlertThreshold.DAY_75,
                UnemploymentAlertThreshold.DAY_80
            ),
            currentThreshold = UnemploymentAlertThreshold.DAY_60
        )

        assertEquals(setOf(UnemploymentAlertThreshold.DAY_60), remaining)
    }

    @Test
    fun noAlertWhenForecastIsNotReady() {
        val forecast = UnemploymentForecast(
            usedDays = 80,
            remainingDays = 10,
            allowedDays = 90,
            clockRunningNow = false,
            currentGapStartDate = null,
            projectedExceedDate = null,
            currentThreshold = UnemploymentAlertThreshold.DAY_80,
            dataQualityState = UnemploymentDataQualityState.NEEDS_HOURS_REVIEW,
            isEstimate = true
        )

        assertNull(highestNewThresholdToNotify(forecast, emptySet()))
    }
}
