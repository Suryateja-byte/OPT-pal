package com.sidekick.opt_pal

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sidekick.opt_pal.core.security.SecuritySessionManager
import com.sidekick.opt_pal.core.session.SessionViewModel
import com.sidekick.opt_pal.data.model.I983WorkflowType
import com.sidekick.opt_pal.di.AppModule
import com.sidekick.opt_pal.feature.auth.LoginRoute
import com.sidekick.opt_pal.feature.auth.RegisterRoute
import com.sidekick.opt_pal.feature.casestatus.UscisCaseStatusRoute
import com.sidekick.opt_pal.feature.compliance.ComplianceHealthRoute
import com.sidekick.opt_pal.feature.dashboard.DashboardRoute
import com.sidekick.opt_pal.feature.employment.AddEmploymentRoute
import com.sidekick.opt_pal.feature.feedback.FeedbackRoute
import com.sidekick.opt_pal.feature.h1b.H1bDashboardRoute
import com.sidekick.opt_pal.feature.i983.I983AssistantRoute
import com.sidekick.opt_pal.feature.legal.LegalRoute
import com.sidekick.opt_pal.feature.pathway.VisaPathwayPlannerRoute
import com.sidekick.opt_pal.feature.policy.PolicyAlertFeedRoute
import com.sidekick.opt_pal.feature.reporting.ManageReportingRoute
import com.sidekick.opt_pal.feature.reporting.ReportingRoute
import com.sidekick.opt_pal.feature.reporting.ReportingWizardRoute
import com.sidekick.opt_pal.feature.scenario.ScenarioSimulatorRoute
import com.sidekick.opt_pal.feature.setup.SetupRoute
import com.sidekick.opt_pal.feature.tax.FicaTaxRefundRoute
import com.sidekick.opt_pal.feature.travel.TravelAdvisorRoute
import com.sidekick.opt_pal.feature.vault.DocumentVaultRoute
import com.sidekick.opt_pal.feature.vault.SecureDocumentViewerRoute
import com.sidekick.opt_pal.navigation.AppScreen
import kotlinx.coroutines.launch

