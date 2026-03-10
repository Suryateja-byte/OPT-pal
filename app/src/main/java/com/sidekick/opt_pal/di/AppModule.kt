package com.sidekick.opt_pal.di

import android.content.Context
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.core.security.DocumentCryptoService
import com.sidekick.opt_pal.core.security.SecureDocumentContentClient
import com.sidekick.opt_pal.core.security.SecurityManager
import com.sidekick.opt_pal.core.security.SecuritySessionManager
import com.sidekick.opt_pal.core.session.FirebaseUserSessionProvider
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.AuthRepositoryImpl
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DashboardRepositoryImpl
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.DocumentRepositoryImpl
import com.sidekick.opt_pal.data.repository.FeedbackRepository
import com.sidekick.opt_pal.data.repository.FeedbackRepositoryImpl
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.data.repository.ReportingRepositoryImpl
import com.sidekick.opt_pal.feature.vault.ContentResolverFileNameResolver
import com.sidekick.opt_pal.feature.vault.FileNameResolver

object AppModule {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val securityManager: SecurityManager by lazy { SecurityManager(appContext) }
    val securitySessionManager: SecuritySessionManager by lazy { SecuritySessionManager(securityManager) }
    val documentCryptoService: DocumentCryptoService by lazy { DocumentCryptoService() }
    val secureDocumentContentClient: SecureDocumentContentClient by lazy { SecureDocumentContentClient() }
    val fileNameResolver: FileNameResolver by lazy { ContentResolverFileNameResolver() }

    val authRepository: AuthRepository by lazy { AuthRepositoryImpl() }
    val dashboardRepository: DashboardRepository by lazy { DashboardRepositoryImpl() }
    val reportingRepository: ReportingRepository by lazy { ReportingRepositoryImpl() }
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
}
