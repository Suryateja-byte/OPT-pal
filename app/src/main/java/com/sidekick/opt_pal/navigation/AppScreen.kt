package com.sidekick.opt_pal.navigation

sealed class AppScreen(val route: String) {
    data object Login : AppScreen("login")
    data object SignUp : AppScreen("signup")
    data object Setup : AppScreen("setup")
    data object Dashboard : AppScreen("dashboard")
    data object TaxRefund : AppScreen("taxRefund")
    data object ComplianceScore : AppScreen("complianceScore")
    data object TravelAdvisor : AppScreen("travelAdvisor")
    data object PolicyAlerts : AppScreen("policyAlerts?alertId={alertId}") {
        const val ALERT_ID_ARG = "alertId"

        fun createRoute(alertId: String? = null): String {
            return if (alertId.isNullOrBlank()) {
                "policyAlerts"
            } else {
                "policyAlerts?alertId=$alertId"
            }
        }
    }
    data object CaseStatus : AppScreen("caseStatus?caseId={caseId}") {
        const val CASE_ID_ARG = "caseId"

        fun createRoute(caseId: String? = null): String {
            return if (caseId.isNullOrBlank()) {
                "caseStatus"
            } else {
                "caseStatus?caseId=$caseId"
            }
        }
    }
    data object AddEmployment : AppScreen("addEmployment")
    data object EditEmployment : AppScreen("editEmployment/{employmentId}") {
        const val EMPLOYMENT_ID_ARG = "employmentId"
        fun createRoute(employmentId: String) = "editEmployment/$employmentId"
    }
    data object Reporting : AppScreen("reporting")
    data object ManageReporting : AppScreen("manageReporting?obligationId={obligationId}") {
        const val OBLIGATION_ID_ARG = "obligationId"
        fun createRoute(obligationId: String? = null): String {
            return if (obligationId.isNullOrBlank()) {
                "manageReporting"
            } else {
                "manageReporting?obligationId=$obligationId"
            }
        }
    }
    data object ReportingWizard : AppScreen("reportingWizard?wizardId={wizardId}&obligationId={obligationId}") {
        const val WIZARD_ID_ARG = "wizardId"
        const val OBLIGATION_ID_ARG = "obligationId"

        fun createRoute(wizardId: String? = null, obligationId: String? = null): String {
            val params = buildList {
                if (!wizardId.isNullOrBlank()) add("wizardId=$wizardId")
                if (!obligationId.isNullOrBlank()) add("obligationId=$obligationId")
            }
            return if (params.isEmpty()) {
                "reportingWizard"
            } else {
                "reportingWizard?${params.joinToString("&")}"
            }
        }
    }
    data object DocumentVault : AppScreen("documentVault")
    data object Feedback : AppScreen("feedback")
    data object Legal : AppScreen("legal")

    // OCR Feature
    data object DocumentSelection : AppScreen("documentSelection")
    data object CameraCapture : AppScreen("cameraCapture")
    data object SetupCameraCapture : AppScreen("setupCameraCapture")
    data object ReviewScanResults : AppScreen("reviewScanResults/{uuid}") {
        const val UUID_ARG = "uuid"
        fun createRoute(uuid: String) = "reviewScanResults/$uuid"
    }
    
    data object Chat : AppScreen("chat")
    data object DocumentViewer : AppScreen("documentViewer/{documentId}") {
        const val DOCUMENT_ID_ARG = "documentId"
        fun createRoute(documentId: String) = "documentViewer/$documentId"
    }
}
