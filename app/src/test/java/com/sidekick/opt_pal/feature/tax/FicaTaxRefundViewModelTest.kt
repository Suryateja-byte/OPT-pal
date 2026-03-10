package com.sidekick.opt_pal.feature.tax

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.EmployerRefundOutcome
import com.sidekick.opt_pal.data.model.FicaEligibilityClassification
import com.sidekick.opt_pal.data.model.FicaEligibilityResult
import com.sidekick.opt_pal.data.model.FicaRefundCase
import com.sidekick.opt_pal.data.model.FicaRefundCaseStatus
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeDocumentRepository
import com.sidekick.opt_pal.testing.fakes.FakeFicaRefundRepository
import com.sidekick.opt_pal.testing.fakes.FakeUserSessionProvider
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import com.sidekick.opt_pal.feature.vault.FileNameResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FicaTaxRefundViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun startCaseFromSelectedW2CreatesRefundCase() = runTest {
        val authRepository = FakeAuthRepository()
        val documentRepository = FakeDocumentRepository()
        val ficaRefundRepository = FakeFicaRefundRepository()
        val viewModel = createViewModel(authRepository, documentRepository, ficaRefundRepository)
        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1", firstUsStudentTaxYear = 2022))
        documentRepository.setDocuments(listOf(sampleW2Document()))
        advanceUntilIdle()

        viewModel.onW2Selected("doc-1")
        viewModel.startCaseFromSelectedW2()
        advanceUntilIdle()

        assertEquals(1, ficaRefundRepository.createdCases.size)
        assertEquals(TaxRefundStep.ELIGIBILITY, viewModel.uiState.value.step)
    }

    @Test
    fun evaluateEligibilityAdvancesToEmployerStep() = runTest {
        val authRepository = FakeAuthRepository()
        val documentRepository = FakeDocumentRepository()
        val ficaRefundRepository = FakeFicaRefundRepository().apply {
            eligibilityResult = Result.success(
                FicaEligibilityResult(
                    classification = FicaEligibilityClassification.ELIGIBLE.wireValue,
                    refundAmount = 765.0
                )
            )
            setCases(
                listOf(
                    FicaRefundCase(
                        id = "case-1",
                        w2DocumentId = "doc-1",
                        taxYear = 2025,
                        employerName = "Acme Corp",
                        status = FicaRefundCaseStatus.INTAKE.wireValue
                    )
                )
            )
        }
        val viewModel = createViewModel(authRepository, documentRepository, ficaRefundRepository)
        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1", firstUsStudentTaxYear = 2022))
        documentRepository.setDocuments(listOf(sampleW2Document()))
        advanceUntilIdle()

        viewModel.selectExistingCase("case-1")
        viewModel.onFirstUsStudentTaxYearChanged("2022")
        viewModel.onAuthorizedEmploymentConfirmedChanged(true)
        viewModel.onMaintainedStudentStatusChanged(true)
        viewModel.onNoResidencyStatusChangeChanged(true)
        viewModel.evaluateEligibility()
        advanceUntilIdle()

        assertEquals(TaxRefundStep.EMPLOYER_REFUND, viewModel.uiState.value.step)
        assertEquals(2022, authRepository.getUserProfileSnapshot("user-1").getOrNull()?.firstUsStudentTaxYear)
        assertEquals("case-1", ficaRefundRepository.updatedInputs.single().first)
    }

    @Test
    fun refusedEmployerOutcomeUnlocksIrsStep() = runTest {
        val authRepository = FakeAuthRepository()
        val documentRepository = FakeDocumentRepository()
        val ficaRefundRepository = FakeFicaRefundRepository().apply {
            setCases(
                listOf(
                    FicaRefundCase(
                        id = "case-1",
                        w2DocumentId = "doc-1",
                        taxYear = 2025,
                        employerName = "Acme Corp",
                        status = FicaRefundCaseStatus.EMPLOYER_OUTREACH.wireValue
                    )
                )
            )
        }
        val viewModel = createViewModel(authRepository, documentRepository, ficaRefundRepository)
        authRepository.emitUser(mockUser("user-1"))
        documentRepository.setDocuments(listOf(sampleW2Document()))
        advanceUntilIdle()

        viewModel.selectExistingCase("case-1")
        viewModel.updateEmployerOutcome(EmployerRefundOutcome.REFUSED)
        advanceUntilIdle()

        assertEquals(TaxRefundStep.IRS_PACKET, viewModel.uiState.value.step)
        assertEquals(
            EmployerRefundOutcome.REFUSED,
            ficaRefundRepository.updatedOutcomes.single().second
        )
    }

    @Test
    fun missingIrsFieldsShowsValidationError() = runTest {
        val authRepository = FakeAuthRepository()
        val documentRepository = FakeDocumentRepository()
        val ficaRefundRepository = FakeFicaRefundRepository().apply {
            setCases(
                listOf(
                    FicaRefundCase(
                        id = "case-1",
                        w2DocumentId = "doc-1",
                        taxYear = 2025,
                        employerName = "Acme Corp",
                        status = FicaRefundCaseStatus.EMPLOYER_OUTREACH.wireValue,
                        employerOutcome = EmployerRefundOutcome.REFUSED.wireValue
                    )
                )
            )
        }
        val viewModel = createViewModel(authRepository, documentRepository, ficaRefundRepository)
        authRepository.emitUser(mockUser("user-1"))
        documentRepository.setDocuments(listOf(sampleW2Document()))
        advanceUntilIdle()

        viewModel.selectExistingCase("case-1")
        viewModel.generateIrsPacket()

        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("full SSN"))
    }

    private fun createViewModel(
        authRepository: FakeAuthRepository,
        documentRepository: FakeDocumentRepository,
        ficaRefundRepository: FakeFicaRefundRepository
    ): FicaTaxRefundViewModel {
        val intakeUseCase = SecureDocumentIntakeUseCase(
            documentRepository = documentRepository,
            userSessionProvider = FakeUserSessionProvider("user-1"),
            fileNameResolver = object : FileNameResolver {
                override fun resolve(uri: android.net.Uri, contentResolver: android.content.ContentResolver): String {
                    return "w2.pdf"
                }
            }
        )
        return FicaTaxRefundViewModel(
            authRepository = authRepository,
            documentRepository = documentRepository,
            ficaRefundRepository = ficaRefundRepository,
            secureDocumentIntakeUseCase = intakeUseCase
        )
    }

    private fun sampleW2Document(): DocumentMetadata {
        return DocumentMetadata(
            id = "doc-1",
            fileName = "w2.pdf",
            userTag = "2025 W-2",
            processingMode = DocumentProcessingMode.ANALYZE.wireValue,
            processingStatus = "processed",
            documentType = "W-2 Form",
            extractedData = mapOf(
                "tax_year" to 2025,
                "employer_name" to "Acme Corp",
                "employee_name" to "Student",
                "employee_ssn_last4" to "6789",
                "employer_ein_masked" to "XX-XXX6789",
                "social_security_tax_box4" to 620.0,
                "medicare_tax_box6" to 145.0,
                "document_type" to "w2"
            )
        )
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
