package com.sidekick.opt_pal.data.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val travelAdvisorEnabled: Boolean? = null,
    val i983AssistantEnabled: Boolean? = null,
    val visaPathwayPlannerEnabled: Boolean? = null,
    val scenarioSimulatorEnabled: Boolean? = null,
    val optType: String? = null,
    val optStartDate: Long? = null,
    val unemploymentTrackingStartDate: Long? = null,
    val optEndDate: Long? = null,
    val sevisId: String? = null,
    val schoolName: String? = null,
    val cipCode: String? = null,
    val majorName: String? = null,
    val firstUsStudentTaxYear: Int? = null,
    val onboardingSource: String? = null,
    val onboardingDocumentIds: List<String> = emptyList(),
    val onboardingConfirmedAt: Long? = null
)
