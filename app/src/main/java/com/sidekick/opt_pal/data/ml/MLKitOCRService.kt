package com.sidekick.opt_pal.data.ml

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * On-Device OCR Service using Google ML Kit
 * Provides maximum privacy - text recognition happens locally
 */
class MLKitOCRService(private val context: Context) {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Extract text from an image file (document scan)
     * 
     * @param imageUri URI of the scanned document
     * @return Extracted text or null if OCR fails
     */
    suspend fun extractTextFromImage(imageUri: Uri): Result<String> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = textRecognizer.process(image).await()
            
            val extractedText = visionText.text
            
            if (extractedText.isNotBlank()) {
                Result.success(extractedText)
            } else {
                Result.failure(Exception("No text found in image"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Failed to load image: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("OCR failed: ${e.message}"))
        }
    }
    
    /**
     * Extract text from multiple images (multi-page documents)
     */
    suspend fun extractTextFromImages(imageUris: List<Uri>): Result<String> {
        return try {
            val allText = StringBuilder()
            
            imageUris.forEachIndexed { index, uri ->
                val result = extractTextFromImage(uri)
                if (result.isSuccess) {
                    allText.append("--- Page ${index + 1} ---\n")
                    allText.append(result.getOrNull())
                    allText.append("\n\n")
                } else {
                    return Result.failure(Exception("Failed to process page ${index + 1}"))
                }
            }
            
            Result.success(allText.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        textRecognizer.close()
    }
}
