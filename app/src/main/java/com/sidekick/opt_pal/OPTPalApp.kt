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
import com.sidekick.opt_pal.di.AppModule
import com.sidekick.opt_pal.feature.auth.LoginRoute
import com.sidekick.opt_pal.feature.auth.RegisterRoute
import com.sidekick.opt_pal.feature.dashboard.DashboardRoute
import com.sidekick.opt_pal.feature.employment.AddEmploymentRoute
import com.sidekick.opt_pal.feature.feedback.FeedbackRoute
import com.sidekick.opt_pal.feature.legal.LegalRoute
import com.sidekick.opt_pal.feature.reporting.ManageReportingRoute
import com.sidekick.opt_pal.feature.reporting.ReportingRoute
import com.sidekick.opt_pal.feature.setup.SetupRoute
import com.sidekick.opt_pal.feature.vault.DocumentVaultRoute
import com.sidekick.opt_pal.feature.vault.SecureDocumentViewerRoute
import com.sidekick.opt_pal.navigation.AppScreen
import kotlinx.coroutines.launch

@Composable
fun OPTPalApp(
    sessionViewModel: SessionViewModel,
    securitySessionManager: SecuritySessionManager,
    navController: NavHostController = rememberNavController()
) {
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()
    val securityState by securitySessionManager.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sessionState.isLoggedIn) {
        securitySessionManager.onAuthStateChanged(sessionState.isLoggedIn)
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
                        onOpenReporting = { navController.navigate(AppScreen.Reporting.route) },
                        onOpenVault = { navController.navigate(AppScreen.DocumentVault.route) },
                        onSendFeedback = { navController.navigate(AppScreen.Feedback.route) },
                        onOpenLegal = { navController.navigate(AppScreen.Legal.route) },
                        onScanDocument = { navController.navigate(AppScreen.DocumentSelection.route) },
                        onOpenChat = { navController.navigate(AppScreen.Chat.route) }
                    )
                }
                composable(AppScreen.AddEmployment.route) {
                    AddEmploymentRoute(onNavigateBack = { navController.popBackStack() })
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
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(AppScreen.Reporting.route) {
                    ReportingRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onAddManualTask = { navController.navigate(AppScreen.ManageReporting.createRoute()) },
                        onEditManualTask = { obligationId ->
                            navController.navigate(AppScreen.ManageReporting.createRoute(obligationId))
                        }
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
