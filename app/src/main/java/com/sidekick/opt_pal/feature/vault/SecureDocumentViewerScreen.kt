package com.sidekick.opt_pal.feature.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.first
import java.io.File

private data class SecureDocumentViewerState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val contentType: String = "",
    val imageBytes: ByteArray? = null,
    val pdfFile: File? = null,
    val errorMessage: String? = null
)

@Composable
fun SecureDocumentViewerRoute(
    documentId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    documentRepository: DocumentRepository = AppModule.documentRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(SecureDocumentViewerState()) }

    DisposableEffect(lifecycleOwner, state.pdfFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                state.pdfFile?.delete()
                state = state.copy(pdfFile = null)
                reloadKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.pdfFile?.delete()
        }
    }

    LaunchedEffect(documentId, reloadKey) {
        state = state.copy(isLoading = true, errorMessage = null)
        val uid = AppModule.userSessionProvider.currentUserId
        if (uid.isNullOrBlank()) {
            state = SecureDocumentViewerState(isLoading = false, errorMessage = "You need to sign in again.")
            return@LaunchedEffect
        }

        val document = documentRepository.getDocuments(uid).first().firstOrNull { it.id == documentId }
        if (document == null) {
            state = SecureDocumentViewerState(isLoading = false, errorMessage = "Document not found.")
            return@LaunchedEffect
        }

        documentRepository.getDocumentContent(document)
            .onSuccess { content ->
                state.pdfFile?.delete()
                if (content.isPdf()) {
                    val tempFile = File.createTempFile("secure-view-", ".pdf", context.cacheDir)
                    tempFile.writeBytes(content.bytes)
                    state = SecureDocumentViewerState(
                        isLoading = false,
                        fileName = document.displayName(),
                        contentType = content.contentType,
                        pdfFile = tempFile
                    )
                } else {
                    state = SecureDocumentViewerState(
                        isLoading = false,
                        fileName = document.displayName(),
                        contentType = content.contentType,
                        imageBytes = content.bytes
                    )
                }
            }
            .onFailure { throwable ->
                state = SecureDocumentViewerState(
                    isLoading = false,
                    errorMessage = throwable.message ?: "Unable to open this document."
                )
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.shapes.small
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            state.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            state.pdfFile != null -> {
                PdfDocumentViewer(
                    file = state.pdfFile!!,
                    paddingValues = paddingValues
                )
            }

            state.imageBytes != null -> {
                val bitmap = remember(state.imageBytes) {
                    BitmapFactory.decodeByteArray(state.imageBytes, 0, state.imageBytes!!.size)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    item {
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    item {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = state.fileName,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfDocumentViewer(
    file: File,
    paddingValues: PaddingValues
) {
    val parcelFileDescriptor = remember(file.absolutePath) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    val renderer = remember(file.absolutePath) { PdfRenderer(parcelFileDescriptor) }
    val pageIndices = remember(renderer) { List(renderer.pageCount) { it } }

    DisposableEffect(renderer, parcelFileDescriptor) {
        onDispose {
            renderer.close()
            parcelFileDescriptor.close()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(pageIndices, key = { it }) { pageIndex ->
            val bitmap = rememberPdfBitmap(renderer, pageIndex)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Composable
private fun rememberPdfBitmap(renderer: PdfRenderer, pageIndex: Int): Bitmap {
    return remember(renderer, pageIndex) {
        renderer.openPage(pageIndex).use { page ->
            val width = page.width * 2
            val height = page.height * 2
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
}

private fun DocumentMetadata.displayName(): String = userTag.ifBlank { fileName }

private fun com.sidekick.opt_pal.data.model.SecureDocumentContent.isPdf(): Boolean {
    return contentType.equals("application/pdf", ignoreCase = true) ||
        fileName.endsWith(".pdf", ignoreCase = true)
}
