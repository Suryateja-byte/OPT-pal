package com.sidekick.opt_pal.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.DocumentUploadConsent

@Composable
fun SecureUploadDialog(
    title: String,
    initialTag: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (tag: String, consent: DocumentUploadConsent) -> Unit
) {
    var tag by remember(initialTag) { mutableStateOf(initialTag) }
    var selectedMode by remember { mutableStateOf<DocumentProcessingMode?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose how this document should be handled before upload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MinimalDialogInput(
                    value = tag,
                    onValueChange = { tag = it },
                    placeholder = "Document name"
                )
                ProcessingChoiceRow(
                    title = "Upload securely only",
                    description = "Encrypted storage only. This document stays out of AI chat and extraction.",
                    selected = selectedMode == DocumentProcessingMode.STORAGE_ONLY,
                    onSelect = { selectedMode = DocumentProcessingMode.STORAGE_ONLY }
                )
                ProcessingChoiceRow(
                    title = "Upload and analyze",
                    description = "Encrypted upload plus OCR and Gemini extraction after consent is recorded.",
                    selected = selectedMode == DocumentProcessingMode.ANALYZE,
                    onSelect = { selectedMode = DocumentProcessingMode.ANALYZE }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mode = selectedMode ?: return@Button
                    onConfirm(tag.trim(), DocumentUploadConsent(processingMode = mode))
                },
                enabled = tag.isNotBlank() && selectedMode != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun ProcessingChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