@Composable
fun OPTPalApp(
    sessionViewModel: SessionViewModel,
    securitySessionManager: SecuritySessionManager,
    pendingUscisCaseId: String? = null,
    onPendingUscisCaseHandled: () -> Unit = {},
    pendingPolicyAlertId: String? = null,
    onPendingPolicyAlertHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()
    val securityState by securitySessionManager.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sessionState.isLoggedIn) {
        securitySessionManager.onAuthStateChanged(sessionState.isLoggedIn)
    }

    LaunchedEffect(sessionState.isLoggedIn, sessionState.userProfile) {
        AppModule.unemploymentAlertCoordinator.syncWithSession(
            isLoggedIn = sessionState.isLoggedIn,
            userProfile = sessionState.userProfile
        )
    }

    LaunchedEffect(sessionState.isLoggedIn, sessionState.isProfileComplete, sessionState.isCheckingAuth) {
        if (!sessionState.isCheckingAuth) {
            val destination = when {
                !sessionState.isLoggedIn -> AppScreen.Login.route
                !sessionState.isProfileComplete -> AppScreen.Setup.route
                else -> AppScreen.Dashboard.route
            }
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != destination) {
                navController.navigate(destination) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(
        sessionState.isLoggedIn,
        sessionState.isProfileComplete,
        sessionState.isCheckingAuth,
        pendingUscisCaseId
    ) {
        if (!sessionState.isCheckingAuth &&
            sessionState.isLoggedIn &&
            sessionState.isProfileComplete &&
            !pendingUscisCaseId.isNullOrBlank()
        ) {
            navController.navigate(AppScreen.CaseStatus.createRoute(pendingUscisCaseId)) {
                launchSingleTop = true
            }
            onPendingUscisCaseHandled()
        }
    }

    LaunchedEffect(
        sessionState.isLoggedIn,
        sessionState.isProfileComplete,
        sessionState.isCheckingAuth,
        pendingPolicyAlertId
    ) {
        if (!sessionState.isCheckingAuth &&
            sessionState.isLoggedIn &&
            sessionState.isProfileComplete &&
            !pendingPolicyAlertId.isNullOrBlank()
        ) {
            navController.navigate(AppScreen.PolicyAlerts.createRoute(pendingPolicyAlertId)) {
                launchSingleTop = true
            }
            onPendingPolicyAlertHandled()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppScreen.Login.route,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                composable(AppScreen.Login.route) {
                    LoginRoute(
                        onNavigateToSignUp = { navController.navigate(AppScreen.SignUp.route) }
                    )
                }
                composable(AppScreen.SignUp.route) {
                    RegisterRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSetup = {}
                    )
                }
                composable(AppScreen.Setup.route) {
                    SetupRoute(
                        onOpenLegal = { navController.navigate(AppScreen.Legal.route) },
                        onScanDocument = { navController.navigate(AppScreen.SetupCameraCapture.route) }
                    )
                }
                composable(AppScreen.Dashboard.route) {
                    DashboardRoute(
                        onAddEmployment = { navController.navigate(AppScreen.AddEmployment.route) },
                        onEditEmployment = { navController.navigate(AppScreen.EditEmployment.createRoute(it)) },
                        onOpenTaxRefund = { navController.navigate(AppScreen.TaxRefund.route) },
                        onOpenComplianceScore = { navController.navigate(AppScreen.ComplianceScore.route) },
                        onOpenTravelAdvisor = { navController.navigate(AppScreen.TravelAdvisor.route) },
                        onOpenVisaPathwayPlanner = { navController.navigate(AppScreen.VisaPathwayPlanner.createRoute()) },
                        onOpenH1bDashboard = { navController.navigate(AppScreen.H1bDashboard.route) },
                        onOpenScenarioSimulator = { navController.navigate(AppScreen.ScenarioSimulator.createRoute()) },
                        onOpenI983Assistant = { navController.navigate(AppScreen.I983Assistant.createRoute()) },
                        onOpenPolicyAlerts = { navController.navigate(AppScreen.PolicyAlerts.createRoute()) },
                        onOpenCaseStatus = { navController.navigate(AppScreen.CaseStatus.createRoute()) },
                        onOpenReporting = { navController.navigate(AppScreen.Reporting.route) },
                        onOpenVault = { navController.navigate(AppScreen.DocumentVault.route) },
                        onSendFeedback = { navController.navigate(AppScreen.Feedback.route) },
                        onOpenLegal = { navController.navigate(AppScreen.Legal.route) },
                        onScanDocument = { navController.navigate(AppScreen.DocumentSelection.route) },
                        onOpenChat = { navController.navigate(AppScreen.Chat.route) }
                    )
                }
                composable(
                    AppScreen.H1bDashboard.route
                ) {
                    H1bDashboardRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenCaseStatus = { navController.navigate(AppScreen.CaseStatus.createRoute()) },
                        onOpenVisaPathwayPlanner = { navController.navigate(AppScreen.VisaPathwayPlanner.createRoute()) },
                        onOpenScenarioSimulator = {
                            navController.navigate(
                                AppScreen.ScenarioSimulator.createRoute(
                                    templateId = "h1b_cap_continuity"
                                )
                            )
                        }
                    )
                }
                composable(
                    AppScreen.ScenarioSimulator.route,
                    arguments = listOf(
                        navArgument(AppScreen.ScenarioSimulator.TEMPLATE_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(AppScreen.ScenarioSimulator.DRAFT_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    ScenarioSimulatorRoute(
                        initialTemplateId = backStackEntry.arguments?.getString(AppScreen.ScenarioSimulator.TEMPLATE_ID_ARG),
                        initialDraftId = backStackEntry.arguments?.getString(AppScreen.ScenarioSimulator.DRAFT_ID_ARG),
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToRoute = { route -> navController.navigate(route) }
                    )
                }
                composable(
                    AppScreen.CaseStatus.route,
                    arguments = listOf(
                        navArgument(AppScreen.CaseStatus.CASE_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    UscisCaseStatusRoute(
                        selectedCaseId = backStackEntry.arguments?.getString(AppScreen.CaseStatus.CASE_ID_ARG),
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppScreen.TaxRefund.route) {
                    FicaTaxRefundRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenDocument = { documentId ->
                            navController.navigate(AppScreen.DocumentViewer.createRoute(documentId))
                        }
                    )
                }
                composable(AppScreen.ComplianceScore.route) {
                    ComplianceHealthRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToRoute = { route -> navController.navigate(route) }
                    )
                }
                composable(AppScreen.TravelAdvisor.route) {
                    TravelAdvisorRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onUploadMissingDocument = { navController.navigate(AppScreen.DocumentSelection.route) },
                        onOpenScenarioSimulator = {
                            navController.navigate(
                                AppScreen.ScenarioSimulator.createRoute(
                                    templateId = "international_travel"
                                )
                            )
                        }
                    )
                }
                composable(
                    AppScreen.VisaPathwayPlanner.route,
                    arguments = listOf(
                        navArgument(AppScreen.VisaPathwayPlanner.PATHWAY_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    VisaPathwayPlannerRoute(
                        initialPathwayId = backStackEntry.arguments?.getString(AppScreen.VisaPathwayPlanner.PATHWAY_ID_ARG),
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToRoute = { route -> navController.navigate(route) },
                        onOpenScenarioSimulator = {
                            navController.navigate(
                                AppScreen.ScenarioSimulator.createRoute(
                                    templateId = "add_or_switch_employer"
                                )
                            )
                        }
                    )
                }
                composable(
                    AppScreen.I983Assistant.route,
                    arguments = listOf(
                        navArgument(AppScreen.I983Assistant.DRAFT_ID_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(AppScreen.I983Assistant.OBLIGATION_ID_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(AppScreen.I983Assistant.EMPLOYMENT_ID_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(AppScreen.I983Assistant.WORKFLOW_TYPE_ARG) { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    I983AssistantRoute(
                        draftId = backStackEntry.arguments?.getString(AppScreen.I983Assistant.DRAFT_ID_ARG),
                        obligationId = backStackEntry.arguments?.getString(AppScreen.I983Assistant.OBLIGATION_ID_ARG),
                        employmentId = backStackEntry.arguments?.getString(AppScreen.I983Assistant.EMPLOYMENT_ID_ARG),
                        workflowType = backStackEntry.arguments?.getString(AppScreen.I983Assistant.WORKFLOW_TYPE_ARG)?.let(I983WorkflowType::fromWireValue),
                        onNavigateBack = { navController.popBackStack() },
                        onOpenReporting = { navController.navigate(AppScreen.Reporting.route) },
                        onOpenVisaPathwayPlanner = { navController.navigate(AppScreen.VisaPathwayPlanner.createRoute()) },
                        onOpenDocument = { documentId -> navController.navigate(AppScreen.DocumentViewer.createRoute(documentId)) }
                    )
                }
                composable(
                    AppScreen.PolicyAlerts.route,
                    arguments = listOf(
                        navArgument(AppScreen.PolicyAlerts.ALERT_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(AppScreen.PolicyAlerts.FILTER_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    PolicyAlertFeedRoute(
                        selectedAlertId = backStackEntry.arguments?.getString(AppScreen.PolicyAlerts.ALERT_ID_ARG),
                        initialFilter = backStackEntry.arguments?.getString(AppScreen.PolicyAlerts.FILTER_ARG),
                        onNavigateBack = { navController.popBackStack() },
                        onOpenTravelAdvisor = { navController.navigate(AppScreen.TravelAdvisor.route) },
                        onOpenVisaPathwayPlanner = { pathwayId ->
                            navController.navigate(AppScreen.VisaPathwayPlanner.createRoute(pathwayId))
                        }
                    )
                }
                composable(AppScreen.AddEmployment.route) {
                    AddEmploymentRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenReportingWizard = { wizardId ->
                            navController.navigate(AppScreen.ReportingWizard.createRoute(wizardId = wizardId))
                        }
                    )
                }
                composable(
                    AppScreen.EditEmployment.route,
                    arguments = listOf(
                        navArgument(AppScreen.EditEmployment.EMPLOYMENT_ID_ARG) { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val employmentId = backStackEntry.arguments?.getString(AppScreen.EditEmployment.EMPLOYMENT_ID_ARG)
                    if (employmentId == null) {
                        navController.popBackStack()
                    } else {
                        AddEmploymentRoute(
                            employmentId = employmentId,
                            onNavigateBack = { navController.popBackStack() },
                            onOpenReportingWizard = { wizardId ->
                                navController.navigate(AppScreen.ReportingWizard.createRoute(wizardId = wizardId))
                            }
                        )
                    }
                }
                composable(AppScreen.Reporting.route) {
                    ReportingRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onAddManualTask = { navController.navigate(AppScreen.ManageReporting.createRoute()) },
                        onEditManualTask = { obligationId ->
                            navController.navigate(AppScreen.ManageReporting.createRoute(obligationId))
                        },
                        onStartWizard = {
                            navController.navigate(AppScreen.ReportingWizard.createRoute())
                        },
                        onContinueWizard = { wizardId, obligationId ->
                            navController.navigate(
                                AppScreen.ReportingWizard.createRoute(
                                    wizardId = wizardId,
                                    obligationId = obligationId
                                )
                            )
                        },
                        onOpenI983Assistant = { obligationId, employmentId, workflowType ->
                            navController.navigate(
                                AppScreen.I983Assistant.createRoute(
                                    obligationId = obligationId,
                                    employmentId = employmentId,
                                    workflowType = workflowType.wireValue
                                )
                            )
                        },
                        onOpenVisaPathwayPlanner = { navController.navigate(AppScreen.VisaPathwayPlanner.createRoute()) }
                    )
                }
                composable(
                    AppScreen.ReportingWizard.route,
                    arguments = listOf(
                        navArgument(AppScreen.ReportingWizard.WIZARD_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(AppScreen.ReportingWizard.OBLIGATION_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    ReportingWizardRoute(
                        wizardId = backStackEntry.arguments?.getString(AppScreen.ReportingWizard.WIZARD_ID_ARG),
                        obligationId = backStackEntry.arguments?.getString(AppScreen.ReportingWizard.OBLIGATION_ID_ARG),
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppScreen.DocumentVault.route) {
                    DocumentVaultRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenDocument = { documentId ->
                            navController.navigate(AppScreen.DocumentViewer.createRoute(documentId))
                        }
                    )
                }
                composable(AppScreen.Feedback.route) {
                    FeedbackRoute(onNavigateBack = { navController.popBackStack() })
                }
                composable(AppScreen.Legal.route) {
                    LegalRoute(onNavigateBack = { navController.popBackStack() })
                }
                composable(
                    AppScreen.ManageReporting.route,
                    arguments = listOf(
                        navArgument(AppScreen.ManageReporting.OBLIGATION_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val obligationId = backStackEntry.arguments?.getString(AppScreen.ManageReporting.OBLIGATION_ID_ARG)
                    ManageReportingRoute(
                        obligationId = obligationId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppScreen.DocumentSelection.route) {
                    com.sidekick.opt_pal.feature.scan.DocumentSelectionScreen(
                        onNavigateToCamera = {
                            navController.navigate(AppScreen.CameraCapture.route)
                        }
                    )
                }
                composable(AppScreen.CameraCapture.route) {
                    com.sidekick.opt_pal.feature.scan.CameraCaptureScreen(
                        onNavigateToReview = {
                            navController.navigate(AppScreen.DocumentVault.route) {
                                popUpTo(AppScreen.Dashboard.route)
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppScreen.SetupCameraCapture.route) {
                    com.sidekick.opt_pal.feature.scan.CameraCaptureScreen(
                        onNavigateToReview = { navController.popBackStack() },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppScreen.Chat.route) {
                    com.sidekick.opt_pal.feature.chat.ChatScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenDocument = { documentId ->
                            navController.navigate(AppScreen.DocumentViewer.createRoute(documentId))
                        }
                    )
                }
                composable(
                    AppScreen.DocumentViewer.route,
                    arguments = listOf(
                        navArgument(AppScreen.DocumentViewer.DOCUMENT_ID_ARG) { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val documentId = backStackEntry.arguments?.getString(AppScreen.DocumentViewer.DOCUMENT_ID_ARG)
                    if (documentId == null) {
                        navController.popBackStack()
                    } else {
                        SecureDocumentViewerRoute(
                            documentId = documentId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            if (sessionState.isCheckingAuth) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            if (sessionState.isLoggedIn && !sessionState.isCheckingAuth &&
                (securityState.requiresSecuritySetup || securityState.isLocked)
            ) {
                SecurityLockOverlay(
                    securitySessionManager = securitySessionManager,
                    showSetupState = securityState.requiresSecuritySetup,
                    errorMessage = securityState.unlockError,
                    onSignOut = {
                        coroutineScope.launch {
                            AppModule.caseStatusRepository.syncMessagingEndpoint(enabled = false)
                            AppModule.authRepository.signOut()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SecurityLockOverlay(
    securitySessionManager: SecuritySessionManager,
    showSetupState: Boolean,
    errorMessage: String?,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (showSetupState) "Device Security Required" else "Unlock OPTPal",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (showSetupState) {
                    "Turn on a device PIN, passcode, or biometric in system settings before accessing your OPT records."
                } else {
                    "Your session is locked after five minutes of inactivity. Verify your identity to continue."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (showSetupState) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        securitySessionManager.refreshCredentialState()
                    }
                ) {
                    Text("Open Security Settings")
                }
            } else {
                Button(
                    onClick = {
                        securitySessionManager.clearUnlockError()
                        if (activity != null) {
                            AppModule.securityManager.authenticateWithBiometric(
                                activity = activity,
                                title = "Unlock OPTPal",
                                subtitle = "Verify your identity to access your records",
                                onSuccess = securitySessionManager::onUnlockSucceeded,
                                onError = securitySessionManager::onUnlockError,
                                onFailed = { securitySessionManager.onUnlockError("Authentication failed.") }
                            )
                        } else {
                            securitySessionManager.onUnlockError("Unable to present device authentication.")
                        }
                    }
                ) {
                    Text("Unlock")
                }
            }
            TextButton(onClick = onSignOut) {
                Text("Sign out")
            }
        }
    }
}
