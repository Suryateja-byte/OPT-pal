package com.sidekick.opt_pal.feature.scan

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ReviewScanResultsScreen(
    uuid: String,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val user = Firebase.auth.currentUser
    
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var documentType by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    // Form states
    val formData = remember { mutableStateMapOf<String, String>() }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    LaunchedEffect(uuid) {
        if (user == null) return@LaunchedEffect
        
        val docRef = Firebase.firestore
            .collection("scan_results")
            .document(user.uid)
            .collection("results")
            .document(uuid)

        val listener = docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                error = e.message
                isLoading = false
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    val status = data["status"] as? String
                    if (status == "success") {
                        scanData = data
                        documentType = data["documentType"] as? String
                        
                        if (formData.isEmpty()) {
                            data.forEach { (k, v) ->
                                if (k !in listOf("status", "documentType", "timestamp") && v is String) {
                                    formData[k] = v
                                }
                            }
                        }
                        isLoading = false
                    } else if (status == "error") {
                        error = data["error"] as? String ?: "Unknown error"
                        isLoading = false
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Analyzing document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = error ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Main Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 48.dp)
                ) {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { 40 }
                    ) {
                        Column {
                            // 1. Editorial Header
                            Text(
                                text = "Review",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Results.",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = (documentType ?: "Document").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // 2. Dynamic Form Fields (Invisible Inputs)
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        formData.filter { (key, _) ->
                            key != "status" && key != "documentType" && key != "timestamp"
                        }.entries.sortedBy { it.key }.forEachIndexed { index, (key, value) ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(300, delayMillis = index * 50))
                            ) {
                                MinimalTextInput(
                                    value = value,
                                    onValueChange = { formData[key] = it },
                                    label = key.replace("_", " ").capitalize(),
                                    placeholder = "Enter value"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // 3. Save Button
                    Button(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                coroutineScope.launch {
                                    saveData(user?.uid, uuid, formData, scanData, documentType, {
                                        // success handling inside coroutine
                                    }, context)
                                    isSaving = false
                                    showSuccess = true
                                    delay(1500)
                                    onNavigateHome()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        enabled = !isSaving,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Confirm & Save", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            // 4. Immersive Success Overlay
            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Saved.",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            ),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            )
        )
    }
}

private suspend fun saveData(
    userId: String?,
    uuid: String,
    data: Map<String, String>,
    scanData: Map<String, Any>?,
    documentType: String?,
    onSuccess: () -> Unit,
    context: android.content.Context
) {
    if (userId == null) return

    try {
        val db = Firebase.firestore
        val batch = db.batch()

        val permRef = db.collection("users").document(userId).collection("documents").document()
        val storagePath = "temp_scans/$userId/$uuid.jpg"
        
        val metadata = DocumentMetadata(
            id = permRef.id,
            fileName = "$documentType.jpg",
            userTag = documentType ?: "Document",
            storagePath = storagePath,
            downloadUrl = "",
            uploadedAt = System.currentTimeMillis(),
            extractedData = scanData?.plus(data) ?: data
        )
        
        batch.set(permRef, metadata)

        val tempRef = db.collection("scan_results")
            .document(userId)
            .collection("results")
            .document(uuid)
            
        batch.delete(tempRef)

        batch.commit().await()
        onSuccess()
    } catch (e: Exception) {
        Log.e("ReviewScan", "Error saving data", e)
        Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
