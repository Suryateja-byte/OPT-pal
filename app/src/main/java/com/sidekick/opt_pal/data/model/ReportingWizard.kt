package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class ReportingActionType(val wireValue: String) {
    OPEN_WIZARD("open_wizard"),
    MANUAL_ONLY("manual_only");

    companion object {
        fun fromWireValue(value: String?): ReportingActionType {
            return entries.firstOrNull { it.wireValue == value } ?: MANUAL_ONLY
        }
    }
}

enum class ReportingWizardEventType(val wireValue: String) {
    NEW_EMPLOYER("new_employer"),
    EMPLOYMENT_ENDED("employment_ended"),
    MATERIAL_CHANGE("material_change");

    companion object {
        fun fromWireValue(value: String?): ReportingWizardEventType {
            return entries.firstOrNull { it.wireValue == value } ?: MATERIAL_CHANGE
        }
    }
}

enum class ReportingWizardOptRegime(val wireValue: String) {
    POST_COMPLETION("post_completion"),
    STEM("stem");

    companion object {
        fun fromOptType(value: String?): ReportingWizardOptRegime {
            return if (value.equals("stem", ignoreCase = true)) STEM else POST_COMPLETION
        }

        fun fromWireValue(value: String?): ReportingWizardOptRegime {
            return entries.firstOrNull { it.wireValue == value } ?: POST_COMPLETION
        }
    }
}

enum class ReportingWizardStatus(val wireValue: String) {
    DRAFTING("drafting"),
    READY("ready"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    companion object {
        fun fromWireValue(value: String?): ReportingWizardStatus {
            return entries.firstOrNull { it.wireValue == value } ?: DRAFTING
        }
    }
}

enum class ReportingDraftClassification(val wireValue: String) {
    DRAFT_ASSISTANCE("draft_assistance"),
    CONSULT_DSO_ATTORNEY("consult_dso_attorney");

    companion object {
        fun fromWireValue(value: String?): ReportingDraftClassification {
            return entries.firstOrNull { it.wireValue == value } ?: DRAFT_ASSISTANCE
        }
    }
}

enum class ReportingDraftConfidence(val wireValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun fromWireValue(value: String?): ReportingDraftConfidence {
            return entries.firstOrNull { it.wireValue == value } ?: LOW
        }
    }
}

data class ReportingWizardInput(
    val employerName: String = "",
    val jobTitle: String = "",
    val majorName: String = "",
    val worksiteAddress: String = "",
    val siteName: String = "",
    val supervisorName: String = "",
    val supervisorEmail: String = "",
    val supervisorPhone: String = "",
    val jobDuties: String = "",
    val toolsAndSkills: String = "",
    val userExplanationNotes: String = "",
    val hoursPerWeek: Int? = null
)

data class ReportingChecklistItem(
    val actor: String = "",
    val title: String = "",
    val details: String = "",
    val sourceLabel: String = "",
    val sourceUrl: String = ""
)

data class ReportingDraftResult(
    val classification: String = ReportingDraftClassification.DRAFT_ASSISTANCE.wireValue,
    val confidence: String = ReportingDraftConfidence.LOW.wireValue,
    val draftParagraph: String = "",
    val whyThisDraftFits: List<String> = emptyList(),
    val missingInputs: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val parsedClassification: ReportingDraftClassification
        get() = ReportingDraftClassification.fromWireValue(classification)

    val parsedConfidence: ReportingDraftConfidence
        get() = ReportingDraftConfidence.fromWireValue(confidence)
}

data class ReportingWizard(
    @DocumentId val id: String = "",
    val eventType: String = ReportingWizardEventType.MATERIAL_CHANGE.wireValue,
    val optRegime: String = ReportingWizardOptRegime.POST_COMPLETION.wireValue,
    val status: String = ReportingWizardStatus.DRAFTING.wireValue,
    val eventDate: Long = 0L,
    val dueDate: Long = 0L,
    val obligationId: String = "",
    val relatedEmploymentId: String = "",
    val userInputs: ReportingWizardInput = ReportingWizardInput(),
    val generatedChecklist: List<ReportingChecklistItem> = emptyList(),
    val generatedDraft: ReportingDraftResult? = null,
    val editedDraft: String = "",
    val policyVersion: String = "",
    val policyLastReviewedAt: Long = 0L,
    val generatedAt: Long? = null,
    val copiedAt: Long? = null,
    val completedAt: Long? = null
) {
    val parsedEventType: ReportingWizardEventType
        get() = ReportingWizardEventType.fromWireValue(eventType)

    val parsedOptRegime: ReportingWizardOptRegime
        get() = ReportingWizardOptRegime.fromWireValue(optRegime)

    val parsedStatus: ReportingWizardStatus
        get() = ReportingWizardStatus.fromWireValue(status)
}

data class ReportingWizardStartResult(
    val wizardId: String,
    val obligationId: String
)
