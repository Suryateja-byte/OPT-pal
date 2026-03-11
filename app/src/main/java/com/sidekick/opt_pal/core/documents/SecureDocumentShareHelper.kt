package com.sidekick.opt_pal.core.documents

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.repository.DocumentRepository
import java.io.File

class SecureDocumentShareHelper(
    private val context: Context,
    private val documentRepository: DocumentRepository
) {
    suspend fun share(document: DocumentMetadata): Result<Unit> {
        return documentRepository.getDocumentContent(document).mapCatching { content ->
            val extension = when {
                content.fileName.contains('.') -> content.fileName.substringAfterLast('.', "")
                content.contentType.contains("pdf", ignoreCase = true) -> "pdf"
                content.contentType.contains("png", ignoreCase = true) -> "png"
                else -> "jpg"
            }
            val directory = File(context.cacheDir, "secure-share").apply { mkdirs() }
            val target = File(directory, "${document.id}.${extension.ifBlank { "bin" }}")
            target.writeBytes(content.bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = content.contentType.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share document"))
        }
    }
}
