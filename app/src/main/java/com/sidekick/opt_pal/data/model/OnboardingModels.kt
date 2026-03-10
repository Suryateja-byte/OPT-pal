package com.sidekick.opt_pal.data.model

enum class OptType(val value: String) {
    INITIAL("initial"),
    STEM("stem")
}

enum class OnboardingSource(val wireValue: String) {
    MANUAL("manual"),
    DOCUMENT_AI("document_ai");

    companion object {
        fun fromWireValue(value: String?): OnboardingSource {
            return entries.firstOrNull { it.wireValue == value } ?: MANUAL
        }
    }
}

enum class OnboardingDocumentType {
    I20,
    EAD
}

enum class OnboardingField {
    OPT_TYPE,
    OPT_START_DATE,
    OPT_END_DATE,
    SEVIS_ID,
    SCHOOL_NAME,
    CIP_CODE
}

data class OnboardingDocumentCandidate(
    val documentId: String,
    val fileName: String,
    val displayName: String,
    val documentType: OnboardingDocumentType,
    val processedAt: Long?,
    val fields: NormalizedOnboardingFields
)

data class NormalizedOnboardingFields(
    val sevisId: String? = null,
    val schoolName: String? = null,
    val cipCode: String? = null,
    val optType: OptType? = null,
    val optStartDate: Long? = null,
    val optEndDate: Long? = null,
    val eadCategory: String? = null,
    val uscisNumber: String? = null
)

data class OnboardingProfileDraft(
    val optType: OptType? = null,
    val optStartDate: Long? = null,
    val optEndDate: Long? = null,
    val sevisId: String = "",
    val schoolName: String = "",
    val cipCode: String = "",
    val onboardingSource: OnboardingSource = OnboardingSource.MANUAL,
    val sourceDocumentIds: List<String> = emptyList(),
    val fieldSources: Map<OnboardingField, String> = emptyMap()
)

data class CompleteSetupRequest(
    val optType: String,
    val optStartDate: Long,
    val optEndDate: Long? = null,
    val sevisId: String? = null,
    val schoolName: String? = null,
    val cipCode: String? = null,
    val onboardingSource: String = OnboardingSource.MANUAL.wireValue,
    val onboardingDocumentIds: List<String> = emptyList(),
    val onboardingConfirmedAt: Long = System.currentTimeMillis()
)
