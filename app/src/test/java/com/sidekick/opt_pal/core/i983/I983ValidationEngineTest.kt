package com.sidekick.opt_pal.core.i983

import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983EmployerSection
import com.sidekick.opt_pal.data.model.I983NarrativeDraft
import com.sidekick.opt_pal.data.model.I983PolicyBundle
import com.sidekick.opt_pal.data.model.I983Readiness
import com.sidekick.opt_pal.data.model.I983StudentSection
import com.sidekick.opt_pal.data.model.I983TrainingPlanSection
import com.sidekick.opt_pal.data.model.I983WorkflowType
import com.sidekick.opt_pal.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class I983ValidationEngineTest {

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun policyBundle(): I983PolicyBundle = I983PolicyBundle(
        version = "test",
        generatedAt = 0L,
        lastReviewedAt = date(2026, 3, 10),
        templateVersion = "template",
        templateSha256 = "sha"
    )

    private fun completeDraft(workflowType: I983WorkflowType = I983WorkflowType.INITIAL_STEM_EXTENSION): I983Draft {
        return I983Draft(
            workflowType = workflowType.wireValue,
            studentSection = I983StudentSection(
                studentName = "Student Name",
                studentEmailAddress = "student@example.com",
                schoolRecommendingStemOpt = "School",
                schoolWhereDegreeWasEarned = "School",
                sevisSchoolCode = "ABC214F00123000",
                dsoNameAndContact = "DSO Name, dso@example.edu",
                studentSevisId = "N0012345678",
                requestedStartDate = date(2026, 6, 1),
                requestedEndDate = date(2028, 5, 31),
                qualifyingMajorAndCipCode = "Computer Science (11.0701)",
                degreeLevel = "Bachelor's",
                degreeAwardedDate = date(2025, 5, 15),
                employmentAuthorizationNumber = "A123456789"
            ),
            employerSection = I983EmployerSection(
                employerName = "Acme Corp",
                streetAddress = "123 Main St",
                employerWebsiteUrl = "https://example.com",
                city = "Austin",
                state = "TX",
                zipCode = "78701",
                employerEin = "12-3456789",
                fullTimeEmployeesInUs = "100",
                naicsCode = "541511",
                hoursPerWeek = 40,
                salaryAmountAndFrequency = "$100,000 annually",
                employmentStartDate = date(2026, 6, 1),
                employerOfficialNameAndTitle = "Manager, Director",
                employingOrganizationName = "Acme Corp"
            ),
            trainingPlanSection = I983TrainingPlanSection(
                siteName = "HQ",
                siteAddress = "123 Main St, Austin, TX 78701",
                officialName = "Manager",
                officialTitle = "Director",
                officialEmail = "manager@example.com",
                officialPhoneNumber = "555-1212",
                studentRole = "Software engineer",
                goalsAndObjectives = "Build distributed systems.",
                employerOversight = "Weekly check-ins and code review.",
                measuresAndAssessments = "Sprint reviews and quality metrics."
            )
        )
    }

    @Test
    fun cleanStemDraftIsReadyToExport() {
        val assessment = I983ValidationEngine().assess(
            draft = completeDraft(),
            policyBundle = policyBundle(),
            profile = UserProfile(uid = "user-1", optType = "stem"),
            linkedEmployment = null,
            linkedObligation = null,
            now = date(2026, 3, 10)
        )

        assertEquals(I983Readiness.READY_TO_EXPORT, assessment.readiness)
        assertTrue(assessment.issues.none { it.message.contains("required", ignoreCase = true) })
    }

    @Test
    fun hoursBelowTwentyBlocksDraft() {
        val assessment = I983ValidationEngine().assess(
            draft = completeDraft().copy(
                employerSection = completeDraft().employerSection.copy(hoursPerWeek = 15)
            ),
            policyBundle = policyBundle(),
            profile = UserProfile(uid = "user-1", optType = "stem"),
            linkedEmployment = null,
            linkedObligation = null,
            now = date(2026, 3, 10)
        )

        assertEquals(I983Readiness.NEEDS_INPUT, assessment.readiness)
        assertTrue(assessment.issues.any { it.id == "hours_per_week_under_minimum" })
    }

    @Test
    fun materialChangeRequiresPriorRevision() {
        val assessment = I983ValidationEngine().assess(
            draft = completeDraft(I983WorkflowType.MATERIAL_CHANGE),
            policyBundle = policyBundle(),
            profile = UserProfile(uid = "user-1", optType = "stem"),
            linkedEmployment = null,
            linkedObligation = null,
            now = date(2026, 3, 10)
        )

        assertTrue(assessment.issues.any { it.id == "material_change_requires_revision" })
    }

    @Test
    fun consultFlagEscalatesToDsoAttorney() {
        val assessment = I983ValidationEngine().assess(
            draft = completeDraft().copy(
                generatedNarrative = I983NarrativeDraft(classification = "consult_dso_attorney")
            ),
            policyBundle = policyBundle(),
            profile = UserProfile(uid = "user-1", optType = "stem"),
            linkedEmployment = null,
            linkedObligation = null,
            now = date(2026, 3, 10)
        )

        assertEquals(I983Readiness.CONTACT_DSO_OR_ATTORNEY, assessment.readiness)
        assertTrue(assessment.issues.any { it.id == "consult_dso_attorney" })
    }
}
