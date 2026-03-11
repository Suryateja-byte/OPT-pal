package com.sidekick.opt_pal.navigation

sealed class AppScreen(val route: String) {
    data object Login : AppScreen("login")
    data object SignUp : AppScreen("signup")
    data object Setup : AppScreen("setup")
    data object Dashboard : AppScreen("dashboard")
    data object H1bDashboard : AppScreen("h1bDashboard")
    data object ScenarioSimulator : AppScreen("scenarioSimulator?templateId={templateId}&draftId={draftId}") {
        const val TEMPLATE_ID_ARG = "templateId"
        const val DRAFT_ID_ARG = "draftId"

        fun createRoute(templateId: String? = null, draftId: String? = null): String {
            val params = buildList {
                if (!templateId.isNullOrBlank()) add("templateId=$templateId")
                if (!draftId.isNullOrBlank()) add("draftId=$draftId")
            }
            return if (params.isEmpty()) {
                "scenarioSimulator"
            } else {
                "scenarioSimulator?${params.joinToString("&")}"
            }
        }
    }
    data object TaxRefund : AppScreen("taxRefund")
    data object ComplianceScore : AppScreen("complianceScore")
    data object TravelAdvisor : AppScreen("travelAdvisor")
    data object VisaPathwayPlanner : AppScreen("visaPathwayPlanner?pathwayId={pathwayId}") {
        const val PATHWAY_ID_ARG = "pathwayId"

        fun createRoute(pathwayId: String? = null): String {
            return if (pathwayId.isNullOrBlank()) {
                "visaPathwayPlanner"
            } else {
                "visaPathwayPlanner?pathwayId=$pathwayId"
            }
        }
    }
    data object I983Assistant : AppScreen("i983Assistant?draftId={draftId}&obligationId={obligationId}&employmentId={employmentId}&workflowType={workflowType}") {
        const val DRAFT_ID_ARG = "draftId"
        const val OBLIGATION_ID_ARG = "obligationId"
        const val EMPLOYMENT_ID_ARG = "employmentId"
        const val WORKFLOW_TYPE_ARG = "workflowType"

        fun createRoute(
            draftId: String? = null,
            obligationId: String? = null,
            employmentId: String? = null,
            workflowType: String? = null
        ): String {
            val params = buildList {
                if (!draftId.isNullOrBlank()) add("draftId=$draftId")
                if (!obligationId.isNullOrBlank()) add("obligationId=$obligationId")
                if (!employmentId.isNullOrBlank()) add("employmentId=$employmentId")
                if (!workflowType.isNullOrBlank()) add("workflowType=$workflowType")
            }
            return if (params.isEmpty()) "i983Assistant" else "i983Assistant?${params.joinToString("&")}"
        }
    }
    data object PolicyAlerts : AppScreen("policyAlerts?alertId={alertId}&filter={filter}") {
        const val ALERT_ID_ARG = "alertId"
        const val FILTER_ARG = "filter"

        fun createRoute(alertId: String? = null, filter: String? = null): String {
            val params = buildList {
                if (!alertId.isNullOrBlank()) add("alertId=$alertId")
                if (!filter.isNullOrBlank()) add("filter=$filter")
            }
            return if (params.isEmpty()) "policyAlerts" else "policyAlerts?${params.joinToString("&")}"
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
