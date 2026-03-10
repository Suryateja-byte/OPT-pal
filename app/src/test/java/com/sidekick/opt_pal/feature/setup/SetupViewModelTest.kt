package com.sidekick.opt_pal.feature.setup

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.model.OnboardingSource
import com.sidekick.opt_pal.data.model.OptType
import com.sidekick.opt_pal.feature.vault.FileNameResolver
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeDocumentRepository
import com.sidekick.opt_pal.testing.fakes.FakeUserSessionProvider
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val documentRepository = FakeDocumentRepository()
    private val userProvider = FakeUserSessionProvider("user-1")
    private val contentResolver: ContentResolver = mockk(relaxed = true)

    @Test
    fun skipToManualSetupMovesToReview() = runTest {
        val viewModel = createViewModel()

        viewModel.skipToManualSetup()

        assertEquals(SetupStage.REVIEW, viewModel.uiState.value.stage)
        assertEquals(OnboardingSource.MANUAL, viewModel.uiState.value.draft.onboardingSource)
    }

    @Test
    fun analyzedUploadAutoAdvancesToReviewWhenDocumentFinishesProcessing() = runTest {
        val viewModel = createViewModel(FakeFileNameResolver("ead.pdf"))
        val uri = mockk<Uri>()
        mockMimeAndSize(uri, mimeType = "application/pdf", sizeBytes = 2_000L)
        advanceUntilIdle()

        viewModel.onUploadFileSelected(uri)
        viewModel.confirmDocumentUpload(
            tag = "EAD Card",
            consent = DocumentUploadConsent(DocumentProcessingMode.ANALYZE),
            contentResolver = contentResolver
        )
        advanceUntilIdle()

        documentRepository.setDocuments(
            listOf(
                processedDocument(
                    id = "ead-1",
                    fileName = "ead.pdf",
                    userTag = "EAD Card",
                    documentType = "EAD Card",
                    extractedData = mapOf(
                        "category" to "C03C",
                        "opt_start_date" to "2025-06-01",
                        "opt_end_date" to "2027-05-31"
                    )
                )
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SetupStage.REVIEW, state.stage)
        assertEquals(OnboardingSource.DOCUMENT_AI, state.draft.onboardingSource)
        assertEquals(OptType.STEM, state.draft.optType)
        assertEquals(listOf("ead-1"), state.draft.sourceDocumentIds)
    }

    @Test
    fun storageOnlyUploadFallsBackToManualReview() = runTest {
        val viewModel = createViewModel(FakeFileNameResolver("i20.pdf"))
        val uri = mockk<Uri>()
        mockMimeAndSize(uri, mimeType = "application/pdf", sizeBytes = 2_000L)
        advanceUntilIdle()

        viewModel.onUploadFileSelected(uri)
        viewModel.confirmDocumentUpload(
            tag = "I-20 Form",
            consent = DocumentUploadConsent(DocumentProcessingMode.STORAGE_ONLY),
            contentResolver = contentResolver
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SetupStage.REVIEW, state.stage)
        assertEquals(OnboardingSource.MANUAL, state.draft.onboardingSource)
        assertTrue(state.infoMessage.orEmpty().contains("stored securely"))
        assertEquals(DocumentProcessingMode.STORAGE_ONLY, documentRepository.uploadRequests.single().processingMode)
    }

    @Test
    fun saveSubmitsCompleteSetupRequest() = runTest {
        val viewModel = createViewModel()

        viewModel.skipToManualSetup()
        viewModel.onOptTypeSelected(OptType.INITIAL)
        viewModel.onShowDatePicker(SetupDateField.START)
        viewModel.onDateSelected(1_700_000_000_000L)
        viewModel.onSevisIdChanged("N1234567890")
        viewModel.onSchoolNameChanged("State University")
        viewModel.onCipCodeChanged("11.0701")
        viewModel.onSave()
        advanceUntilIdle()

        val request = authRepository.completedSetupRequests.single()
        assertEquals("initial", request.optType)
        assertEquals(1_700_000_000_000L, request.optStartDate)
        assertEquals("N1234567890", request.sevisId)
        assertEquals("State University", request.schoolName)
        assertEquals("11.0701", request.cipCode)
        assertEquals(OnboardingSource.MANUAL.wireValue, request.onboardingSource)
    }

    private fun createViewModel(fileNameResolver: FileNameResolver = FakeFileNameResolver()): SetupViewModel {
        return SetupViewModel(
            authRepository = authRepository,
            documentRepository = documentRepository,
            userSessionProvider = userProvider,
            secureDocumentIntakeUseCase = SecureDocumentIntakeUseCase(
                documentRepository = documentRepository,
                userSessionProvider = userProvider,
                fileNameResolver = fileNameResolver
            )
        )
    }

    private fun processedDocument(
        id: String,
        fileName: String,
        userTag: String,
        documentType: String,
        extractedData: Map<String, Any>
    ): DocumentMetadata {
        return DocumentMetadata(
            id = id,
            fileName = fileName,
            userTag = userTag,
            processingMode = DocumentProcessingMode.ANALYZE.wireValue,
            processingStatus = "processed",
            documentType = documentType,
            extractedData = extractedData,
            processedAt = 100L
        )
    }

    private fun mockMimeAndSize(uri: Uri, mimeType: String, sizeBytes: Long) {
        every { contentResolver.getType(uri) } returns mimeType
        val cursor = mockk<Cursor>()
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor.moveToFirst() } returns true
        every { cursor.getLong(0) } returns sizeBytes
        every { cursor.close() } returns Unit
        every { contentResolver.query(uri, any(), null, null, null) } returns cursor
    }

    private class FakeFileNameResolver(private val name: String = "test.pdf") : FileNameResolver {
        override fun resolve(uri: Uri, contentResolver: ContentResolver): String = name
    }
}
