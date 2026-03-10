package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class FicaRefundCaseStatus(val wireValue: String) {
    INTAKE("intake"),
    ELIGIBILITY_READY("eligibility_ready"),
    MANUAL_REVIEW_REQUIRED("manual_review_required"),
    EMPLOYER_OUTREACH("employer_outreach"),
    IRS_PACKET_READY("irs_packet_ready"),
    CLOSED_REFUNDED("closed_refunded"),
    CLOSED_OUT_OF_SCOPE("closed_out_of_scope");

    companion object {
        fun fromWireValue(value: String?): FicaRefundCaseStatus {
            return entries.firstOrNull { it.wireValue == value } ?: INTAKE
        }
    }
}

enum class FicaEligibilityClassification(val wireValue: String) {
    ELIGIBLE("eligible"),
    NOT_APPLICABLE("not_applicable"),
    MANUAL_REVIEW_REQUIRED("manual_review_required"),
    OUT_OF_SCOPE("out_of_scope");

    companion object {
        fun fromWireValue(value: String?): FicaEligibilityClassification {
            return entries.firstOrNull { it.wireValue == value } ?: MANUAL_REVIEW_REQUIRED
        }
    }
}

enum class EmployerRefundOutcome(val wireValue: String) {
    UNKNOWN(""),
    REFUNDED("refunded"),
    PROMISED_CORRECTION("promised_correction"),
    REFUSED("refused"),
    NO_RESPONSE("no_response");

    companion object {
        fun fromWireValue(value: String?): EmployerRefundOutcome {
            return entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
        }
    }
}

data class FicaRefundPacket(
    val documentId: String = "",
    val fileName: String = "",
    val generatedAt: Long = 0L,
    val kind: String = ""
)

data class FicaEligibilityResult(
    val classification: String = FicaEligibilityClassification.MANUAL_REVIEW_REQUIRED.wireValue,
    val refundAmount: Double? = null,
    val eligibilityReasons: List<String> = emptyList(),
    val blockingIssues: List<String> = emptyList(),
    val requiredAttachments: List<String> = emptyList(),
    val recommendedNextStep: String = "",
    val statuteWarning: String = ""
) {
    val parsedClassification: FicaEligibilityClassification
        get() = FicaEligibilityClassification.fromWireValue(classification)
}

data class FicaUserTaxInputs(
    val firstUsStudentTaxYear: Int? = null,
    val authorizedEmploymentConfirmed: Boolean = false,
    val maintainedStudentStatusForEntireTaxYear: Boolean = false,
    val noResidencyStatusChangeConfirmed: Boolean = false,
    val currentMailingAddress: String = ""
)

data class W2StateWageRow(
    val stateCode: String = "",
    val wages: Double? = null,
    val withholding: Double? = null
)

data class W2ExtractionDraft(
    val documentId: String,
    val fileName: String,
    val displayName: String,
    val taxYear: Int? = null,
    val employerName: String = "",
    val employerEinMasked: String = "",
    val employeeName: String = "",
    val employeeSsnLast4: String = "",
    val wagesBox1: Double? = null,
    val federalWithholdingBox2: Double? = null,
    val socialSecurityWagesBox3: Double? = null,
    val socialSecurityTaxBox4: Double? = null,
    val medicareWagesBox5: Double? = null,
    val medicareTaxBox6: Double? = null,
    val stateWageRows: List<W2StateWageRow> = emptyList()
)

data class FicaRefundCase(
    @DocumentId val id: String = "",
    val w2DocumentId: String = "",
    val taxYear: Int = 0,
    val employerName: String = "",
    val userInputs: FicaUserTaxInputs = FicaUserTaxInputs(),
    val employerOutcome: String = EmployerRefundOutcome.UNKNOWN.wireValue,
    val eligibilityResult: FicaEligibilityResult? = null,
    val employerPacket: FicaRefundPacket? = null,
    val irsPacket: FicaRefundPacket? = null,
    val status: String = FicaRefundCaseStatus.INTAKE.wireValue,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val parsedStatus: FicaRefundCaseStatus
        get() = FicaRefundCaseStatus.fromWireValue(status)

    val parsedEmployerOutcome: EmployerRefundOutcome
        get() = EmployerRefundOutcome.fromWireValue(employerOutcome)
}
