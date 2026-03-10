package com.sidekick.opt_pal.core.security

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SecureDocumentContentClient {

    suspend fun downloadDocument(documentId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val authToken = Firebase.auth.currentUser
                ?.getIdToken(false)
                ?.await()
                ?.token
                ?: error("User is not authenticated.")
            val appCheckToken = FirebaseAppCheck.getInstance()
                .getAppCheckToken(false)
                .await()
                .token
            val encodedDocumentId = URLEncoder.encode(documentId, StandardCharsets.UTF_8.name())
            val endpoint = buildString {
                append("https://us-central1-")
                append(FirebaseApp.getInstance().options.projectId)
                append(".cloudfunctions.net/documentContent?documentId=")
                append(encodedDocumentId)
            }

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("X-Firebase-AppCheck", appCheckToken)
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IOException(
                        if (errorBody.isNotBlank()) errorBody else "Secure document request failed with $responseCode."
                    )
                }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
