package com.sidekick.opt_pal.di

import android.content.Context
import com.sidekick.opt_pal.core.casestatus.UscisCaseNotificationManager
import com.sidekick.opt_pal.core.compliance.ComplianceScoreSnapshotStore
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.core.policy.PolicyAlertNotificationManager
import com.sidekick.opt_pal.core.security.DocumentCryptoService
import com.sidekick.opt_pal.core.security.SecureDocumentContentClient
import com.sidekick.opt_pal.core.security.SecurityManager
import com.sidekick.opt_pal.core.security.SecuritySessionManager
import com.sidekick.opt_pal.core.session.FirebaseUserSessionProvider
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.core.unemployment.UnemploymentAlertCoordinator
import com.sidekick.opt_pal.core.unemployment.UnemploymentAlertScheduler
import com.sidekick.opt_pal.core.unemployment.UnemploymentAlertStore
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.AuthRepositoryImpl
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepositoryImpl
import com.sidekick.opt_pal.data.repository.ComplianceHealthRepository
import com.sidekick.opt_pal.data.repository.ComplianceHealthRepositoryImpl
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DashboardRepositoryImpl
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.DocumentRepositoryImpl
import com.sidekick.opt_pal.data.repository.FeedbackRepository
import com.sidekick.opt_pal.data.repository.FeedbackRepositoryImpl
import com.sidekick.opt_pal.data.repository.FicaRefundRepository
import com.sidekick.opt_pal.data.repository.FicaRefundRepositoryImpl
import com.sidekick.opt_pal.data.repository.H1bDashboardRepository
import com.sidekick.opt_pal.data.repository.H1bDashboardRepositoryImpl
import com.sidekick.opt_pal.data.repository.I983AssistantRepository
import com.sidekick.opt_pal.data.repository.I983AssistantRepositoryImpl
import com.sidekick.opt_pal.data.repository.NotificationDeviceRepository
import com.sidekick.opt_pal.data.repository.NotificationDeviceRepositoryImpl
import com.sidekick.opt_pal.data.repository.PolicyAlertRepository
import com.sidekick.opt_pal.data.repository.PolicyAlertRepositoryImpl
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.data.repository.ReportingRepositoryImpl
import com.sidekick.opt_pal.data.repository.ScenarioSimulatorRepository
import com.sidekick.opt_pal.data.repository.ScenarioSimulatorRepositoryImpl
import com.sidekick.opt_pal.data.repository.TravelAdvisorRepository
import com.sidekick.opt_pal.data.repository.TravelAdvisorRepositoryImpl
import com.sidekick.opt_pal.data.repository.VisaPathwayPlannerRepository
import com.sidekick.opt_pal.data.repository.VisaPathwayPlannerRepositoryImpl
import com.sidekick.opt_pal.feature.vault.ContentResolverFileNameResolver
import com.sidekick.opt_pal.feature.vault.FileNameResolver

object AppModule {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val securityManager: SecurityManager by lazy { SecurityManager(appContext) }
    val securitySessionManager: SecuritySessionManager by lazy { SecuritySessionManager(securityManager) }
    val uscisCaseNotificationManager: UscisCaseNotificationManager by lazy {
        UscisCaseNotificationManager(appContext)
    }
    val policyAlertNotificationManager: PolicyAlertNotificationManager by lazy {
        PolicyAlertNotificationManager(appContext)
    }
    val documentCryptoService: DocumentCryptoService by lazy { DocumentCryptoService() }
    val secureDocumentContentClient: SecureDocumentContentClient by lazy { SecureDocumentContentClient() }
    val fileNameResolver: FileNameResolver by lazy { ContentResolverFileNameResolver() }
    val unemploymentAlertStore: UnemploymentAlertStore by lazy { UnemploymentAlertStore(securityManager) }
    val unemploymentAlertScheduler: UnemploymentAlertScheduler by lazy {
        UnemploymentAlertScheduler(appContext, unemploymentAlertStore)
    }
    val complianceScoreSnapshotStore: ComplianceScoreSnapshotStore by lazy {
        ComplianceScoreSnapshotStore(securityManager)
    }

    val authRepository: AuthRepository by lazy { AuthRepositoryImpl() }
    val notificationDeviceRepository: NotificationDeviceRepository by lazy {
        NotificationDeviceRepositoryImpl(
            context = appContext,
            securityManager = securityManager
        )
    }
    val caseStatusRepository: CaseStatusRepository by lazy {
        CaseStatusRepositoryImpl(
            notificationDeviceRepository = notificationDeviceRepository
        )
    }
    val dashboardRepository: DashboardRepository by lazy { DashboardRepositoryImpl() }
    val reportingRepository: ReportingRepository by lazy { ReportingRepositoryImpl() }
    val travelAdvisorRepository: TravelAdvisorRepository by lazy { TravelAdvisorRepositoryImpl(appContext) }
    val i983AssistantRepository: I983AssistantRepository by lazy { I983AssistantRepositoryImpl(appContext) }
    val visaPathwayPlannerRepository: VisaPathwayPlannerRepository by lazy { VisaPathwayPlannerRepositoryImpl(appContext) }
    val h1bDashboardRepository: H1bDashboardRepository by lazy { H1bDashboardRepositoryImpl(appContext) }
    val scenarioSimulatorRepository: ScenarioSimulatorRepository by lazy { ScenarioSimulatorRepositoryImpl(appContext) }
    val policyAlertRepository: PolicyAlertRepository by lazy {
        PolicyAlertRepositoryImpl(
            notificationDeviceRepository = notificationDeviceRepository
        )
    }
    val complianceHealthRepository: ComplianceHealthRepository by lazy {
        ComplianceHealthRepositoryImpl(
            snapshotStore = complianceScoreSnapshotStore
        )
    }
    val ficaRefundRepository: FicaRefundRepository by lazy { FicaRefundRepositoryImpl() }
    val documentRepository: DocumentRepository by lazy {
        DocumentRepositoryImpl(
            documentCryptoService = documentCryptoService,
            secureDocumentContentClient = secureDocumentContentClient
        )
    }
    val secureDocumentIntakeUseCase: SecureDocumentIntakeUseCase by lazy {
        SecureDocumentIntakeUseCase(
            documentRepository = documentRepository,
            userSessionProvider = userSessionProvider,
            fileNameResolver = fileNameResolver
        )
    }
    val feedbackRepository: FeedbackRepository by lazy { FeedbackRepositoryImpl() }
    val userSessionProvider: UserSessionProvider by lazy { FirebaseUserSessionProvider }
    val unemploymentAlertCoordinator: UnemploymentAlertCoordinator by lazy {
        UnemploymentAlertCoordinator(
            authRepository = authRepository,
            dashboardRepository = dashboardRepository,
            userSessionProvider = userSessionProvider,
            scheduler = unemploymentAlertScheduler
        )
    }
}
